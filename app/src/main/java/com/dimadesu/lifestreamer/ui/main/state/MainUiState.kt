package com.dimadesu.lifestreamer.ui.main.state

import com.dimadesu.lifestreamer.models.AudioDebugInfo

/**
 * Consolidated UI state for the Main / Preview screen, organized into cohesive sub-states
 * to support scalable Jetpack Compose UI architecture.
 */
data class MainUiState(
    val stream: StreamStatusState = StreamStatusState(),
    val videoSource: VideoSourceState = VideoSourceState(),
    val audioSource: AudioSourceState = AudioSourceState(),
    val overlays: UiOverlayState = UiOverlayState()
)

data class StreamStatusState(
    val statusText: String = "NOT_STREAMING",
    val isStreaming: Boolean = false,
    val isReconnecting: Boolean = false,
    val bitrateText: String? = null,
    val encoderStatsText: String? = null,
    val uptimeText: String? = null,
    val reconnectionStatus: String? = null,
    val rtmpStatus: String? = null
)

data class CameraDeviceInfo(
    val id: String,
    val displayName: String,
    val facing: String = ""
)

data class VideoSourceState(
    val isCamera: Boolean = true,
    val isUvc: Boolean = false,
    val isScreen: Boolean = false,
    val isRtmp: Boolean = false,
    val activeRtmpIndex: Int? = null,
    val availableCameras: List<CameraDeviceInfo> = emptyList(),
    val selectedCameraId: String? = null,
    val isFlashOn: Boolean = false,
    val isAutoWhiteBalanceOn: Boolean = true,
    val isAutoFocusOn: Boolean = true,
    val exposureCompensation: Float = 0f,
    val zoomRatio: Float = 1f,
    val lensDistance: Float = 0f,
    val showExposureSlider: Boolean = false,
    val showZoomSlider: Boolean = false,
    val showLensDistanceSlider: Boolean = false,
    val showCameraControls: Boolean = true
)

data class AudioSourceState(
    val isMuted: Boolean = false,
    val isMonitorAudioOn: Boolean = false,
    val useBluetoothMic: Boolean = false,
    val useSystemAudioForCamera: Boolean = false,
    val showBluetoothToggle: Boolean = true,
    val audioSourceIndicator: String? = null,
    val scoState: String? = null,
    val audioDebugOverlayVisible: Boolean = false,
    val audioDebugInfo: AudioDebugInfo? = null,
    val audioLevel: Float = 0f
)

data class UiOverlayState(
    val isSrtlaStatsVisible: Boolean = false,
    val isSrtlaEndpoint: Boolean = false,
    val toastMessage: String? = null,
    val streamerError: String? = null,
    val endpointError: String? = null
)
