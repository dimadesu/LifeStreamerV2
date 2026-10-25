package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Camera adjustment sliders (exposure compensation, zoom ratio and manual focus lens distance).
 */
@Composable
fun CameraSliders(
    viewModel: PreviewViewModel,
    modifier: Modifier = Modifier
) {
    val showExposure by viewModel.showExposureSlider.observeAsState(false)
    val exposureRange by viewModel.exposureCompensationRange.observeAsState()
    val exposureStep by viewModel.exposureCompensationStep.observeAsState()
    val exposure by viewModel.exposureCompensation.collectAsState()

    val showZoom by viewModel.showZoomSlider.observeAsState(false)
    val zoomRange by viewModel.zoomRatioRange.observeAsState()
    val zoom by viewModel.zoomRatio.collectAsState()

    val showLensDistance by viewModel.showLensDistanceSlider.observeAsState(false)
    val lensDistanceRange by viewModel.lensDistanceRange.observeAsState()
    val lensDistance by viewModel.lensDistance.collectAsState()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        exposureRange?.let { range ->
            if (showExposure) {
                val min = range.lower.toFloat()
                val max = range.upper.toFloat()
                val step = exposureStep?.toFloat() ?: 0f
                // `steps` is the number of intermediate stops between min and max.
                val steps = if (step > 0f) ((max - min) / step).roundToInt() - 1 else 0
                VerticalSlider(
                    value = exposure,
                    onValueChange = viewModel::setExposureCompensation,
                    valueRange = min..max,
                    steps = steps.coerceAtLeast(0)
                )
            }
        }
        zoomRange?.let { range ->
            if (showZoom) {
                VerticalSlider(
                    value = zoom,
                    onValueChange = viewModel::setZoomRatio,
                    valueRange = range.lower..range.upper
                )
            }
        }
        lensDistanceRange?.let { range ->
            if (showLensDistance) {
                VerticalSlider(
                    value = lensDistance,
                    onValueChange = viewModel::setLensDistance,
                    valueRange = range.lower..range.upper
                )
            }
        }
    }
}

private val sliderColors
    @Composable get() = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color(0xFF6200EE),
        inactiveTrackColor = Color(0x66FFFFFF)
    )

/**
 * Material slider rotated to be vertical (min at the bottom), 200dp long like the old XML.
 * Shows a value label to the left of the thumb while dragging, like the old Material slider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    // An empty range (e.g. fixed-focus lens) would crash the Slider.
    if (valueRange.isEmpty() || valueRange.start == valueRange.endInclusive) return

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()

    Slider(
        value = value.coerceIn(valueRange),
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        interactionSource = interactionSource,
        colors = sliderColors,
        thumb = {
            Box(contentAlignment = Alignment.Center) {
                SliderDefaults.Thumb(interactionSource = interactionSource, colors = sliderColors)
                if (isPressed || isDragged) {
                    ValueLabel(formatValue(value))
                }
            }
        },
        modifier = Modifier
            .graphicsLayer { rotationZ = 270f }
            .layout { measurable, _ ->
                // Measure horizontally, report swapped size so the rotated slider fits.
                val length = 200.dp.roundToPx()
                val thickness = 48.dp.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(length, thickness))
                layout(thickness, length) {
                    placeable.place(
                        x = (thickness - length) / 2,
                        y = (length - thickness) / 2
                    )
                }
            }
    )
}

/**
 * Label drawn "above" the thumb in slider space, which is to the left of it on screen after
 * the slider is rotated. Counter-rotated so the text stays upright. Takes no layout space.
 */
@Composable
private fun ValueLabel(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val offset = 42.dp.roundToPx()
                layout(0, 0) {
                    placeable.place(-placeable.width / 2, -offset - placeable.height / 2)
                }
            }
            .graphicsLayer { rotationZ = 90f }
            .background(Color(0xCC000000), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** Same format as Material's default slider label: integers without decimals, else 2 decimals. */
private fun formatValue(value: Float): String =
    if (value % 1f == 0f) String.format(Locale.US, "%.0f", value)
    else String.format(Locale.US, "%.2f", value)

@Preview(showBackground = true, backgroundColor = 0xFF222222)
@Composable
private fun VerticalSliderPreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VerticalSlider(value = 1f, onValueChange = {}, valueRange = -4f..4f, steps = 15)
        VerticalSlider(value = 2.5f, onValueChange = {}, valueRange = 1f..10f)
    }
}
