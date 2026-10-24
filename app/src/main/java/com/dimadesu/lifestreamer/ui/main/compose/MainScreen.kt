package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.models.StreamStatus
import com.dimadesu.lifestreamer.ui.main.state.MainUiEvent
import com.dimadesu.lifestreamer.ui.main.state.MainUiState

/**
 * Root Compose overlay for the Main Screen.
 * Places the status bar, source switcher, audio panel, camera controls,
 * live toggle button, and status banners over the camera/video preview.
 */
@Composable
fun MainScreenOverlay(
    uiState: MainUiState,
    streamStatus: StreamStatus = StreamStatus.NOT_STREAMING,
    rtmpSourceCount: Int = 1,
    onEvent: (MainUiEvent) -> Unit,
    onLiveClick: () -> Unit = {},
    onScreenClick: () -> Unit = { onEvent(MainUiEvent.ToggleScreenSource()) },
    onRtmpClick: (index: Int) -> Unit = { idx -> onEvent(MainUiEvent.ToggleRtmpSource(index = idx)) },
    onOpenRtmpServer: () -> Unit = {},
    onOpenUvcSettings: () -> Unit = {},
    onToggleSystemAudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 1. Top-Right Status Bar
        StatusBar(
            streamStatusState = uiState.stream,
            audioSourceState = uiState.audioSource,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        )

        // 2. Left Source Bar
        SourceBar(
            videoSourceState = uiState.videoSource,
            rtmpSourceCount = rtmpSourceCount,
            onCameraClick = { onEvent(MainUiEvent.ToggleCamera) },
            onSelectCamera = { camId -> onEvent(MainUiEvent.SelectCamera(camId)) },
            onRtmpClick = onRtmpClick,
            onRtmpServerClick = onOpenRtmpServer,
            onUvcClick = { onEvent(MainUiEvent.ToggleUvcSource) },
            onUvcSettingsClick = onOpenUvcSettings,
            onScreenClick = onScreenClick,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
        )

        // 3. Bottom-Left Audio Panel
        AudioPanel(
            audioSourceState = uiState.audioSource,
            isCameraSource = uiState.videoSource.isCamera,
            onToggleBluetooth = {
                onEvent(MainUiEvent.SetUseBluetoothMic(!uiState.audioSource.useBluetoothMic))
            },
            onToggleSystemAudio = onToggleSystemAudio,
            onToggleMonitorAudio = { onEvent(MainUiEvent.ToggleMonitorAudio) },
            onToggleAudioDebug = { onEvent(MainUiEvent.ToggleAudioDebugOverlay) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 16.dp, start = 8.dp)
        )

        // 4. Right Camera Controls Bar
        CameraControlsBar(
            videoSourceState = uiState.videoSource,
            isMuted = uiState.audioSource.isMuted,
            onToggleCamera = { onEvent(MainUiEvent.ToggleCamera) },
            onToggleFlash = { onEvent(MainUiEvent.ToggleFlash) },
            onToggleAutoWhiteBalance = { onEvent(MainUiEvent.ToggleAutoWhiteBalance) },
            onToggleExposureSlider = { onEvent(MainUiEvent.ToggleExposureSlider) },
            onToggleZoomSlider = { onEvent(MainUiEvent.ToggleZoomSlider) },
            onToggleAutoFocus = { onEvent(MainUiEvent.ToggleAutoFocus) },
            onToggleMute = { onEvent(MainUiEvent.SetMute(!uiState.audioSource.isMuted)) },
            onExposureChanged = { onEvent(MainUiEvent.SetExposure(it)) },
            onZoomChanged = { onEvent(MainUiEvent.SetZoom(it)) },
            onLensDistanceChanged = { onEvent(MainUiEvent.SetLensDistance(it)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
        )

        // 5. Bottom-Center Live / Stop Stream Button
        LiveButton(
            status = streamStatus,
            isStreaming = uiState.stream.isStreaming,
            onClick = onLiveClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )

        // 6. Center Reconnection Alert Banner
        AnimatedVisibility(
            visible = !uiState.stream.reconnectionStatus.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            uiState.stream.reconnectionStatus?.let { status ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xD9000000))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = status,
                        color = Color(0xFFFFB74D),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
