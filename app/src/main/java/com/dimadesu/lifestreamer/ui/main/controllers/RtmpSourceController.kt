package com.dimadesu.lifestreamer.ui.main.controllers

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.dimadesu.lifestreamer.data.storage.DataStoreRepository
import com.dimadesu.lifestreamer.models.StreamOrientation
import com.dimadesu.lifestreamer.rtmp.audio.MediaProjectionAudioSourceFactory
import com.dimadesu.lifestreamer.services.CameraStreamerService
import com.dimadesu.lifestreamer.ui.main.RtmpSourceSwitchHelper
import io.github.thibaultbee.streampack.core.elements.sources.video.bitmap.BitmapSourceFactory
import io.github.thibaultbee.streampack.core.elements.sources.video.bitmap.IBitmapSource
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSourceFactory
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.ICameraSource
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Controller responsible for RTMP video/audio source switching, ExoPlayer lifecycle,
 * disconnection monitoring, automatic reconnect/retry loop, and stream restart fallback.
 */
class RtmpSourceController(
    private val application: Application,
    private val scope: CoroutineScope,
    private val storageRepository: DataStoreRepository,
    private val mediaProjectionTokenStore: MediaProjectionTokenStore,
    private val cameraControlsController: CameraControlsController,
    private val streamerProvider: () -> SingleStreamer?,
    private val serviceProvider: () -> CameraStreamerService?,
    private val serviceBinderProvider: () -> CameraStreamerService.CameraStreamerServiceBinder?,
    private val testBitmapProvider: () -> Bitmap,
    private val lastUsedCameraIdProvider: () -> String?,
    private val onLastUsedCameraIdChanged: (String?) -> Unit,
    private val defaultCameraIdProvider: () -> String,
    private val streamOrientationProvider: () -> StreamOrientation,
    private val isUseBluetoothMicProvider: () -> Boolean,
    private val isUseSystemAudioForCameraProvider: () -> Boolean,
    private val isMonitorAudioOnProvider: () -> Boolean,
    private val onShowBluetoothToggleChanged: (Boolean) -> Unit,
    private val onSuppressPassthroughObserverChanged: (Boolean) -> Unit,
    private val onRtmpActiveChanged: (Int?) -> Unit,
    private val onBeforeSourceSwitch: suspend () -> Unit,
    private val onAfterSourceSwitch: suspend () -> Unit,
    private val onAudioRoutingUpdateRequired: suspend () -> Unit,
    private val onStreamReconnectRequired: (String) -> Unit,
    private val onTriggerBluetoothMicActivation: () -> Unit,
    private val onSetupAudioLevelMonitoring: () -> Unit,
    private val onStopAudioPassthrough: () -> Unit,
    private val onApplyMonitorAudioState: () -> Unit,
    private val onToastMessage: (String) -> Unit,
    private val onErrorMessage: (String) -> Unit
) {
    companion object {
        private const val TAG = "RtmpSourceCtrl"
    }

    internal val _activeRtmpIndex = MutableLiveData<Int?>(null)
    val activeRtmpIndex: LiveData<Int?> get() = _activeRtmpIndex
    val isRtmpSource: LiveData<Boolean> = _activeRtmpIndex.map { it != null }

    internal val _rtmpStatusLiveData = MutableLiveData<String?>()
    val rtmpStatusLiveData: LiveData<String?> get() = _rtmpStatusLiveData

    var currentRtmpPlayer: ExoPlayer? = null
        private set

    private var rtmpDisconnectListener: Player.Listener? = null
    private var rtmpRetryJob: Job? = null
    private var bufferingCheckJob: Job? = null
    private var rtmpBufferingStartTime = 0L
    private var isHandlingDisconnection = false

    fun toggleVideoSource(
        mediaProjectionLauncher: ActivityResultLauncher<Intent>? = null,
        rtmpIndex: Int = 1
    ) {
        val currentStreamer = streamerProvider()
        if (currentStreamer == null) {
            Log.e(TAG, "Streamer service not available for video source toggle")
            onErrorMessage("Service not available")
            return
        }

        val videoSource = currentStreamer.videoInput?.sourceFlow?.value
        val isCurrentlyStreaming = currentStreamer.isStreamingFlow.value == true
        val currentActiveIndex = _activeRtmpIndex.value

        scope.launch {
            val rtmpUrl = try {
                withContext(Dispatchers.IO) {
                    storageRepository.rtmpSourceUrlFlow(rtmpIndex).first()
                }
            } catch (e: Exception) {
                storageRepository.defaultRtmpSourceUrl(rtmpIndex)
            }

            when {
                currentActiveIndex != rtmpIndex -> {
                    Log.i(TAG, "Switching to RTMP source $rtmpIndex: $rtmpUrl (streaming: $isCurrentlyStreaming)")

                    val needProjection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                            && mediaProjectionTokenStore.startupMediaProjection == null
                            && mediaProjectionTokenStore.streamingMediaProjection == null
                            && mediaProjectionTokenStore.helper.getMediaProjection() == null

                    val doRtmpSwitch: suspend () -> Unit = {
                        _activeRtmpIndex.postValue(rtmpIndex)
                        onRtmpActiveChanged(rtmpIndex)

                        onSuppressPassthroughObserverChanged(true)

                        if (isMonitorAudioOnProvider()) {
                            onStopAudioPassthrough()
                            Log.i(TAG, "Stopped mic passthrough when switching to RTMP (will use ExoPlayer volume instead)")
                        }

                        if (videoSource is ICameraSource) {
                            onLastUsedCameraIdChanged(videoSource.cameraId)
                            Log.d(TAG, "Saved camera ID for later: ${videoSource.cameraId}")
                        }

                        cameraControlsController.hideSliders()
                        onShowBluetoothToggleChanged(false)

                        onBeforeSourceSwitch()

                        rtmpRetryJob?.cancel()
                        rtmpRetryJob = RtmpSourceSwitchHelper.switchToRtmpSource(
                            application = application,
                            currentStreamer = currentStreamer,
                            testBitmap = testBitmapProvider(),
                            videoSourceUrl = rtmpUrl,
                            storageRepository = storageRepository,
                            mediaProjectionHelper = mediaProjectionTokenStore.helper,
                            streamingMediaProjection = mediaProjectionTokenStore.streamingMediaProjection,
                            postError = { msg -> onErrorMessage(msg) },
                            postRtmpStatus = { msg -> _rtmpStatusLiveData.postValue(msg) },
                            onRtmpConnected = { player ->
                                monitorRtmpConnection(player)
                                scope.launch {
                                    onAfterSourceSwitch()
                                }
                            }
                        )

                        try { serviceBinderProvider()?.refreshNotification() } catch (_: Throwable) {}
                    }

                    if (needProjection) {
                        if (mediaProjectionLauncher != null) {
                            Log.i(TAG, "Requesting MediaProjection permission for RTMP source switch")
                            mediaProjectionTokenStore.requestProjection(mediaProjectionLauncher) { projection ->
                                Log.i(TAG, "MediaProjection callback received during RTMP switch: ${projection != null}")
                                scope.launch {
                                    if (projection == null) {
                                        onErrorMessage("MediaProjection permission denied - staying on camera source")
                                        return@launch
                                    }
                                    if (isCurrentlyStreaming) {
                                        mediaProjectionTokenStore.streamingMediaProjection = projection
                                    }
                                    doRtmpSwitch()
                                }
                            }
                            return@launch
                        } else {
                            Log.w(TAG, "MediaProjection required but no launcher available to request it")
                            onErrorMessage("MediaProjection permission required to use RTMP audio")
                            return@launch
                        }
                    } else {
                        doRtmpSwitch()
                    }
                }
                else -> {
                    Log.i(TAG, "Switching from RTMP $rtmpIndex back to Camera source (streaming: $isCurrentlyStreaming)")

                    _activeRtmpIndex.postValue(null)
                    onRtmpActiveChanged(null)
                    onShowBluetoothToggleChanged(true)
                    onSuppressPassthroughObserverChanged(false)

                    _rtmpStatusLiveData.value = null

                    rtmpRetryJob?.cancel()
                    rtmpRetryJob = null
                    isHandlingDisconnection = false
                    Log.i(TAG, "Cancelled RTMP retry loop and reset disconnection flag")

                    bufferingCheckJob?.cancel()
                    bufferingCheckJob = null
                    rtmpBufferingStartTime = 0L

                    rtmpDisconnectListener?.let { listener ->
                        try {
                            currentRtmpPlayer?.removeListener(listener)
                            Log.i(TAG, "Removed RTMP disconnect listener")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to remove RTMP listener: ${e.message}")
                        }
                    }
                    rtmpDisconnectListener = null
                    currentRtmpPlayer = null

                    if (mediaProjectionTokenStore.streamingMediaProjection == null &&
                        mediaProjectionTokenStore.startupMediaProjection == null) {
                        mediaProjectionTokenStore.helper.release()
                    }

                    delay(300)
                    onBeforeSourceSwitch()

                    val cameraId = lastUsedCameraIdProvider()
                    if (cameraId != null) {
                        currentStreamer.setVideoSource(CameraSourceFactory(cameraId))
                        Log.i(TAG, "Switched back to camera video (restored camera: $cameraId) with BT-aware audio")
                    } else {
                        currentStreamer.setVideoSource(CameraSourceFactory(defaultCameraIdProvider()))
                        Log.i(TAG, "Switched to camera video (default camera) with BT-aware audio")
                    }
                    onAudioRoutingUpdateRequired()

                    if (isUseBluetoothMicProvider() && !isUseSystemAudioForCameraProvider()) {
                        onTriggerBluetoothMicActivation()
                    }

                    onSetupAudioLevelMonitoring()
                    onAfterSourceSwitch()

                    if (isMonitorAudioOnProvider()) {
                        onApplyMonitorAudioState()
                    }
                }
            }
            Log.i(TAG, "Switch video source completed")
        }
    }

    fun monitorRtmpConnection(player: ExoPlayer) {
        rtmpDisconnectListener?.let { listener ->
            try { currentRtmpPlayer?.removeListener(listener) } catch (_: Exception) {}
        }

        bufferingCheckJob?.cancel()
        bufferingCheckJob = null
        rtmpBufferingStartTime = 0L

        currentRtmpPlayer = player
        onSuppressPassthroughObserverChanged(false)

        if (isMonitorAudioOnProvider()) {
            onApplyMonitorAudioState()
        }

        val maxBufferingDuration = 2_000L

        rtmpDisconnectListener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "RTMP stream error detected: ${error.message}", error)
                bufferingCheckJob?.cancel()
                handleRtmpDisconnection()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, "RTMP playback state changed to: $playbackState")
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        Log.w(TAG, "RTMP stream ended")
                        bufferingCheckJob?.cancel()
                        handleRtmpDisconnection()
                    }
                    Player.STATE_IDLE -> {
                        Log.w(TAG, "RTMP stream went idle (unexpected stop)")
                        bufferingCheckJob?.cancel()
                        handleRtmpDisconnection()
                    }
                    Player.STATE_BUFFERING -> {
                        if (rtmpBufferingStartTime == 0L) {
                            rtmpBufferingStartTime = System.currentTimeMillis()
                            Log.d(TAG, "RTMP stream started buffering")

                            bufferingCheckJob = scope.launch {
                                delay(maxBufferingDuration)
                                if (rtmpBufferingStartTime > 0 && player.playbackState == Player.STATE_BUFFERING) {
                                    Log.w(TAG, "RTMP stream buffering for too long (${maxBufferingDuration}ms) - treating as disconnection")
                                    handleRtmpDisconnection()
                                }
                            }
                        }
                    }
                    Player.STATE_READY -> {
                        if (rtmpBufferingStartTime > 0L) {
                            val bufferingDuration = System.currentTimeMillis() - rtmpBufferingStartTime
                            Log.d(TAG, "RTMP stream recovered from buffering after ${bufferingDuration}ms")
                            rtmpBufferingStartTime = 0L
                            bufferingCheckJob?.cancel()
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "RTMP isPlaying changed to: $isPlaying, state=${player.playbackState}")
                if (!isPlaying && player.playbackState == Player.STATE_READY) {
                    Log.d(TAG, "RTMP stream not playing despite being ready - monitoring")
                }
            }
        }

        player.addListener(rtmpDisconnectListener!!)
        Log.i(TAG, "Started monitoring RTMP connection for disconnections (including buffering stalls)")
    }

    fun handleRtmpDisconnection() {
        val currentStreamer = streamerProvider() ?: return

        if (isHandlingDisconnection) {
            Log.d(TAG, "Already handling RTMP disconnection, ignoring duplicate call")
            return
        }

        val isStreaming = currentStreamer.isStreamingFlow.value == true
        isHandlingDisconnection = true

        scope.launch {
            try {
                if (!isStreaming) {
                    handleRtmpDisconnectionWithHotSwap(currentStreamer, isStreaming = false)
                } else {
                    val shouldRestartStream = storageRepository.rtmpSourceRestartOnDisconnectFlow.first()
                    if (shouldRestartStream) {
                        handleRtmpDisconnectionWithRestart(currentStreamer)
                    } else {
                        handleRtmpDisconnectionWithHotSwap(currentStreamer, isStreaming = true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling RTMP disconnection: ${e.message}", e)
                isHandlingDisconnection = false
            }
        }
    }

    private suspend fun handleRtmpDisconnectionWithRestart(currentStreamer: SingleStreamer) {
        Log.i(TAG, "Handling RTMP disconnection - stopping stream, switching to bitmap, restarting")

        val service = serviceProvider()
        val reconnecting = service?.beginReconnection("RTMP stream playback stopped. Restarting stream") ?: false
        if (reconnecting) {
            service?.setReconnectionMessage("RTMP stream playback stopped. Restarting stream")
            Log.i(TAG, "Set reconnecting state to preserve orientation during RTMP restart")
        } else {
            Log.w(TAG, "Could not set reconnecting state - orientation may unlock during restart")
        }

        rtmpRetryJob?.cancelAndJoin()
        rtmpRetryJob = null

        bufferingCheckJob?.cancel()
        bufferingCheckJob = null
        rtmpBufferingStartTime = 0L

        rtmpDisconnectListener?.let { listener ->
            try { currentRtmpPlayer?.removeListener(listener) } catch (_: Exception) {}
        }
        rtmpDisconnectListener = null

        currentRtmpPlayer?.let { player ->
            try {
                Log.i(TAG, "Releasing old RTMP ExoPlayer")
                player.stop()
                player.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing old ExoPlayer: ${e.message}")
            }
        }
        currentRtmpPlayer = null

        Log.i(TAG, "Stopping SRT stream...")
        try {
            currentStreamer.stopStream()
            currentStreamer.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping stream: ${e.message}")
        }

        delay(500)

        Log.i(TAG, "Switching to bitmap source with MediaProjection audio...")
        currentStreamer.setVideoSource(BitmapSourceFactory(testBitmapProvider()))

        val projection = mediaProjectionTokenStore.currentProjection
        if (projection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                currentStreamer.setAudioSource(MediaProjectionAudioSourceFactory(projection))
                Log.i(TAG, "Kept MediaProjection audio source")
            } catch (e: Exception) {
                Log.w(TAG, "MediaProjection audio failed, falling back to mic: ${e.message}")
                currentStreamer.setAudioSource(com.dimadesu.lifestreamer.audio.ConditionalAudioSourceFactory())
            }
        } else {
            currentStreamer.setAudioSource(com.dimadesu.lifestreamer.audio.ConditionalAudioSourceFactory())
            Log.i(TAG, "No MediaProjection available, using mic")
        }

        val fixedRotation = streamOrientationProvider().toSurfaceRotation()
        val rotationToApply = if (fixedRotation != null) {
            Log.i(TAG, "Applying fixed orientation setting $fixedRotation before RTMP restart")
            fixedRotation
        } else {
            val savedRotation = service?.getSavedStreamingOrientation()
            if (savedRotation != null) {
                Log.i(TAG, "Applying saved rotation $savedRotation before RTMP restart")
                savedRotation
            } else {
                val windowManager = application.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.rotation
            }
        }

        try {
            currentStreamer.setTargetRotation(rotationToApply)
            service?.lockStreamRotation(rotationToApply)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply rotation before RTMP restart: ${e.message}")
        }

        Log.i(TAG, "Restarting SRT stream...")
        try {
            val descriptor = storageRepository.endpointDescriptorFlow.first()
            currentStreamer.open(descriptor)
            currentStreamer.startStream()

            if (reconnecting) {
                service?.completeReconnection()
                Log.i(TAG, "Cleared reconnecting state after successful RTMP restart")
            }

            Log.i(TAG, "Stream restarted with bitmap source (orientation preserved)")
            _rtmpStatusLiveData.postValue("RTMP disconnected - stream restarted with bitmap")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart stream after RTMP disconnect: ${e.message}", e)
            if (reconnecting) {
                service?.cancelReconnection()
            }
            isHandlingDisconnection = false
            onStreamReconnectRequired("RTMP restart failed: ${e.message}")
            return
        }

        val activeIndexRestart = _activeRtmpIndex.value ?: 1
        val rtmpUrlRestart = storageRepository.rtmpSourceUrlFlow(activeIndexRestart).first()
        rtmpRetryJob = RtmpSourceSwitchHelper.switchToRtmpSource(
            application = application,
            currentStreamer = currentStreamer,
            testBitmap = testBitmapProvider(),
            videoSourceUrl = rtmpUrlRestart,
            storageRepository = storageRepository,
            mediaProjectionHelper = mediaProjectionTokenStore.helper,
            streamingMediaProjection = mediaProjectionTokenStore.streamingMediaProjection,
            postError = { msg -> onErrorMessage(msg) },
            postRtmpStatus = { msg -> _rtmpStatusLiveData.postValue(msg) },
            onRtmpConnected = { player ->
                monitorRtmpConnection(player)
                isHandlingDisconnection = false
                scope.launch {
                    onAfterSourceSwitch()
                }
            }
        )
    }

    private suspend fun handleRtmpDisconnectionWithHotSwap(currentStreamer: SingleStreamer, isStreaming: Boolean) {
        Log.i(TAG, "Handling RTMP disconnection - falling back to bitmap and retrying (hot-swap, streaming=$isStreaming)")

        rtmpRetryJob?.cancelAndJoin()
        rtmpRetryJob = null

        bufferingCheckJob?.cancel()
        bufferingCheckJob = null
        rtmpBufferingStartTime = 0L

        rtmpDisconnectListener?.let { listener ->
            try { currentRtmpPlayer?.removeListener(listener) } catch (_: Exception) {}
        }
        rtmpDisconnectListener = null

        if (isStreaming) {
            onBeforeSourceSwitch()
        }

        currentStreamer.setVideoSource(BitmapSourceFactory(testBitmapProvider()))
        Log.i(TAG, "Switched to bitmap fallback (video only, keeping current audio source)")

        if (isStreaming) {
            onAfterSourceSwitch()
        }

        delay(100)

        currentRtmpPlayer?.let { player ->
            try {
                Log.i(TAG, "Releasing old RTMP ExoPlayer to prevent audio echo")
                player.stop()
                player.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing old ExoPlayer: ${e.message}")
            }
        }
        currentRtmpPlayer = null

        val activeIndexHotSwap = _activeRtmpIndex.value ?: 1
        val rtmpUrlHotSwap = storageRepository.rtmpSourceUrlFlow(activeIndexHotSwap).first()
        rtmpRetryJob = RtmpSourceSwitchHelper.switchToRtmpSource(
            application = application,
            currentStreamer = currentStreamer,
            testBitmap = testBitmapProvider(),
            videoSourceUrl = rtmpUrlHotSwap,
            storageRepository = storageRepository,
            mediaProjectionHelper = mediaProjectionTokenStore.helper,
            streamingMediaProjection = mediaProjectionTokenStore.streamingMediaProjection,
            postError = { msg -> onErrorMessage(msg) },
            postRtmpStatus = { msg -> _rtmpStatusLiveData.postValue(msg) },
            onRtmpConnected = { player ->
                monitorRtmpConnection(player)
                isHandlingDisconnection = false
                if (isStreaming) {
                    scope.launch {
                        onAfterSourceSwitch()
                    }
                }
            }
        )
    }

    fun restartRtmpRetryIfNeeded(currentStreamer: SingleStreamer) {
        val videoSource = currentStreamer.videoInput?.sourceFlow?.value
        val activeIndex = _activeRtmpIndex.value
        if (videoSource is IBitmapSource && activeIndex != null && rtmpRetryJob?.isActive != true) {
            scope.launch {
                val rtmpUrl = storageRepository.rtmpSourceUrlFlow(activeIndex).first()
                rtmpRetryJob = RtmpSourceSwitchHelper.switchToRtmpSource(
                    application = application,
                    currentStreamer = currentStreamer,
                    testBitmap = testBitmapProvider(),
                    videoSourceUrl = rtmpUrl,
                    storageRepository = storageRepository,
                    mediaProjectionHelper = mediaProjectionTokenStore.helper,
                    streamingMediaProjection = mediaProjectionTokenStore.streamingMediaProjection,
                    postError = { msg -> onErrorMessage(msg) },
                    postRtmpStatus = { msg -> _rtmpStatusLiveData.postValue(msg) },
                    onRtmpConnected = { player ->
                        monitorRtmpConnection(player)
                        scope.launch {
                            onAfterSourceSwitch()
                        }
                    }
                )
            }
        }
    }

    fun cancelRetryJob() {
        rtmpRetryJob?.cancel()
        rtmpRetryJob = null
    }

    fun release() {
        rtmpRetryJob?.cancel()
        rtmpRetryJob = null
        bufferingCheckJob?.cancel()
        bufferingCheckJob = null
        rtmpDisconnectListener?.let { listener ->
            try { currentRtmpPlayer?.removeListener(listener) } catch (_: Exception) {}
        }
        rtmpDisconnectListener = null
        currentRtmpPlayer?.let { player ->
            try {
                player.stop()
                player.release()
                Log.i(TAG, "Released RTMP ExoPlayer in release()")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release RTMP ExoPlayer: ${e.message}")
            }
        }
        currentRtmpPlayer = null
    }
}
