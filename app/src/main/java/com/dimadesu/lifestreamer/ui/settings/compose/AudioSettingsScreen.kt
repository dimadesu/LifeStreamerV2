package com.dimadesu.lifestreamer.ui.settings.compose

import android.media.MediaFormat
import android.media.MediaRecorder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.ui.theme.DarkBackground

@Composable
fun AudioSettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs by viewModel.preferencesFlow.collectAsState()

    var showSourceTypeDialog by remember { mutableStateOf(false) }
    var showEncoderDialog by remember { mutableStateOf(false) }
    var showBitrateDialog by remember { mutableStateOf(false) }
    var showSampleRateDialog by remember { mutableStateOf(false) }
    var showChannelDialog by remember { mutableStateOf(false) }

    // Audio enabled
    val enableKey = context.getString(R.string.audio_enable_key)
    val enable = prefs?.get(booleanPreferencesKey(enableKey)) ?: true

    // Audio Source Type
    val sourceTypeKey = context.getString(R.string.audio_source_type_key)
    val sourceType = prefs?.get(stringPreferencesKey(sourceTypeKey)) ?: MediaRecorder.AudioSource.CAMCORDER.toString()

    // Encoder
    val encoderKey = context.getString(R.string.audio_encoder_key)
    val encoder = prefs?.get(stringPreferencesKey(encoderKey)) ?: stringResource(R.string.default_audio_encoder)

    // Bitrate
    val bitrateKey = context.getString(R.string.audio_bitrate_key)
    val bitrate = prefs?.get(stringPreferencesKey(bitrateKey)) ?: "128000"

    // Sample rate
    val sampleRateKey = context.getString(R.string.audio_sample_rate_key)
    val sampleRate = prefs?.get(stringPreferencesKey(sampleRateKey)) ?: "44100"

    // Channel config
    val channelKey = context.getString(R.string.audio_channel_config_key)
    val channel = prefs?.get(stringPreferencesKey(channelKey)) ?: "12" // Stereo

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = stringResource(R.string.audio),
                onBackClick = onBackClick
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
        ) {
            SettingsCard(title = "Master Control") {
                SettingSwitchRow(
                    title = stringResource(R.string.audio_enable),
                    checked = enable,
                    onCheckedChange = { viewModel.saveBoolean(enableKey, it) }
                )
            }

            if (enable) {
                Spacer(modifier = Modifier.height(10.dp))

                SettingsCard(title = "Audio Source") {
                    SettingValuePickerRow(
                        title = "Input Source",
                        summary = "Try Voice Communication if USB/UVC audio is too loud or clipping",
                        value = when (sourceType.toIntOrNull()) {
                            MediaRecorder.AudioSource.CAMCORDER -> "CAMCORDER"
                            MediaRecorder.AudioSource.MIC -> "MIC (Default Microphone)"
                            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION (AEC/AGC)"
                            MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED (Raw Audio)"
                            else -> "CAMCORDER"
                        },
                        onClick = { showSourceTypeDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                SettingsCard(title = "Encoding Format") {
                    SettingValuePickerRow(
                        title = stringResource(R.string.audio_encoder),
                        value = if (encoder.contains("opus", ignoreCase = true)) "Opus" else "AAC",
                        onClick = { showEncoderDialog = true }
                    )

                    SettingValuePickerRow(
                        title = stringResource(R.string.audio_bitrate),
                        value = "${(bitrate.toIntOrNull() ?: 128000) / 1000} kbps",
                        onClick = { showBitrateDialog = true }
                    )

                    SettingValuePickerRow(
                        title = stringResource(R.string.audio_sample_rate),
                        value = "${(sampleRate.toFloatOrNull() ?: 44100f) / 1000f} kHz",
                        onClick = { showSampleRateDialog = true }
                    )

                    SettingValuePickerRow(
                        title = stringResource(R.string.audio_number_of_channels),
                        value = if (channel == "16") "Mono (1 channel)" else "Stereo (2 channels)",
                        onClick = { showChannelDialog = true }
                    )
                }
            }
        }
    }

    if (showSourceTypeDialog) {
        val sourceOptions = listOf(
            MediaRecorder.AudioSource.CAMCORDER.toString() to "CAMCORDER (Standard video)",
            MediaRecorder.AudioSource.VOICE_COMMUNICATION.toString() to "VOICE_COMMUNICATION (Fixes USB clipping)",
            MediaRecorder.AudioSource.MIC.toString() to "MIC (Standard)",
            MediaRecorder.AudioSource.UNPROCESSED.toString() to "UNPROCESSED (Raw direct)"
        )
        SettingSelectionDialog(
            title = "Audio Input Source",
            options = sourceOptions,
            selectedKey = sourceType,
            onSelect = { viewModel.saveString(sourceTypeKey, it) },
            onDismiss = { showSourceTypeDialog = false }
        )
    }

    if (showEncoderDialog) {
        val encoderOptions = listOf(
            MediaFormat.MIMETYPE_AUDIO_AAC to "AAC (Default)",
            MediaFormat.MIMETYPE_AUDIO_OPUS to "Opus"
        )
        SettingSelectionDialog(
            title = stringResource(R.string.audio_encoder),
            options = encoderOptions,
            selectedKey = encoder,
            onSelect = { viewModel.saveString(encoderKey, it) },
            onDismiss = { showEncoderDialog = false }
        )
    }

    if (showBitrateDialog) {
        val bitrateOptions = listOf(
            "64000" to "64 kbps",
            "96000" to "96 kbps",
            "128000" to "128 kbps (Recommended)",
            "192000" to "192 kbps (High Quality)",
            "256000" to "256 kbps",
            "320000" to "320 kbps (Max)"
        )
        SettingSelectionDialog(
            title = stringResource(R.string.audio_bitrate),
            options = bitrateOptions,
            selectedKey = bitrate,
            onSelect = { viewModel.saveString(bitrateKey, it) },
            onDismiss = { showBitrateDialog = false }
        )
    }

    if (showSampleRateDialog) {
        val sampleRateOptions = listOf(
            "44100" to "44.1 kHz (Standard)",
            "48000" to "48.0 kHz (Broadcast standard)"
        )
        SettingSelectionDialog(
            title = stringResource(R.string.audio_sample_rate),
            options = sampleRateOptions,
            selectedKey = sampleRate,
            onSelect = { viewModel.saveString(sampleRateKey, it) },
            onDismiss = { showSampleRateDialog = false }
        )
    }

    if (showChannelDialog) {
        val channelOptions = listOf(
            "12" to "Stereo (2 channels)",
            "16" to "Mono (1 channel)"
        )
        SettingSelectionDialog(
            title = stringResource(R.string.audio_number_of_channels),
            options = channelOptions,
            selectedKey = channel,
            onSelect = { viewModel.saveString(channelKey, it) },
            onDismiss = { showChannelDialog = false }
        )
    }
}
