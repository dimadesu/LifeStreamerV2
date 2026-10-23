package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.models.StreamStatus
import com.dimadesu.lifestreamer.ui.theme.*

@Composable
fun MainHudOverlay(
    // Telemetry state
    streamStatus: StreamStatus,
    streamStatusText: String,
    bitrateText: String?,
    fpsText: String?,
    uptimeText: String?,
    audioSourceText: String?,
    rtmpStatusMessage: String?,
    reconnectionStatusMessage: String?,

    // Source states
    isRtmpSource: Boolean,
    activeRtmpIndex: Int?,
    rtmpSourceCount: Int,
    isUvcSource: Boolean,
    isScreenSource: Boolean,
    isMonitorAudioOn: Boolean,
    useBluetoothMic: Boolean,
    showBluetoothToggle: Boolean,
    isAudioDebugOverlayVisible: Boolean,
    isSrtlaEndpoint: Boolean,
    isSrtlaStatsVisible: Boolean,

    // Camera states
    isFlashAvailable: Boolean,
    isAutoWhiteBalanceAvailable: Boolean,
    isExposureAvailable: Boolean,
    isZoomAvailable: Boolean,
    isAutoFocusAvailable: Boolean,
    showCameraControls: Boolean,
    showZoomSlider: Boolean,
    showExposureSlider: Boolean,
    zoomRatio: Float,
    zoomRange: ClosedFloatingPointRange<Float>,
    exposureValue: Float,
    exposureRange: ClosedFloatingPointRange<Float>,
    isMuted: Boolean,

    // Actions
    onLiveToggleClick: () -> Unit,
    onRtmpSourceClick: (Int) -> Unit,
    onRtmpServerClick: () -> Unit,
    onUvcToggleClick: () -> Unit,
    onUvcSettingsClick: () -> Unit,
    onScreenToggleClick: () -> Unit,
    onMonitorAudioClick: () -> Unit,
    onBluetoothToggleClick: () -> Unit,
    onSystemAudioToggleClick: () -> Unit,
    onAudioSourceDebugClick: () -> Unit,
    onSrtlaStatsClick: () -> Unit,
    onFlashToggleClick: () -> Unit,
    onWhiteBalanceToggleClick: () -> Unit,
    onExposureToggleClick: () -> Unit,
    onZoomToggleClick: () -> Unit,
    onAutoFocusToggleClick: () -> Unit,
    onMuteToggleClick: (Boolean) -> Unit,
    onZoomChange: (Float) -> Unit,
    onExposureChange: (Float) -> Unit,

    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // TOP: Telemetry Header
        TopTelemetryHeader(
            streamStatus = streamStatus,
            streamStatusText = streamStatusText,
            bitrateText = bitrateText,
            fpsText = fpsText,
            uptimeText = uptimeText,
            audioSourceText = audioSourceText,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        )

        // CENTER: Alerts & Reconnection messages
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!reconnectionStatusMessage.isNullOrBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = reconnectionStatusMessage,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = WarningOrange,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            if (!rtmpStatusMessage.isNullOrBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = rtmpStatusMessage,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // LEFT: Source Switchers Column
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp, top = 60.dp, bottom = 80.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // RTMP Primary Source
            if (!isUvcSource && !isScreenSource && (activeRtmpIndex == null || activeRtmpIndex == 1)) {
                HudSourceButton(
                    text = "RTMP SRC",
                    isActive = isRtmpSource && (activeRtmpIndex == null || activeRtmpIndex == 1),
                    onClick = { onRtmpSourceClick(1) }
                )
            }

            // Additional RTMP Sources (2, 3...)
            if (!isUvcSource && !isScreenSource && rtmpSourceCount > 1) {
                for (i in 2..rtmpSourceCount) {
                    HudSourceButton(
                        text = "RTMP $i",
                        isActive = isRtmpSource && activeRtmpIndex == i,
                        onClick = { onRtmpSourceClick(i) }
                    )
                }
            }

            // RTMP SRVR
            if (!isUvcSource && !isScreenSource) {
                HudSourceButton(
                    text = "RTMP SRVR",
                    isActive = false,
                    onClick = onRtmpServerClick
                )
            }

            // USB / UVC Source
            if (!isRtmpSource && !isScreenSource) {
                HudSourceButton(
                    text = "USB SRC",
                    isActive = isUvcSource,
                    onClick = onUvcToggleClick
                )
                if (!isUvcSource) {
                    HudSourceButton(
                        text = "USB ⚙️",
                        isActive = false,
                        onClick = onUvcSettingsClick
                    )
                }
            }

            // SCREEN Source
            if (!isRtmpSource && !isUvcSource) {
                HudSourceButton(
                    text = "SCREEN",
                    isActive = isScreenSource,
                    onClick = onScreenToggleClick
                )
            }

            // Audio Monitor Toggle
            HudSourceButton(
                text = "MONITOR 🎤",
                isActive = isMonitorAudioOn,
                onClick = onMonitorAudioClick
            )

            // Bluetooth Mic Toggle
            if (showBluetoothToggle) {
                HudSourceButton(
                    text = "BT MIC",
                    isActive = useBluetoothMic,
                    onClick = onBluetoothToggleClick
                )
            }

            // Audio Output
            if (!isRtmpSource) {
                HudSourceButton(
                    text = "AUDIO OUT",
                    isActive = false,
                    onClick = onSystemAudioToggleClick
                )
                HudSourceButton(
                    text = "AUDIO SRC",
                    isActive = isAudioDebugOverlayVisible,
                    onClick = onAudioSourceDebugClick
                )
            }

            // SRTLA Stats Toggle
            if (isSrtlaEndpoint) {
                HudSourceButton(
                    text = "SRTLA STATS",
                    isActive = isSrtlaStatsVisible,
                    onClick = onSrtlaStatsClick
                )
            }
        }

        // RIGHT: Camera Controls & Sliders
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp, top = 80.dp, bottom = 80.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Zoom / Exposure Sliders (if expanded)
            if (showZoomSlider) {
                VerticalSlider(
                    value = zoomRatio,
                    valueRange = zoomRange,
                    onValueChange = onZoomChange,
                    modifier = Modifier
                        .height(220.dp)
                        .padding(end = 8.dp)
                )
            }

            if (showExposureSlider) {
                VerticalSlider(
                    value = exposureValue,
                    valueRange = exposureRange,
                    onValueChange = onExposureChange,
                    modifier = Modifier
                        .height(220.dp)
                        .padding(end = 8.dp)
                )
            }

            // Camera Quick Buttons Column
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showCameraControls) {
                    if (isFlashAvailable) {
                        HudIconButton(
                            icon = Icons.Default.FlashOn,
                            contentDescription = "Torch",
                            onClick = onFlashToggleClick
                        )
                    }
                    if (isAutoWhiteBalanceAvailable) {
                        HudIconButton(
                            icon = Icons.Default.WbAuto,
                            contentDescription = "White Balance",
                            onClick = onWhiteBalanceToggleClick
                        )
                    }
                    if (isExposureAvailable) {
                        HudIconButton(
                            icon = Icons.Default.Exposure,
                            contentDescription = "Exposure",
                            isActive = showExposureSlider,
                            onClick = onExposureToggleClick
                        )
                    }
                    if (isZoomAvailable) {
                        HudIconButton(
                            icon = Icons.Default.ZoomIn,
                            contentDescription = "Zoom",
                            isActive = showZoomSlider,
                            onClick = onZoomToggleClick
                        )
                    }
                    if (isAutoFocusAvailable) {
                        HudIconButton(
                            icon = Icons.Default.CenterFocusStrong,
                            contentDescription = "Auto Focus",
                            onClick = onAutoFocusToggleClick
                        )
                    }
                }

                // Mute Mic Toggle
                HudIconButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mute",
                    isActive = isMuted,
                    activeColor = LiveRed,
                    onClick = { onMuteToggleClick(!isMuted) }
                )
            }
        }

        // BOTTOM: Glowing Live / Stop Button
        val isLive = streamStatus == StreamStatus.STREAMING ||
                     streamStatus == StreamStatus.STARTING ||
                     streamStatus == StreamStatus.CONNECTING

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Button(
                onClick = onLiveToggleClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLive) LiveRed else ButtonActiveGreen
                ),
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isLive) Color.White else ActiveGreen)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isLive) "STOP STREAM" else "START LIVE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun TopTelemetryHeader(
    streamStatus: StreamStatus,
    streamStatusText: String,
    bitrateText: String?,
    fpsText: String?,
    uptimeText: String?,
    audioSourceText: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            color = TelemetryChipBackground,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!fpsText.isNullOrBlank()) {
                    Text(
                        text = fpsText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = TextPrimary
                    )
                }
                if (!uptimeText.isNullOrBlank()) {
                    Text(
                        text = uptimeText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = TextPrimary
                    )
                }
                if (!bitrateText.isNullOrBlank()) {
                    Text(
                        text = bitrateText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = ActiveGreen
                    )
                }
                Text(
                    text = streamStatusText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = when (streamStatus) {
                        StreamStatus.STREAMING -> ActiveGreen
                        StreamStatus.STARTING, StreamStatus.CONNECTING -> WarningOrange
                        StreamStatus.ERROR -> LiveRed
                        else -> TextSecondary
                    }
                )
            }
        }

        if (!audioSourceText.isNullOrBlank()) {
            Surface(
                color = TelemetryChipBackground,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = audioSourceText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun HudSourceButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isActive) ActiveGreen else ButtonGray.copy(alpha = 0.85f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .defaultMinSize(minWidth = 72.dp, minHeight = 34.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = Color.White
            )
        }
    }
}

@Composable
fun HudIconButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean = false,
    activeColor: Color = ActiveGreen,
    onClick: () -> Unit
) {
    Surface(
        color = if (isActive) activeColor else Color.Black.copy(alpha = 0.5f),
        shape = CircleShape,
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun VerticalSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(36.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = AccentBlue,
                inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
            )
        )
    }
}
