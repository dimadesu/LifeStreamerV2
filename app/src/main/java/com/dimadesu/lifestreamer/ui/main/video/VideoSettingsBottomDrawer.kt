package com.dimadesu.lifestreamer.ui.main.video

import android.util.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.models.StreamOrientation
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel
import com.dimadesu.lifestreamer.ui.main.compose.SettingsDrawerPanel
import com.dimadesu.lifestreamer.ui.main.compose.SettingsDropdownRow
import com.dimadesu.lifestreamer.ui.main.compose.SettingsSwitchRow
import com.dimadesu.lifestreamer.ui.main.compose.SettingsWarningText

/**
 * Stateful Composable for the Video Settings drawer/panel:
 * - Slides in as a bottom drawer (portrait) or side inspector (landscape).
 * - Displays orientation, encoder, resolution, FPS, bitrate, profile and level.
 */
@Composable
fun VideoSettingsBottomDrawer(
    viewModel: PreviewViewModel,
    modifier: Modifier = Modifier
) {
    val video = viewModel.videoDrawer
    val isOpen by video.isOpen.observeAsState(initial = false)
    val isStreaming by viewModel.isStreamingLiveData.observeAsState(initial = false)
    val state by video.settingsState.observeAsState(initial = VideoSettingsState())

    VideoSettingsBottomDrawer(
        isOpen = isOpen,
        state = state,
        isStreaming = isStreaming,
        onSelectOrientation = { video.setOrientation(it) },
        onSelectEncoder = { video.setEncoder(it) },
        onSelectResolution = { video.setResolution(it) },
        onToggleHardwareResolutionsOnly = { video.setShowHardwareResolutionsOnly(it) },
        onSelectCameraFps = { video.setCameraFps(it) },
        onToggleMatchFps = { video.setMatchFps(it) },
        onSelectFps = { video.setFps(it) },
        onSelectBitrate = { video.setBitrateKbps(it) },
        onSelectProfile = { video.setProfile(it) },
        onSelectLevel = { video.setLevel(it) },
        onClose = { video.setDrawerOpen(false) },
        modifier = modifier
    )
}

/**
 * Stateless drawer for the video settings.
 */
@Composable
fun VideoSettingsBottomDrawer(
    isOpen: Boolean,
    state: VideoSettingsState,
    isStreaming: Boolean = false,
    onSelectOrientation: (StreamOrientation) -> Unit,
    onSelectEncoder: (String) -> Unit,
    onSelectResolution: (Size) -> Unit,
    onToggleHardwareResolutionsOnly: (Boolean) -> Unit,
    onSelectCameraFps: (Int) -> Unit,
    onToggleMatchFps: (Boolean) -> Unit,
    onSelectFps: (Int) -> Unit,
    onSelectBitrate: (Int) -> Unit,
    onSelectProfile: (Int) -> Unit,
    onSelectLevel: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsDrawerPanel(
        isOpen = isOpen,
        title = stringResource(R.string.video_settings_title),
        closeContentDescription = stringResource(R.string.close_video_settings),
        onClose = onClose,
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingsDropdownRow(
                title = stringResource(R.string.stream_orientation_title),
                currentValue = state.orientation,
                currentValueName = state.availableOrientations
                    .firstOrNull { it.first == state.orientation }?.second ?: state.orientation.id,
                options = state.availableOrientations,
                onSelectOption = onSelectOrientation
            )

            SettingsDropdownRow(
                title = stringResource(R.string.video_encoder),
                currentValue = state.encoder,
                currentValueName = state.availableEncoders
                    .firstOrNull { it.first == state.encoder }?.second ?: state.encoder,
                options = state.availableEncoders,
                onSelectOption = onSelectEncoder
            )

            SettingsDropdownRow(
                title = stringResource(R.string.video_resolution),
                currentValue = state.resolution,
                currentValueName = state.resolution.toString(),
                options = state.availableResolutions,
                onSelectOption = onSelectResolution
            )

            SettingsSwitchRow(
                title = stringResource(R.string.video_resolution_hardware_filter_title),
                summary = stringResource(R.string.video_resolution_hardware_filter_summary),
                checked = state.showHardwareResolutionsOnly,
                onCheckedChange = onToggleHardwareResolutionsOnly
            )

            HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)

            SettingsDropdownRow(
                title = stringResource(R.string.camera_fps),
                currentValue = state.cameraFps,
                currentValueName = "${state.cameraFps}",
                options = state.availableCameraFps,
                onSelectOption = onSelectCameraFps
            )

            state.cameraFpsWarning?.let {
                SettingsWarningText(it)
            }

            SettingsSwitchRow(
                title = stringResource(R.string.match_fps_title),
                checked = state.matchFps,
                onCheckedChange = onToggleMatchFps
            )

            if (!state.matchFps) {
                SettingsDropdownRow(
                    title = stringResource(R.string.video_fps),
                    currentValue = state.fps,
                    currentValueName = "${state.fps}",
                    options = state.availableFps,
                    onSelectOption = onSelectFps
                )
                Text(
                    text = stringResource(R.string.video_fps_note_summary),
                    fontSize = 12.sp,
                    color = Color(0x99FFFFFF),
                    lineHeight = 15.sp
                )
            }

            HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)

            SettingsDropdownRow(
                title = stringResource(R.string.video_bitrate),
                currentValue = state.bitrateKbps,
                currentValueName = state.availableBitrates
                    .firstOrNull { it.first == state.bitrateKbps }?.second
                    ?: "${state.bitrateKbps} Kbps",
                options = state.availableBitrates,
                onSelectOption = onSelectBitrate
            )

            if (state.availableProfiles.isNotEmpty()) {
                SettingsDropdownRow(
                    title = stringResource(R.string.video_profile),
                    currentValue = state.profile,
                    currentValueName = state.availableProfiles
                        .firstOrNull { it.first == state.profile }?.second ?: "${state.profile}",
                    options = state.availableProfiles,
                    onSelectOption = onSelectProfile
                )
            }

            if (state.availableLevels.isNotEmpty()) {
                SettingsDropdownRow(
                    title = stringResource(R.string.video_level),
                    currentValue = state.level,
                    currentValueName = state.availableLevels
                        .firstOrNull { it.first == state.level }?.second ?: "${state.level}",
                    options = state.availableLevels,
                    onSelectOption = onSelectLevel
                )
            }

            if (isStreaming) {
                SettingsWarningText(stringResource(R.string.video_settings_apply_after_stream))
            }
        }
    }
}

@Preview(name = "Video Drawer - Portrait", widthDp = 390, heightDp = 840, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewVideoSettingsBottomDrawer() {
    VideoSettingsBottomDrawer(
        isOpen = true,
        state = VideoSettingsState(
            resolution = Size(1920, 1080),
            bitrateKbps = 4000,
            matchFps = false,
            availableOrientations = listOf(StreamOrientation.AUTO to "Auto (follow device rotation)"),
            availableEncoders = listOf("video/avc" to "AVC/H.264"),
            availableResolutions = listOf(Size(1920, 1080) to "1920x1080"),
            availableCameraFps = listOf(30 to "30"),
            availableFps = listOf(30 to "30"),
            availableBitrates = listOf(4000 to "4000 Kbps"),
            availableProfiles = listOf(8 to "High"),
            availableLevels = listOf(512 to "Level4"),
            profile = 8,
            level = 512
        ),
        isStreaming = true,
        onSelectOrientation = {},
        onSelectEncoder = {},
        onSelectResolution = {},
        onToggleHardwareResolutionsOnly = {},
        onSelectCameraFps = {},
        onToggleMatchFps = {},
        onSelectFps = {},
        onSelectBitrate = {},
        onSelectProfile = {},
        onSelectLevel = {},
        onClose = {}
    )
}
