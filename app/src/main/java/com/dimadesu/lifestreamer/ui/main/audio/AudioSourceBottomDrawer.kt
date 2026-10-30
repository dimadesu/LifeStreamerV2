package com.dimadesu.lifestreamer.ui.main.audio

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.ApplicationConstants
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.models.AudioDebugInfo
import com.dimadesu.lifestreamer.ui.main.compose.SettingsDropdownRow
import com.dimadesu.lifestreamer.ui.main.compose.verticalScrollbar
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel
import java.util.Locale

/**
 * Representation of an audio source option from arrays.xml.
 */
data class AudioSourceOption(
    val name: String,
    val value: Int
)

/**
 * Stateful Composable for the Audio Settings & Source drawer/panel:
 * - Slides in as a bottom drawer (portrait) or side inspector (landscape).
 * - Displays Audio Settings at the top (Encoder, Channels, Bitrate, Sample Rate, Profile).
 * - Displays Audio Source Selection (CAMCORDER, VOICE_COMMUNICATION) in the middle.
 * - Displays Actual System State diagnostic metrics at the bottom.
 */
@Composable
fun AudioSourceBottomDrawer(
    viewModel: PreviewViewModel,
    modifier: Modifier = Modifier
) {
    val audio = viewModel.audioDrawer
    val isOpen by audio.isOpen.observeAsState(initial = false)
    val isRtmpSource by viewModel.isRtmpSource.observeAsState(initial = false)
    val isStreaming by viewModel.isStreamingLiveData.observeAsState(initial = false)
    val audioSettingsState by audio.settingsState.observeAsState(
        initial = AudioSettingsState()
    )
    val selectedSourceType by audio.selectedSourceType.observeAsState(
        initial = android.media.MediaRecorder.AudioSource.CAMCORDER
    )
    val audioDebugInfo by audio.debugInfo.observeAsState(initial = null)

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
        isRtmpSource = isRtmpSource,
        audioSettingsState = audioSettingsState,
        isStreaming = isStreaming,
        onSelectEncoder = { audio.setEncoder(it) },
        onSelectChannelConfig = { audio.setChannelConfig(it) },
        onSelectBitrate = { audio.setBitrate(it) },
        onSelectSampleRate = { audio.setSampleRate(it) },
        onSelectProfile = { audio.setProfile(it) },
        availableOptions = options,
        selectedSourceType = selectedSourceType,
        debugInfo = audioDebugInfo,
        onSelectSourceType = { newType ->
            if (newType != selectedSourceType) {
                audio.setSelectedSourceType(newType)
                audio.applySelectedSource()
            }
        },
        onClose = { audio.setDrawerOpen(false) },
        modifier = modifier
    )
}

/**
 * Stateless responsive drawer for Audio Settings, Source selection, and live system stats:
 * - Audio Settings on top (Encoder, Channels, Bitrate, Sample Rate, Profile).
 * - Existing UI of Audio Source drawer at the bottom (Audio Source options + hint + Actual System State).
 * - Slides in as a side inspector from the right edge in landscape.
 * - Slides in as a bottom drawer above the live button in portrait.
 * - Dynamic max height calculation prevents overflow on all screen sizes.
 */
@Composable
fun AudioSourceBottomDrawer(
    isOpen: Boolean,
    isRtmpSource: Boolean,
    audioSettingsState: AudioSettingsState,
    isStreaming: Boolean = false,
    onSelectEncoder: (String) -> Unit,
    onSelectChannelConfig: (Int) -> Unit,
    onSelectBitrate: (Int) -> Unit,
    onSelectSampleRate: (Int) -> Unit,
    onSelectProfile: (Int) -> Unit,
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
            visible = isOpen,
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
                (screenHeight * 0.85f).coerceAtMost(640.dp)
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
                                painter = painterResource(id = R.drawable.ic_baseline_settings_24),
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "AUDIO SETTINGS",
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

                    // 1. Audio Settings Section (TOP)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "AUDIO SETTINGS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0x99FFFFFF),
                            letterSpacing = 0.5.sp
                        )

                        // Encoder Dropdown
                        val currentEncoderName = audioSettingsState.availableEncoders
                            .firstOrNull { it.first == audioSettingsState.encoder }?.second
                            ?: audioSettingsState.encoder
                        SettingsDropdownRow(
                            title = "Encoder",
                            currentValue = audioSettingsState.encoder,
                            currentValueName = currentEncoderName,
                            options = audioSettingsState.availableEncoders,
                            onSelectOption = onSelectEncoder
                        )

                        // Channels Dropdown
                        val currentChannelName = audioSettingsState.availableChannelConfigs
                            .firstOrNull { it.first == audioSettingsState.channelConfig }?.second
                            ?: ApplicationConstants.Audio.channelConfigOptions
                                .firstOrNull { it.first == audioSettingsState.channelConfig }?.second
                            ?: "${audioSettingsState.channelConfig}"
                        SettingsDropdownRow(
                            title = "Channels",
                            currentValue = audioSettingsState.channelConfig,
                            currentValueName = currentChannelName,
                            options = audioSettingsState.availableChannelConfigs,
                            onSelectOption = onSelectChannelConfig
                        )

                        // Bitrate Dropdown
                        val currentBitrateName = audioSettingsState.availableBitrates
                            .firstOrNull { it.first == audioSettingsState.bitrate }?.second
                            ?: "${audioSettingsState.bitrate / 1000} Kbps"
                        SettingsDropdownRow(
                            title = "Bitrate",
                            currentValue = audioSettingsState.bitrate,
                            currentValueName = currentBitrateName,
                            options = audioSettingsState.availableBitrates,
                            onSelectOption = onSelectBitrate
                        )

                        // Sample Rate Dropdown
                        val currentSampleRateName = audioSettingsState.availableSampleRates
                            .firstOrNull { it.first == audioSettingsState.sampleRate }?.second
                            ?: "${"%.1f".format(Locale.US, audioSettingsState.sampleRate.toFloat() / 1000)} kHz"
                        SettingsDropdownRow(
                            title = "Sample Rate",
                            currentValue = audioSettingsState.sampleRate,
                            currentValueName = currentSampleRateName,
                            options = audioSettingsState.availableSampleRates,
                            onSelectOption = onSelectSampleRate
                        )

                        // Profile Dropdown (visible if AAC)
                        if (audioSettingsState.isProfileVisible && audioSettingsState.availableProfiles.isNotEmpty()) {
                            val currentProfileName = audioSettingsState.availableProfiles
                                .firstOrNull { it.first == audioSettingsState.profile }?.second
                                ?: "${audioSettingsState.profile}"
                            SettingsDropdownRow(
                                title = "Profile",
                                currentValue = audioSettingsState.profile,
                                currentValueName = currentProfileName,
                                options = audioSettingsState.availableProfiles,
                                onSelectOption = onSelectProfile
                            )
                        }

                        if (isStreaming) {
                            Text(
                                text = "Changes to audio settings will apply after stream ends.",
                                fontSize = 13.sp,
                                color = Color(0xFFFFB74D),
                                lineHeight = 16.sp
                            )
                            Text(
                                text = if (isRtmpSource) {
                                    "Audio source is saved and applies when using the device mic."
                                } else {
                                    "Audio source can be changed during the stream."
                                },
                                fontSize = 13.sp,
                                color = Color(0xFFFFB74D),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)

                    // 2. Audio Source Selection Section (BOTTOM existing UI)
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
                                    text = if (isRtmpSource) {
                                        "RTMP source audio is captured via MediaProjection. This setting applies to the device mic."
                                    } else {
                                        "Try VOICE_COMMUNICATION if USB audio is too loud or clipping."
                                    },
                                    fontSize = 12.sp,
                                    color = Color(0xCCFFFFFF),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)

                    // 3. Actual System State Section (BOTTOM existing UI)
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

private val sampleAudioSettingsState = AudioSettingsState(
    encoder = "audio/mp4a-latm",
    channelConfig = 12,
    bitrate = 128000,
    sampleRate = 48000,
    profile = 2,
    availableEncoders = listOf("audio/mp4a-latm" to "AAC", "audio/opus" to "OPUS"),
    availableChannelConfigs = listOf(12 to "Stereo", 16 to "Mono"),
    availableBitrates = listOf(64000 to "64 Kbps", 128000 to "128 Kbps", 192000 to "192 Kbps"),
    availableSampleRates = listOf(44100 to "44.1 kHz", 48000 to "48.0 kHz"),
    availableProfiles = listOf(2 to "LC", 1 to "Main"),
    isProfileVisible = true
)

@Preview(name = "Audio Drawer - Portrait Active", widthDp = 390, heightDp = 840, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewAudioSourceBottomDrawerPortraitActive() {
    AudioSourceBottomDrawer(
        isOpen = true,
        isRtmpSource = false,
        audioSettingsState = sampleAudioSettingsState,
        isStreaming = false,
        onSelectEncoder = {},
        onSelectChannelConfig = {},
        onSelectBitrate = {},
        onSelectSampleRate = {},
        onSelectProfile = {},
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
        isRtmpSource = false,
        audioSettingsState = sampleAudioSettingsState,
        isStreaming = false,
        onSelectEncoder = {},
        onSelectChannelConfig = {},
        onSelectBitrate = {},
        onSelectSampleRate = {},
        onSelectProfile = {},
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
        isRtmpSource = false,
        audioSettingsState = sampleAudioSettingsState,
        isStreaming = false,
        onSelectEncoder = {},
        onSelectChannelConfig = {},
        onSelectBitrate = {},
        onSelectSampleRate = {},
        onSelectProfile = {},
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
