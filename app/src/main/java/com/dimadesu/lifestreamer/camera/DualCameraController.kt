package com.dimadesu.lifestreamer.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.thibaultbee.streampack.core.elements.sources.video.IVideoSource
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.ICameraSource
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.cameraManager
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.defaultCameraId
import io.github.thibaultbee.streampack.core.interfaces.IWithVideoSource
import io.github.thibaultbee.streampack.core.interfaces.setCameraId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Controller managing concurrent dual camera (PiP / Split-Screen) state and transitions.
 */
class DualCameraController {
    private val _concurrentCameraPairs = MutableLiveData<List<Pair<String, String>>>(emptyList())
    val concurrentCameraPairs: LiveData<List<Pair<String, String>>> = _concurrentCameraPairs

    private val _isActive = MutableLiveData(false)
    val isActive: LiveData<Boolean> = _isActive

    private val _compositionMode = MutableLiveData(CompositionMode.PIP_TOP_RIGHT)
    val compositionMode: LiveData<CompositionMode> = _compositionMode

    private var secondaryCameraErrorJob: Job? = null

    fun loadPairs(cameraManager: CameraManager?) {
        _concurrentCameraPairs.postValue(cameraManager?.concurrentCameraPairs.orEmpty())
    }

    fun onSourceChanged(
        videoSource: IVideoSource?,
        scope: CoroutineScope,
        onToast: (String) -> Unit
    ) {
        secondaryCameraErrorJob?.cancel()
        secondaryCameraErrorJob = null

        if (videoSource is ConcurrentDualCameraSource) {
            _isActive.postValue(true)
            _compositionMode.postValue(videoSource.compositionMode)
            secondaryCameraErrorJob = scope.launch {
                videoSource.secondaryCameraErrorFlow.filterNotNull().collect { error ->
                    Log.e(TAG, "Secondary camera failed to start", error)
                    onToast("PiP camera failed to start, using main camera only")
                }
            }
        } else {
            _isActive.postValue(false)
            _compositionMode.postValue(CompositionMode.PIP_TOP_RIGHT)
        }
    }

    fun setCompositionMode(streamer: IWithVideoSource?, mode: CompositionMode) {
        _compositionMode.postValue(mode)
        val videoSource = streamer?.videoInput?.sourceFlow?.value
        if (videoSource is ConcurrentDualCameraSource) {
            videoSource.compositionMode = mode
        }
    }

    @SuppressLint("MissingPermission")
    fun toggle(
        context: Context,
        streamer: IWithVideoSource?,
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
        videoSourceMutex: Mutex,
        onError: (String) -> Unit
    ) {
        if (streamer == null) {
            Log.e(TAG, "Streamer service not available for dual camera toggle")
            onError("Service not available")
            return
        }

        scope.launch(dispatcher) {
            videoSourceMutex.withLock {
                try {
                    val activeSource = streamer.videoInput.sourceFlow.value
                    if (activeSource is ConcurrentDualCameraSource) {
                        val currentMainCameraId = activeSource.primaryCameraId
                        _isActive.postValue(false)
                        _compositionMode.postValue(CompositionMode.PIP_TOP_RIGHT)
                        streamer.setCameraId(currentMainCameraId)
                        Log.i(TAG, "Reverted PiP off to single camera: $currentMainCameraId")
                    } else {
                        val activeCameraId = if (activeSource is ICameraSource) {
                            activeSource.cameraId
                        } else {
                            context.cameraManager.defaultCameraId
                        }

                        val pairs = context.cameraManager.concurrentCameraPairs
                        val matchedPair = pairs.firstOrNull {
                            it.first == activeCameraId || it.second == activeCameraId
                        }
                        if (matchedPair == null) {
                            Log.w(TAG, "Concurrent camera streaming not supported for camera $activeCameraId")
                            onError("PIP not supported for the selected camera")
                            return@withLock
                        }
                        val primaryId = activeCameraId
                        val secondaryId =
                            if (matchedPair.first == activeCameraId) matchedPair.second else matchedPair.first

                        val mode = CompositionMode.PIP_TOP_RIGHT
                        _compositionMode.postValue(mode)
                        streamer.setConcurrentDualCamera(
                            primaryCameraId = primaryId,
                            secondaryCameraId = secondaryId,
                            compositionMode = mode
                        )
                        _isActive.postValue(true)
                        Log.i(TAG, "Activated PiP dual camera: Main=$primaryId, PiP=$secondaryId ($mode)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to toggle concurrent dual camera", e)
                    onError("Dual camera error: ${e.message}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "DualCameraController"
    }
}

