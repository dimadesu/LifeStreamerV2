package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel

/**
 * Stateful Composable for the main-screen controls, grouped into VIDEO (sources + settings),
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
        modifier = modifier
    )
}

/**
 * Stateless Composable for the main-screen controls: a bottom-left column of group buttons
 * (VIDEO / AUDIO / ENDPOINT) and a centered panel with the buttons of the expanded group.
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
    modifier: Modifier = Modifier
) {
    val isRtmpSource = activeRtmpIndex != null

    // Group buttons that open a drawer or toggle stats should also dismiss the panel.
    fun collapseAfter(action: () -> Unit): () -> Unit = {
        onExpandedGroupChange(null)
        action()
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (expandedGroup != null) {
            // Tap outside the panel to dismiss it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { onExpandedGroupChange(null) } }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .wrapContentHeight()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
        ) {
            ControlGroup.entries.forEach { group ->
                SourceButton(
                    text = group.label,
                    isActive = expandedGroup == group,
                    onClick = {
                        onExpandedGroupChange(if (expandedGroup == group) null else group)
                    }
                )
            }
        }

        if (expandedGroup != null) {
            ControlGroupPanel(
                title = expandedGroup.label,
                onClose = { onExpandedGroupChange(null) },
                modifier = Modifier.align(Alignment.Center)
            ) {
                when (expandedGroup) {
                    ControlGroup.VIDEO -> VideoGroupContent(
                        cameras = cameras,
                        selectedCameraId = selectedCameraId,
                        showCameraControls = showCameraControls,
                        isCameraControlsDrawerOpen = isCameraControlsDrawerOpen,
                        isRtmpSettingsDrawerOpen = isRtmpSettingsDrawerOpen,
                        isVideoSettingsDrawerOpen = isVideoSettingsDrawerOpen,
                        activeRtmpIndex = activeRtmpIndex,
                        rtmpSourceCount = rtmpSourceCount,
                        isUvcSource = isUvcSource,
                        isScreenSource = isScreenSource,
                        onSelectCamera = onSelectCamera,
                        onToggleCameraControls = collapseAfter(onToggleCameraControls),
                        onToggleRtmpSettings = collapseAfter(onToggleRtmpSettings),
                        onToggleRtmpSource = onToggleRtmpSource,
                        onToggleUvc = onToggleUvc,
                        onLaunchUvcTest = collapseAfter(onLaunchUvcTest),
                        onToggleScreen = onToggleScreen,
                        onToggleVideoSettingsDrawer = collapseAfter(onToggleVideoSettingsDrawer)
                    )

                    ControlGroup.AUDIO -> AudioGroupContent(
                        isRtmpSource = isRtmpSource,
                        isMonitorAudioOn = isMonitorAudioOn,
                        showBluetoothToggle = showBluetoothToggle,
                        useBluetoothMic = useBluetoothMic,
                        useSystemAudioForCamera = useSystemAudioForCamera,
                        isAudioSettingsActive = isAudioSourceDrawerOpen || isAudioDebugOverlayVisible,
                        onToggleMonitorAudio = onToggleMonitorAudio,
                        onToggleBluetoothMic = onToggleBluetoothMic,
                        onToggleSystemAudio = onToggleSystemAudio,
                        onToggleAudioSourceDrawer = collapseAfter(onToggleAudioSourceDrawer)
                    )

                    ControlGroup.ENDPOINT -> EndpointGroupContent(
                        isEndpointSettingsDrawerOpen = isEndpointSettingsDrawerOpen,
                        isSrtlaEndpoint = isSrtlaEndpoint,
                        isSrtlaStatsVisible = isSrtlaStatsVisible,
                        onToggleEndpointSettingsDrawer = collapseAfter(onToggleEndpointSettingsDrawer),
                        onToggleSrtlaStats = collapseAfter(onToggleSrtlaStats)
                    )
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

private val GroupPanelWidth = 260.dp

/**
 * Centered floating panel that hosts the buttons of the expanded [ControlGroup].
 */
@Composable
private fun ControlGroupPanel(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xE6212121),
        contentColor = Color.White,
        shadowElevation = 8.dp,
        // Swallow taps on the panel background so they don't reach the dismiss layer.
        modifier = modifier
            .width(GroupPanelWidth)
            .heightIn(max = 320.dp)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onClose,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 32.dp)
                ) {
                    Text(text = "✕", color = Color.White, fontSize = 16.sp)
                }
            }
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

/** Row with a wide primary button and an optional square settings button next to it. */
@Composable
private fun SourceButtonRow(
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
    isVideoSettingsDrawerOpen: Boolean,
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
    onToggleScreen: () -> Unit,
    onToggleVideoSettingsDrawer: () -> Unit
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

    SourceButton(
        text = "VIDEO SETTINGS",
        trailingIcon = R.drawable.ic_baseline_settings_24,
        isActive = isVideoSettingsDrawerOpen,
        onClick = onToggleVideoSettingsDrawer,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AudioGroupContent(
    isRtmpSource: Boolean,
    isMonitorAudioOn: Boolean,
    showBluetoothToggle: Boolean,
    useBluetoothMic: Boolean,
    useSystemAudioForCamera: Boolean,
    isAudioSettingsActive: Boolean,
    onToggleMonitorAudio: () -> Unit,
    onToggleBluetoothMic: () -> Unit,
    onToggleSystemAudio: () -> Unit,
    onToggleAudioSourceDrawer: () -> Unit
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

        SourceButton(
            text = "AUDIO SETTINGS",
            trailingIcon = R.drawable.ic_baseline_settings_24,
            isActive = isAudioSettingsActive,
            onClick = onToggleAudioSourceDrawer,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EndpointGroupContent(
    isEndpointSettingsDrawerOpen: Boolean,
    isSrtlaEndpoint: Boolean,
    isSrtlaStatsVisible: Boolean,
    onToggleEndpointSettingsDrawer: () -> Unit,
    onToggleSrtlaStats: () -> Unit
) {
    SourceButton(
        text = "ENDPOINT SETTINGS",
        trailingIcon = R.drawable.ic_baseline_settings_24,
        isActive = isEndpointSettingsDrawerOpen,
        onClick = onToggleEndpointSettingsDrawer,
        modifier = Modifier.fillMaxWidth()
    )

    if (isSrtlaEndpoint) {
        SourceButton(
            text = "SRTLA STATS",
            isActive = isSrtlaStatsVisible,
            onClick = onToggleSrtlaStats,
            modifier = Modifier.fillMaxWidth()
        )
    }
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

