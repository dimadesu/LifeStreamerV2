package com.dimadesu.lifestreamer.ui.main.compose

import android.content.res.Configuration
import android.hardware.camera2.CaptureResult
import android.util.Range
import android.util.Rational
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Stateful bottom drawer hosting rich camera controls:
 * - Torch toggle switch
 * - White balance dropdown
 * - Focus mode dropdown with inline manual focus slider
 * - Inline horizontal exposure compensation slider
 * - Inline horizontal zoom slider
 */
@Composable
fun CameraControlsBottomDrawer(
    viewModel: PreviewViewModel,
    modifier: Modifier = Modifier
) {
    val isOpen by viewModel.isCameraControlsDrawerOpen.observeAsState(initial = false)
    val showCameraControls by viewModel.showCameraControls.observeAsState(initial = false)

    val cameras by viewModel.availableCamerasLiveData.observeAsState(initial = emptyList())
    val currentCameraId by viewModel.currentCameraIdLiveData.observeAsState(initial = null)
    val activeCameraName = cameras.firstOrNull { it.id == currentCameraId }?.displayName

    // Flash / Torch
    val isFlashAvailable by viewModel.isFlashAvailable.observeAsState(initial = false)
    val isTorchOn by viewModel.isTorchOn.observeAsState(initial = false)

    // White Balance
    val isAutoWhiteBalanceAvailable by viewModel.isAutoWhiteBalanceAvailable.observeAsState(initial = false)
    val availableWhiteBalanceModes by viewModel.availableWhiteBalanceModes.observeAsState(initial = emptyList())
    val currentWhiteBalanceMode by viewModel.currentWhiteBalanceMode.observeAsState(initial = CaptureResult.CONTROL_AWB_MODE_AUTO)

    // Focus
    val isAutoFocusModeAvailable by viewModel.isAutoFocusModeAvailable.observeAsState(initial = false)
    val availableAutoFocusModes by viewModel.availableAutoFocusModes.observeAsState(initial = emptyList())
    val currentAutoFocusMode by viewModel.currentAutoFocusMode.observeAsState(initial = CaptureResult.CONTROL_AF_MODE_AUTO)
    val lensDistanceRange by viewModel.lensDistanceRange.observeAsState()
    val lensDistance by viewModel.lensDistance.collectAsState()

    // EV Compensation
    val isExposureCompensationAvailable by viewModel.isExposureCompensationAvailable.observeAsState(initial = false)
    val exposureRange by viewModel.exposureCompensationRange.observeAsState()
    val exposureStep by viewModel.exposureCompensationStep.observeAsState()
    val exposure by viewModel.exposureCompensation.collectAsState()

    // Zoom
    val isZoomAvailable by viewModel.isZoomAvailable.observeAsState(initial = false)
    val zoomRange by viewModel.zoomRatioRange.observeAsState()
    val zoom by viewModel.zoomRatio.collectAsState()

    CameraControlsBottomDrawer(
        isOpen = isOpen,
        showCameraControls = showCameraControls,
        cameraDisplayName = activeCameraName,
        isFlashAvailable = isFlashAvailable,
        isTorchOn = isTorchOn,
        onSetTorch = { viewModel.setTorch(it) },
        isAutoWhiteBalanceAvailable = isAutoWhiteBalanceAvailable,
        availableWhiteBalanceModes = availableWhiteBalanceModes,
        currentWhiteBalanceMode = currentWhiteBalanceMode,
        onSelectWhiteBalanceMode = { viewModel.setWhiteBalanceMode(it) },
        isAutoFocusModeAvailable = isAutoFocusModeAvailable,
        availableAutoFocusModes = availableAutoFocusModes,
        currentAutoFocusMode = currentAutoFocusMode,
        onSelectAutoFocusMode = { viewModel.setAutoFocusMode(it) },
        lensDistanceRange = lensDistanceRange,
        lensDistance = lensDistance,
        onLensDistanceChange = { viewModel.setLensDistance(it) },
        isExposureCompensationAvailable = isExposureCompensationAvailable,
        exposureRange = exposureRange,
        exposureStep = exposureStep,
        exposure = exposure,
        onExposureChange = { viewModel.setExposureCompensation(it) },
        isZoomAvailable = isZoomAvailable,
        zoomRange = zoomRange,
        zoom = zoom,
        onZoomChange = { viewModel.setZoomRatio(it) },
        onClose = { viewModel.setCameraControlsDrawerOpen(false) },
        modifier = modifier
    )
}

/**
 * Renders a sleek vertical scrollbar on the edge of the container when content overflows.
 */
private fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    color: Color = Color(0x66FFFFFF),
    width: Dp = 3.dp
): Modifier = this.then(
    Modifier.drawWithContent {
        drawContent()
        val maxVal = scrollState.maxValue
        if (maxVal > 0) {
            val totalHeight = size.height
            val visibleRatio = (totalHeight / (totalHeight + maxVal)).coerceIn(0.15f, 1f)
            val thumbHeight = totalHeight * visibleRatio
            val scrollRatio = scrollState.value.toFloat() / maxVal.toFloat()
            val thumbOffset = (totalHeight - thumbHeight) * scrollRatio
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width - width.toPx(), thumbOffset),
                size = Size(width.toPx(), thumbHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
            )
        }
    }
)

/**
 * Stateless responsive panel for camera controls:
 * - Slides in as a sleek side inspector from the right edge in landscape.
 * - Slides in as a bottom drawer above the live button in portrait.
 * - Dynamic max height calculation prevents overflow on all screen sizes.
 * - Integrated vertical scrollbar shows scroll progress when content overflows.
 * - Visible only for camera video sources; closed explicitly via close (X) button, camera cog icon, or switching to non-camera source.
 */
@Composable
fun CameraControlsBottomDrawer(
    isOpen: Boolean,
    showCameraControls: Boolean,
    cameraDisplayName: String?,
    isFlashAvailable: Boolean,
    isTorchOn: Boolean,
    onSetTorch: (Boolean) -> Unit,
    isAutoWhiteBalanceAvailable: Boolean,
    availableWhiteBalanceModes: List<Int>,
    currentWhiteBalanceMode: Int,
    onSelectWhiteBalanceMode: (Int) -> Unit,
    isAutoFocusModeAvailable: Boolean,
    availableAutoFocusModes: List<Int>,
    currentAutoFocusMode: Int,
    onSelectAutoFocusMode: (Int) -> Unit,
    lensDistanceRange: Range<Float>?,
    lensDistance: Float,
    onLensDistanceChange: (Float) -> Unit,
    isExposureCompensationAvailable: Boolean,
    exposureRange: Range<Int>?,
    exposureStep: Rational?,
    exposure: Float,
    onExposureChange: (Float) -> Unit,
    isZoomAvailable: Boolean,
    zoomRange: Range<Float>?,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenHeight = configuration.screenHeightDp.dp

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AnimatedVisibility(
            visible = isOpen && showCameraControls,
            enter = if (isLandscape) {
                slideInHorizontally(initialOffsetX = { it }) + fadeIn()
            } else {
                slideInVertically(initialOffsetY = { it }) + fadeIn()
            },
            exit = if (isLandscape) {
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            } else {
                slideOutVertically(targetOffsetY = { it }) + fadeOut()
            },
            modifier = Modifier.align(
                if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter
            )
        ) {
            val scrollState = rememberScrollState()
            val maxPanelHeight = if (isLandscape) {
                (screenHeight - 24.dp).coerceAtLeast(200.dp)
            } else {
                (screenHeight * 0.65f).coerceAtMost(440.dp)
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF21C1C1E),
                tonalElevation = 10.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                modifier = Modifier
                    .then(
                        if (isLandscape) {
                            Modifier
                                .padding(end = 12.dp, top = 8.dp, bottom = 8.dp)
                                .widthIn(min = 300.dp, max = 340.dp)
                        } else {
                            Modifier
                                .padding(start = 16.dp, end = 16.dp, bottom = 76.dp)
                                .widthIn(min = 320.dp, max = 390.dp)
                        }
                    )
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .heightIn(max = maxPanelHeight)
                        .verticalScroll(scrollState)
                        .verticalScrollbar(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                // Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_settings_24),
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (cameraDisplayName != null) "CONTROLS • $cameraDisplayName".uppercase() else "CAMERA CONTROLS",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
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

                HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)

                // 1. Torch Toggle Switch
                if (isFlashAvailable) {
                    TorchSwitchRow(
                        isTorchOn = isTorchOn,
                        onCheckedChange = onSetTorch
                    )
                }

                // 2. White Balance Dropdown
                if (isAutoWhiteBalanceAvailable && availableWhiteBalanceModes.isNotEmpty()) {
                    CameraDropdownRow(
                        title = "White Balance",
                        iconRes = R.drawable.ic_baseline_wb_auto_24,
                        currentValueName = PreviewViewModel.getWhiteBalanceModeName(currentWhiteBalanceMode),
                        options = availableWhiteBalanceModes.map { it to PreviewViewModel.getWhiteBalanceModeName(it) },
                        currentValue = currentWhiteBalanceMode,
                        onSelectOption = onSelectWhiteBalanceMode
                    )
                }

                // 3. Focus Mode Dropdown
                if (isAutoFocusModeAvailable && availableAutoFocusModes.isNotEmpty()) {
                    CameraDropdownRow(
                        title = "Focus",
                        iconRes = R.drawable.ic_baseline_center_focus_strong_24,
                        currentValueName = PreviewViewModel.getAutoFocusModeName(currentAutoFocusMode),
                        options = availableAutoFocusModes.map { it to PreviewViewModel.getAutoFocusModeName(it) },
                        currentValue = currentAutoFocusMode,
                        onSelectOption = onSelectAutoFocusMode
                    )
                }

                // 4. Manual Focus Slider (appears when Manual focus mode is chosen)
                val isManualFocus = currentAutoFocusMode == CaptureResult.CONTROL_AF_MODE_OFF
                AnimatedVisibility(
                    visible = isManualFocus && lensDistanceRange != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    if (lensDistanceRange != null) {
                        ManualFocusSliderBlock(
                            lensDistance = lensDistance,
                            range = lensDistanceRange,
                            onValueChange = onLensDistanceChange
                        )
                    }
                }

                val hasSliders = (isExposureCompensationAvailable && exposureRange != null) ||
                        (isZoomAvailable && zoomRange != null)
                if (hasSliders) {
                    HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 0.5.dp)
                }

                // 5. Exposure Compensation Inline Slider
                if (isExposureCompensationAvailable && exposureRange != null) {
                    ExposureCompensationSliderBlock(
                        exposure = exposure,
                        range = exposureRange,
                        step = exposureStep,
                        onValueChange = onExposureChange
                    )
                }

                // 6. Zoom Inline Slider
                if (isZoomAvailable && zoomRange != null) {
                    ZoomSliderBlock(
                        zoom = zoom,
                        range = zoomRange,
                        onValueChange = onZoomChange
                    )
                }
            }
        }
    }
}
}

/**
 * Row displaying Torch switch with icon and on/off feedback.
 */
@Composable
private fun TorchSwitchRow(
    isTorchOn: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_flash_on_24),
                contentDescription = null,
                tint = if (isTorchOn) Color(0xFFFFD54F) else Color(0xB3FFFFFF),
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = "Torch",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isTorchOn) "On" else "Off",
                    color = if (isTorchOn) Color(0xFFFFD54F) else Color(0x80FFFFFF),
                    fontSize = 11.sp
                )
            }
        }

        Switch(
            checked = isTorchOn,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF4CAF50),
                uncheckedThumbColor = Color(0xFFAAAAAA),
                uncheckedTrackColor = Color(0x33FFFFFF),
                uncheckedBorderColor = Color(0x44FFFFFF)
            )
        )
    }
}

/**
 * Clean camera-style dropdown row with icon, title, and compact interactive selection pill.
 */
@Composable
private fun CameraDropdownRow(
    title: String,
    iconRes: Int,
    currentValueName: String,
    options: List<Pair<Int, String>>,
    currentValue: Int,
    onSelectOption: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color(0xB3FFFFFF),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(8.dp),
                color = Color(0x2BFFFFFF),
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                modifier = Modifier.wrapContentSize()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = currentValueName,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color(0xCCFFFFFF),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(Color(0xFF242426))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            ) {
                options.forEach { (optionId, optionName) ->
                    val isSelected = optionId == currentValue
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optionName,
                                color = if (isSelected) Color(0xFF4CAF50) else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        onClick = {
                            onSelectOption(optionId)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Inline horizontal slider block for Manual Focus distance.
 */
@Composable
private fun ManualFocusSliderBlock(
    lensDistance: Float,
    range: Range<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x1A4CAF50), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x334CAF50), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
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
                    painter = painterResource(id = R.drawable.ic_baseline_center_focus_strong_24),
                    contentDescription = null,
                    tint = Color(0xFF81C784),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Manual Focus Distance",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = String.format(Locale.US, "%.2f", lensDistance),
                color = Color(0xFF81C784),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0x334CAF50), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        if (range.lower < range.upper) {
            Slider(
                value = lensDistance.coerceIn(range.lower, range.upper),
                onValueChange = onValueChange,
                valueRange = range.lower..range.upper,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF4CAF50),
                    inactiveTrackColor = Color(0x33FFFFFF)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Text("Far (∞)", fontSize = 10.sp, color = Color(0x80FFFFFF))
                Text("Near", fontSize = 10.sp, color = Color(0x80FFFFFF))
            }
        }
    }
}

/**
 * Inline horizontal slider block for Exposure Compensation.
 */
@Composable
private fun ExposureCompensationSliderBlock(
    exposure: Float,
    range: Range<Int>,
    step: Rational?,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val min = range.lower.toFloat()
    val max = range.upper.toFloat()
    val stepVal = step?.toFloat() ?: 0f
    val steps = if (stepVal > 0f) ((max - min) / stepVal).roundToInt() - 1 else 0

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
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
                    painter = painterResource(id = R.drawable.ic_baseline_exposure_24),
                    contentDescription = null,
                    tint = Color(0xB3FFFFFF),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "EV Compensation",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = formatEv(exposure),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0x22FFFFFF), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        if (min < max) {
            Slider(
                value = exposure.coerceIn(min, max),
                onValueChange = onValueChange,
                valueRange = min..max,
                steps = steps.coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF4CAF50),
                    inactiveTrackColor = Color(0x33FFFFFF)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Text(formatEv(min), fontSize = 10.sp, color = Color(0x80FFFFFF))
                Text(formatEv(max), fontSize = 10.sp, color = Color(0x80FFFFFF))
            }
        }
    }
}

/**
 * Inline horizontal slider block for Zoom.
 */
@Composable
private fun ZoomSliderBlock(
    zoom: Float,
    range: Range<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
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
                    painter = painterResource(id = R.drawable.ic_baseline_zoom_in_24),
                    contentDescription = null,
                    tint = Color(0xB3FFFFFF),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Zoom",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = String.format(Locale.US, "%.1fx", zoom),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0x22FFFFFF), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        if (range.lower < range.upper) {
            Slider(
                value = zoom.coerceIn(range.lower, range.upper),
                onValueChange = onValueChange,
                valueRange = range.lower..range.upper,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF4CAF50),
                    inactiveTrackColor = Color(0x33FFFFFF)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Text(String.format(Locale.US, "%.1fx", range.lower), fontSize = 10.sp, color = Color(0x80FFFFFF))
                Text(String.format(Locale.US, "%.1fx", range.upper), fontSize = 10.sp, color = Color(0x80FFFFFF))
            }
        }
    }
}

private fun formatEv(value: Float): String =
    if (value > 0f) String.format(Locale.US, "+%.1f EV", value)
    else if (value < 0f) String.format(Locale.US, "%.1f EV", value)
    else "0.0 EV"

@Preview(name = "Side Inspector - Landscape", widthDp = 840, heightDp = 390, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewCameraControlsLandscapeSideInspector() {
    CameraControlsBottomDrawer(
        isOpen = true,
        showCameraControls = true,
        cameraDisplayName = "Back (0)",
        isFlashAvailable = true,
        isTorchOn = false,
        onSetTorch = {},
        isAutoWhiteBalanceAvailable = true,
        availableWhiteBalanceModes = listOf(
            CaptureResult.CONTROL_AWB_MODE_AUTO,
            CaptureResult.CONTROL_AWB_MODE_DAYLIGHT,
            CaptureResult.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
            CaptureResult.CONTROL_AWB_MODE_INCANDESCENT
        ),
        currentWhiteBalanceMode = CaptureResult.CONTROL_AWB_MODE_AUTO,
        onSelectWhiteBalanceMode = {},
        isAutoFocusModeAvailable = true,
        availableAutoFocusModes = listOf(
            CaptureResult.CONTROL_AF_MODE_AUTO,
            CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
            CaptureResult.CONTROL_AF_MODE_OFF
        ),
        currentAutoFocusMode = CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
        onSelectAutoFocusMode = {},
        lensDistanceRange = Range(0f, 10f),
        lensDistance = 0f,
        onLensDistanceChange = {},
        isExposureCompensationAvailable = true,
        exposureRange = Range(-4, 4),
        exposureStep = Rational(1, 2),
        exposure = 0.5f,
        onExposureChange = {},
        isZoomAvailable = true,
        zoomRange = Range(1f, 8f),
        zoom = 1.5f,
        onZoomChange = {},
        onClose = {}
    )
}

@Preview(name = "Bottom Drawer - Portrait", widthDp = 390, heightDp = 840, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewCameraControlsBottomDrawerPortrait() {
    CameraControlsBottomDrawer(
        isOpen = true,
        showCameraControls = true,
        cameraDisplayName = "Back (0)",
        isFlashAvailable = true,
        isTorchOn = false,
        onSetTorch = {},
        isAutoWhiteBalanceAvailable = true,
        availableWhiteBalanceModes = listOf(
            CaptureResult.CONTROL_AWB_MODE_AUTO,
            CaptureResult.CONTROL_AWB_MODE_DAYLIGHT,
            CaptureResult.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
            CaptureResult.CONTROL_AWB_MODE_INCANDESCENT
        ),
        currentWhiteBalanceMode = CaptureResult.CONTROL_AWB_MODE_AUTO,
        onSelectWhiteBalanceMode = {},
        isAutoFocusModeAvailable = true,
        availableAutoFocusModes = listOf(
            CaptureResult.CONTROL_AF_MODE_AUTO,
            CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
            CaptureResult.CONTROL_AF_MODE_OFF
        ),
        currentAutoFocusMode = CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
        onSelectAutoFocusMode = {},
        lensDistanceRange = Range(0f, 10f),
        lensDistance = 0f,
        onLensDistanceChange = {},
        isExposureCompensationAvailable = true,
        exposureRange = Range(-4, 4),
        exposureStep = Rational(1, 2),
        exposure = 0.5f,
        onExposureChange = {},
        isZoomAvailable = true,
        zoomRange = Range(1f, 8f),
        zoom = 1.5f,
        onZoomChange = {},
        onClose = {}
    )
}

@Preview(name = "Drawer Open - Manual Focus Active", widthDp = 840, heightDp = 390, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewCameraControlsBottomDrawerManualFocus() {
    CameraControlsBottomDrawer(
        isOpen = true,
        showCameraControls = true,
        cameraDisplayName = "Back (0)",
        isFlashAvailable = true,
        isTorchOn = true,
        onSetTorch = {},
        isAutoWhiteBalanceAvailable = true,
        availableWhiteBalanceModes = listOf(
            CaptureResult.CONTROL_AWB_MODE_AUTO,
            CaptureResult.CONTROL_AWB_MODE_DAYLIGHT
        ),
        currentWhiteBalanceMode = CaptureResult.CONTROL_AWB_MODE_DAYLIGHT,
        onSelectWhiteBalanceMode = {},
        isAutoFocusModeAvailable = true,
        availableAutoFocusModes = listOf(
            CaptureResult.CONTROL_AF_MODE_AUTO,
            CaptureResult.CONTROL_AF_MODE_OFF
        ),
        currentAutoFocusMode = CaptureResult.CONTROL_AF_MODE_OFF,
        onSelectAutoFocusMode = {},
        lensDistanceRange = Range(0f, 10f),
        lensDistance = 3.5f,
        onLensDistanceChange = {},
        isExposureCompensationAvailable = true,
        exposureRange = Range(-2, 2),
        exposureStep = Rational(1, 1),
        exposure = 0f,
        onExposureChange = {},
        isZoomAvailable = true,
        zoomRange = Range(1f, 5f),
        zoom = 2.0f,
        onZoomChange = {},
        onClose = {}
    )
}
