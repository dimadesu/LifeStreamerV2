package com.dimadesu.lifestreamer.ui.main.state

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher

/**
 * Unified UI events from Compose or XML components to be processed by [PreviewViewModel].
 */
sealed interface MainUiEvent {
    // Stream lifecycle
    object StartStream : MainUiEvent
    data class StartStreamWithProjection(val launcher: ActivityResultLauncher<Intent>) : MainUiEvent
    object StopStream : MainUiEvent
    data class SetMute(val isMuted: Boolean) : MainUiEvent

    // Video Source selection & switching
    object ToggleCamera : MainUiEvent
    object SwitchBackToFront : MainUiEvent
    data class ToggleScreenSource(val launcher: ActivityResultLauncher<Intent>? = null) : MainUiEvent
    object ToggleUvcSource : MainUiEvent
    data class ToggleRtmpSource(val launcher: ActivityResultLauncher<Intent>? = null, val index: Int = 1) : MainUiEvent

    // Camera parameter controls
    object ToggleFlash : MainUiEvent
    object ToggleAutoWhiteBalance : MainUiEvent
    object ToggleAutoFocus : MainUiEvent
    object ToggleExposureSlider : MainUiEvent
    object ToggleZoomSlider : MainUiEvent
    object ToggleLensDistanceSlider : MainUiEvent
    data class SetExposure(val value: Float) : MainUiEvent
    data class SetZoom(val value: Float) : MainUiEvent
    data class SetLensDistance(val value: Float) : MainUiEvent

    // Audio routing & monitoring
    object ToggleMonitorAudio : MainUiEvent
    data class SetUseBluetoothMic(val enabled: Boolean) : MainUiEvent
    data class ToggleSystemAudioForCamera(val launcher: ActivityResultLauncher<Intent>) : MainUiEvent
    object ToggleAudioDebugOverlay : MainUiEvent
    object RefreshAudioDebugInfo : MainUiEvent
    object ApplySelectedAudioSource : MainUiEvent

    // Overlays & errors
    data class SetSrtlaStatsVisible(val visible: Boolean) : MainUiEvent
    object ClearToast : MainUiEvent
    object ClearStreamerError : MainUiEvent
    object ClearEndpointError : MainUiEvent
    object ClearBluetoothConnectRequest : MainUiEvent

    // Lifecycle
    object UiResumed : MainUiEvent
    object UiPaused : MainUiEvent
}
