package com.dimadesu.lifestreamer.ui.settings.compose

import android.media.MediaFormat
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
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.ui.theme.DarkBackground

@Composable
fun VideoSettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs by viewModel.preferencesFlow.collectAsState()

    var showOrientationDialog by remember { mutableStateOf(false) }
    var showEncoderDialog by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var showCameraFpsDialog by remember { mutableStateOf(false) }
    var showVideoFpsDialog by remember { mutableStateOf(false) }

    // Orientation
    val orientationKey = context.getString(R.string.stream_orientation_key)
    val orientation = prefs?.get(stringPreferencesKey(orientationKey)) ?: stringResource(R.string.stream_orientation_value_auto)

    // Encoder
    val encoderKey = context.getString(R.string.video_encoder_key)
    val encoder = prefs?.get(stringPreferencesKey(encoderKey)) ?: stringResource(R.string.default_video_encoder)

    // Resolution Filter
    val resFilterKey = context.getString(R.string.video_resolution_hardware_filter_key)
    val resFilter = prefs?.get(booleanPreferencesKey(resFilterKey)) ?: true

    // Resolution
    val resolutionKey = context.getString(R.string.video_resolution_key)
    val resolution = prefs?.get(stringPreferencesKey(resolutionKey)) ?: stringResource(R.string.default_video_resolution)

    // FPS
    val cameraFpsKey = context.getString(R.string.camera_fps_key)
    val cameraFps = prefs?.get(stringPreferencesKey(cameraFpsKey)) ?: stringResource(R.string.default_fps)

    val matchFpsKey = context.getString(R.string.match_fps_key)
    val matchFps = prefs?.get(booleanPreferencesKey(matchFpsKey)) ?: true

    val videoFpsKey = context.getString(R.string.video_fps_key)
    val videoFps = prefs?.get(stringPreferencesKey(videoFpsKey)) ?: stringResource(R.string.default_fps)

    // Bitrate
    val bitrateKey = context.getString(R.string.video_bitrate_key)
    val bitrate = prefs?.get(intPreferencesKey(bitrateKey)) ?: 1000

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = stringResource(R.string.video),
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
            SettingsCard(title = "Orientation & Codec") {
                SettingValuePickerRow(
                    title = stringResource(R.string.stream_orientation_title),
                    summary = stringResource(R.string.stream_orientation_summary),
                    value = when (orientation) {
                        "auto" -> "Automatic"
                        "0" -> "0° (Landscape)"
                        "90" -> "90° (Portrait)"
                        "180" -> "180° (Reverse Landscape)"
                        "270" -> "270° (Reverse Portrait)"
                        else -> orientation
                    },
                    onClick = { showOrientationDialog = true }
                )

                SettingValuePickerRow(
                    title = stringResource(R.string.video_encoder),
                    value = when (encoder) {
                        MediaFormat.MIMETYPE_VIDEO_AVC -> stringResource(R.string.video_encoder_h264)
                        MediaFormat.MIMETYPE_VIDEO_HEVC -> stringResource(R.string.video_encoder_h265)
                        MediaFormat.MIMETYPE_VIDEO_VP9 -> stringResource(R.string.video_encoder_vp9)
                        MediaFormat.MIMETYPE_VIDEO_VP8 -> stringResource(R.string.video_encoder_vp8)
                        MediaFormat.MIMETYPE_VIDEO_AV1 -> stringResource(R.string.video_encoder_av1)
                        else -> encoder
                    },
                    onClick = { showEncoderDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            SettingsCard(title = "Resolution & Frame Rate") {
                SettingSwitchRow(
                    title = stringResource(R.string.video_resolution_hardware_filter_title),
                    summary = stringResource(R.string.video_resolution_hardware_filter_summary),
                    checked = resFilter,
                    onCheckedChange = { viewModel.saveBoolean(resFilterKey, it) }
                )

                SettingValuePickerRow(
                    title = stringResource(R.string.video_resolution),
                    value = resolution,
                    onClick = { showResolutionDialog = true }
                )

                SettingValuePickerRow(
                    title = stringResource(R.string.camera_fps),
                    value = "$cameraFps fps",
                    onClick = { showCameraFpsDialog = true }
                )

                SettingSwitchRow(
                    title = stringResource(R.string.match_fps_title),
                    checked = matchFps,
                    onCheckedChange = { viewModel.saveBoolean(matchFpsKey, it) }
                )

                if (!matchFps) {
                    SettingValuePickerRow(
                        title = stringResource(R.string.video_fps),
                        value = "$videoFps fps",
                        onClick = { showVideoFpsDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            SettingsCard(title = "Video Bitrate") {
                SettingSliderRow(
                    title = stringResource(R.string.video_bitrate),
                    value = bitrate.toFloat(),
                    valueText = "$bitrate kbps",
                    valueRange = 500f..10000f,
                    onValueChange = { viewModel.saveInt(bitrateKey, it.toInt()) }
                )
            }
        }
    }

    // Dialogs
    if (showOrientationDialog) {
        val orientationOptions = listOf(
            "auto" to "Automatic",
            "0" to "0° (Landscape)",
            "90" to "90° (Portrait)",
            "180" to "180° (Reverse Landscape)",
            "270" to "270° (Reverse Portrait)"
        )
        SettingSelectionDialog(
            title = stringResource(R.string.stream_orientation_title),
            options = orientationOptions,
            selectedKey = orientation,
            onSelect = { viewModel.saveString(orientationKey, it) },
            onDismiss = { showOrientationDialog = false }
        )
    }

    if (showEncoderDialog) {
        val encoderOptions = listOf(
            MediaFormat.MIMETYPE_VIDEO_AVC to stringResource(R.string.video_encoder_h264),
            MediaFormat.MIMETYPE_VIDEO_HEVC to stringResource(R.string.video_encoder_h265),
            MediaFormat.MIMETYPE_VIDEO_VP9 to stringResource(R.string.video_encoder_vp9),
            MediaFormat.MIMETYPE_VIDEO_AV1 to stringResource(R.string.video_encoder_av1)
        )
        SettingSelectionDialog(
            title = stringResource(R.string.video_encoder),
            options = encoderOptions,
            selectedKey = encoder,
            onSelect = { viewModel.saveString(encoderKey, it) },
            onDismiss = { showEncoderDialog = false }
        )
    }

    if (showResolutionDialog) {
        val resolutionOptions = listOf(
            "3840x2160" to "3840x2160 (4K UHD)",
            "2560x1440" to "2560x1440 (2K QHD)",
            "1920x1080" to "1920x1080 (1080p FHD)",
            "1280x720" to "1280x720 (720p HD)",
            "854x480" to "854x480 (480p SD)",
            "640x360" to "640x360 (360p)",
            "1080x1920" to "1080x1920 (Portrait 1080p)",
            "720x1280" to "720x1280 (Portrait 720p)"
        )
        SettingSelectionDialog(
            title = stringResource(R.string.video_resolution),
            options = resolutionOptions,
            selectedKey = resolution,
            onSelect = { viewModel.saveString(resolutionKey, it) },
            onDismiss = { showResolutionDialog = false }
        )
    }

    if (showCameraFpsDialog) {
        val fpsOptions = listOf("60" to "60 fps", "30" to "30 fps", "24" to "24 fps", "15" to "15 fps")
        SettingSelectionDialog(
            title = stringResource(R.string.camera_fps),
            options = fpsOptions,
            selectedKey = cameraFps,
            onSelect = { viewModel.saveString(cameraFpsKey, it) },
            onDismiss = { showCameraFpsDialog = false }
        )
    }

    if (showVideoFpsDialog) {
        val fpsOptions = listOf("60" to "60 fps", "30" to "30 fps", "24" to "24 fps", "15" to "15 fps")
        SettingSelectionDialog(
            title = stringResource(R.string.video_fps),
            options = fpsOptions,
            selectedKey = videoFps,
            onSelect = { viewModel.saveString(videoFpsKey, it) },
            onDismiss = { showVideoFpsDialog = false }
        )
    }
}
