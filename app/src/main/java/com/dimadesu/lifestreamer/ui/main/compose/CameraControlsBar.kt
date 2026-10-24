package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel

/**
 * Stateful vertical action bar hosting camera action toggles and mic mute.
 */
@Composable
fun CameraControlsBar(
    viewModel: PreviewViewModel,
    modifier: Modifier = Modifier
) {
    val showCameraControls by viewModel.showCameraControls.observeAsState(initial = false)
    val isFlashAvailable by viewModel.isFlashAvailable.observeAsState(initial = false)
    val isAutoWhiteBalanceAvailable by viewModel.isAutoWhiteBalanceAvailable.observeAsState(initial = false)
    val isExposureCompensationAvailable by viewModel.isExposureCompensationAvailable.observeAsState(initial = false)
    val isZoomAvailable by viewModel.isZoomAvailable.observeAsState(initial = false)
    val isAutoFocusModeAvailable by viewModel.isAutoFocusModeAvailable.observeAsState(initial = false)
    val isMuted by viewModel.isMutedLiveData.observeAsState(initial = false)

    CameraControlsBar(
        showCameraControls = showCameraControls,
        isFlashAvailable = isFlashAvailable,
        isAutoWhiteBalanceAvailable = isAutoWhiteBalanceAvailable,
        isExposureCompensationAvailable = isExposureCompensationAvailable,
        isZoomAvailable = isZoomAvailable,
        isAutoFocusModeAvailable = isAutoFocusModeAvailable,
        isMuted = isMuted,
        onToggleFlash = { viewModel.toggleFlash() },
        onToggleAutoWhiteBalance = { viewModel.toggleAutoWhiteBalanceMode() },
        onToggleExposureSlider = { viewModel.toggleExposureSlider() },
        onToggleZoomSlider = { viewModel.toggleZoomSlider() },
        onToggleAutoFocus = { viewModel.toggleAutoFocusMode() },
        onToggleMute = { viewModel.setMute(!isMuted) },
        modifier = modifier
    )
}

/**
 * Stateless vertical action bar for previewability and testing.
 */
@Composable
fun CameraControlsBar(
    showCameraControls: Boolean,
    isFlashAvailable: Boolean,
    isAutoWhiteBalanceAvailable: Boolean,
    isExposureCompensationAvailable: Boolean,
    isZoomAvailable: Boolean,
    isAutoFocusModeAvailable: Boolean,
    isMuted: Boolean,
    onToggleFlash: () -> Unit,
    onToggleAutoWhiteBalance: () -> Unit,
    onToggleExposureSlider: () -> Unit,
    onToggleZoomSlider: () -> Unit,
    onToggleAutoFocus: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            if (showCameraControls) {
                if (isFlashAvailable) {
                    CameraControlButton(
                        iconRes = R.drawable.ic_baseline_flash_on_24,
                        contentDescription = stringResource(R.string.turn_on_off_torch),
                        onClick = onToggleFlash
                    )
                }
                if (isAutoWhiteBalanceAvailable) {
                    CameraControlButton(
                        iconRes = R.drawable.ic_baseline_wb_auto_24,
                        contentDescription = stringResource(R.string.set_white_balance),
                        onClick = onToggleAutoWhiteBalance
                    )
                }
                if (isExposureCompensationAvailable) {
                    CameraControlButton(
                        iconRes = R.drawable.ic_baseline_exposure_24,
                        contentDescription = stringResource(R.string.set_exposure_compensation),
                        onClick = onToggleExposureSlider
                    )
                }
                if (isZoomAvailable) {
                    CameraControlButton(
                        iconRes = R.drawable.ic_baseline_zoom_in_24,
                        contentDescription = stringResource(R.string.set_zoom_ratio),
                        onClick = onToggleZoomSlider
                    )
                }
                if (isAutoFocusModeAvailable) {
                    CameraControlButton(
                        iconRes = R.drawable.ic_baseline_center_focus_strong_24,
                        contentDescription = stringResource(R.string.set_auto_focus_mode),
                        onClick = onToggleAutoFocus
                    )
                }
            }

            CameraControlButton(
                iconRes = if (isMuted) R.drawable.ic_baseline_mic_off_24 else R.drawable.ic_baseline_mic_24,
                contentDescription = stringResource(R.string.mute_unmute_microphone),
                onClick = onToggleMute
            )
        }
    }
}

@Composable
fun CameraControlButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = Color.White
        )
    }
}

@Preview(name = "All Controls Available", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewCameraControlsBarAll() {
    CameraControlsBar(
        showCameraControls = true,
        isFlashAvailable = true,
        isAutoWhiteBalanceAvailable = true,
        isExposureCompensationAvailable = true,
        isZoomAvailable = true,
        isAutoFocusModeAvailable = true,
        isMuted = false,
        onToggleFlash = {},
        onToggleAutoWhiteBalance = {},
        onToggleExposureSlider = {},
        onToggleZoomSlider = {},
        onToggleAutoFocus = {},
        onToggleMute = {}
    )
}

@Preview(name = "Only Mute (No Camera Controls)", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewCameraControlsBarOnlyMute() {
    CameraControlsBar(
        showCameraControls = false,
        isFlashAvailable = false,
        isAutoWhiteBalanceAvailable = false,
        isExposureCompensationAvailable = false,
        isZoomAvailable = false,
        isAutoFocusModeAvailable = false,
        isMuted = true,
        onToggleFlash = {},
        onToggleAutoWhiteBalance = {},
        onToggleExposureSlider = {},
        onToggleZoomSlider = {},
        onToggleAutoFocus = {},
        onToggleMute = {}
    )
}
