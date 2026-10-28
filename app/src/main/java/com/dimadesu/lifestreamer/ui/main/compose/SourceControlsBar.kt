package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel

/**
 * Stateful Composable for the right-edge controls (mute + groups), grouped into VIDEO (sources + settings),
 * AUDIO (monitor, bluetooth, system audio + settings) and ENDPOINT (settings + SRTLA stats).
 */
@Composable
fun SourceControlsBar(
    viewModel: PreviewViewModel,
    onSelectCamera: (String) -> Unit,
    onToggleRtmpSource: (Int) -> Unit,
    onLaunchRtmpServer: () -> Unit = {},
    onToggleUvc: () -> Unit,
    onLaunchUvcTest: () -> Unit,
    onToggleScreen: () -> Unit,
    onToggleMonitorAudio: () -> Unit,
    onToggleBluetoothMic: () -> Unit,
    onToggleSystemAudio: () -> Unit,
    onToggleAudioDebug: () -> Unit,
    onToggleSrtlaStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cameras by viewModel.availableCamerasLiveData.observeAsState(initial = emptyList())
    val showCameraControls by viewModel.showCameraControls.observeAsState(initial = true)
    val activeRtmpIndex by viewModel.activeRtmpIndex.observeAsState(initial = null)
    val rtmpSourceCount by viewModel.rtmpSourceCount.observeAsState(initial = 1)
    val isUvcSource by viewModel.isUvcSource.observeAsState(initial = false)
    val isScreenSource by viewModel.isScreenSource.observeAsState(initial = false)
    val isMonitorAudioOn by viewModel.isMonitorAudioOn.observeAsState(initial = false)
    val showBluetoothToggle by viewModel.showBluetoothToggle.observeAsState(initial = false)
    val useBluetoothMic by viewModel.useBluetoothMic.observeAsState(initial = false)
    val useSystemAudioForCamera by viewModel.useSystemAudioForCameraLiveData.observeAsState(initial = false)
    val isSrtlaEndpoint by viewModel.isSrtlaEndpoint.observeAsState(initial = false)
    val isSrtlaStatsVisible by viewModel.isSrtlaStatsVisible.observeAsState(initial = false)
    val currentCameraIdFromStreamer by viewModel.currentCameraIdLiveData.observeAsState(initial = null)
    val isCameraControlsDrawerOpen by viewModel.isCameraControlsDrawerOpen.observeAsState(initial = false)
    val isRtmpSettingsDrawerOpen by viewModel.isRtmpSettingsDrawerOpen.observeAsState(initial = false)
    val isAudioSourceDrawerOpen by viewModel.audioDrawer.isOpen.observeAsState(initial = false)
    val isVideoSettingsDrawerOpen by viewModel.videoDrawer.isOpen.observeAsState(initial = false)
    val isEndpointSettingsDrawerOpen by viewModel.endpointDrawer.isOpen.observeAsState(initial = false)
    val isMuted by viewModel.isMutedLiveData.observeAsState(initial = false)

    var localSelectedCameraId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentCameraIdFromStreamer) {
        if (currentCameraIdFromStreamer != null) {
            localSelectedCameraId = currentCameraIdFromStreamer
        }
    }
    val activeCameraId = localSelectedCameraId ?: currentCameraIdFromStreamer
    var expandedGroup by rememberSaveable { mutableStateOf<ControlGroup?>(null) }

    SourceControlsBar(
        cameras = cameras,
        selectedCameraId = activeCameraId,
        showCameraControls = showCameraControls,
        isCameraControlsDrawerOpen = isCameraControlsDrawerOpen,
        isRtmpSettingsDrawerOpen = isRtmpSettingsDrawerOpen,
        isAudioSourceDrawerOpen = isAudioSourceDrawerOpen,
        isVideoSettingsDrawerOpen = isVideoSettingsDrawerOpen,
        isEndpointSettingsDrawerOpen = isEndpointSettingsDrawerOpen,
        activeRtmpIndex = activeRtmpIndex,
        rtmpSourceCount = rtmpSourceCount,
        isUvcSource = isUvcSource,
        isScreenSource = isScreenSource,
        isMonitorAudioOn = isMonitorAudioOn,
        showBluetoothToggle = showBluetoothToggle,
        useBluetoothMic = useBluetoothMic,
        useSystemAudioForCamera = useSystemAudioForCamera,
        isAudioDebugOverlayVisible = isAudioSourceDrawerOpen,
        isSrtlaEndpoint = isSrtlaEndpoint,
        isSrtlaStatsVisible = isSrtlaStatsVisible,
        onSelectCamera = { cameraId ->
            localSelectedCameraId = cameraId
            onSelectCamera(cameraId)
        },
        onToggleCameraControls = { viewModel.toggleCameraControlsDrawer() },
        onToggleRtmpSettings = { viewModel.toggleRtmpSettingsDrawer() },
        onToggleRtmpSource = onToggleRtmpSource,
        onLaunchRtmpServer = onLaunchRtmpServer,
        onToggleUvc = onToggleUvc,
        onLaunchUvcTest = onLaunchUvcTest,
        onToggleScreen = onToggleScreen,
        onToggleMonitorAudio = onToggleMonitorAudio,
        onToggleBluetoothMic = onToggleBluetoothMic,
        onToggleSystemAudio = onToggleSystemAudio,
        onToggleAudioDebug = onToggleAudioDebug,
        onToggleAudioSourceDrawer = { viewModel.audioDrawer.toggleDrawer() },
        onToggleVideoSettingsDrawer = { viewModel.videoDrawer.toggleDrawer() },
        onToggleEndpointSettingsDrawer = { viewModel.endpointDrawer.toggleDrawer() },
        onToggleSrtlaStats = onToggleSrtlaStats,
        expandedGroup = expandedGroup,
        onExpandedGroupChange = { expandedGroup = it },
        columnHeader = {
            CameraControlButton(
                iconRes = if (isMuted) R.drawable.ic_baseline_mic_off_24 else R.drawable.ic_baseline_mic_24,
                contentDescription = stringResource(R.string.mute_unmute_microphone),
                onClick = { viewModel.setMute(!isMuted) }
            )
        },
        modifier = modifier
    )
}

/**
 * Stateless Composable for the right-edge controls: a column with [columnHeader] (e.g. mute)
 * above the group buttons (VIDEO / AUDIO / ENDPOINT), and a second column to its left with the
 * scrollable buttons of the expanded group.
 */
@Composable
fun SourceControlsBar(
    cameras: List<PreviewViewModel.CameraInfo>,
    selectedCameraId: String?,
    showCameraControls: Boolean,
    isCameraControlsDrawerOpen: Boolean = false,
    isRtmpSettingsDrawerOpen: Boolean = false,
    isAudioSourceDrawerOpen: Boolean = false,
    isVideoSettingsDrawerOpen: Boolean = false,
    isEndpointSettingsDrawerOpen: Boolean = false,
    activeRtmpIndex: Int?,
    rtmpSourceCount: Int,
    isUvcSource: Boolean,
    isScreenSource: Boolean,
    isMonitorAudioOn: Boolean,
    showBluetoothToggle: Boolean,
    useBluetoothMic: Boolean,
    useSystemAudioForCamera: Boolean,
    isAudioDebugOverlayVisible: Boolean,
    isSrtlaEndpoint: Boolean,
    isSrtlaStatsVisible: Boolean,
    onSelectCamera: (String) -> Unit,
    onToggleCameraControls: () -> Unit = {},
    onToggleRtmpSettings: () -> Unit = {},
    onToggleRtmpSource: (Int) -> Unit,
    onLaunchRtmpServer: () -> Unit = {},
    onToggleUvc: () -> Unit,
    onLaunchUvcTest: () -> Unit,
    onToggleScreen: () -> Unit,
    onToggleMonitorAudio: () -> Unit,
    onToggleBluetoothMic: () -> Unit,
    onToggleSystemAudio: () -> Unit,
    onToggleAudioDebug: () -> Unit = {},
    onToggleAudioSourceDrawer: () -> Unit = onToggleAudioDebug,
    onToggleVideoSettingsDrawer: () -> Unit = {},
    onToggleEndpointSettingsDrawer: () -> Unit = {},
    onToggleSrtlaStats: () -> Unit,
    expandedGroup: ControlGroup? = null,
    onExpandedGroupChange: (ControlGroup?) -> Unit = {},
    columnHeader: @Composable ColumnScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isRtmpSource = activeRtmpIndex != null

    Row(
        modifier = modifier.fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Second column (to the left): buttons of the expanded group.
        if (expandedGroup != null && !(expandedGroup == ControlGroup.ENDPOINT && !isSrtlaEndpoint)) {
            ControlGroupPanel(
                modifier = Modifier.align(Alignment.Bottom)
            ) {
                when (expandedGroup) {
                    ControlGroup.VIDEO -> VideoGroupContent(
                        cameras = cameras,
                        selectedCameraId = selectedCameraId,
                        showCameraControls = showCameraControls,
                        isCameraControlsDrawerOpen = isCameraControlsDrawerOpen,
                        isRtmpSettingsDrawerOpen = isRtmpSettingsDrawerOpen,
                        activeRtmpIndex = activeRtmpIndex,
                        rtmpSourceCount = rtmpSourceCount,
                        isUvcSource = isUvcSource,
                        isScreenSource = isScreenSource,
                        onSelectCamera = onSelectCamera,
                        onToggleCameraControls = onToggleCameraControls,
                        onToggleRtmpSettings = onToggleRtmpSettings,
                        onToggleRtmpSource = onToggleRtmpSource,
                        onToggleUvc = onToggleUvc,
                        onLaunchUvcTest = onLaunchUvcTest,
                        onToggleScreen = onToggleScreen
                    )

                    ControlGroup.AUDIO -> AudioGroupContent(
                        isRtmpSource = isRtmpSource,
                        isMonitorAudioOn = isMonitorAudioOn,
                        showBluetoothToggle = showBluetoothToggle,
                        useBluetoothMic = useBluetoothMic,
                        useSystemAudioForCamera = useSystemAudioForCamera,
                        onToggleMonitorAudio = onToggleMonitorAudio,
                        onToggleBluetoothMic = onToggleBluetoothMic,
                        onToggleSystemAudio = onToggleSystemAudio
                    )

                    ControlGroup.ENDPOINT -> EndpointGroupContent(
                        isSrtlaStatsVisible = isSrtlaStatsVisible,
                        onToggleSrtlaStats = onToggleSrtlaStats
                    )
                }
            }
        }

        // First column (right edge): header (e.g. mute) above the group buttons.
        Column(
            modifier = Modifier
                .align(Alignment.Bottom)
                .wrapContentHeight()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
        ) {
            columnHeader()
            ControlGroup.entries.forEach { group ->
                val settingsContentDescription = when (group) {
                    ControlGroup.VIDEO -> stringResource(R.string.video_settings_title)
                    ControlGroup.AUDIO -> stringResource(R.string.audio_settings_title)
                    ControlGroup.ENDPOINT -> stringResource(R.string.endpoint_settings_title)
                }
                val isSettingsOpen = when (group) {
                    ControlGroup.VIDEO -> isVideoSettingsDrawerOpen
                    ControlGroup.AUDIO -> isAudioSourceDrawerOpen || isAudioDebugOverlayVisible
                    ControlGroup.ENDPOINT -> isEndpointSettingsDrawerOpen
                }
                val onToggleSettings = when (group) {
                    ControlGroup.VIDEO -> onToggleVideoSettingsDrawer
                    ControlGroup.AUDIO -> onToggleAudioSourceDrawer
                    ControlGroup.ENDPOINT -> onToggleEndpointSettingsDrawer
                }
                // Audio settings don't apply to an RTMP source; keep the cog slot so columns align.
                val hasSettings = !(group == ControlGroup.AUDIO && isRtmpSource)
                // ENDPOINT has nothing to expand for non-SRTLA endpoints.
                val hasExpandableContent = !(group == ControlGroup.ENDPOINT && !isSrtlaEndpoint)

                SourceButtonRow(modifier = Modifier) {
                    SourceButton(
                        text = group.label,
                        isActive = expandedGroup == group,
                        minWidth = GroupButtonMinWidth,
                        onClick = {
                            when {
                                !hasExpandableContent -> onToggleSettings()
                                else -> onExpandedGroupChange(if (expandedGroup == group) null else group)
                            }
                        }
                    )
                    if (hasSettings) {
                        SettingsCogButton(
                            isOpen = isSettingsOpen,
                            contentDescription = settingsContentDescription,
                            onClick = onToggleSettings
                        )
                    } else {
                        Spacer(Modifier.size(40.dp))
                    }
                }
            }
        }
    }
}

/** Groups of main-screen controls. Only the group buttons are visible until one is expanded. */
enum class ControlGroup(val label: String) {
    VIDEO("VIDEO"),
    AUDIO("AUDIO"),
    ENDPOINT("ENDPOINT")
}

private val GroupPanelWidth = 200.dp
private val GroupButtonMinWidth = 110.dp

/**
 * Scrollable column, bottom-aligned next to the group buttons, that hosts the buttons of the
 * expanded [ControlGroup].
 */
@Composable
private fun ControlGroupPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .width(GroupPanelWidth)
            .wrapContentHeight()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** Row with a wide primary button and an optional square settings button next to it. */
@Composable
private fun SourceButtonRow(
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

@Composable
private fun VideoGroupContent(
    cameras: List<PreviewViewModel.CameraInfo>,
    selectedCameraId: String?,
    showCameraControls: Boolean,
    isCameraControlsDrawerOpen: Boolean,
    isRtmpSettingsDrawerOpen: Boolean,
    activeRtmpIndex: Int?,
    rtmpSourceCount: Int,
    isUvcSource: Boolean,
    isScreenSource: Boolean,
    onSelectCamera: (String) -> Unit,
    onToggleCameraControls: () -> Unit,
    onToggleRtmpSettings: () -> Unit,
    onToggleRtmpSource: (Int) -> Unit,
    onToggleUvc: () -> Unit,
    onLaunchUvcTest: () -> Unit,
    onToggleScreen: () -> Unit
) {
    val isRtmpSource = activeRtmpIndex != null

    // Dynamic camera selection buttons
    if (showCameraControls && cameras.isNotEmpty()) {
        cameras.forEach { camera ->
            val isSelected = (camera.id == selectedCameraId)
            SourceButtonRow {
                SourceButton(
                    text = camera.displayName,
                    isActive = isSelected,
                    onClick = { onSelectCamera(camera.id) },
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    CameraSettingsButton(
                        isOpen = isCameraControlsDrawerOpen,
                        onClick = onToggleCameraControls
                    )
                }
            }
        }
    }

    // RTMP source button(s) with settings cog
    if (!isUvcSource && !isScreenSource) {
        if (activeRtmpIndex != null) {
            SourceButtonRow {
                SourceButton(
                    text = if (activeRtmpIndex == 1) "RTMP SRC" else "RTMP SRC $activeRtmpIndex",
                    isActive = true,
                    onClick = { onToggleRtmpSource(activeRtmpIndex) },
                    modifier = Modifier.weight(1f)
                )
                RtmpSettingsButton(
                    isOpen = isRtmpSettingsDrawerOpen,
                    onClick = onToggleRtmpSettings
                )
            }
        } else {
            val maxIndex = rtmpSourceCount.coerceAtLeast(1)
            for (i in 1..maxIndex) {
                SourceButtonRow {
                    SourceButton(
                        text = if (i == 1) "RTMP SRC" else "RTMP SRC $i",
                        isActive = false,
                        onClick = { onToggleRtmpSource(i) },
                        modifier = Modifier.weight(1f)
                    )
                    if (i == 1) {
                        RtmpSettingsButton(
                            isOpen = isRtmpSettingsDrawerOpen,
                            onClick = onToggleRtmpSettings
                        )
                    }
                }
            }
        }
    }

    // USB source button
    if (!isRtmpSource && !isScreenSource) {
        SourceButtonRow {
            SourceButton(
                text = "USB SRC",
                isActive = isUvcSource,
                onClick = onToggleUvc,
                modifier = Modifier.weight(1f)
            )
            if (!isUvcSource) {
                UsbSettingsButton(onClick = onLaunchUvcTest)
            }
        }
    }

    // Screen capture button
    if (!isRtmpSource && !isUvcSource) {
        SourceButton(
            text = "SCREEN",
            isActive = isScreenSource,
            onClick = onToggleScreen,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AudioGroupContent(
    isRtmpSource: Boolean,
    isMonitorAudioOn: Boolean,
    showBluetoothToggle: Boolean,
    useBluetoothMic: Boolean,
    useSystemAudioForCamera: Boolean,
    onToggleMonitorAudio: () -> Unit,
    onToggleBluetoothMic: () -> Unit,
    onToggleSystemAudio: () -> Unit
) {
    SourceButton(
        text = "MONITOR 🎤",
        isActive = isMonitorAudioOn,
        onClick = onToggleMonitorAudio,
        modifier = Modifier.fillMaxWidth()
    )

    if (showBluetoothToggle) {
        SourceButton(
            text = "🎤",
            leadingIcon = R.drawable.ic_bluetooth,
            isActive = useBluetoothMic,
            onClick = onToggleBluetoothMic,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (!isRtmpSource) {
        SourceButton(
            text = "AUDIO OUT",
            isActive = useSystemAudioForCamera,
            onClick = onToggleSystemAudio,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EndpointGroupContent(
    isSrtlaStatsVisible: Boolean,
    onToggleSrtlaStats: () -> Unit
) {
    SourceButton(
        text = "SRTLA STATS",
        isActive = isSrtlaStatsVisible,
        onClick = onToggleSrtlaStats,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Standard button styled to match the AppCompat/Material buttons previously defined in XML.
 */
@Composable
fun SourceButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = Dp.Unspecified,
    leadingIcon: Int? = null,
    trailingIcon: Int? = null,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) Color(0xFF4CAF50) else Color(0xFF616161),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = modifier.defaultMinSize(minWidth = minWidth, minHeight = 40.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    painter = painterResource(id = leadingIcon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp
            )
            if (trailingIcon != null) {
                Icon(
                    painter = painterResource(id = trailingIcon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Square cog button displayed next to a group button; toggles that group's settings drawer.
 */
@Composable
private fun SettingsCogButton(
    isOpen: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isOpen) Color(0xFF4CAF50) else Color(0xFF616161),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(40.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_baseline_settings_24),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Settings button displayed next to the currently selected camera.
 * Toggles the camera controls bottom drawer/panel.
 */
@Composable
fun CameraSettingsButton(
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isOpen) Color(0xFF4CAF50) else Color(0xFF616161),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(40.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_baseline_settings_24),
            contentDescription = stringResource(R.string.camera_controls),
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Settings button displayed next to the RTMP source button.
 * Toggles the RTMP settings bottom drawer/panel.
 */
@Composable
fun RtmpSettingsButton(
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isOpen) Color(0xFF4CAF50) else Color(0xFF616161),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(40.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_baseline_settings_24),
            contentDescription = stringResource(R.string.rtmp_settings),
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Settings button displayed next to the USB source button.
 * Launches the UVC test/configuration activity.
 */
@Composable
fun UsbSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF616161),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(40.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_baseline_settings_24),
            contentDescription = stringResource(R.string.usb_settings),
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Preview(name = "Video Group Expanded", showBackground = true, backgroundColor = 0xFF000000, widthDp = 640, heightDp = 360)
@Composable
private fun PreviewSourceControlsBarVideoGroupExpanded() {
    SourceControlsBar(
        cameras = listOf(
            PreviewViewModel.CameraInfo("0", "Back (0)", "BACK"),
            PreviewViewModel.CameraInfo("1", "Front (1)", "FRONT")
        ),
        selectedCameraId = "0",
        showCameraControls = true,
        activeRtmpIndex = null,
        rtmpSourceCount = 1,
        isUvcSource = false,
        isScreenSource = false,
        isMonitorAudioOn = false,
        showBluetoothToggle = false,
        useBluetoothMic = false,
        useSystemAudioForCamera = false,
        isAudioDebugOverlayVisible = false,
        isSrtlaEndpoint = true,
        isSrtlaStatsVisible = false,
        onSelectCamera = {},
        onToggleRtmpSource = {},
        onToggleUvc = {},
        onLaunchUvcTest = {},
        onToggleScreen = {},
        onToggleMonitorAudio = {},
        onToggleBluetoothMic = {},
        onToggleSystemAudio = {},
        onToggleSrtlaStats = {},
        expandedGroup = ControlGroup.VIDEO
    )
}

@Preview(name = "Default Camera Mode", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewSourceControlsBarCameraMode() {
    SourceControlsBar(
        cameras = listOf(
            PreviewViewModel.CameraInfo("0", "Back (0)", "BACK"),
            PreviewViewModel.CameraInfo("1", "Front (1)", "FRONT")
        ),
        selectedCameraId = "0",
        showCameraControls = true,
        isCameraControlsDrawerOpen = false,
        isRtmpSettingsDrawerOpen = false,
        activeRtmpIndex = null,
        rtmpSourceCount = 2,
        isUvcSource = false,
        isScreenSource = false,
        isMonitorAudioOn = false,
        showBluetoothToggle = true,
        useBluetoothMic = false,
        useSystemAudioForCamera = false,
        isAudioDebugOverlayVisible = false,
        isSrtlaEndpoint = true,
        isSrtlaStatsVisible = false,
        onSelectCamera = {},
        onToggleCameraControls = {},
        onToggleRtmpSettings = {},
        onToggleRtmpSource = {},
        onLaunchRtmpServer = {},
        onToggleUvc = {},
        onLaunchUvcTest = {},
        onToggleScreen = {},
        onToggleMonitorAudio = {},
        onToggleBluetoothMic = {},
        onToggleSystemAudio = {},
        onToggleAudioDebug = {},
        onToggleSrtlaStats = {}
    )
}

@Preview(name = "Camera Mode Drawer Open", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewSourceControlsBarDrawerOpen() {
    SourceControlsBar(
        cameras = listOf(
            PreviewViewModel.CameraInfo("0", "Back (0)", "BACK"),
            PreviewViewModel.CameraInfo("1", "Front (1)", "FRONT")
        ),
        selectedCameraId = "0",
        showCameraControls = true,
        isCameraControlsDrawerOpen = true,
        isRtmpSettingsDrawerOpen = false,
        activeRtmpIndex = null,
        rtmpSourceCount = 2,
        isUvcSource = false,
        isScreenSource = false,
        isMonitorAudioOn = false,
        showBluetoothToggle = true,
        useBluetoothMic = false,
        useSystemAudioForCamera = false,
        isAudioDebugOverlayVisible = false,
        isSrtlaEndpoint = true,
        isSrtlaStatsVisible = false,
        onSelectCamera = {},
        onToggleCameraControls = {},
        onToggleRtmpSettings = {},
        onToggleRtmpSource = {},
        onLaunchRtmpServer = {},
        onToggleUvc = {},
        onLaunchUvcTest = {},
        onToggleScreen = {},
        onToggleMonitorAudio = {},
        onToggleBluetoothMic = {},
        onToggleSystemAudio = {},
        onToggleAudioDebug = {},
        onToggleSrtlaStats = {}
    )
}

@Preview(name = "RTMP Mode Active", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewSourceControlsBarRtmpMode() {
    SourceControlsBar(
        cameras = emptyList(),
        selectedCameraId = null,
        showCameraControls = false,
        isCameraControlsDrawerOpen = false,
        isRtmpSettingsDrawerOpen = false,
        activeRtmpIndex = 1,
        rtmpSourceCount = 2,
        isUvcSource = false,
        isScreenSource = false,
        isMonitorAudioOn = true,
        showBluetoothToggle = false,
        useBluetoothMic = false,
        useSystemAudioForCamera = false,
        isAudioDebugOverlayVisible = false,
        isSrtlaEndpoint = false,
        isSrtlaStatsVisible = false,
        onSelectCamera = {},
        onToggleCameraControls = {},
        onToggleRtmpSettings = {},
        onToggleRtmpSource = {},
        onLaunchRtmpServer = {},
        onToggleUvc = {},
        onLaunchUvcTest = {},
        onToggleScreen = {},
        onToggleMonitorAudio = {},
        onToggleBluetoothMic = {},
        onToggleSystemAudio = {},
        onToggleAudioDebug = {},
        onToggleSrtlaStats = {}
    )
}

@Preview(name = "RTMP Mode Drawer Open", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewSourceControlsBarRtmpDrawerOpen() {
    SourceControlsBar(
        cameras = emptyList(),
        selectedCameraId = null,
        showCameraControls = false,
        isCameraControlsDrawerOpen = false,
        isRtmpSettingsDrawerOpen = true,
        activeRtmpIndex = 1,
        rtmpSourceCount = 2,
        isUvcSource = false,
        isScreenSource = false,
        isMonitorAudioOn = true,
        showBluetoothToggle = false,
        useBluetoothMic = false,
        useSystemAudioForCamera = false,
        isAudioDebugOverlayVisible = false,
        isSrtlaEndpoint = false,
        isSrtlaStatsVisible = false,
        onSelectCamera = {},
        onToggleCameraControls = {},
        onToggleRtmpSettings = {},
        onToggleRtmpSource = {},
        onLaunchRtmpServer = {},
        onToggleUvc = {},
        onLaunchUvcTest = {},
        onToggleScreen = {},
        onToggleMonitorAudio = {},
        onToggleBluetoothMic = {},
        onToggleSystemAudio = {},
        onToggleAudioDebug = {},
        onToggleSrtlaStats = {}
    )
}

@Preview(name = "USB Mode Active", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewSourceControlsBarUsbMode() {
    SourceControlsBar(
        cameras = emptyList(),
        selectedCameraId = null,
        showCameraControls = false,
        isCameraControlsDrawerOpen = false,
        isRtmpSettingsDrawerOpen = false,
        isAudioSourceDrawerOpen = false,
        activeRtmpIndex = null,
        rtmpSourceCount = 1,
        isUvcSource = true,
        isScreenSource = false,
        isMonitorAudioOn = false,
        showBluetoothToggle = false,
        useBluetoothMic = false,
        useSystemAudioForCamera = false,
        isAudioDebugOverlayVisible = false,
        isSrtlaEndpoint = false,
        isSrtlaStatsVisible = false,
        onSelectCamera = {},
        onToggleCameraControls = {},
        onToggleRtmpSettings = {},
        onToggleRtmpSource = {},
        onLaunchRtmpServer = {},
        onToggleUvc = {},
        onLaunchUvcTest = {},
        onToggleScreen = {},
        onToggleMonitorAudio = {},
        onToggleBluetoothMic = {},
        onToggleSystemAudio = {},
        onToggleAudioDebug = {},
        onToggleAudioSourceDrawer = {},
        onToggleSrtlaStats = {}
    )
}

@Preview(name = "Audio Source Drawer Open", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewSourceControlsBarAudioDrawerOpen() {
    SourceControlsBar(
        cameras = listOf(
            PreviewViewModel.CameraInfo("0", "Back (0)", "BACK"),
            PreviewViewModel.CameraInfo("1", "Front (1)", "FRONT")
        ),
        selectedCameraId = "0",
        showCameraControls = true,
        isCameraControlsDrawerOpen = false,
        isRtmpSettingsDrawerOpen = false,
        isAudioSourceDrawerOpen = true,
        activeRtmpIndex = null,
        rtmpSourceCount = 2,
        isUvcSource = false,
        isScreenSource = false,
        isMonitorAudioOn = false,
        showBluetoothToggle = true,
        useBluetoothMic = false,
        useSystemAudioForCamera = false,
        isAudioDebugOverlayVisible = false,
        isSrtlaEndpoint = true,
        isSrtlaStatsVisible = false,
        onSelectCamera = {},
        onToggleCameraControls = {},
        onToggleRtmpSettings = {},
        onToggleRtmpSource = {},
        onLaunchRtmpServer = {},
        onToggleUvc = {},
        onLaunchUvcTest = {},
        onToggleScreen = {},
        onToggleMonitorAudio = {},
        onToggleBluetoothMic = {},
        onToggleSystemAudio = {},
        onToggleAudioDebug = {},
        onToggleAudioSourceDrawer = {},
        onToggleSrtlaStats = {}
    )
}

