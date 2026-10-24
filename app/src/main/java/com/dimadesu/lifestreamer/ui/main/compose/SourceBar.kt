package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.ui.main.state.VideoSourceState

/**
 * Vertical bar of video source selection chips:
 * - RTMP sources (1, 2, 3...)
 * - RTMP Server
 * - USB / UVC Camera
 * - Screen capture
 */
@Composable
fun SourceBar(
    videoSourceState: VideoSourceState,
    rtmpSourceCount: Int = 1,
    onCameraClick: () -> Unit,
    onSelectCamera: (cameraId: String) -> Unit = {},
    onRtmpClick: (index: Int) -> Unit,
    onRtmpServerClick: () -> Unit,
    onUvcClick: () -> Unit,
    onUvcSettingsClick: () -> Unit,
    onScreenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Camera Devices: visible when on Camera source (equivalent to legacy cameraButtonsContainer)
        val showCameraDevices = !videoSourceState.isRtmp && !videoSourceState.isUvc && !videoSourceState.isScreen
        AnimatedVisibility(
            visible = showCameraDevices && videoSourceState.availableCameras.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                videoSourceState.availableCameras.forEach { camera ->
                    SourceChip(
                        text = camera.displayName,
                        isActive = camera.id == videoSourceState.selectedCameraId,
                        onClick = { onSelectCamera(camera.id) }
                    )
                }
            }
        }

        // RTMP Sources: visible only when not in UVC or Screen mode
        val showRtmp = !videoSourceState.isUvc && !videoSourceState.isScreen
        AnimatedVisibility(
            visible = showRtmp,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // If an RTMP index is active, only show the active one; otherwise show all available
                val activeIdx = videoSourceState.activeRtmpIndex
                if (activeIdx != null) {
                    SourceChip(
                        text = if (activeIdx == 1) "RTMP SRC" else "RTMP SRC $activeIdx",
                        isActive = true,
                        onClick = { onRtmpClick(activeIdx) }
                    )
                } else {
                    for (i in 1..maxOf(1, rtmpSourceCount)) {
                        SourceChip(
                            text = if (i == 1) "RTMP SRC" else "RTMP SRC $i",
                            isActive = false,
                            onClick = { onRtmpClick(i) }
                        )
                    }
                }

                // RTMP Server toggle
                SourceChip(
                    text = "RTMP SRVR",
                    isActive = false,
                    onClick = onRtmpServerClick
                )
            }
        }

        // USB / UVC source: visible when not in RTMP or Screen mode
        val showUvc = !videoSourceState.isRtmp && !videoSourceState.isScreen
        AnimatedVisibility(
            visible = showUvc,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SourceChip(
                    text = "USB SRC",
                    isActive = videoSourceState.isUvc,
                    onClick = onUvcClick
                )

                if (!videoSourceState.isUvc) {
                    SourceChip(
                        text = "⚙️",
                        isActive = false,
                        onClick = onUvcSettingsClick
                    )
                }
            }
        }

        // SCREEN source: visible when not in RTMP or UVC mode
        val showScreen = !videoSourceState.isRtmp && !videoSourceState.isUvc
        AnimatedVisibility(
            visible = showScreen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SourceChip(
                text = "SCREEN",
                isActive = videoSourceState.isScreen,
                onClick = onScreenClick
            )
        }
    }
}

@Composable
fun SourceChip(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isActive) Color(0xFF2E7D32) else Color(0x99212121)
    val borderColor = if (isActive) Color(0xFF81C784) else Color(0x33FFFFFF)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
        )
    }
}
