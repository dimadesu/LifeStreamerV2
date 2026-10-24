/*
 * Copyright (C) 2021 Thibault B.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dimadesu.lifestreamer.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CaptureResult
import android.media.projection.MediaProjection
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.Range
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.databinding.Bindable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.dimadesu.lifestreamer.BR
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.data.rotation.RotationRepository
import com.dimadesu.lifestreamer.data.storage.DataStoreRepository
import com.dimadesu.lifestreamer.ui.main.usecases.BuildStreamerUseCase
import com.dimadesu.lifestreamer.rtmp.audio.MediaProjectionHelper
import com.dimadesu.lifestreamer.rtmp.video.RTMPVideoSource
import com.dimadesu.lifestreamer.uvc.UvcVideoSource
import com.dimadesu.lifestreamer.audio.ConditionalAudioSourceFactory
import io.github.thibaultbee.streampack.core.elements.sources.IMediaProjectionSource
import io.github.thibaultbee.streampack.core.elements.sources.video.mediaprojection.MediaProjectionVideoSourceFactory
import com.dimadesu.lifestreamer.utils.ObservableViewModel
import com.dimadesu.lifestreamer.utils.dataStore
import com.dimadesu.lifestreamer.utils.isEmpty
import com.dimadesu.lifestreamer.utils.setNextCameraId
import com.dimadesu.lifestreamer.utils.switchBackToFront
import com.dimadesu.lifestreamer.utils.ReconnectTimer
import com.dimadesu.lifestreamer.utils.StreamConfigurationHelper
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.cameras
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.cameraManager
import io.github.thibaultbee.streampack.core.configuration.mediadescriptor.UriMediaDescriptor
import io.github.thibaultbee.streampack.core.elements.endpoints.MediaSinkType
import io.github.thibaultbee.streampack.core.elements.sources.audio.audiorecord.IAudioRecordSource
import io.github.thibaultbee.streampack.core.elements.sources.audio.audiorecord.MicrophoneSourceFactory
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSourceFactory
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.isFpsSupported
import io.github.thibaultbee.streampack.core.interfaces.IWithVideoSource
import com.dimadesu.lifestreamer.rtmp.audio.MediaProjectionAudioSourceFactory
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.withAudio
import io.github.thibaultbee.streampack.core.streamers.single.withVideo
import io.github.thibaultbee.streampack.core.utils.extensions.isClosedException
import io.github.thibaultbee.streampack.core.elements.sources.audio.IAudioSourceInternal
import io.github.thibaultbee.streampack.core.elements.sources.video.IVideoSourceInternal
import io.github.thibaultbee.streampack.core.configuration.mediadescriptor.MediaDescriptor
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSettings
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.ICameraSource
import io.github.thibaultbee.streampack.core.elements.sources.video.bitmap.IBitmapSource
import io.github.thibaultbee.streampack.core.elements.sources.video.bitmap.BitmapSourceFactory
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig
import com.dimadesu.lifestreamer.services.CameraStreamerService
import io.github.thibaultbee.streampack.core.interfaces.IWithVideoRotation
import com.dimadesu.lifestreamer.models.StreamStatus
import com.dimadesu.lifestreamer.srtla.SrtlaManager
import com.serenegiant.utils.UVCUtils.getApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)


class PreviewViewModel(private val application: Application) : ObservableViewModel() {
    private val storageRepository = DataStoreRepository(application, application.dataStore)
    private val streamConfigurationHelper = StreamConfigurationHelper(storageRepository)
    private val rotationRepository = RotationRepository.getInstance(application)
    val mediaProjectionTokenStore = com.dimadesu.lifestreamer.ui.main.controllers.MediaProjectionTokenStore(application)
    val mediaProjectionHelper: MediaProjectionHelper get() = mediaProjectionTokenStore.helper
    private val buildStreamerUseCase = BuildStreamerUseCase(application)

    // Dispatcher for camera/video source operations to avoid blocking Main thread with mutex
    private val defaultDispatcher = Dispatchers.IO

    // Mutex to prevent race conditions on rapid start/stop operations
    private val streamOperationMutex = Mutex()

    // Mutex to prevent race conditions on video source operations (camera switch, etc.)
    private val videoSourceMutex = Mutex()

    // Service binding for background streaming
    // Controller for camera controls and camera switching
    val cameraControlsController = com.dimadesu.lifestreamer.ui.main.controllers.CameraControlsController(
        application = application,
        scope = viewModelScope,
        streamerProvider = { serviceStreamer },
        videoSourceMutex = videoSourceMutex,
        defaultDispatcher = defaultDispatcher,
        onToastMessage = { _toastMessageLiveData.postValue(it) },
        onErrorMessage = { _streamerErrorLiveData.postValue(it) }
    ).apply {
        onExposureCompensationChanged = { notifyPropertyChanged(BR.exposureCompensation) }
        onZoomRatioChanged = { notifyPropertyChanged(BR.zoomRatio) }
        onLensDistanceChanged = { notifyPropertyChanged(BR.lensDistance) }
    }

    // Controller for audio debug overlay and audio source switching
    val audioDebugController = com.dimadesu.lifestreamer.ui.main.controllers.AudioDebugController(
        application = application,
        scope = viewModelScope,
        storageRepository = storageRepository,
        streamerProvider = { serviceStreamer },
        serviceProvider = { streamerService },
        isBluetoothActiveProvider = { _useBluetoothMic.value == true },
        isSystemAudioActiveProvider = { _useSystemAudioForCamera },
        isRtmpActiveProvider = { _activeRtmpIndex.value != null },
        isMonitorAudioOnProvider = { _isMonitorAudioOn.value == true },
        onErrorMessage = { _streamerErrorLiveData.postValue(it) }
    )

    // Controller for VU meter / audio level monitoring
    val audioLevelMonitor = com.dimadesu.lifestreamer.ui.main.controllers.AudioLevelMonitor(
        scope = viewModelScope,
        streamerProvider = { serviceStreamer },
        isUiInForegroundProvider = { isUiInForeground }
    )

    // Controller for audio source routing, BT mic, SYS AUDIO, and audio monitoring
    val audioRoutingController: com.dimadesu.lifestreamer.ui.main.controllers.AudioRoutingController =
        com.dimadesu.lifestreamer.ui.main.controllers.AudioRoutingController(
            application = application,
            scope = viewModelScope,
            streamerProvider = { serviceStreamer },
            serviceProvider = { streamerService },
            serviceBinderProvider = { serviceBinder },
            mediaProjectionTokenStore = mediaProjectionTokenStore,
            userToggledUvcProvider = { _userToggledUvc.value == true },
            currentRtmpPlayerProvider = { rtmpSourceController.currentRtmpPlayer },
            onRequestBluetoothPermission = { _bluetoothConnectRequestLiveData.postValue(Unit) }
        )

    // Controller for screen capture / MediaProjection video source
    val screenSourceController: com.dimadesu.lifestreamer.ui.main.controllers.ScreenSourceController =
        com.dimadesu.lifestreamer.ui.main.controllers.ScreenSourceController(
            application = application,
            scope = viewModelScope,
            streamerProvider = { serviceStreamer },
            serviceBinderProvider = { serviceBinder },
            mediaProjectionTokenStore = mediaProjectionTokenStore,
            cameraControlsController = cameraControlsController,
            fpsProvider = { videoConfigLiveData.value?.fps ?: 30 },
            lastUsedCameraIdProvider = { lastUsedCameraId },
            onLastUsedCameraIdChanged = { lastUsedCameraId = it },
            onScreenSourceActiveChanged = { active ->
                if (active) {
                    _activeRtmpIndex.postValue(null)
                    _userToggledUvc.postValue(false)
                }
            },
            onBeforeSourceSwitch = { removeBitrateRegulatorIfNeeded() },
            onAfterSourceSwitch = { readdBitrateRegulatorIfNeeded() },
            onAudioRoutingUpdateRequired = { setAudioSourceBasedOnVideoSource() },
            onErrorMessage = { _streamerErrorLiveData.postValue(it) }
        )

    // Controller for UVC (USB camera) source
    val uvcSourceController: com.dimadesu.lifestreamer.ui.main.controllers.UvcSourceController =
        com.dimadesu.lifestreamer.ui.main.controllers.UvcSourceController(
            application = application,
            scope = viewModelScope,
            streamerProvider = { serviceStreamer },
            lastUsedCameraIdProvider = { lastUsedCameraId },
            onLastUsedCameraIdChanged = { lastUsedCameraId = it },
            defaultCameraIdProvider = { application.cameraManager.cameras.firstOrNull() ?: "0" },
            testBitmapProvider = { testBitmap },
            onBeforeSourceSwitch = { removeBitrateRegulatorIfNeeded() },
            onAfterSourceSwitch = {
                readdBitrateRegulatorIfNeeded()
                if (_isMonitorAudioOn.value == true) {
                    applyMonitorAudioState()
                }
            },
            onUvcActiveChanged = { active ->
                if (active) {
                    _activeRtmpIndex.postValue(null)
                    _isScreenSource.postValue(false)
                }
            },
            onToastMessage = { _toastMessageLiveData.postValue(it) },
            onErrorMessage = { _streamerErrorLiveData.postValue(it) }
        )

    // Controller for RTMP video/audio source
    val rtmpSourceController: com.dimadesu.lifestreamer.ui.main.controllers.RtmpSourceController =
        com.dimadesu.lifestreamer.ui.main.controllers.RtmpSourceController(
            application = application,
            scope = viewModelScope,
            storageRepository = storageRepository,
            mediaProjectionTokenStore = mediaProjectionTokenStore,
            cameraControlsController = cameraControlsController,
            streamerProvider = { serviceStreamer },
            serviceProvider = { streamerService },
            serviceBinderProvider = { serviceBinder },
            testBitmapProvider = { testBitmap },
            lastUsedCameraIdProvider = { lastUsedCameraId },
            onLastUsedCameraIdChanged = { lastUsedCameraId = it },
            defaultCameraIdProvider = { application.cameraManager.cameras.firstOrNull() ?: "0" },
            streamOrientationProvider = { streamOrientationFlow.value },
            isUseBluetoothMicProvider = { _useBluetoothMic.value == true },
            isUseSystemAudioForCameraProvider = { _useSystemAudioForCamera },
            isMonitorAudioOnProvider = { _isMonitorAudioOn.value == true },
            onShowBluetoothToggleChanged = { _showBluetoothToggle.postValue(it) },
            onSuppressPassthroughObserverChanged = { suppressPassthroughObserver = it },
            onRtmpActiveChanged = { index ->
                if (index != null) {
                    _userToggledUvc.postValue(false)
                    _isScreenSource.postValue(false)
                }
            },
            onBeforeSourceSwitch = { removeBitrateRegulatorIfNeeded() },
            onAfterSourceSwitch = { readdBitrateRegulatorIfNeeded() },
            onAudioRoutingUpdateRequired = { setAudioSourceBasedOnVideoSource() },
            onStreamReconnectRequired = { reason -> handleDisconnection(reason) },
            onTriggerBluetoothMicActivation = { service?.triggerBluetoothMicActivation() },
            onSetupAudioLevelMonitoring = { setupAudioLevelMonitoring() },
            onStopAudioPassthrough = { service?.stopAudioPassthrough() },
            onApplyMonitorAudioState = { applyMonitorAudioState() },
            onToastMessage = { _toastMessageLiveData.postValue(it) },
            onErrorMessage = { _streamerErrorLiveData.postValue(it) }
        )

    // Controller for foreground service connection and flow observation
    val streamerServiceConnection: com.dimadesu.lifestreamer.ui.main.controllers.StreamerServiceConnection = com.dimadesu.lifestreamer.ui.main.controllers.StreamerServiceConnection(
        application = application,
        scope = viewModelScope,
        onConnected = { _, _, streamer ->
            streamerFlow.value = streamer
            setupAudioLevelMonitoring()
        },
        onDisconnected = {
            disableAudioLevelMonitoring()
            streamerFlow.value = null
            streamerService?.setStreamStatus(StreamStatus.NOT_STREAMING)
        },
        onReconnectionStatus = { _reconnectionStatusLiveData.postValue(it) },
        onBitrateUpdated = { _bitrateLiveData.postValue(it) },
        onEncoderStatsUpdated = { _encoderStatsLiveData.postValue(it) },
        onPassthroughStateChanged = { running ->
            val isRtmpSource = serviceStreamer?.videoInput?.sourceFlow?.value is RTMPVideoSource
            audioRoutingController.onPassthroughServiceStateChanged(running, isRtmpSource)
        },
        onStreamStatusChanged = { svcStatus ->
            try {
                service?.setStreamStatus(svcStatus)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to map service status: ${t.message}")
            }
        },
        onMutedChanged = { _isMutedLiveData.postValue(it) },
        onScoStateChanged = { state ->
            try { _scoStateLiveData.postValue(state.name) } catch (_: Throwable) {}
            try {
                when (state) {
                    com.dimadesu.lifestreamer.audio.BluetoothAudioManager.ScoState.FAILED -> {
                        audioRoutingController.handleScoStateFailedOrDisconnected()
                        _toastMessageLiveData.postValue("Bluetooth mic unavailable")
                    }
                    com.dimadesu.lifestreamer.audio.BluetoothAudioManager.ScoState.DISCONNECTED -> {
                        audioRoutingController.handleScoStateFailedOrDisconnected()
                        _toastMessageLiveData.postValue("Bluetooth mic disconnected")
                    }
                    else -> {}
                }
            } catch (_: Throwable) {}
        },
        onBluetoothConnectRequested = {
            try { _bluetoothConnectRequestLiveData.postValue(Unit) } catch (_: Throwable) {}
        },
        onUptimeUpdated = {
            try { _uptimeLiveData.postValue(it) } catch (_: Throwable) {}
        }
    )

    /**
     * Public getters for service, binder, and streamer from connection
     */
    val streamerService: CameraStreamerService? get() = streamerServiceConnection.streamerService
    val service: CameraStreamerService? get() = streamerServiceConnection.streamerService
    val serviceBinder: CameraStreamerService.CameraStreamerServiceBinder? get() = streamerServiceConnection.serviceBinder
    val serviceStreamer: SingleStreamer? get() = streamerServiceConnection.serviceStreamer
    private val _serviceReady: StateFlow<Boolean> get() = streamerServiceConnection.isServiceReady

    // If rotation changes while streaming we queue it here and apply it when streaming stops
    private var pendingTargetRotation: Int? = null
    private val streamerFlow = MutableStateFlow<SingleStreamer?>(null)

    // UI-visible current bitrate string
    private val _bitrateLiveData = MutableLiveData<String?>()
    val bitrateLiveData: LiveData<String?> get() = _bitrateLiveData
    // Uptime string exposed by the service (e.g., "00:01:23")
    private val _uptimeLiveData = MutableLiveData<String?>(null)
    val uptimeLiveData: LiveData<String?> get() = _uptimeLiveData
    // Expose current mute state to the UI
    private val _isMutedLiveData = MutableLiveData<Boolean>(false)
    val isMutedLiveData: LiveData<Boolean> get() = _isMutedLiveData
    // SCO negotiation state exposed to UI
    private val _scoStateLiveData = MutableLiveData<String>(null)
    val scoStateLiveData: LiveData<String> get() = _scoStateLiveData
    // LiveData event to notify UI to request BLUETOOTH_CONNECT permission
    private val _bluetoothConnectRequestLiveData = MutableLiveData<Unit?>(null)
    val bluetoothConnectRequestLiveData: LiveData<Unit?> get() = _bluetoothConnectRequestLiveData
    
    // Audio level monitoring
    val audioLevelFlow: StateFlow<com.dimadesu.lifestreamer.audio.AudioLevel> = audioLevelMonitor.audioLevelFlow

    // Encoder stats display
    private val _encoderStatsLiveData = MutableLiveData<String?>(null)
    val encoderStatsLiveData: LiveData<String?> get() = _encoderStatsLiveData

    val streamOrientationFlow: StateFlow<com.dimadesu.lifestreamer.models.StreamOrientation> =
        storageRepository.streamOrientationFlow.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            com.dimadesu.lifestreamer.models.StreamOrientation.AUTO
        )

    // Track whether the UI is in foreground (to avoid camera operations when paused)
    @Volatile
    private var isUiInForeground = true
    
    // Pending video/audio config to apply when UI resumes
    @Volatile
    private var pendingVideoConfig: VideoConfig? = null
    @Volatile
    private var pendingAudioConfig: AudioConfig? = null
    
    fun onUiResumed() {
        isUiInForeground = true
        
        // Restart audio level monitoring (if already streaming, it will just re-attach the effect)
        setupAudioLevelMonitoring()
        
        // Apply any pending configs that were skipped while in background
        val savedVideoConfig = pendingVideoConfig
        val savedAudioConfig = pendingAudioConfig
        pendingVideoConfig = null
        pendingAudioConfig = null
        
        if (savedVideoConfig != null || savedAudioConfig != null) {
            viewModelScope.launch {
                // Wait for service to be ready before applying config
                _serviceReady.first { it }
                val streamer = serviceStreamer
                if (streamer != null && streamer.isStreamingFlow.value != true) {
                    savedVideoConfig?.let { config ->
                        try {
                            Log.i(TAG, "Applying pending video config after UI resumed")
                            streamer.setVideoConfig(config)
                        } catch (t: Throwable) {
                            Log.e(TAG, "setVideoConfig failed (deferred)", t)
                            _streamerErrorLiveData.postValue("setVideoConfig: ${t.message ?: t::class.java.simpleName}")
                        }
                    }
                    savedAudioConfig?.let { config ->
                        // Check permission before applying audio config
                        if (ActivityCompat.checkSelfPermission(
                                application,
                                Manifest.permission.RECORD_AUDIO
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            Log.i(TAG, "Re-queuing pending audio config - RECORD_AUDIO permission not granted yet")
                            pendingAudioConfig = config  // Re-queue so it's applied after permission granted
                            return@let
                        }
                        try {
                            Log.i(TAG, "Applying pending audio config after UI resumed")
                            applyAudioConfigSafely(streamer, config)
                        } catch (t: Throwable) {
                            Log.e(TAG, "setAudioConfig failed (deferred)", t)
                            _streamerErrorLiveData.postValue("setAudioConfig: ${t.message ?: t::class.java.simpleName}")
                        }
                    }
                } else if (streamer == null) {
                    Log.w(TAG, "Cannot apply pending configs - streamer is null even after service ready")
                } else {
                    Log.i(TAG, "Skipping pending configs - streamer is streaming")
                }
            }
        }
    }
    
    fun onUiPaused() {
        isUiInForeground = false
        
        Log.i(TAG, "UI paused: disabling audio level monitoring")
        disableAudioLevelMonitoring()
    }

    /**
     * Apply pending audio config after RECORD_AUDIO permission is granted.
     * Called from permission callback in PreviewFragment.
     */
    fun applyPendingAudioConfig() {
        val config = pendingAudioConfig ?: return
        pendingAudioConfig = null
        
        if (ActivityCompat.checkSelfPermission(
                application,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "applyPendingAudioConfig called but permission still not granted")
            pendingAudioConfig = config  // Re-queue
            return
        }
        
        viewModelScope.launch {
            _serviceReady.first { it }
            val streamer = serviceStreamer
            if (streamer != null && streamer.isStreamingFlow.value != true) {
                try {
                    Log.i(TAG, "Applying pending audio config after permission granted")
                    applyAudioConfigSafely(streamer, config)
                } catch (t: Throwable) {
                    Log.e(TAG, "setAudioConfig failed (after permission)", t)
                    _streamerErrorLiveData.postValue("setAudioConfig: ${t.message ?: t::class.java.simpleName}")
                }
            }
        }
    }

    fun clearBluetoothConnectRequest() {
        _bluetoothConnectRequestLiveData.postValue(null)
    }
    
    // Remember last used camera ID when switching to RTMP/bitmap sources
    private var lastUsedCameraId: String? = null

    // Streamer access through service (with fallback for backward compatibility)
    val streamer: SingleStreamer?
        get() = serviceStreamer

    // Service readiness for UI binding
    val serviceReadyFlow = _serviceReady
    val streamerLiveData = serviceReadyFlow.map { ready ->
        if (ready) serviceStreamer else null
    }.asLiveData()

    /**
     * Test bitmap for [BitmapSource].
     */
    private val testBitmap =
        BitmapFactory.decodeResource(application.resources, R.drawable.img_test)

    /**
     * User intent flags - track what the user toggled ON (not what fallback is active)
     * These allow buttons to show correct state even when source falls back to bitmap
     */
    private val _activeRtmpIndex: MutableLiveData<Int?> get() = rtmpSourceController._activeRtmpIndex
    private val _userToggledUvc: MutableLiveData<Boolean> get() = uvcSourceController._userToggledUvc
    private val _isScreenSource: MutableLiveData<Boolean> get() = screenSourceController._isScreenSource

    /**
     * Camera settings.
     */
    val cameraSettings: CameraSettings?
        get() {
            val currentStreamer = serviceStreamer
            val videoSource = (currentStreamer as? IWithVideoSource)?.videoInput?.sourceFlow?.value
            return (videoSource as? ICameraSource)?.settings
        }

    val requiredPermissions: List<String>
        get() {
            val permissions = mutableListOf<String>()
            val currentStreamer = serviceStreamer
            if (currentStreamer?.videoInput?.sourceFlow is ICameraSource) {
                permissions.add(Manifest.permission.CAMERA)
            }
            if (currentStreamer?.audioInput?.sourceFlow?.value is IAudioRecordSource) {
                permissions.add(Manifest.permission.RECORD_AUDIO)
            }
            storageRepository.endpointDescriptorFlow.asLiveData().value?.let {
                if (it is UriMediaDescriptor) {
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }

            return permissions
        }

    /**
     * Determines if MediaProjection is required for the current streaming setup.
     * MediaProjection is needed when streaming from RTMP source for audio capture.
     * UVC → bitmap fallback uses mic audio, not MediaProjection — same as a camera source.
     */
    fun requiresMediaProjection(): Boolean {
        val currentVideoSource = serviceStreamer?.videoInput?.sourceFlow?.value
        val isScreenSourceActive = _isScreenSource.value == true
        if (isScreenSourceActive) return true
        
        // UVC bitmap fallback does NOT need MediaProjection — it uses mic audio just like camera.
        // Only RTMP (live or its bitmap fallback) needs MediaProjection for audio.
        val isUvcBitmapFallback = currentVideoSource is IBitmapSource && (_userToggledUvc.value == true)
        return (currentVideoSource is RTMPVideoSource || currentVideoSource is IBitmapSource) && !isUvcBitmapFallback
    }

    // Streamer errors (nullable to support single-event pattern - cleared after observation)
    private val _streamerErrorLiveData: MutableLiveData<String?> = MutableLiveData()
    val streamerErrorLiveData: LiveData<String?> = _streamerErrorLiveData

    // UI toggle: Use Bluetooth mic when available
    val useBluetoothMic: LiveData<Boolean> get() = audioRoutingController.useBluetoothMic
    private val _useBluetoothMic: LiveData<Boolean> get() = audioRoutingController.useBluetoothMic
    
    // BT toggle visibility - hide when using MediaProjection audio (RTMP/Bitmap sources)
    private val _showBluetoothToggle = MutableLiveData<Boolean>(true)
    val showBluetoothToggle: LiveData<Boolean> get() = _showBluetoothToggle

    fun setUseBluetoothMic(enabled: Boolean) = audioRoutingController.setUseBluetoothMic(enabled)

    // Data-binding getter expected by generated binding code
    // Note: do not add a Java-style getter here; the Kotlin property `useBluetoothMic`
    // already exposes `getUseBluetoothMic()` to data binding. Keeping another method
    // with the same name causes an ambiguous reference during kapt/Java compilation.

    // Toast messages (nullable to support single-event pattern - cleared after observation)
    private val _toastMessageLiveData: MutableLiveData<String?> = MutableLiveData()
    val toastMessageLiveData: LiveData<String?> = _toastMessageLiveData
    private val _endpointErrorLiveData: MutableLiveData<String?> = MutableLiveData()
    val endpointErrorLiveData: LiveData<String?> = _endpointErrorLiveData
    
    // Clear methods for single-event pattern (prevents re-showing errors on orientation change)
    fun clearStreamerError() {
        _streamerErrorLiveData.value = null
    }
    
    fun clearEndpointError() {
        _endpointErrorLiveData.value = null
    }

    fun clearToastMessage() {
        _toastMessageLiveData.value = null
    }

    // RTMP status for UI display
    val rtmpStatusLiveData: LiveData<String?> get() = rtmpSourceController.rtmpStatusLiveData
    
    // Monitor audio toggle for RTMP sources (OFF by default)
    val isMonitorAudioOn: LiveData<Boolean> get() = audioRoutingController.isMonitorAudioOn
    private val _isMonitorAudioOn: LiveData<Boolean> get() = audioRoutingController.isMonitorAudioOn
    // Flag to suppress passthrough observer updates during RTMP source switch
    var suppressPassthroughObserver: Boolean
        get() = audioRoutingController.suppressPassthroughObserver
        set(value) { audioRoutingController.suppressPassthroughObserver = value }

    // Streamer states
    val isStreamingLiveData: LiveData<Boolean>
        get() = serviceReadyFlow.flatMapLatest { ready ->
            Log.d(TAG, "isStreamingLiveData: serviceReady = $ready, serviceStreamer = $serviceStreamer")
            if (ready && serviceStreamer != null) {
                val streamingFlow = serviceStreamer!!.isStreamingFlow
                Log.d(TAG, "isStreamingLiveData: using streamingFlow = $streamingFlow, current value = ${streamingFlow.value}")
                streamingFlow
            } else {
                // When service is not ready, we don't know the streaming state
                // Return false for now, but this will update once service reconnects
                Log.d(TAG, "isStreamingLiveData: service not ready, returning false (will update on reconnect)")
                kotlinx.coroutines.flow.flowOf(false)
            }
        }.asLiveData()

    // Unified stream status for UI and notifications (shared enum)
    // Stream status - observe from Service to keep UI and notification in sync
    val streamStatus: StateFlow<StreamStatus> = serviceReadyFlow.flatMapLatest { ready ->
        if (ready && service != null) {
            service!!.serviceStreamStatus
        } else {
            flowOf(StreamStatus.NOT_STREAMING)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StreamStatus.NOT_STREAMING)

    // LiveData for data binding to track streaming status changes
    val streamStatusLiveData: LiveData<StreamStatus> = streamStatus.asLiveData()

    // Human-friendly status string for UI binding
    val streamStatusTextLiveData: LiveData<String> = streamStatus.map { status ->
        when (status) {
            StreamStatus.NOT_STREAMING -> application.getString(R.string.status_not_streaming)
            StreamStatus.STARTING -> application.getString(R.string.status_starting)
            StreamStatus.CONNECTING -> application.getString(R.string.status_connecting)
            StreamStatus.STREAMING -> application.getString(R.string.status_streaming)
            StreamStatus.ERROR -> application.getString(R.string.status_error)
        }
    }.asLiveData()

    // Audio source indicator for displaying current audio source type
    private val _audioSourceIndicatorLiveData = MutableLiveData<String>()
    val audioSourceIndicatorLiveData: LiveData<String> = _audioSourceIndicatorLiveData

    // Audio debug information for overlay display
    val audioDebugInfoLiveData: LiveData<com.dimadesu.lifestreamer.models.AudioDebugInfo?> =
        audioDebugController.audioDebugInfo.asLiveData()

    // Audio debug overlay visibility state
    val isAudioDebugOverlayVisible: LiveData<Boolean> =
        audioDebugController.isOverlayVisible.asLiveData()
    
    // Selected audio source type for testing (MediaRecorder.AudioSource constants)
    val selectedAudioSourceType: LiveData<Int> =
        audioDebugController.selectedAudioSourceType.asLiveData()
    
    fun setSelectedAudioSourceType(sourceType: Int) {
        audioDebugController.setSelectedAudioSourceType(sourceType)
    }
    
    // MediaProjection session for streaming
    var streamingMediaProjection: MediaProjection?
        get() = mediaProjectionTokenStore.streamingMediaProjection
        set(value) { mediaProjectionTokenStore.streamingMediaProjection = value }

    // MediaProjection token held outside of a stream session — acquired on first RTMP/SYS AUDIO use
    var startupMediaProjection: MediaProjection?
        get() = mediaProjectionTokenStore.startupMediaProjection
        set(value) { mediaProjectionTokenStore.startupMediaProjection = value }

    // Toggle: camera/UVC sources use full-phone system audio instead of mic
    private val _useSystemAudioForCamera: Boolean get() = audioRoutingController.useSystemAudioForCamera
    val useSystemAudioForCameraLiveData: LiveData<Boolean> get() = audioRoutingController.useSystemAudioForCameraLiveData

    fun hasMediaProjection(): Boolean = mediaProjectionTokenStore.hasProjection()

    var rtmpAudioExplanationShown = false
    var sysAudioExplanationShown = false
    var pendingRtmpIndex: Int? = null

    fun toggleSystemAudioForCamera(
        mediaProjectionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    ) = audioRoutingController.toggleSystemAudioForCamera(mediaProjectionLauncher)

    // Connection retry mechanism (inspired by Moblin)
    private val reconnectTimer = ReconnectTimer()
    
    // Observe reconnection state from Service
    val isReconnectingLiveData: LiveData<Boolean> = serviceReadyFlow.flatMapLatest { ready ->
        if (ready && service != null) {
            service!!.isReconnecting
        } else {
            flowOf(false)
        }
    }.asLiveData()
    
    private var lastDisconnectReason: String? = null
    private var rotationIgnoredDuringReconnection: Int? = null // Store rotation changes during reconnection
    
    // LiveData for reconnection status UI feedback
    private val _reconnectionStatusLiveData = MutableLiveData<String?>()
    val reconnectionStatusLiveData: LiveData<String?> = _reconnectionStatusLiveData

    // Camera information for button creation
    data class CameraInfo(val id: String, val displayName: String, val facing: String)
    
    val availableCamerasLiveData: LiveData<List<CameraInfo>> = cameraControlsController.availableCameras.asLiveData()

    init {
        // Bind to streaming service for background streaming capability
        bindToStreamerService()

        // Try to recover a MediaProjection token from a still-running
        // MediaProjectionService (e.g. after the user swiped the UI from recents
        // but the foreground service survived).
        mediaProjectionTokenStore.tryReconnect()

        // Initialize LiveData flows
        viewModelScope.launch {
            serviceReadyFlow.collect { isReady ->
                if (isReady && serviceStreamer != null) {
                    Log.i(TAG, "Service ready and serviceStreamer available - initializing sources")
                    initializeStreamerSources()
                } else {
                    Log.i(TAG, "Service ready: $isReady, serviceStreamer: ${serviceStreamer != null}")
                }
            }
        }
        
        // Observe audio source type changes from DataStore (e.g., from Settings activity)
        // This applies changes in real-time without waiting for fragment resume
        viewModelScope.launch {
            var isFirstEmission = true
            storageRepository.audioSourceTypeFlow
                .distinctUntilChanged()
                .collect { newSourceType ->
                    if (isFirstEmission) {
                        // First emission is just the initial value - update LiveData but don't apply
                        audioDebugController.setSelectedAudioSourceType(newSourceType)
                        Log.d(TAG, "Initial audio source type from DataStore: ${getAudioSourceName(newSourceType)}")
                        isFirstEmission = false
                    } else if (serviceStreamer != null) {
                        // Subsequent emissions mean the value changed (e.g., in Settings)
                        val currentValue = audioDebugController.selectedAudioSourceType.value
                        if (currentValue != newSourceType) {
                            Log.i(TAG, "Audio source changed in DataStore: ${getAudioSourceName(currentValue)} -> ${getAudioSourceName(newSourceType)}")
                            audioDebugController.setSelectedAudioSourceType(newSourceType)
                            applySelectedAudioSource()
                        }
                    }
                }
        }

        // Status-to-notification messaging removed; UI no longer shows sliding panel
    }

    /**
     * Remove bitrate regulator from streamer if streaming with SRT.
     * Safe to call even if regulator is not attached.
     */
    private suspend fun removeBitrateRegulatorIfNeeded() {
        val currentStreamer = serviceStreamer ?: return
        val isCurrentlyStreaming = currentStreamer.isStreamingFlow.value == true
        if (!isCurrentlyStreaming) return
        
        val sinkType = storageRepository.endpointDescriptorFlow.first().type.sinkType
        if (sinkType != MediaSinkType.SRT && sinkType != MediaSinkType.RTMP) return
        
        try {
            Log.i(TAG, "Removing bitrate regulator before video source switch")
            currentStreamer.bitrateRegulatorControllerFactory = null
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove bitrate regulator: ${e.message}")
        }
    }
    
    /**
     * Re-add bitrate regulator to streamer if streaming with SRT.
     * Waits briefly for encoder to initialize before attaching.
     */
    private suspend fun readdBitrateRegulatorIfNeeded() {
        val currentStreamer = serviceStreamer ?: return
        val isCurrentlyStreaming = currentStreamer.isStreamingFlow.value == true
        if (!isCurrentlyStreaming) return
        
        val sinkType = storageRepository.endpointDescriptorFlow.first().type.sinkType
        if (sinkType != MediaSinkType.SRT && sinkType != MediaSinkType.RTMP) return
        
        try {
            if (storageRepository.bitrateRegulatorConfigFlow.first() != null) {
                // Small delay to let the new encoder initialize
                delay(200)
                streamConfigurationHelper.attachBitrateRegulator(
                    currentStreamer as? io.github.thibaultbee.streampack.core.streamers.single.IVideoSingleStreamer,
                    sinkType,
                    TAG
                )
                Log.i(TAG, "Re-added bitrate regulator after video source switch")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not re-add bitrate regulator: ${e.message}")
        }
    }

    /**
     * Helper functions to interact with streamer directly (service compatibility layer)
     * 
     * @param shouldSuppressErrors If true, errors won't be posted to streamerErrorLiveData (for auto-retry)
     */
    private suspend fun startServiceStreaming(descriptor: MediaDescriptor, shouldSuppressErrors: Boolean = false): Boolean {
        // Check if user stopped before we even start
        if (service?.userStoppedManually?.value == true) {
            Log.i(TAG, "startServiceStreaming: User stopped manually, aborting")
            return false
        }
        val currentService = service
        if (currentService == null) {
            Log.e(TAG, "startServiceStreaming: service is null!")
            if (!shouldSuppressErrors) {
                _streamerErrorLiveData.postValue("Streaming service not available")
            }
            return false
        }

        val result = currentService.startStream(descriptor)
        return result.fold(
            onSuccess = {
                Log.i(TAG, "startServiceStreaming: Stream started successfully via service")
                true
            },
            onFailure = { e ->
                Log.e(TAG, "startServiceStreaming failed: ${e.message}", e)
                if (!shouldSuppressErrors) {
                    val msg = if (e is kotlinx.coroutines.TimeoutCancellationException) {
                        "Connection timeout - check server address and network"
                    } else {
                        "Stream start failed: ${e.message}"
                    }
                    _streamerErrorLiveData.postValue(msg)
                }
                false
            }
        )
    }

    /**
     * Core streaming logic shared between initial start and reconnection.
     * Handles descriptor retrieval, stream start, and bitrate regulator setup.
     * 
     * @param shouldAutoRetry If true, will trigger reconnection on connection failure
     * @return true if stream started successfully, false otherwise
     */
    private suspend fun doStartStream(shouldAutoRetry: Boolean = false): Boolean {
        val currentStreamer = serviceStreamer
        if (currentStreamer == null) {
            Log.e(TAG, "doStartStream: serviceStreamer is null!")
            _streamerErrorLiveData.postValue("Service streamer not available")
            return false
        }

        // Validate that both video and audio sources are configured
        val (sourcesValid, sourceError) = validateSourcesConfigured(currentStreamer)
        if (!sourcesValid) {
            Log.e(TAG, "doStartStream: Cannot start stream: $sourceError")
            if (!shouldAutoRetry) {
                _streamerErrorLiveData.postValue("Missing video or audio source - please check configuration")
            }
            return false
        }
        Log.i(TAG, "doStartStream: Sources validated successfully")

        try {
            val descriptor = storageRepository.endpointDescriptorFlow.first()
            Log.i(TAG, "doStartStream: Starting stream with descriptor: $descriptor")
            
            val success = startServiceStreaming(descriptor, shouldSuppressErrors = shouldAutoRetry)
            if (!success) {
                Log.e(TAG, "doStartStream: Stream start failed - startServiceStreaming returned false")
                
                // Check if user stopped manually - if so, don't trigger reconnection
                if (service?.userStoppedManually?.value == true) {
                    Log.d(TAG, "User stopped stream, skipping error handling")
                    return false
                }
                
                // Trigger auto-retry if requested and not already reconnecting
                if (shouldAutoRetry && service?.isReconnecting?.value != true) {
                    val errorMessage = "Connection failed - unable to establish stream"
                    Log.i(TAG, "Connection failed, triggering auto-retry (error dialog suppressed)")
                    handleDisconnection(errorMessage, isInitialConnection = true)
                } else if (!shouldAutoRetry && service?.isReconnecting?.value != true) {
                    // Only show error dialog if not auto-retrying AND not already reconnecting
                    _streamerErrorLiveData.postValue("Connection failed - unable to establish stream")
                } else {
                    Log.d(TAG, "Connection failed during reconnection, error dialog suppressed")
                }
                
                return false
            }
            
            Log.i(TAG, "doStartStream: Stream started successfully")
            
            // Trigger BT mic activation if using mic-based audio (not MediaProjection)
            // This covers Camera, UVC, and any fallback to mic scenarios
            // Use type checking instead of class name to avoid R8 obfuscation
            val currentAudioSource = currentStreamer.audioInput?.sourceFlow?.value
            val isMicBasedAudio = currentAudioSource != null && currentAudioSource !is IMediaProjectionSource
            if (isMicBasedAudio) {
                Log.i(TAG, "doStartStream: Mic-based audio detected (${currentAudioSource?.javaClass?.simpleName}), triggering BT activation")
                service?.triggerBluetoothMicActivation()
            } else {
                Log.i(TAG, "doStartStream: MediaProjection audio detected, skipping BT activation")
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "doStartStream failed: ${e.message}", e)
            val errorMessage = "Stream start failed: ${e.message}"
            
            // Check if user stopped manually - if so, don't trigger reconnection
            if (service?.userStoppedManually?.value == true) {
                Log.d(TAG, "User stopped stream, skipping error handling")
                return false
            }
            
            // Trigger auto-retry if requested and not already reconnecting
            if (shouldAutoRetry && service?.isReconnecting?.value != true) {
                Log.i(TAG, "Connection failed, triggering auto-retry (error dialog suppressed)")
                handleDisconnection(errorMessage, isInitialConnection = true)
            } else if (!shouldAutoRetry && service?.isReconnecting?.value != true) {
                // Only show error dialog if not auto-retrying AND not already reconnecting
                _streamerErrorLiveData.postValue(errorMessage)
            } else {
                Log.d(TAG, "Exception during reconnection, error dialog suppressed")
            }
            
            return false
        }
    }

    private suspend fun stopServiceStreaming(): Boolean {
        return service?.stopStream() ?: false
    }

    /**
     * Validates that both video and audio sources are configured for a streamer.
     * 
     * @param streamer The streamer instance to validate
     * @return Pair of (isValid, errorMessage). If valid, errorMessage is null.
     */
    private fun validateSourcesConfigured(streamer: SingleStreamer): Pair<Boolean, String?> {
        val videoSource = streamer.videoInput?.sourceFlow?.value
        val audioSource = streamer.audioInput?.sourceFlow?.value
        
        return if (videoSource == null || audioSource == null) {
            val errorMsg = "video source=${videoSource != null}, audio source=${audioSource != null}"
            false to errorMsg
        } else {
            Log.d(TAG, "Sources validated - video: ${videoSource.javaClass.simpleName}, audio: ${audioSource.javaClass.simpleName}")
            true to null
        }
    }

    /**
     * Determines and sets the appropriate audio source based on the current video source.
     * Priority for camera/UVC sources: SYS AUDIO (full-phone MP) > BT mic > built-in mic.
     * RTMP/Bitmap sources always use MediaProjection (app-only), falling back to mic.
     *
     * Note: BT mic SCO negotiation is handled separately by BluetoothAudioManager. This method
     * only switches the factory; callers must re-apply BT policy when transitioning off SYS AUDIO.
     */
    private suspend fun setAudioSourceBasedOnVideoSource() {
        audioRoutingController.setAudioSourceBasedOnVideoSource()
    }

    private suspend fun switchToUvcBitmapFallback(streamer: SingleStreamer, bitmap: Bitmap) {
        uvcSourceController.switchToUvcBitmapFallback(streamer, bitmap)
    }

    /**
     * Bind to the CameraStreamerService for background streaming.
     * This handles both starting a new service and reconnecting to an existing one.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun bindToStreamerService() {
        streamerServiceConnection.bind()
    }

    /**
     * Initialize streamer sources after service is ready.
     * Only initializes if streamer is not already streaming to avoid configuration conflicts.
     */
    private suspend fun initializeStreamerSources() {
        val currentStreamer = serviceStreamer ?: return

        // Don't reinitialize sources if already streaming - this prevents configuration conflicts
        if (currentStreamer.isStreamingFlow.value == true) {
            Log.i(TAG, "Streamer is already streaming - skipping source initialization to avoid conflicts")
            observeStreamerFlows()
            // Set initial audio source indicator based on current source
            val initialAudioSource = currentStreamer.audioInput?.sourceFlow?.value
            val audioSourceLabel = getAudioSourceLabel(initialAudioSource)
            _audioSourceIndicatorLiveData.postValue(audioSourceLabel)
            Log.i(TAG, "Initial audio source (already streaming): $audioSourceLabel (${initialAudioSource?.javaClass?.simpleName})")
            return
        }

        Log.i(TAG, "Initializing streamer sources - Audio enabled: ${currentStreamer.withAudio}, Video enabled: ${currentStreamer.withVideo}")

        // Set audio source and video source only if not streaming
        if (currentStreamer.withAudio) {
            Log.i(TAG, "Audio source is enabled. Setting audio based on video source")
            setAudioSourceBasedOnVideoSource()
        } else {
            Log.i(TAG, "Audio source is disabled")
        }

        if (currentStreamer.withVideo) {
            if (ActivityCompat.checkSelfPermission(
                    application,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG, "Camera permission granted, setting video source")
                currentStreamer.setVideoSource(CameraSourceFactory(application))
            } else {
                Log.w(TAG, "Camera permission not granted")
            }
        } else {
            Log.i(TAG, "Video source is disabled")
        }

        // Set up flow observers for the service-based streamer
        observeStreamerFlows()

        // Set initial audio source indicator based on current source
        val initialAudioSource = currentStreamer.audioInput?.sourceFlow?.value
        val audioSourceLabel = getAudioSourceLabel(initialAudioSource)
        _audioSourceIndicatorLiveData.postValue(audioSourceLabel)
        Log.i(TAG, "Initial audio source: $audioSourceLabel (${initialAudioSource?.javaClass?.simpleName})")
    }

    /**
     * Set up flow observers for streamer state changes.
     */
    private fun observeStreamerFlows() {
        val currentStreamer = serviceStreamer ?: return

        viewModelScope.launch {
            currentStreamer.videoInput?.sourceFlow?.collect {
                notifySourceChanged()
            }
        }

        // Observe audio source changes to update the audio source indicator
        viewModelScope.launch {
            currentStreamer.audioInput?.sourceFlow?.collect { audioSource ->
                val audioSourceLabel = getAudioSourceLabel(audioSource)
                _audioSourceIndicatorLiveData.postValue(audioSourceLabel)
                Log.i(TAG, "Audio source changed: $audioSourceLabel (${audioSource?.javaClass?.simpleName})")
            }
        }

        viewModelScope.launch {
            currentStreamer.throwableFlow.filterNotNull().filter { !it.isClosedException }
                .map { "${it.javaClass.simpleName}: ${it.message}" }.collect { errorMessage ->
                    // Don't show error dialog during reconnection attempts
                    if (service?.isReconnecting?.value != true) {
                        _streamerErrorLiveData.postValue(errorMessage)
                    } else {
                        Log.w(TAG, "Error during reconnection (dialog suppressed): $errorMessage")
                    }
                }
        }

        viewModelScope.launch {
            currentStreamer.throwableFlow.filterNotNull().filter { it.isClosedException }
                .map { "Connection lost: ${it.message}" }.collect { errorMessage ->
                    Log.w(TAG, "Connection lost detected: $errorMessage, status=${streamStatus.value}, isReconnecting=${service?.isReconnecting?.value}")
                    
                    // Check if user stopped manually - if so, don't trigger reconnection
                    if (service?.userStoppedManually?.value == true) {
                        Log.d(TAG, "User stopped stream, skipping connection error handling")
                        return@collect
                    }
                    
                    // Suppress error if we're already in reconnection mode
                    if (service?.isReconnecting?.value == true) {
                        Log.i(TAG, "Connection error during reconnection - dialog suppressed")
                    } else {
                        // Connection lost - trigger reconnection
                        Log.i(TAG, "Connection lost - will auto-retry (error dialog suppressed)")
                        handleDisconnection(errorMessage, isInitialConnection = false)
                    }
                }
        }
        viewModelScope.launch {
            serviceReadyFlow.collect { isReady ->
                if (isReady) {
                    serviceStreamer?.isOpenFlow?.collect {
                        Log.i(TAG, "Streamer is opened: $it")
                    }
                }
            }
        }
        viewModelScope.launch {
            serviceReadyFlow.collect { isReady ->
                if (isReady) {
                    // Track the previous value of isStreamingFlow itself rather than reading
                    // streamStatus.value, because the service's onStreamingStopped callback sets
                    // status to NOT_STREAMING a few ms BEFORE isStreamingFlow emits false.
                    // Reading previousStatus at that point gives NOT_STREAMING even when the stream
                    // was actively running, causing wasStreaming=false and suppressing reconnection.
                    var previouslyStreaming = serviceStreamer?.isStreamingFlow?.value ?: false
                    serviceStreamer?.isStreamingFlow?.collect { isStreaming ->
                        val previousStatus = streamStatus.value
                        Log.i(TAG, "isStreamingFlow changed: $isStreaming, previouslyStreaming=$previouslyStreaming, previousStatus=$previousStatus, userStoppedManually=${service?.userStoppedManually?.value}, isReconnecting=${service?.isReconnecting?.value}")
                        
                        if (isStreaming) {
                            previouslyStreaming = true
                            // Don't change to STREAMING during reconnection - keep CONNECTING status
                            // This ensures stop button works during reconnection attempts
                            if (service?.isReconnecting?.value != true) {
                                service?.setStreamStatus(StreamStatus.STREAMING)
                            } else {
                                Log.d(TAG, "Stream started during reconnection - keeping CONNECTING status for stop button")
                            }
                        } else {
                            // Stream stopped - check if this was unexpected (should trigger reconnection).
                            // Use previouslyStreaming (tracks actual isStreamingFlow transitions) instead
                            // of previousStatus (which can be prematurely set to NOT_STREAMING by the
                            // service's streaming-stopped callback before isStreamingFlow fires false).
                            val wasStreaming = previouslyStreaming
                            previouslyStreaming = false
                            
                            Log.d(TAG, "Stream stopped - wasStreaming=$wasStreaming, userStopped=${service?.userStoppedManually?.value}, isReconnecting=${service?.isReconnecting?.value}")
                            
                            if (wasStreaming && service?.userStoppedManually?.value != true && service?.isReconnecting?.value != true) {
                                // Unexpected disconnection - trigger reconnection
                                Log.w(TAG, "Unexpected stream stop detected - triggering reconnection")
                                handleDisconnection("Stream stopped unexpectedly", isInitialConnection = false)
                            } else {
                                // Normal stop or already handling reconnection
                                if (service?.isReconnecting?.value == true) {
                                    Log.d(TAG, "Stream stopped during reconnection - keeping CONNECTING status")
                                    service?.setStreamStatus(StreamStatus.CONNECTING)
                                } else if (service?.userStoppedManually?.value == true) {
                                    Log.d(TAG, "User stopped stream - setting NOT_STREAMING")
                                    service?.setStreamStatus(StreamStatus.NOT_STREAMING)
                                } else if (!wasStreaming) {
                                    // Stream was never streaming, safe to set NOT_STREAMING
                                    Log.d(TAG, "Stream never started - setting NOT_STREAMING")
                                    service?.setStreamStatus(StreamStatus.NOT_STREAMING)
                                }
                                // If none of the above, don't change status (keep whatever it is)
                            }
                        }
                    }
                }
            }
        }
        // Clear bitrate display when streaming stops
        viewModelScope.launch {
            serviceReadyFlow.collect { isReady ->
                if (isReady) {
                    serviceStreamer?.isStreamingFlow?.collect { isStreaming ->
                        if (!isStreaming) {
                            Log.i(TAG, "Streamer stopped - clearing bitrate display")
                            _bitrateLiveData.postValue(null)
                        }
                    }
                }
            }
        }
        // Apply rotation changes, but if streamer is currently streaming or reconnecting,
        // queue the change and apply it when streaming stops to avoid conflicts in encoding pipeline.
        viewModelScope.launch {
            rotationRepository.rotationFlow.collect { rotation ->
                val current = serviceStreamer
                val isCurrentlyStreaming = current?.isStreamingFlow?.value == true
                val currentStatus = streamStatus.value
                val isInStreamingProcess = currentStatus == StreamStatus.STARTING ||
                                           currentStatus == StreamStatus.CONNECTING ||
                                           currentStatus == StreamStatus.STREAMING
                
                // During reconnection, ignore rotation changes to maintain locked orientation
                // But remember the latest rotation so we can apply it when reconnection ends
                if (service?.isReconnecting?.value == true) {
                    rotationIgnoredDuringReconnection = rotation
                    Log.i(TAG, "Rotation change to $rotation ignored during reconnection (will apply later)")
                    return@collect
                }
                
                if (isCurrentlyStreaming || isInStreamingProcess) {
                    Log.i(TAG, "Rotation change to $rotation queued (streaming: $isCurrentlyStreaming, status: $currentStatus)")
                    pendingTargetRotation = rotation
                } else {
                    try {
                        current?.setTargetRotation(rotation)
                        Log.i(TAG, "Rotation change to $rotation applied immediately (not streaming)")
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to set target rotation: $t")
                    }
                }
            }
        }

        // When streaming stops, apply any pending rotation change.
        // BUT: Don't apply during reconnection - we want to keep the locked orientation
        viewModelScope.launch {
            serviceReadyFlow.collect { isReady ->
                if (isReady) {
                    serviceStreamer?.isStreamingFlow?.collect { isStreaming ->
                        if (!isStreaming && service?.isReconnecting?.value != true) {
                            pendingTargetRotation?.let { pending ->
                                Log.i(TAG, "Applying pending rotation $pending after stream stopped")
                                try {
                                    serviceStreamer?.setTargetRotation(pending)
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Failed to apply pending rotation: $t")
                                }
                                pendingTargetRotation = null
                            }
                        } else if (!isStreaming && service?.isReconnecting?.value == true) {
                            Log.i(TAG, "Skipping pending rotation application during reconnection (pending: $pendingTargetRotation)")
                        }
                    }
                }
            }
        }
        
        // Observe manual stop from notification - cancel reconnection if in progress
        // Use a separate coroutine that waits for service to be ready and then observes the flow
        viewModelScope.launch {
            // Wait for service to be ready
            _serviceReady.first { it }
            // Now observe the stop signal
            service?.userStoppedFromNotification?.collect {
                Log.i(TAG, "User stopped from notification - cancelling reconnection")
                // Mark as manual stop to prevent reconnection
                service?.markUserStoppedManually()
                // Cancel any pending reconnection
                reconnectTimer.stop()
                // isReconnecting managed by service
                _reconnectionStatusLiveData.postValue(null)
                service?.setStreamStatus(StreamStatus.NOT_STREAMING)
                // Update service notification to reflect stopped state
                service?.updateStreamStatus(StreamStatus.NOT_STREAMING)
            }
        }
        
        // Observe critical errors from service (e.g., start from notification failures)
        // Trigger reconnection for these errors
        viewModelScope.launch {
            _serviceReady.first { it }
            service?.let { svc ->
                try {
                    svc.criticalErrors.collect { errorMessage ->
                        Log.w(TAG, "Critical error from service: $errorMessage")
                        
                        // Critical errors from notification starts should trigger reconnection
                        // regardless of userStoppedManually flag (it's a new start attempt)
                        // Only skip if already reconnecting
                        if (service?.isReconnecting?.value != true) {
                            Log.i(TAG, "Triggering reconnection due to service critical error (notification start failure)")
                            // Reset userStoppedManually since this is a new start attempt from notification
                            service?.clearUserStoppedManually()
                            handleDisconnection(errorMessage, isInitialConnection = true)
                        } else {
                            Log.d(TAG, "Skipping reconnection trigger - already reconnecting")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to collect critical errors from service: ${e.message}")
                }
            }
        }
        
        viewModelScope.launch {
            storageRepository.isAudioEnableFlow.combine(storageRepository.isVideoEnableFlow) { isAudioEnable, isVideoEnable ->
                Pair(isAudioEnable, isVideoEnable)
            }.drop(1).collect { (_, _) ->
                val previousStreamer = streamer
                streamerFlow.emit(buildStreamerUseCase(previousStreamer))
                if (previousStreamer != streamer) {
                    previousStreamer?.release()
                }
            }
        }
        viewModelScope.launch {
            storageRepository.audioConfigFlow
                .collect { config ->
                    // Don't change audio config while streaming to avoid configuration conflicts
                    if (serviceStreamer?.isStreamingFlow?.value == true) {
                        Log.i(TAG, "Skipping audio config change - streamer is currently streaming")
                        return@collect
                    }
                    
                    // Don't change audio config when UI is in background - defer until resume
                    if (!isUiInForeground) {
                        Log.i(TAG, "Deferring audio config change - UI is in background")
                        pendingAudioConfig = config
                        return@collect
                    }

                    if (ActivityCompat.checkSelfPermission(
                            application,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        config?.let {
                            try {
                                serviceStreamer?.let { s -> applyAudioConfigSafely(s, it) }
                            } catch (t: Throwable) {
                                Log.e(TAG, "setAudioConfig failed", t)
                                _streamerErrorLiveData.postValue("setAudioConfig: ${t.message ?: t::class.java.simpleName}")
                            }
                        } ?: Log.i(TAG, "Audio is disabled")
                    } else {
                        // Permission not granted - defer until permission is granted
                        Log.i(TAG, "Deferring audio config - RECORD_AUDIO permission not granted yet")
                        pendingAudioConfig = config
                    }
                }
        }
        viewModelScope.launch {
            storageRepository.videoConfigFlow
                .collect { config ->
                    // Don't change video config while streaming to avoid configuration conflicts
                    if (serviceStreamer?.isStreamingFlow?.value == true) {
                        Log.i(TAG, "Skipping video config change - streamer is currently streaming")
                        return@collect
                    }
                    
                    // Don't change video config when UI is in background - surfaces may be invalid
                    // Save it to apply when UI resumes
                    if (!isUiInForeground) {
                        Log.i(TAG, "Deferring video config change - UI is in background")
                        pendingVideoConfig = config
                        return@collect
                    }

                    config?.let {
                        try {
                            serviceStreamer?.setVideoConfig(it)
                        } catch (t: Throwable) {
                            Log.e(TAG, "setVideoConfig failed", t)
                            _streamerErrorLiveData.postValue("setVideoConfig: ${t.message ?: t::class.java.simpleName}")
                        }
                    } ?: Log.i(TAG, "Video is disabled")
                }
        }
    }

    fun onZoomRationOnPinchChanged() {
        notifyPropertyChanged(BR.zoomRatio)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun initializeVideoSource() {
        viewModelScope.launch {
            val currentStreamer = serviceStreamer
            if (currentStreamer?.videoInput?.sourceFlow?.value == null) {
                currentStreamer?.setVideoSource(CameraSourceFactory(application))
            } else {
                Log.i(TAG, "Camera source already set")
            }
        }
    }

    fun startStream() {
        viewModelScope.launch {
            // Pre-flight checks with mutex held briefly
            val canStart = streamOperationMutex.withLock {
                Log.d(TAG, "startStream() - Acquired lock for pre-flight checks")
                
                // Use service's centralized check instead of local flag
                val serviceCanStart = service?.canStartStream() ?: false
                if (!serviceCanStart) {
                    Log.w(TAG, "Cannot start stream - service reports cannot start (cleanup in progress or blocked)")
                    _streamerErrorLiveData.postValue("Please wait - stopping previous stream...")
                    return@withLock false
                }
                
                // Only clear userStoppedManually for user-initiated starts, not reconnection attempts
                // Use service's centralized state
                val isCurrentlyReconnecting = service?.isReconnecting?.value ?: false
                if (!isCurrentlyReconnecting) {
                    service?.clearUserStoppedManually()
                    Log.i(TAG, "startStream() - Cleared userStoppedManually via service (user-initiated start)")
                } else {
                    Log.d(TAG, "startStream() - Keeping userStoppedManually as-is (reconnection attempt)")
                }
                return@withLock true
            }
            
            if (!canStart) {
                Log.d(TAG, "startStream() - Pre-flight check failed, aborting")
                return@launch
            }
            
            service?.setStreamStatus(StreamStatus.STARTING)
            Log.i(TAG, "startStream() called")
            
            // Lock stream rotation BEFORE starting to ensure it matches the user's
            // orientation preference.  If a fixed orientation is configured, honour it
            // instead of blindly reading the display.
            val fixedRotation = streamOrientationFlow.value.toSurfaceRotation()
            val currentRotation = if (fixedRotation != null) {
                Log.i(TAG, "Using fixed stream orientation setting: ${streamOrientationFlow.value}")
                fixedRotation
            } else {
                val windowManager = application.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                @Suppress("DEPRECATION")
                val displayRotation = windowManager.defaultDisplay.rotation
                Log.i(TAG, "Using display rotation (AUTO): $displayRotation")
                displayRotation
            }
            // Tell StreamPack's encoder about the rotation so it builds the correct dimensions
            try {
                (serviceStreamer as? IWithVideoRotation)?.setTargetRotation(currentRotation)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to set target rotation on streamer: ${t.message}")
            }
            service?.lockStreamRotation(currentRotation)
            Log.i(TAG, "Pre-locked stream rotation to $currentRotation before starting")
            
            val currentStreamer = serviceStreamer
            val serviceReady = _serviceReady.value

            Log.i(TAG, "startStream: serviceStreamer = $currentStreamer, serviceReady = $serviceReady")

            if (currentStreamer == null) {
                Log.w(TAG, "Service streamer not ready, cannot start stream")
                _streamerErrorLiveData.postValue("Streaming service not ready")
                return@launch
            }

            // Check if sources are configured
            val hasVideoSource = currentStreamer.videoInput?.sourceFlow?.value != null
            val hasAudioSource = currentStreamer.audioInput?.sourceFlow?.value != null
            val videoSource = currentStreamer.videoInput?.sourceFlow?.value
            val audioSource = currentStreamer.audioInput?.sourceFlow?.value

            Log.i(TAG, "Source check before stream start:")
            Log.i(TAG, "  hasVideoSource: $hasVideoSource (${videoSource?.javaClass?.simpleName})")
            Log.i(TAG, "  hasAudioSource: $hasAudioSource (${audioSource?.javaClass?.simpleName})")

            // Special case: If video source is bitmap (RTMP fallback) but no audio, set appropriate audio
            if (hasVideoSource && !hasAudioSource && videoSource is IBitmapSource) {
                Log.w(TAG, "Bitmap source detected without audio - setting audio source")
                try {
                    setAudioSourceBasedOnVideoSource()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set audio for bitmap source: ${e.message}")
                }
            }

            if (!hasVideoSource || !hasAudioSource) {
                Log.w(TAG, "Sources not fully configured - initializing...")
                initializeStreamerSources()
                kotlinx.coroutines.delay(500)
                
                // Re-check after initialization
                val videoAfterInit = currentStreamer.videoInput?.sourceFlow?.value
                val audioAfterInit = currentStreamer.audioInput?.sourceFlow?.value
                Log.i(TAG, "After initialization - Video: ${videoAfterInit?.javaClass?.simpleName}, Audio: ${audioAfterInit?.javaClass?.simpleName}")
                
                if (videoAfterInit == null || audioAfterInit == null) {
                    val error = "Failed to initialize sources - Video: ${videoAfterInit != null}, Audio: ${audioAfterInit != null}"
                    Log.e(TAG, error)
                    _streamerErrorLiveData.postValue(error)
                    return@launch
                }
            }

            service?.setStreamStatus(StreamStatus.CONNECTING)
            Log.i(TAG, "startStream: Set status to CONNECTING")
            var autoRetryTriggered = false
            try {
                val success = doStartStream(shouldAutoRetry = true)
                if (!success) {
                    Log.e(TAG, "Initial connection failed - isReconnecting=${service?.isReconnecting?.value}, status=${streamStatus.value}")
                    // handleDisconnection was already called in doStartStream
                    // Check if reconnection is active before clearing status
                    if (service?.isReconnecting?.value == true) {
                        autoRetryTriggered = true
                        Log.i(TAG, "Auto-retry triggered, keeping status=${streamStatus.value}")
                    } else {
                        service?.setStreamStatus(StreamStatus.ERROR)
                        Log.i(TAG, "No auto-retry, setting status to ERROR")
                    }
                    return@launch
                }
                Log.i(TAG, "Stream started successfully")
                service?.setStreamStatus(StreamStatus.STREAMING)
            } catch (e: Throwable) {
                Log.e(TAG, "startStream failed: ${e.message}, isReconnecting=${service?.isReconnecting?.value}", e)
                // Don't show error dialog if reconnection will handle it
                if (service?.isReconnecting?.value != true) {
                    _streamerErrorLiveData.postValue("startStream: ${e.message ?: e::class.java.simpleName}")
                }
                // Check if reconnection was triggered by the exception
                if (service?.isReconnecting?.value == true) {
                    autoRetryTriggered = true
                    Log.i(TAG, "Exception but reconnecting, keeping status=${streamStatus.value}")
                } else {
                    service?.setStreamStatus(StreamStatus.ERROR)
                    Log.i(TAG, "Exception and no reconnection, setting status to ERROR")
                }
            } finally {
                Log.i(TAG, "startStream finally: autoRetryTriggered=$autoRetryTriggered, isReconnecting=${service?.isReconnecting?.value}, status=${streamStatus.value}, userStoppedManually=${service?.userStoppedManually?.value}")
                // Don't override status if auto-retry was triggered or if we're reconnecting
                // Also set to NOT_STREAMING if user manually stopped (handles race condition)
                if (service?.userStoppedManually?.value == true) {
                    Log.i(TAG, "startStream finally: User stopped manually, setting NOT_STREAMING")
                    service?.setStreamStatus(StreamStatus.NOT_STREAMING)
                } else if (!autoRetryTriggered && service?.isReconnecting?.value != true && streamStatus.value != StreamStatus.STREAMING) {
                    Log.w(TAG, "startStream finally: Overriding status to NOT_STREAMING")
                    service?.setStreamStatus(StreamStatus.NOT_STREAMING)
                } else {
                    Log.i(TAG, "startStream finally: NOT overriding status, keeping ${streamStatus.value}")
                }
            }
        }
    }

    /**
     * Start streaming with MediaProjection support.
     * Request MediaProjection permission and keep it active during streaming.
     */
    fun startStreamWithMediaProjection(
        mediaProjectionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Clear manual stop flag when starting a new stream
        service?.clearUserStoppedManually()
        Log.i(TAG, "startStreamWithMediaProjection() - Reset userStoppedManually=false")
        
        service?.setStreamStatus(StreamStatus.STARTING)

        val existingProjection = startupMediaProjection
            ?: streamingMediaProjection
            ?: mediaProjectionHelper.getMediaProjection()

        // Android invalidates a MediaProjection token for createVirtualDisplay once its virtual
        // display has been stopped. Reusing an exhausted token here would crash, so fall through
        // to requesting a fresh one instead when screen video capture is active.
        val needsFreshProjectionForVideo = _isScreenSource.value == true &&
                existingProjection != null &&
                MediaProjectionVideoSourceFactory.isProjectionExhaustedForVideo(existingProjection)

        if (existingProjection != null && !needsFreshProjectionForVideo) {
            Log.i(TAG, "startStreamWithMediaProjection: reusing existing MediaProjection token")
            streamingMediaProjection = existingProjection
            startupMediaProjection = existingProjection
            
            viewModelScope.launch {
                try {
                    // Only recreate MediaProjection video surfaces for SCREEN.
                    // RTMP/SYS AUDIO also reuse this token; blindly re-applying here
                    // would replace the current video source with screen capture.
                    if (_isScreenSource.value == true) {
                        serviceStreamer?.let {
                            switchToMediaProjectionVideoSource(it, existingProjection)
                        }
                    }

                    setAudioSourceBasedOnVideoSource()
                    setupAudioLevelMonitoring()
                    service?.setStreamStatus(StreamStatus.CONNECTING)
                    startStreamInternal(onSuccess, onError)
                } catch (e: Exception) {
                    val error = "Failed to configure MediaProjection audio: ${e.message}"
                    Log.e(TAG, error, e)
                    _streamerErrorLiveData.postValue(error)
                    service?.setStreamStatus(StreamStatus.ERROR)
                    onError(error)
                }
            }
            return
        }

        mediaProjectionHelper.requestProjection(mediaProjectionLauncher) { mediaProjection ->
            Log.i(TAG, "MediaProjection callback received - mediaProjection: ${if (mediaProjection != null) "SUCCESS" else "NULL"}")
            if (mediaProjection != null) {
                streamingMediaProjection = mediaProjection
                // The startup MP was invalidated when requestProjection() called release() to start
                // a fresh service. Sync startupMediaProjection to the new valid token so SYS AUDIO
                // continues to work if/when this stream ends and the user returns to camera.
                startupMediaProjection = mediaProjection
                Log.i(TAG, "MediaProjection acquired for streaming session - starting setup...")

                viewModelScope.launch {
                    try {
                        // Recreate SCREEN surfaces after a first-time grant too.
                        // Preview may have been created before this token existed, or
                        // orientation may have changed while the permission dialog was up.
                        if (_isScreenSource.value == true) {
                            serviceStreamer?.let {
                                switchToMediaProjectionVideoSource(it, mediaProjection)
                            }
                        }

                        // Set appropriate audio source based on current video source
                        setAudioSourceBasedOnVideoSource()

                        // Re-enable VU meter now that MediaProjection audio source is set.
                        // Monitoring was paused when the user switched to RTMP source pre-stream.
                        // setupAudioLevelMonitoring() waits for source + config to be ready internally.
                        setupAudioLevelMonitoring()

                        // Start the actual stream
                        service?.setStreamStatus(StreamStatus.CONNECTING)
                        startStreamInternal(onSuccess, onError)
                    } catch (e: Exception) {
                        val error = "Failed to configure MediaProjection audio: ${e.message}"
                        Log.e(TAG, error, e)
                        _streamerErrorLiveData.postValue(error)
                        service?.setStreamStatus(StreamStatus.ERROR)
                        onError(error)  // Also call callback for custom handling if needed
                    }
                }
            } else {
                val error = "MediaProjection permission required for streaming"
                Log.e(TAG, error)
                _streamerErrorLiveData.postValue(error)
                service?.setStreamStatus(StreamStatus.ERROR)
                onError(error)  // Also call callback for custom handling if needed
            }
        }
    }

    private fun startStreamInternal(onSuccess: () -> Unit, onError: (String) -> Unit) {
        Log.i(TAG, "startStreamInternal called - beginning setup...")
        viewModelScope.launch {
            try {
                val descriptor = storageRepository.endpointDescriptorFlow.first()
                Log.i(TAG, "Starting stream with descriptor: $descriptor")
                Log.i(TAG, "About to call startServiceStreaming()...")
                val success = startServiceStreaming(descriptor, shouldSuppressErrors = true)
                
                if (!success) {
                    Log.e(TAG, "startServiceStreaming() returned false - stream start failed")
                    val errorMessage = "Connection failed - unable to establish stream"
                    
                    // Check if user stopped manually - if so, don't trigger reconnection
                    if (service?.userStoppedManually?.value == true) {
                        Log.d(TAG, "User stopped stream, skipping reconnection")
                        return@launch
                    }
                    
                    // Trigger auto-retry unless already reconnecting
                    if (service?.isReconnecting?.value != true) {
                        Log.i(TAG, "Connection failed, triggering auto-retry (error dialog suppressed)")
                        handleDisconnection(errorMessage, isInitialConnection = true)
                    }
                    return@launch
                }
                
                Log.i(TAG, "startServiceStreaming() completed successfully")
                Log.i(TAG, "Stream setup completed successfully, calling onSuccess()")
                service?.setStreamStatus(StreamStatus.STREAMING)
                onSuccess()
            } catch (e: Throwable) {
                val error = "Stream start failed: ${e.message ?: e::class.java.simpleName}"
                Log.e(TAG, "STREAM START EXCEPTION: $error", e)
                
                // Check if user stopped manually - if so, don't trigger reconnection
                if (service?.userStoppedManually?.value == true) {
                    Log.d(TAG, "User stopped stream, skipping reconnection")
                    return@launch
                }
                
                // Trigger auto-retry unless already reconnecting
                if (service?.isReconnecting?.value != true) {
                    Log.i(TAG, "Connection failed with exception, triggering auto-retry (error dialog suppressed)")
                    handleDisconnection(error, isInitialConnection = true)
                } else {
                    Log.d(TAG, "Exception during reconnection, error dialog suppressed")
                }
            } finally {
                Log.i(TAG, "startStreamInternal finally block")
            }
        }
    }

    fun stopStream() {
        // CRITICAL: Mark user stopped IMMEDIATELY via service, BEFORE acquiring mutex
        // This ensures error handlers see the flag even if they fire before we get the lock
        service?.markUserStoppedManually()
        Log.i(TAG, "stopStream() - Marked userStoppedManually via service before acquiring mutex")
        
        // Also immediately cancel any pending reconnection timer
        reconnectTimer.stop()
        Log.i(TAG, "stopStream() - Cancelled reconnection timer")
        
        // If cleanup is already in progress, just update UI and return
        // Don't try to stop again - it will just get blocked
        val cleanupInProgress = service?.isCleanupInProgress ?: false
        if (cleanupInProgress) {
            Log.i(TAG, "stopStream() - Cleanup already in progress, just updating UI state")
            service?.cancelReconnection()
            service?.setStreamStatus(StreamStatus.NOT_STREAMING)
            return
        }
        
        viewModelScope.launch {
            // Track if we took an early exit path (these paths handle their own cleanup)
            var tookEarlyExit = false
            
            streamOperationMutex.withLock {
                Log.d(TAG, "stopStream() - Acquired lock")
                
                try {
                    val currentStreamer = serviceStreamer

                    if (currentStreamer == null) {
                        Log.w(TAG, "Service streamer not ready, cannot stop stream")
                        return@launch
                    }

                    val currentStreamingState = currentStreamer.isStreamingFlow.value
                    val currentStatus = streamStatus.value
                    val isCurrentlyReconnecting = service?.isReconnecting?.value ?: false
                    Log.i(TAG, "stopStream() called - Streaming state: $currentStreamingState, Status: $currentStatus, isReconnecting: $isCurrentlyReconnecting")

                    // If reconnecting, always cancel reconnection regardless of streaming state
                    if (isCurrentlyReconnecting) {
                        Log.i(TAG, "Cancelling active reconnection...")
                        
                        // userStoppedManually already marked via service before acquiring mutex
                        
                        // Cancel reconnection timer and clear service flags
                        reconnectTimer.stop()
                        service?.cancelReconnection()
                        
                        // Update UI state so user gets instant feedback
                        service?.setStreamStatus(StreamStatus.NOT_STREAMING)
                        Log.i(TAG, "UI updated immediately - reconnection cancelled by user")
                        
                        // Unlock stream rotation since we're truly stopped
                        service?.unlockStreamRotation()
                        
                        // Stop SRTLA proxy since we're aborting the session entirely
                        if (SrtlaManager.isRunning) {
                            Log.i(TAG, "stopStream() - Stopping SRTLA during reconnection cancel")
                            SrtlaManager.stop()
                        }

                        // If we were actually streaming, do slow cleanup in background with flag
                        // If just reconnecting/connecting, close quickly without the flag
                        if (currentStreamingState == true) {
                            // Was actually streaming, need slow cleanup
                            service?.isCleanupInProgress = true
                            Log.i(TAG, "stopStream() - Set service.isCleanupInProgress=true for reconnection cancel cleanup (was streaming)")
                            viewModelScope.launch {
                                try {
                                    Log.d(TAG, "Closing endpoint in background (was streaming, may take time)...")
                                    withTimeout(3000) {
                                        currentStreamer.close()
                                    }
                                    Log.i(TAG, "Reconnection cancelled - endpoint closed")
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error closing endpoint during reconnection cancel: ${e.message}")
                                } finally {
                                    service?.isCleanupInProgress = false
                                    Log.i(TAG, "stopStream() - Cleared service.isCleanupInProgress after reconnection cancel cleanup")
                                }
                            }
                        } else {
                            // Was just connecting/reconnecting, close quickly
                            try {
                                Log.d(TAG, "Closing endpoint (was just connecting, should be fast)...")
                                withContext(Dispatchers.IO) {
                                    withTimeout(1000) {
                                        currentStreamer.close()
                                    }
                                }
                                Log.i(TAG, "Reconnection cancelled - endpoint closed quickly")
                            } catch (e: Exception) {
                                Log.w(TAG, "Error closing endpoint during reconnection cancel: ${e.message}")
                            }
                        }
                        
                        tookEarlyExit = true
                        return@launch
                    }

                    // If already stopped and not in CONNECTING state, don't do anything
                    if (currentStreamingState != true && currentStatus != StreamStatus.CONNECTING) {
                        Log.i(TAG, "Stream is already stopped, skipping stop sequence")
                        tookEarlyExit = true
                        return@launch
                    }
                    
                    // If in CONNECTING state but not streaming, force stop the connection attempt
                    // This handles initial connection attempts that aren't part of reconnection
                    if (currentStatus == StreamStatus.CONNECTING && currentStreamingState != true) {
                        Log.i(TAG, "Stopping connection attempt (userStoppedManually already set)")
                        
                        // userStoppedManually already set to true before acquiring mutex
                        
                        // Stop SRTLA proxy if it was started for this connection attempt
                        if (SrtlaManager.isRunning) {
                            Log.i(TAG, "stopStream() - Stopping SRTLA during connection abort")
                            SrtlaManager.stop()
                        }
                        
                        // Cancel any pending reconnection attempts
                        reconnectTimer.stop()
                        // isReconnecting managed by service
                        
                        // Update UI state so user gets instant feedback
                        service?.setStreamStatus(StreamStatus.NOT_STREAMING)
                        Log.i(TAG, "UI updated immediately - connection attempt cancelled by user")
                        
                        // Unlock stream rotation since we're truly stopped
                        service?.unlockStreamRotation()
                        
                        // Close endpoint synchronously - it should be fast since we never actually streamed
                        // No need for isCleanupInProgress flag since this is quick
                        try {
                            Log.d(TAG, "Closing endpoint (connection attempt never succeeded)...")
                            withContext(Dispatchers.IO) {
                                withTimeout(1000) {
                                    currentStreamer.close()
                                }
                            }
                            Log.i(TAG, "Connection attempt aborted - endpoint closed")
                        } catch (e: Exception) {
                            Log.w(TAG, "Error closing endpoint during connection abort: ${e.message}")
                        }
                        
                        tookEarlyExit = true
                        return@launch
                    }

                    Log.i(TAG, "Stopping stream...")

                    // Keep the MediaProjection token alive after stream stops — it is transferred to
                    // startupMediaProjection so SYS AUDIO and future streams can reuse it without
                    // requiring a new permission dialog.
                    if (streamingMediaProjection != null) {
                        startupMediaProjection = streamingMediaProjection
                        streamingMediaProjection = null
                        Log.i(TAG, "MediaProjection transferred to startupMediaProjection after stream stop")
                    } else {
                        Log.i(TAG, "No streamingMediaProjection to transfer; keeping existing startupMediaProjection")
                    }

                    // Stop streaming via helper method
                    try {
                        stopServiceStreaming()
                        Log.i(TAG, "Stream stop command sent")
                    } catch (e: Exception) {
                        Log.w(TAG, "Error stopping stream: ${e.message}", e)
                    }

                    // Remove bitrate regulator
                    try {
                        currentStreamer.bitrateRegulatorControllerFactory = null
                        Log.i(TAG, "Bitrate regulator removed")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not remove bitrate regulator: ${e.message}")
                    }

                } catch (e: Throwable) {
                    Log.e(TAG, "stopStream failed", e)
                    // Force clear state — on error, stop everything and allow re-acquisition
                    streamingMediaProjection?.stop()
                    streamingMediaProjection = null
                    startupMediaProjection = null
                } finally {
                    // Set status to NOT_STREAMING FIRST so any pending callbacks see it immediately
                    service?.setStreamStatus(StreamStatus.NOT_STREAMING)
                    
                    // User stopped flag already set via service before acquiring mutex
                    
                    // Cancel any pending reconnection attempts (already done via service.markUserStoppedManually)
                    reconnectTimer.stop()
                    
                    // Apply any rotation that was ignored during reconnection
                    rotationIgnoredDuringReconnection?.let { ignoredRotation ->
                        viewModelScope.launch {
                            try {
                                serviceStreamer?.setTargetRotation(ignoredRotation)
                                Log.i(TAG, "Applied rotation ($ignoredRotation) that was ignored during reconnection")
                            } catch (t: Throwable) {
                                Log.w(TAG, "Failed to apply ignored rotation: $t")
                            }
                        }
                        rotationIgnoredDuringReconnection = null
                    }
                    
                    // Only set cleanup flag if we didn't take an early exit path
                    // (Early exit paths manage their own cleanup flags)
                    if (!tookEarlyExit) {
                        // Mark that cleanup will continue outside mutex
                        // This prevents race condition if user rapidly taps stop->start
                        service?.isCleanupInProgress = true
                        Log.i(TAG, "stopStream() - Set service.isCleanupInProgress=true before releasing lock")
                    }
                }
            } // Release mutex lock
            Log.d(TAG, "stopStream() - Released lock")
            
            // Skip cleanup if we took an early exit (those paths handle their own cleanup)
            if (tookEarlyExit) {
                Log.d(TAG, "stopStream() - Skipping main cleanup path (early exit taken)")
                return@launch
            }
            
            // Move cleanup to IO dispatcher to avoid blocking main thread for up to 8+ seconds
            // Wait for stream to stop and close endpoint OUTSIDE the mutex
            // Protected by isCleanupInProgress flag to prevent start() racing with cleanup
            withContext(Dispatchers.IO) {
                try {
                    val currentStreamer = serviceStreamer
                    if (currentStreamer != null) {
                        // Wait for stream to actually stop before closing endpoint
                        // This prevents calling close() while stopStream() is still executing
                        var retries = 0
                        while (currentStreamer.isStreamingFlow.value == true && retries < 50) {
                            kotlinx.coroutines.delay(100)
                            retries++
                        }
                        
                        if (retries >= 50) {
                            Log.w(TAG, "Timeout waiting for stream to stop - forcing close anyway")
                        }
                        
                        // Close the endpoint connection to allow fresh connection on next start
                        // Without this, the endpoint stays open and cannot be reopened on next start
                        try {
                            withTimeout(3000) {
                                currentStreamer.close()
                            }
                            Log.i(TAG, "Endpoint connection closed - ready for next start")
                        } catch (e: Exception) {
                            Log.e(TAG, "CRITICAL: Failed to close endpoint - second start will fail!", e)
                        }
                        
                        // Do not clear MediaProjection from the service on stream stop — the token
                        // is transferred to startupMediaProjection and can be reused for SYS AUDIO
                        // or the next stream without a new permission dialog.
                        
                        if (currentStreamer.isStreamingFlow.value == true) {
                            Log.w(TAG, "Stream did not stop cleanly after 5 seconds - forcing cleanup")
                        }
                        
                        // Restart VU meter after stream stops — audio source remains valid regardless
                        // of video source since startupMediaProjection is always available.
                        setupAudioLevelMonitoring()
                        Log.i(TAG, "Restarted VU meter after stream stop")

                        Log.i(TAG, "Stream stop completed successfully")
                        
                        // Explicitly unlock stream rotation in the service
                        // This allows rotation to follow sensor again when truly stopped
                        service?.unlockStreamRotation()
                    } else {
                        Log.w(TAG, "serviceStreamer is null during cleanup - skipping streamer cleanup")
                    }
                } finally {
                    // ALWAYS clear cleanup flag - even if serviceStreamer was null or cleanup failed
                    // This prevents the flag from getting stuck and blocking future stream starts
                    service?.isCleanupInProgress = false
                    Log.i(TAG, "stopStream() - Cleared service.isCleanupInProgress, cleanup complete")
                }
            }
        }
    }

    /**
     * Handles connection loss and triggers automatic reconnection.
     * Inspired by Moblin's reconnection pattern with 5-second delay.
     * 
     * @param reason The reason for the disconnection
     * @param isInitialConnection If true, skips status check (for initial connection failures)
     */
    private fun handleDisconnection(reason: String, isInitialConnection: Boolean = false) {
        val userStopped = service?.userStoppedManually?.value ?: false
        val isCurrentlyReconnecting = service?.isReconnecting?.value ?: false
        Log.i(TAG, "handleDisconnection called: reason='$reason', isInitialConnection=$isInitialConnection, currentStatus=${streamStatus.value}, isReconnecting=$isCurrentlyReconnecting, userStopped=$userStopped")
        
        // Use service's centralized method to attempt beginning reconnection
        // This handles all the checks (userStoppedManually, isReconnecting=${service?.isReconnecting?.value}, cleanup in progress)
        val canReconnect = service?.beginReconnection(reason) ?: false
        if (!canReconnect) {
            Log.i(TAG, "Reconnection skipped by service (user stopped, already reconnecting, or cleanup in progress)")
            return
        }

        // Check if we should attempt reconnection
        val currentStreamer = serviceStreamer
        if (currentStreamer == null) {
            Log.w(TAG, "Cannot reconnect: streamer not available")
            service?.cancelReconnection()
            return
        }

        lastDisconnectReason = reason
        
        // Clear any pending rotation changes - we want to maintain current locked orientation
        if (pendingTargetRotation != null) {
            Log.i(TAG, "Clearing pending rotation $pendingTargetRotation - maintaining locked orientation during reconnection")
            pendingTargetRotation = null
        }
        
        Log.i(TAG, "Connection lost: $reason - will attempt reconnection in 5 seconds")
        // Status and message already set by service.beginReconnection()
        // Just update our local _streamStatus to match
        service?.setStreamStatus(StreamStatus.CONNECTING)

        // Stop current stream cleanly before reconnecting
        viewModelScope.launch {
            try {
                // Cancel any RTMP retry/upgrade tasks before reconnecting
                // This prevents stale MediaProjection token from being used
                rtmpSourceController.cancelRetryJob()
                Log.d(TAG, "Cancelled RTMP retry job before reconnection")
                
                // Clean up current connection
                // Only call stopStream() if stream was actually started
                // If open() failed, the stream is not running and stopStream() will hang
                val isCurrentlyStreaming = currentStreamer.isStreamingFlow.value == true
                if (isCurrentlyStreaming) {
                    Log.d(TAG, "Stopping failed stream before reconnection...")
                    currentStreamer.stopStream()
                    Log.d(TAG, "Stream stopped")
                } else {
                    Log.d(TAG, "Stream was never started - skipping stopStream()")
                }
                
                // Remove any bitrate regulator
                try {
                    currentStreamer.bitrateRegulatorControllerFactory = null
                    Log.d(TAG, "Bitrate regulator removed")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not remove bitrate regulator: ${e.message}")
                }
                
                // Close the endpoint connection before reconnecting
                // This ensures clean state for reconnection attempt
                try {
                    withTimeout(3000) {
                        currentStreamer.close()
                    }
                    Log.i(TAG, "Endpoint closed before reconnection - clean slate established")
                } catch (e: Exception) {
                    Log.e(TAG, "CRITICAL: Failed to close endpoint before reconnection!", e)
                }

                // Wait for stream to actually stop before reconnecting
                // This ensures clean state for the reconnection attempt
                var retries = 0
                while (currentStreamer.isStreamingFlow.value == true && retries < 50) {
                    kotlinx.coroutines.delay(100)
                    retries++
                }
                
                if (currentStreamer.isStreamingFlow.value == true) {
                    Log.w(TAG, "Stream did not stop cleanly after 5 seconds during reconnection cleanup")
                }
                
                Log.d(TAG, "Stream confirmed stopped - ready for reconnection (keeping existing audio/video sources)")

                // NOTE: We do NOT reset audio/video sources during reconnection because:
                // - RTMP source needs to keep MediaProjection audio
                // - Camera source already has microphone audio
                // - Changing sources mid-reconnection can cause initialization issues
                // Sources are only reset during manual stopStream() to prepare for new configuration

                // Schedule reconnection after 5 seconds
                reconnectTimer.startSingleShot(timeoutSeconds = 5) {
                    // Double-check if user manually stopped during the delay (using service state)
                    val userStopped = service?.userStoppedManually?.value ?: false
                    if (userStopped) {
                        Log.d(TAG, "User stopped streaming during delay, cancelling reconnection")
                        service?.cancelReconnection()
                        return@startSingleShot
                    }
                    Log.i(TAG, "Attempting to reconnect after disconnection...")
                    attemptReconnection()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during pre-reconnect cleanup: ${e.message}", e)
                if (SrtlaManager.isRunning) SrtlaManager.stop()
                service?.cancelReconnection()
                service?.setStreamStatus(StreamStatus.ERROR)
                _streamerErrorLiveData.postValue("Reconnection failed: ${e.message}")
            }
        }
    }

    /**
     * Attempts to reconnect to the stream endpoint.
     */
    private fun attemptReconnection() {
        viewModelScope.launch {
            try {
                // Check if user manually stopped streaming FIRST (using service state)
                val userStopped = service?.userStoppedManually?.value ?: false
                if (userStopped) {
                    Log.d(TAG, "User stopped streaming, cancelling reconnection attempt")
                    service?.cancelReconnection()
                    return@launch
                }
                
                // Check if cleanup is still in progress from a previous stop
                val cleanupInProgress = service?.isCleanupInProgress ?: false
                if (cleanupInProgress) {
                    Log.w(TAG, "Cleanup still in progress, delaying reconnection attempt")
                    // Reschedule after a short delay to let cleanup finish
                    reconnectTimer.startSingleShot(timeoutSeconds = 2) {
                        val stillUserStopped = service?.userStoppedManually?.value ?: false
                        if (!stillUserStopped) {
                            attemptReconnection()
                        }
                    }
                    return@launch
                }
                
                val currentStreamer = serviceStreamer
                if (currentStreamer == null) {
                    Log.w(TAG, "Reconnection failed: streamer no longer available")
                    if (SrtlaManager.isRunning) SrtlaManager.stop()
                    service?.cancelReconnection()
                    service?.setStreamStatus(StreamStatus.ERROR)
                    return@launch
                }

                Log.i(TAG, "Executing reconnection attempt...")
                service?.setReconnectionMessage("Reconnecting...")
                
                // Set status to CONNECTING so UI shows stop button and it actually works
                service?.setStreamStatus(StreamStatus.CONNECTING)

                // Validate that both sources exist before attempting reconnection
                val (sourcesValid, sourceError) = validateSourcesConfigured(currentStreamer)
                if (!sourcesValid) {
                    Log.e(TAG, "Reconnection failed: $sourceError")
                    if (SrtlaManager.isRunning) SrtlaManager.stop()
                    service?.setReconnectionMessage("Reconnection failed. Sources not configured")
                    service?.cancelReconnection()
                    service?.setStreamStatus(StreamStatus.ERROR)
                    return@launch
                }
                
                // During reconnection, DON'T reset audio source - it may invalidate MediaProjection token
                // The audio source is already correctly configured from before the disconnection
                val currentVideoSource = currentStreamer.videoInput?.sourceFlow?.value!!
                val currentAudioSource = currentStreamer.audioInput?.sourceFlow?.value!!
                
                Log.d(TAG, "Reconnection video source: ${currentVideoSource.javaClass.simpleName}")
                Log.d(TAG, "Reconnection audio source: ${currentAudioSource.javaClass.simpleName} (keeping existing)")

                // Use the same stream start logic as initial connection
                // Pass shouldAutoRetry=true to suppress error dialogs during reconnection
                val success = doStartStream(shouldAutoRetry = true)
                
                if (success) {
                    Log.i(TAG, "Reconnection successful!")
                    service?.completeReconnection()
                    service?.setStreamStatus(StreamStatus.STREAMING)
                    
                    // Clear any stored rotation since we successfully reconnected with locked orientation
                    rotationIgnoredDuringReconnection = null
                    
                    // If reconnected with bitmap source and user had RTMP toggled,
                    // restart the RTMP source retry loop so it eventually switches back to RTMP video.
                    // This handles the case where RTMP disconnect restart failed, we fell through
                    // to handleDisconnection -> attemptReconnection, and the publishing stream
                    // recovered but the RTMP source retry was never started.
                    ensureRtmpRetryRunning(currentStreamer)
                } else {
                    // Check if user stopped before showing failure message (using service state)
                    val userStopped = service?.userStoppedManually?.value ?: false
                    if (userStopped) {
                        Log.d(TAG, "User stopped during reconnection failure handling, skipping retry")
                        service?.cancelReconnection()
                        return@launch
                    }
                    
                    Log.w(TAG, "Reconnection attempt failed - will retry again in 5 seconds")
                    service?.setReconnectionMessage("Reconnection failed. Retrying in 5 seconds")
                    
                    // While SRT publishing is retrying, also try to restore RTMP source in parallel.
                    // If RTMP comes back before SRT, the video source switches from bitmap to RTMP,
                    // so when SRT finally reconnects it streams RTMP video instead of bitmap.
                    ensureRtmpRetryRunning(currentStreamer)
                    
                    // Keep isReconnecting = true for retry attempt
                    // Schedule another reconnection attempt directly (don't call handleDisconnection as it would be blocked)
                    reconnectTimer.startSingleShot(timeoutSeconds = 5) {
                        Log.i(TAG, "Retrying reconnection after previous failure...")
                        attemptReconnection()
                    }
                    return@launch
                }
            } catch (e: Exception) {
                // Check if user stopped before showing failure message
                if (service?.userStoppedManually?.value == true) {
                    Log.d(TAG, "User stopped during reconnection exception handling, skipping retry")
                    // isReconnecting managed by service
                    _reconnectionStatusLiveData.value = null
                    return@launch
                }
                
                Log.e(TAG, "Reconnection attempt threw exception: ${e.message}", e)
                _reconnectionStatusLiveData.postValue("Reconnection failed. Retrying in 5 seconds")
                
                // While SRT publishing is retrying, also try to restore RTMP source in parallel
                serviceStreamer?.let { ensureRtmpRetryRunning(it) }
                
                // Keep isReconnecting = true for retry attempt
                // Schedule another reconnection attempt directly
                reconnectTimer.startSingleShot(timeoutSeconds = 5) {
                    Log.i(TAG, "Retrying reconnection after exception...")
                    attemptReconnection()
                }
                return@launch
            }
        }
    }
    
    /**
     * Start the RTMP source retry loop if it's not already running,
     * the video source is bitmap, and the user toggled RTMP ON.
     * This allows RTMP playback to recover in parallel with SRT publishing reconnection.
     */
    private suspend fun ensureRtmpRetryRunning(currentStreamer: SingleStreamer) {
        rtmpSourceController.restartRtmpRetryIfNeeded(currentStreamer)
    }
    
    private fun setupAudioLevelMonitoring() {
        audioLevelMonitor.setupAudioLevelMonitoring()
    }
    
    /**
     * Disable audio level monitoring (e.g., when not streaming to save CPU).
     */
    private fun disableAudioLevelMonitoring() {
        audioLevelMonitor.disableAudioLevelMonitoring()
    }

    /**
     * Apply an audio config change, pausing VU meter capture if active.
     * AudioRecord can't be reconfigured while recording, so we must
     * stop capture first and restart it after.
     */
    private suspend fun applyAudioConfigSafely(streamer: io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer, config: io.github.thibaultbee.streampack.core.elements.encoders.AudioCodecConfig) {
        val wasMonitoring = audioLevelMonitor.isMonitoringActive
        if (wasMonitoring) disableAudioLevelMonitoring()
        streamer.setAudioConfig(config)
        if (wasMonitoring) setupAudioLevelMonitoring()
    }


    fun setMute(isMuted: Boolean) {
        // Perform mute operations off the main thread to avoid blocking UI.
        viewModelScope.launch(Dispatchers.Default) {
            // Prefer calling the bound service to centralize mutation and notification updates
            val svc = streamerService
            if (svc != null) {
                try {
                    svc.setMuted(isMuted)
                    // Show toast message
                    val message = if (isMuted) {
                        application.getString(R.string.service_notification_action_mute)
                    } else {
                        application.getString(R.string.service_notification_action_unmute)
                    }
                    _toastMessageLiveData.postValue(message)
                    return@launch
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to set mute via service: ${t.message}")
                }
            }

            // Fallback: directly write to streamer audio input for backward compatibility
            try {
                streamer?.audioInput?.isMuted = isMuted
                // Ensure UI reflects the change immediately even if service wasn't bound
                _isMutedLiveData.postValue(isMuted)
                // Show toast message
                val message = if (isMuted) {
                    application.getString(R.string.service_notification_action_mute)
                } else {
                    application.getString(R.string.service_notification_action_unmute)
                }
                _toastMessageLiveData.postValue(message)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to set mute directly on streamer: ${t.message}")
            }
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    fun switchBackToFront(): Boolean = cameraControlsController.switchBackToFront()

    @RequiresPermission(Manifest.permission.CAMERA)
    fun toggleCamera() = cameraControlsController.toggleCamera()
    
    /**
     * Toggle audio monitoring - intelligently switches between RTMP audio and microphone monitoring
     */
    fun toggleMonitorAudio() = audioRoutingController.toggleMonitorAudio()

    /**
     * Set monitor UI state without invoking start/stop logic.
     * Useful when synchronizing UI with the service snapshot on bind.
     */
    fun setMonitorUiState(state: Boolean) = audioRoutingController.setMonitorUiState(state)
    
    /**
     * Apply audio monitoring based on current video/audio source configuration
     */
    private fun applyMonitorAudioState() = audioRoutingController.applyMonitorAudioState()
    
    /**
     * Stop all audio monitoring
     */
    private fun stopAllMonitoring() = audioRoutingController.stopAllMonitoring()
    
    /**
     * Load available cameras and update LiveData
     */
    fun loadAvailableCameras() = cameraControlsController.loadAvailableCameras()

    @RequiresPermission(Manifest.permission.CAMERA)
    fun toggleVideoSource(
        mediaProjectionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>? = null,
        rtmpIndex: Int = 1
    ) = rtmpSourceController.toggleVideoSource(mediaProjectionLauncher, rtmpIndex)

    fun toggleUvcSource() = uvcSourceController.toggleUvcSource()

    val isCameraSource: LiveData<Boolean>
        get() = serviceReadyFlow.flatMapLatest { ready ->
            if (ready && serviceStreamer != null) {
                Log.d(TAG, "isCameraSource: serviceStreamer available, checking video source")
                serviceStreamer!!.videoInput?.sourceFlow?.map { source ->
                    val isCam = source is ICameraSource
                    Log.d(TAG, "isCameraSource: video source = $source, isCameraSource = $isCam")
                    isCam
                } ?: kotlinx.coroutines.flow.flowOf(false)
            } else {
                Log.d(TAG, "isCameraSource: service not ready or serviceStreamer null, returning false")
                kotlinx.coroutines.flow.flowOf(false)
            }
        }.asLiveData()

    /**
     * Expose user toggle state for UVC button
     */
    val isUvcSource: LiveData<Boolean> get() = uvcSourceController.isUvcSource

    val isScreenSource: LiveData<Boolean> get() = screenSourceController.isScreenSource

    fun toggleScreenSource(mediaProjectionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>? = null) =
        screenSourceController.toggleScreenSource(mediaProjectionLauncher)

    private suspend fun switchToMediaProjectionVideoSource(currentStreamer: SingleStreamer, projection: MediaProjection) =
        screenSourceController.switchToMediaProjectionVideoSource(currentStreamer, projection)

    /**
     * Expose user toggle state for RTMP button.
     * True when any RTMP source is active.
     */
    val isRtmpSource: LiveData<Boolean> get() = rtmpSourceController.isRtmpSource
    val activeRtmpIndex: LiveData<Int?> get() = rtmpSourceController.activeRtmpIndex

    val isSrtlaEndpoint: LiveData<Boolean> = storageRepository.srtlaConfigFlow
        .map { it != null }
        .asLiveData()

    val videoConfigLiveData: LiveData<VideoConfig?> = storageRepository.videoConfigFlow.asLiveData()

    /**
     * Raw "user tapped SRTLA STATS" toggle. Not directly exposed: actual visibility is
     * derived from this AND the current endpoint being SRTLA (see [isSrtlaStatsVisible]),
     * so the panel auto-hides when the endpoint changes away from SRTLA, and auto-reappears
     * if the endpoint is switched back to SRTLA while the toggle is still on.
     */
    private val _isSrtlaStatsToggledOn = MutableLiveData(false)

    /**
     * Whether the SRTLA stats panel should be shown. Derived from [isSrtlaEndpoint] combined
     * with the user's toggle state, so it automatically hides (and re-shows) as the endpoint
     * changes, without requiring the SRTLA STATS button to be visible to dismiss it.
     */
    val isSrtlaStatsVisible: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        value = false
        var endpointIsSrtla = false
        var toggledOn = false
        addSource(isSrtlaEndpoint) {
            endpointIsSrtla = it
            value = endpointIsSrtla && toggledOn
        }
        addSource(_isSrtlaStatsToggledOn) {
            toggledOn = it
            value = endpointIsSrtla && toggledOn
        }
    }

    fun setSrtlaStatsVisible(visible: Boolean) {
        _isSrtlaStatsToggledOn.value = visible
    }

    /**
     * Expose which RTMP source index is active (1-based), or null if not on RTMP.
     */

    /**
     * Number of configured RTMP source URLs.
     */
    val rtmpSourceCount: LiveData<Int> = storageRepository.rtmpSourceCountFlow.asLiveData()

    /**
     * Show camera controls only when on camera source (neither RTMP nor UVC toggle is ON)
     */
    val showCameraControls: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        value = true // Initial value: show controls when both toggles are OFF
        
        var rtmpOn = _activeRtmpIndex.value != null
        var uvcOn = _userToggledUvc.value ?: false
        var screenOn = _isScreenSource.value ?: false
        
        addSource(_activeRtmpIndex) { 
            rtmpOn = it != null
            value = !rtmpOn && !uvcOn && !screenOn
        }
        addSource(_userToggledUvc) { 
            uvcOn = it ?: false
            value = !rtmpOn && !uvcOn && !screenOn
        }
        addSource(_isScreenSource) {
            screenOn = it ?: false
            value = !rtmpOn && !uvcOn && !screenOn
        }
    }

    val isRtmpOrBitmapSource: LiveData<Boolean>
        get() = serviceReadyFlow.flatMapLatest { ready ->
            if (ready && serviceStreamer != null) {
                Log.d(TAG, "isRtmpOrBitmapSource: serviceStreamer available, checking video source")
                serviceStreamer!!.videoInput?.sourceFlow?.map { source ->
                    val isNotCamera = source !is ICameraSource && source != null
                    Log.d(TAG, "isRtmpOrBitmapSource: video source = $source, isRtmpOrBitmapSource = $isNotCamera")
                    isNotCamera
                } ?: kotlinx.coroutines.flow.flowOf(false)
            } else {
                Log.d(TAG, "isRtmpOrBitmapSource: service not ready or serviceStreamer null, returning false")
                kotlinx.coroutines.flow.flowOf(false)
            }
        }.asLiveData()

    /**
     * Check if current video source is actually RTMP (not bitmap fallback)
     * Used to show monitor audio button only for RTMP sources
     */
    val isActualRtmpSource: LiveData<Boolean>
        get() = serviceReadyFlow.flatMapLatest { ready ->
            if (ready && serviceStreamer != null) {
                serviceStreamer!!.videoInput?.sourceFlow?.map { source ->
                    source is RTMPVideoSource
                } ?: kotlinx.coroutines.flow.flowOf(false)
            } else {
                kotlinx.coroutines.flow.flowOf(false)
            }
        }.asLiveData()

    val isFlashAvailable: LiveData<Boolean> = cameraControlsController.isFlashAvailable.asLiveData()
    fun toggleFlash() = cameraControlsController.toggleFlash()

    val isAutoWhiteBalanceAvailable: LiveData<Boolean> = cameraControlsController.isAutoWhiteBalanceAvailable.asLiveData()
    fun toggleAutoWhiteBalanceMode() = cameraControlsController.toggleAutoWhiteBalanceMode()

    val showExposureSlider: LiveData<Boolean> = cameraControlsController.showExposureSlider.asLiveData()
    fun toggleExposureSlider() = cameraControlsController.toggleExposureSlider()

    val isExposureCompensationAvailable: LiveData<Boolean> = cameraControlsController.isExposureCompensationAvailable.asLiveData()
    val exposureCompensationRange: LiveData<Range<Int>?> = cameraControlsController.exposureCompensationRange.asLiveData()
    val exposureCompensationStep: LiveData<Rational?> = cameraControlsController.exposureCompensationStep.asLiveData()
    var exposureCompensation: Float
        @Bindable get() = cameraControlsController.exposureCompensation
        set(value) {
            cameraControlsController.exposureCompensation = value
        }

    val showZoomSlider: LiveData<Boolean> = cameraControlsController.showZoomSlider.asLiveData()
    fun toggleZoomSlider() = cameraControlsController.toggleZoomSlider()

    val isZoomAvailable: LiveData<Boolean> = cameraControlsController.isZoomAvailable.asLiveData()
    val zoomRatioRange: LiveData<Range<Float>?> = cameraControlsController.zoomRatioRange.asLiveData()
    var zoomRatio: Float
        @Bindable get() = cameraControlsController.zoomRatio
        set(value) {
            cameraControlsController.zoomRatio = value
        }

    val isAutoFocusModeAvailable: LiveData<Boolean> = cameraControlsController.isAutoFocusModeAvailable.asLiveData()
    fun toggleAutoFocusMode() = cameraControlsController.toggleAutoFocusMode()

    val showLensDistanceSlider: LiveData<Boolean> = cameraControlsController.showLensDistanceSlider.asLiveData()
    val lensDistanceRange: LiveData<Range<Float>?> = cameraControlsController.lensDistanceRange.asLiveData()
    var lensDistance: Float
        @Bindable get() = cameraControlsController.lensDistance
        set(value) {
            cameraControlsController.lensDistance = value
        }

    private fun notifySourceChanged() {
        val videoSource = streamer?.videoInput?.sourceFlow?.value
        cameraControlsController.onSourceChanged(videoSource)
    }

    /**
     * Get user-friendly label for audio source type.
     * Only 2 sources are used in the app:
     * - MicrophoneSourceFactory: Phone Mic
     * - MediaProjectionAudioSourceFactory: Media Projection Audio
     */
    private fun getAudioSourceLabel(audioSource: Any?): String {
        return when {
            audioSource == null -> application.getString(R.string.audio_source_none)
            audioSource is IMediaProjectionSource ->
                application.getString(R.string.audio_source_rtmp)
            audioSource is com.dimadesu.lifestreamer.audio.BluetoothAudioSource ->
                application.getString(R.string.audio_source_bluetooth)
            else -> application.getString(R.string.audio_source_microphone)
        }
    }

    override fun onCleared() {
        super.onCleared()
        
        // Clean up audio level monitoring
        disableAudioLevelMonitoring()
        audioDebugController.cleanup()
        
        // Cancel any pending reconnection attempts
        reconnectTimer.stop()
        // isReconnecting managed by service
        rotationIgnoredDuringReconnection = null
        Log.i(TAG, "Cancelled reconnect timer in onCleared()")
        
        // Clean up RTMP controller
        rtmpSourceController.release()
        
        // Clean up UVC camera helper
        uvcSourceController.release()
        
        // try {
        //     streamer.releaseBlocking()
        // } catch (t: Throwable) {
        //     Log.e(TAG, "Streamer release failed", t)
        // }

        unbindStreamerService()

        // Unbind from the MediaProjection service, but don't stop it — the
        // MediaProjectionService may still be running as a foreground service, and
        // a new ViewModel (after UI recreation) can recover the token via tryReconnect().
        // Full release only happens in prepareForExit() when the user explicitly quits.
        mediaProjectionTokenStore.unbind()
        Log.i(TAG, "PreviewViewModel cleared (MediaProjection service preserved for possible UI recreation)")
    }

    /**
     * Drop the service bind so a following [Context.stopService] can destroy
     * CameraStreamerService. Safe to call more than once.
     */
    fun prepareForExit() {
        Log.i(TAG, "prepareForExit: unbinding so stopService can take effect")
        try {
            streamerService?.markUserStoppedManually()
        } catch (_: Throwable) {}
        unbindStreamerService()
        releaseMediaProjection()
    }

    private fun unbindStreamerService() {
        streamerServiceConnection.unbind()
    }

    private fun releaseMediaProjection() {
        mediaProjectionTokenStore.release()
    }


    /**
     * Refresh audio debug information by reading current audio configuration from the streamer.
     */
    fun refreshAudioDebugInfo() = audioDebugController.refreshAudioDebugInfo()

    /**
     * Toggle audio debug overlay visibility
     */
    fun toggleAudioDebugOverlay() = audioDebugController.toggleAudioDebugOverlay()

    /**
     * Apply the selected audio source type from the debug overlay.
     */
    fun applySelectedAudioSource() = audioDebugController.applySelectedAudioSource()
    
    /**
     * Reload audio settings from DataStore and apply if they differ from current ViewModel values.
     */
    fun reloadAndApplyAudioSettingsIfChanged() = audioDebugController.reloadAndApplyAudioSettingsIfChanged()

    // Unified UI State for Jetpack Compose
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(com.dimadesu.lifestreamer.ui.main.state.MainUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<com.dimadesu.lifestreamer.ui.main.state.MainUiState> = _uiState

    fun updateUiState(transform: (com.dimadesu.lifestreamer.ui.main.state.MainUiState) -> com.dimadesu.lifestreamer.ui.main.state.MainUiState) {
        _uiState.value = transform(_uiState.value)
    }

    /**
     * Centralized event handler for Jetpack Compose UI interactions.
     */
    fun onEvent(event: com.dimadesu.lifestreamer.ui.main.state.MainUiEvent) {
        when (event) {
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.StartStream -> startStream()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.StartStreamWithProjection -> startStreamWithMediaProjection(event.launcher)
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.StopStream -> stopStream()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.SetMute -> setMute(event.isMuted)
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ToggleCamera -> toggleCamera()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.SwitchBackToFront -> { switchBackToFront() }
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ToggleScreenSource -> toggleScreenSource(event.launcher)
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ToggleUvcSource -> toggleUvcSource()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ToggleRtmpSource -> toggleVideoSource(event.launcher, event.index)
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ToggleFlash -> toggleFlash()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ToggleAutoWhiteBalance -> toggleAutoWhiteBalanceMode()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ToggleAutoFocus -> toggleAutoFocusMode()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ToggleExposureSlider -> toggleExposureSlider()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ToggleZoomSlider -> toggleZoomSlider()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ToggleLensDistanceSlider -> toggleAutoFocusMode()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.SetExposure -> exposureCompensation = event.value
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.SetZoom -> zoomRatio = event.value
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.SetLensDistance -> lensDistance = event.value
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ToggleMonitorAudio -> toggleMonitorAudio()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.SetUseBluetoothMic -> setUseBluetoothMic(event.enabled)
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ToggleSystemAudioForCamera -> toggleSystemAudioForCamera(event.launcher)
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ToggleAudioDebugOverlay -> toggleAudioDebugOverlay()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.RefreshAudioDebugInfo -> refreshAudioDebugInfo()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ApplySelectedAudioSource -> applySelectedAudioSource()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.SetSrtlaStatsVisible -> setSrtlaStatsVisible(event.visible)
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ClearToast -> clearToastMessage()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ClearStreamerError -> clearStreamerError()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ClearEndpointError -> clearEndpointError()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.ClearBluetoothConnectRequest -> clearBluetoothConnectRequest()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.UiResumed -> onUiResumed()
            is com.dimadesu.lifestreamer.ui.main.state.MainUiEvent.UiPaused -> onUiPaused()
        }
    }

    companion object {
        private const val TAG = "PreviewViewModel"
        
        /**
         * Helper to convert audio source int to readable name
         */
        fun getAudioSourceName(source: Int): String =
            com.dimadesu.lifestreamer.ui.main.controllers.AudioDebugController.getAudioSourceName(source)
    }
}
