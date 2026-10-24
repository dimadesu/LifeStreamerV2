package com.dimadesu.lifestreamer.ui.main.controllers

import android.app.Application
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.thibaultbee.streampack.core.elements.sources.video.mediaprojection.MediaProjectionVideoSourceFactory
import com.dimadesu.lifestreamer.services.CameraStreamerService
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSourceFactory
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.ICameraSource
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages Screen source toggling, MediaProjection virtual display setup,
 * and switching between screen and camera sources.
 */
class ScreenSourceController(
    private val application: Application,
    private val scope: CoroutineScope,
    private val streamerProvider: () -> SingleStreamer?,
    private val serviceBinderProvider: () -> CameraStreamerService.CameraStreamerServiceBinder?,
    private val mediaProjectionTokenStore: MediaProjectionTokenStore,
    private val cameraControlsController: CameraControlsController,
    private val fpsProvider: () -> Int,
    private val lastUsedCameraIdProvider: () -> String?,
    private val onLastUsedCameraIdChanged: (String?) -> Unit,
    private val onScreenSourceActiveChanged: (Boolean) -> Unit,
    private val onBeforeSourceSwitch: suspend () -> Unit,
    private val onAfterSourceSwitch: suspend () -> Unit,
    private val onAudioRoutingUpdateRequired: suspend () -> Unit,
    private val onErrorMessage: (String) -> Unit
) {
    companion object {
        private const val TAG = "ScreenSourceCtrl"
    }

    internal val _isScreenSource = MutableLiveData<Boolean>(false)
    val isScreenSource: LiveData<Boolean> get() = _isScreenSource

    fun toggleScreenSource(mediaProjectionLauncher: ActivityResultLauncher<Intent>? = null) {
        val currentStreamer = streamerProvider()
        if (currentStreamer == null) {
            onErrorMessage("Service not available")
            return
        }

        val isCurrentlyStreaming = currentStreamer.isStreamingFlow.value == true

        scope.launch {
            if (_isScreenSource.value != true) {
                // Switching to Screen Source
                Log.i(TAG, "Switching to Screen source")

                val cachedProjection = mediaProjectionTokenStore.currentProjection
                val needProjection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        && (cachedProjection == null || MediaProjectionVideoSourceFactory.isProjectionExhaustedForVideo(cachedProjection))

                val doScreenSwitch: suspend (MediaProjection?) -> Unit = { proj ->
                    _isScreenSource.postValue(true)
                    onScreenSourceActiveChanged(true)
                    cameraControlsController.hideSliders()

                    if (proj != null) {
                        switchToMediaProjectionVideoSource(currentStreamer, proj)
                    } else {
                        onErrorMessage("Failed to get MediaProjection")
                    }
                    onAudioRoutingUpdateRequired()
                    try { serviceBinderProvider()?.refreshNotification() } catch (_: Throwable) {}
                }

                if (needProjection) {
                    if (mediaProjectionLauncher != null) {
                        Log.i(TAG, "Requesting MediaProjection for Screen Recording")
                        mediaProjectionTokenStore.requestProjection(mediaProjectionLauncher) { projection ->
                            scope.launch {
                                if (projection == null) {
                                    onErrorMessage("MediaProjection permission denied - staying on camera source")
                                    return@launch
                                }
                                if (isCurrentlyStreaming) mediaProjectionTokenStore.streamingMediaProjection = projection

                                doScreenSwitch(projection)
                            }
                        }
                    } else {
                        onErrorMessage("MediaProjection permission required")
                    }
                } else {
                    doScreenSwitch(cachedProjection)
                }
            } else {
                // Switching off Screen Source
                Log.i(TAG, "Switching off Screen source")
                _isScreenSource.postValue(false)
                onScreenSourceActiveChanged(false)

                if (mediaProjectionTokenStore.streamingMediaProjection == null && mediaProjectionTokenStore.startupMediaProjection == null) {
                    mediaProjectionTokenStore.helper.release()
                }

                delay(300)
                onBeforeSourceSwitch()

                val cameraId = lastUsedCameraIdProvider()
                if (cameraId != null) {
                    currentStreamer.setVideoSource(CameraSourceFactory(cameraId))
                    Log.i(TAG, "Switched back to camera video (restored camera: $cameraId)")
                } else {
                    currentStreamer.setVideoSource(CameraSourceFactory(application))
                    Log.i(TAG, "Switched to camera video (default camera)")
                }

                onAfterSourceSwitch()
            }
        }
    }

    suspend fun switchToMediaProjectionVideoSource(currentStreamer: SingleStreamer, projection: MediaProjection) {
        onBeforeSourceSwitch()
        val videoSource = currentStreamer.videoInput?.sourceFlow?.value
        if (videoSource is ICameraSource) {
            onLastUsedCameraIdChanged(videoSource.cameraId)
        }

        val fps = fpsProvider()
        Log.i(TAG, "switchToMediaProjectionVideoSource: Switching to CFR MediaProjection source with fps=$fps")
        currentStreamer.setVideoSource(MediaProjectionVideoSourceFactory(projection, fps))

        onAfterSourceSwitch()
    }
}
