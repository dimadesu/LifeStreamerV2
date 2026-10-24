package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.ui.main.state.VideoSourceState

/**
 * Vertical camera action bar (flash, white-balance, exposure, zoom, auto-focus, mute)
 * and interactive floating sliders.
 */
@Composable
fun CameraControlsBar(
    videoSourceState: VideoSourceState,
    isMuted: Boolean,
    onToggleFlash: () -> Unit,
    onToggleAutoWhiteBalance: () -> Unit,
    onToggleExposureSlider: () -> Unit,
    onToggleZoomSlider: () -> Unit,
    onToggleAutoFocus: () -> Unit,
    onToggleMute: () -> Unit,
    onExposureChanged: (Float) -> Unit,
    onZoomChanged: (Float) -> Unit,
    onLensDistanceChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Floating sliders (Zoom / Exposure / Focus)
        AnimatedVisibility(
            visible = videoSourceState.showZoomSlider,
            enter = fadeIn() + slideInHorizontally(),
            exit = fadeOut() + slideOutHorizontally()
        ) {
            VerticalSliderCard(
                label = "ZOOM ${"%.1f".format(videoSourceState.zoomRatio)}x",
                value = videoSourceState.zoomRatio,
                range = 1f..10f,
                onValueChange = onZoomChanged
            )
        }

        AnimatedVisibility(
            visible = videoSourceState.showExposureSlider,
            enter = fadeIn() + slideInHorizontally(),
            exit = fadeOut() + slideOutHorizontally()
        ) {
            VerticalSliderCard(
                label = "EV ${"%.1f".format(videoSourceState.exposureCompensation)}",
                value = videoSourceState.exposureCompensation,
                range = -6f..6f,
                onValueChange = onExposureChanged
            )
        }

        // Action icon buttons column
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (videoSourceState.showCameraControls) {
                // Torch / Flash
                CameraActionButton(
                    iconRes = R.drawable.ic_baseline_flash_on_24,
                    contentDescription = "Flash",
                    isActive = videoSourceState.isFlashOn,
                    onClick = onToggleFlash
                )

                // Auto White Balance
                CameraActionButton(
                    iconRes = R.drawable.ic_baseline_wb_auto_24,
                    contentDescription = "White Balance",
                    isActive = videoSourceState.isAutoWhiteBalanceOn,
                    onClick = onToggleAutoWhiteBalance
                )

                // Exposure Slider Toggle
                CameraActionButton(
                    iconRes = R.drawable.ic_baseline_exposure_24,
                    contentDescription = "Exposure",
                    isActive = videoSourceState.showExposureSlider,
                    onClick = onToggleExposureSlider
                )

                // Zoom Slider Toggle
                CameraActionButton(
                    iconRes = R.drawable.ic_baseline_zoom_in_24,
                    contentDescription = "Zoom",
                    isActive = videoSourceState.showZoomSlider,
                    onClick = onToggleZoomSlider
                )

                // Auto Focus
                CameraActionButton(
                    iconRes = R.drawable.ic_baseline_center_focus_strong_24,
                    contentDescription = "Auto Focus",
                    isActive = videoSourceState.isAutoFocusOn,
                    onClick = onToggleAutoFocus
                )
            }

            // Mic Mute Toggle
            CameraActionButton(
                iconRes = if (isMuted) R.drawable.ic_baseline_mic_off_24 else R.drawable.ic_baseline_mic_24,
                contentDescription = "Mute Microphone",
                isActive = isMuted,
                activeColor = Color(0xFFFF5252), // Red when muted
                onClick = onToggleMute
            )
        }
    }
}

@Composable
fun CameraActionButton(
    iconRes: Int,
    contentDescription: String,
    isActive: Boolean,
    activeColor: Color = Color(0xFF4CAF50),
    inactiveColor: Color = Color(0x99212121),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (isActive) activeColor else inactiveColor)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun VerticalSliderCard(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xCC000000))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onValueChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF4CAF50),
                    inactiveTrackColor = Color(0x66FFFFFF)
                ),
                modifier = Modifier.width(180.dp)
            )
        }
    }
}
