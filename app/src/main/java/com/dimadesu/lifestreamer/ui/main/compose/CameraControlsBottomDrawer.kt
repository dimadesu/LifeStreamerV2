package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel

/**
 * Stateful bottom drawer hosting camera action buttons (Torch, WB, EV, Zoom, AF).
 * Appears when the cog icon next to the active camera button is pressed.
 */
@Composable
fun CameraControlsBottomDrawer(
    viewModel: PreviewViewModel,
    modifier: Modifier = Modifier
) {
    val isOpen by viewModel.isCameraControlsDrawerOpen.observeAsState(initial = false)
    val showCameraControls by viewModel.showCameraControls.observeAsState(initial = false)
    val isFlashAvailable by viewModel.isFlashAvailable.observeAsState(initial = false)
    val isAutoWhiteBalanceAvailable by viewModel.isAutoWhiteBalanceAvailable.observeAsState(initial = false)
    val isExposureCompensationAvailable by viewModel.isExposureCompensationAvailable.observeAsState(initial = false)
    val isZoomAvailable by viewModel.isZoomAvailable.observeAsState(initial = false)
    val isAutoFocusModeAvailable by viewModel.isAutoFocusModeAvailable.observeAsState(initial = false)

    val showExposureSlider by viewModel.showExposureSlider.observeAsState(initial = false)
    val showZoomSlider by viewModel.showZoomSlider.observeAsState(initial = false)
    val showLensDistanceSlider by viewModel.showLensDistanceSlider.observeAsState(initial = false)

    val cameras by viewModel.availableCamerasLiveData.observeAsState(initial = emptyList())
    val currentCameraId by viewModel.currentCameraIdLiveData.observeAsState(initial = null)
    val activeCameraName = cameras.firstOrNull { it.id == currentCameraId }?.displayName

    CameraControlsBottomDrawer(
        isOpen = isOpen,
        showCameraControls = showCameraControls,
        cameraDisplayName = activeCameraName,
        isFlashAvailable = isFlashAvailable,
        isAutoWhiteBalanceAvailable = isAutoWhiteBalanceAvailable,
        isExposureCompensationAvailable = isExposureCompensationAvailable,
        isZoomAvailable = isZoomAvailable,
        isAutoFocusModeAvailable = isAutoFocusModeAvailable,
        showExposureSlider = showExposureSlider,
        showZoomSlider = showZoomSlider,
        showLensDistanceSlider = showLensDistanceSlider,
        onToggleFlash = { viewModel.toggleFlash() },
        onToggleAutoWhiteBalance = { viewModel.toggleAutoWhiteBalanceMode() },
        onToggleExposureSlider = { viewModel.toggleExposureSlider() },
        onToggleZoomSlider = { viewModel.toggleZoomSlider() },
        onToggleAutoFocus = { viewModel.toggleAutoFocusMode() },
        onClose = { viewModel.setCameraControlsDrawerOpen(false) },
        modifier = modifier
    )
}

/**
 * Stateless non-modal bottom panel for camera controls.
 */
@Composable
fun CameraControlsBottomDrawer(
    isOpen: Boolean,
    showCameraControls: Boolean,
    cameraDisplayName: String?,
    isFlashAvailable: Boolean,
    isAutoWhiteBalanceAvailable: Boolean,
    isExposureCompensationAvailable: Boolean,
    isZoomAvailable: Boolean,
    isAutoFocusModeAvailable: Boolean,
    showExposureSlider: Boolean,
    showZoomSlider: Boolean,
    showLensDistanceSlider: Boolean,
    onToggleFlash: () -> Unit,
    onToggleAutoWhiteBalance: () -> Unit,
    onToggleExposureSlider: () -> Unit,
    onToggleZoomSlider: () -> Unit,
    onToggleAutoFocus: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOpen && showCameraControls,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xDD1E1E1E),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            modifier = Modifier.wrapContentSize()
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .wrapContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_settings_24),
                            contentDescription = null,
                            tint = Color(0xB3FFFFFF),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (cameraDisplayName != null) "CONTROLS • $cameraDisplayName" else "CAMERA CONTROLS",
                            color = Color(0xB3FFFFFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_close_24),
                            contentDescription = stringResource(R.string.close_camera_controls),
                            tint = Color(0xB3FFFFFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Buttons Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 8.dp)
                ) {
                    if (isFlashAvailable) {
                        DrawerItem(
                            iconRes = R.drawable.ic_baseline_flash_on_24,
                            label = "Torch",
                            contentDescription = stringResource(R.string.turn_on_off_torch),
                            onClick = onToggleFlash
                        )
                    }
                    if (isAutoWhiteBalanceAvailable) {
                        DrawerItem(
                            iconRes = R.drawable.ic_baseline_wb_auto_24,
                            label = "WB",
                            contentDescription = stringResource(R.string.set_white_balance),
                            onClick = onToggleAutoWhiteBalance
                        )
                    }
                    if (isExposureCompensationAvailable) {
                        DrawerItem(
                            iconRes = R.drawable.ic_baseline_exposure_24,
                            label = "EV",
                            contentDescription = stringResource(R.string.set_exposure_compensation),
                            isActive = showExposureSlider,
                            onClick = onToggleExposureSlider
                        )
                    }
                    if (isZoomAvailable) {
                        DrawerItem(
                            iconRes = R.drawable.ic_baseline_zoom_in_24,
                            label = "Zoom",
                            contentDescription = stringResource(R.string.set_zoom_ratio),
                            isActive = showZoomSlider,
                            onClick = onToggleZoomSlider
                        )
                    }
                    if (isAutoFocusModeAvailable) {
                        DrawerItem(
                            iconRes = R.drawable.ic_baseline_center_focus_strong_24,
                            label = "Focus",
                            contentDescription = stringResource(R.string.set_auto_focus_mode),
                            isActive = showLensDistanceSlider,
                            onClick = onToggleAutoFocus
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(
    iconRes: Int,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        CameraControlButton(
            iconRes = iconRes,
            contentDescription = contentDescription,
            isActive = isActive,
            onClick = onClick
        )
        Text(
            text = label,
            color = Color(0xCCFFFFFF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(name = "Drawer Open - All Controls Available", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewCameraControlsBottomDrawerOpen() {
    CameraControlsBottomDrawer(
        isOpen = true,
        showCameraControls = true,
        cameraDisplayName = "Back (0)",
        isFlashAvailable = true,
        isAutoWhiteBalanceAvailable = true,
        isExposureCompensationAvailable = true,
        isZoomAvailable = true,
        isAutoFocusModeAvailable = true,
        showExposureSlider = false,
        showZoomSlider = false,
        showLensDistanceSlider = false,
        onToggleFlash = {},
        onToggleAutoWhiteBalance = {},
        onToggleExposureSlider = {},
        onToggleZoomSlider = {},
        onToggleAutoFocus = {},
        onClose = {}
    )
}

@Preview(name = "Drawer Open - Sliders Active", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewCameraControlsBottomDrawerSlidersActive() {
    CameraControlsBottomDrawer(
        isOpen = true,
        showCameraControls = true,
        cameraDisplayName = "Front (1)",
        isFlashAvailable = false,
        isAutoWhiteBalanceAvailable = true,
        isExposureCompensationAvailable = true,
        isZoomAvailable = true,
        isAutoFocusModeAvailable = false,
        showExposureSlider = true,
        showZoomSlider = false,
        showLensDistanceSlider = false,
        onToggleFlash = {},
        onToggleAutoWhiteBalance = {},
        onToggleExposureSlider = {},
        onToggleZoomSlider = {},
        onToggleAutoFocus = {},
        onClose = {}
    )
}
