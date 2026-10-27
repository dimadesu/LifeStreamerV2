package com.dimadesu.lifestreamer.ui.main.compose

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.models.AudioDebugInfo
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel

/**
 * Representation of an audio source option from arrays.xml.
 */
data class AudioSourceOption(
    val name: String,
    val value: Int
)

/**
 * Stateful Composable for the Audio Source & Real-time Debug drawer/panel:
 * - Slides in as a bottom drawer (portrait) or side inspector (landscape).
 * - Allows switching between audio input sources (e.g. CAMCORDER vs VOICE_COMMUNICATION).
 * - Real-time system audio debugging metrics (hardware route, effects, sample rate, bit format, channels).
 */
@Composable
fun AudioSourceBottomDrawer(
    viewModel: PreviewViewModel,
    modifier: Modifier = Modifier
) {
    val isOpen by viewModel.isAudioSourceDrawerOpen.observeAsState(initial = false)
    val isRtmpSource by viewModel.isRtmpSource.observeAsState(initial = false)
    val selectedSourceType by viewModel.selectedAudioSourceType.observeAsState(initial = android.media.MediaRecorder.AudioSource.CAMCORDER)
    val audioDebugInfo by viewModel.audioDebugInfoLiveData.observeAsState(initial = null)

    val context = LocalContext.current
    val options = remember(context) {
        val entries = context.resources.getStringArray(R.array.AudioSourceTypeEntries)
        val values = context.resources.getStringArray(R.array.AudioSourceTypeEntryValues)
        entries.zip(values.mapNotNull { it.toIntOrNull() }).map { (name, value) ->
            AudioSourceOption(name = name, value = value)
        }
    }

    AudioSourceBottomDrawer(
        isOpen = isOpen,
        isVisibleForSource = !isRtmpSource,
        availableOptions = options,
        selectedSourceType = selectedSourceType,
        debugInfo = audioDebugInfo,
        onSelectSourceType = { newType ->
            if (newType != selectedSourceType) {
                viewModel.setSelectedAudioSourceType(newType)
                viewModel.applySelectedAudioSource()
            }
        },
        onClose = { viewModel.setAudioSourceDrawerOpen(false) },
        modifier = modifier
    )
}

/**
 * Renders a vertical scrollbar on the edge of the container when content overflows.
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
 * Stateless responsive drawer for Audio Source settings and live system stats:
 * - Slides in as a side inspector from the right edge in landscape.
 * - Slides in as a bottom drawer above the live button in portrait.
 * - Dynamic max height calculation prevents overflow on all screen sizes.
 */
@Composable
fun AudioSourceBottomDrawer(
    isOpen: Boolean,
    isVisibleForSource: Boolean,
    availableOptions: List<AudioSourceOption>,
    selectedSourceType: Int,
    debugInfo: AudioDebugInfo?,
    onSelectSourceType: (Int) -> Unit,
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
            visible = isOpen && isVisibleForSource,
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
                (screenHeight * 0.75f).coerceAtMost(520.dp)
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
                                .widthIn(min = 320.dp, max = 360.dp)
                        } else {
                            Modifier
                                .padding(start = 16.dp, end = 16.dp, bottom = 76.dp)
                                .widthIn(min = 320.dp, max = 400.dp)
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                painter = painterResource(id = R.drawable.ic_baseline_mic_24),
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "AUDIO SOURCE",
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
                                contentDescription = stringResource(R.string.close_audio_source_controls),
                                tint = Color(0xB3FFFFFF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)

                    // 1. Audio Source Selection Section
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "AUDIO SOURCE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0x99FFFFFF),
                            letterSpacing = 0.5.sp
                        )

                        // Option Buttons (stacked on separate lines)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            availableOptions.forEach { option ->
                                val isSelected = option.value == selectedSourceType
                                AudioSourceOptionButton(
                                    name = option.name,
                                    isSelected = isSelected,
                                    onClick = { onSelectSourceType(option.value) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Helper hint banner
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x14FFFFFF),
                            border = BorderStroke(0.5.dp, Color(0x22FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "💡",
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Try VOICE_COMMUNICATION if USB audio is too loud or clipping.",
                                    fontSize = 12.sp,
                                    color = Color(0xCCFFFFFF),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)

                    // 2. Actual System State Section
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ACTUAL SYSTEM STATE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0x99FFFFFF),
                            letterSpacing = 0.5.sp
                        )

                        // Metrics Card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x0FFFFFFF),
                            border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                AudioStatRow(
                                    label = "Input",
                                    value = debugInfo?.audioSource ?: "N/A"
                                )
                                HorizontalDivider(color = Color(0x12FFFFFF), thickness = 0.5.dp)

                                AudioStatRow(
                                    label = "Audio Source",
                                    value = debugInfo?.getActualSystemSourceDisplay() ?: "N/A"
                                )
                                HorizontalDivider(color = Color(0x12FFFFFF), thickness = 0.5.dp)

                                AudioStatRow(
                                    label = "Effects",
                                    value = debugInfo?.getAudioEffectsString() ?: "N/A"
                                )
                                HorizontalDivider(color = Color(0x12FFFFFF), thickness = 0.5.dp)

                                AudioStatRow(
                                    label = "Sample Rate",
                                    value = debugInfo?.getSampleRateKHz() ?: "N/A"
                                )
                                HorizontalDivider(color = Color(0x12FFFFFF), thickness = 0.5.dp)

                                AudioStatRow(
                                    label = "Bit Format",
                                    value = debugInfo?.getBitFormatString() ?: "N/A"
                                )
                                HorizontalDivider(color = Color(0x12FFFFFF), thickness = 0.5.dp)

                                AudioStatRow(
                                    label = "Channels",
                                    value = debugInfo?.getChannelConfigString() ?: "N/A"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern selectable button for choosing an audio source.
 */
@Composable
private fun AudioSourceOptionButton(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0x2E4CAF50) else Color(0x1AFFFFFF),
            contentColor = if (isSelected) Color(0xFF81C784) else Color(0xB3FFFFFF)
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF4CAF50) else Color(0x22FFFFFF)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier
                        .size(14.dp)
                        .padding(end = 4.dp)
                )
            }
            Text(
                text = name,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.3.sp
            )
        }
    }
}

/**
 * Key-value stat row for audio debug information.
 */
@Composable
private fun AudioStatRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0x88FFFFFF)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "Audio Drawer - Portrait Active", widthDp = 390, heightDp = 840, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewAudioSourceBottomDrawerPortraitActive() {
    AudioSourceBottomDrawer(
        isOpen = true,
        isVisibleForSource = true,
        availableOptions = listOf(
            AudioSourceOption("CAMCORDER", 5),
            AudioSourceOption("VOICE_COMMUNICATION", 7)
        ),
        selectedSourceType = 5,
        debugInfo = AudioDebugInfo(
            audioSource = "CAMCORDER",
            actualSystemSource = "Built-in Mic",
            sampleRate = 48000,
            bitFormat = android.media.AudioFormat.ENCODING_PCM_16BIT,
            channelConfig = android.media.AudioFormat.CHANNEL_IN_STEREO,
            noiseSuppression = false,
            acousticEchoCanceler = false,
            automaticGainControl = false,
            hasActiveRecording = true
        ),
        onSelectSourceType = {},
        onClose = {}
    )
}

@Preview(name = "Audio Drawer - Landscape Active", widthDp = 840, heightDp = 390, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewAudioSourceBottomDrawerLandscapeActive() {
    AudioSourceBottomDrawer(
        isOpen = true,
        isVisibleForSource = true,
        availableOptions = listOf(
            AudioSourceOption("CAMCORDER", 5),
            AudioSourceOption("VOICE_COMMUNICATION", 7)
        ),
        selectedSourceType = 7,
        debugInfo = AudioDebugInfo(
            audioSource = "VOICE_COMMUNICATION",
            actualSystemSource = "USB Audio",
            sampleRate = 48000,
            bitFormat = android.media.AudioFormat.ENCODING_PCM_16BIT,
            channelConfig = android.media.AudioFormat.CHANNEL_IN_STEREO,
            noiseSuppression = true,
            acousticEchoCanceler = true,
            automaticGainControl = true,
            hasActiveRecording = true
        ),
        onSelectSourceType = {},
        onClose = {}
    )
}

@Preview(name = "Audio Drawer - Idle", widthDp = 390, heightDp = 840, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewAudioSourceBottomDrawerIdle() {
    AudioSourceBottomDrawer(
        isOpen = true,
        isVisibleForSource = true,
        availableOptions = listOf(
            AudioSourceOption("CAMCORDER", 5),
            AudioSourceOption("VOICE_COMMUNICATION", 7)
        ),
        selectedSourceType = 5,
        debugInfo = null,
        onSelectSourceType = {},
        onClose = {}
    )
}
