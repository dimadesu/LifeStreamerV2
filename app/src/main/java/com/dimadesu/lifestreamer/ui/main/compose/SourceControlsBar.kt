package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 * Stateful Composable for the bottom-left controls column:
 * video source toggles (dynamic camera buttons, RTMP, USB, Screen) and audio/diagnostic toggles.
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
    val isAudioDebugOverlayVisible by viewModel.isAudioDebugOverlayVisible.observeAsState(initial = false)
    val isSrtlaEndpoint by viewModel.isSrtlaEndpoint.observeAsState(initial = false)
    val isSrtlaStatsVisible by viewModel.isSrtlaStatsVisible.observeAsState(initial = false)
    val currentCameraIdFromStreamer by viewModel.currentCameraIdLiveData.observeAsState(initial = null)
    val isCameraControlsDrawerOpen by viewModel.isCameraControlsDrawerOpen.observeAsState(initial = false)
    val isRtmpSettingsDrawerOpen by viewModel.isRtmpSettingsDrawerOpen.observeAsState(initial = false)
    val isAudioSourceDrawerOpen by viewModel.isAudioSourceDrawerOpen.observeAsState(initial = false)

    var localSelectedCameraId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentCameraIdFromStreamer) {
        if (currentCameraIdFromStreamer != null) {
            localSelectedCameraId = currentCameraIdFromStreamer
        }
    }
    val activeCameraId = localSelectedCameraId ?: currentCameraIdFromStreamer

    SourceControlsBar(
        cameras = cameras,
        selectedCameraId = activeCameraId,
        showCameraControls = showCameraControls,
        isCameraControlsDrawerOpen = isCameraControlsDrawerOpen,
        isRtmpSettingsDrawerOpen = isRtmpSettingsDrawerOpen,
        isAudioSourceDrawerOpen = isAudioSourceDrawerOpen,
        activeRtmpIndex = activeRtmpIndex,
        rtmpSourceCount = rtmpSourceCount,
        isUvcSource = isUvcSource,
        isScreenSource = isScreenSource,
        isMonitorAudioOn = isMonitorAudioOn,
        showBluetoothToggle = showBluetoothToggle,
        useBluetoothMic = useBluetoothMic,
        useSystemAudioForCamera = useSystemAudioForCamera,
        isAudioDebugOverlayVisible = isAudioDebugOverlayVisible,
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
        onToggleAudioSourceDrawer = { viewModel.toggleAudioSourceDrawer() },
        onToggleSrtlaStats = onToggleSrtlaStats,
        modifier = modifier
    )
}

/**
 * Stateless Composable for the bottom-left controls column.
 */
@Composable
fun SourceControlsBar(
    cameras: List<PreviewViewModel.CameraInfo>,
    selectedCameraId: String?,
    showCameraControls: Boolean,
    isCameraControlsDrawerOpen: Boolean = false,
    isRtmpSettingsDrawerOpen: Boolean = false,
    isAudioSourceDrawerOpen: Boolean = false,
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
    onToggleSrtlaStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRtmpSource = activeRtmpIndex != null

    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.BottomStart
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
        ) {
            // 1. Dynamic Camera Selection buttons
            if (showCameraControls && cameras.isNotEmpty()) {
                cameras.forEach { camera ->
                    val isSelected = (camera.id == selectedCameraId)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SourceButton(
                            text = camera.displayName,
                            isActive = isSelected,
                            onClick = { onSelectCamera(camera.id) }
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

            // 2. RTMP Source button(s) with settings cog
            if (!isUvcSource && !isScreenSource) {
                if (activeRtmpIndex != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SourceButton(
                            text = if (activeRtmpIndex == 1) "RTMP SRC" else "RTMP SRC $activeRtmpIndex",
                            isActive = true,
                            onClick = { onToggleRtmpSource(activeRtmpIndex) }
                        )
                        RtmpSettingsButton(
                            isOpen = isRtmpSettingsDrawerOpen,
                            onClick = onToggleRtmpSettings
                        )
                    }
                } else {
                    val maxIndex = rtmpSourceCount.coerceAtLeast(1)
                    for (i in 1..maxIndex) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SourceButton(
                                text = if (i == 1) "RTMP SRC" else "RTMP SRC $i",
                                isActive = false,
                                onClick = { onToggleRtmpSource(i) }
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

            // 4. USB Source Button
            if (!isRtmpSource && !isScreenSource) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SourceButton(
                        text = "USB SRC",
                        isActive = isUvcSource,
                        minWidth = 50.dp,
                        onClick = onToggleUvc
                    )
                    if (!isUvcSource) {
                        UsbSettingsButton(
                            onClick = onLaunchUvcTest
                        )
                    }
                }
            }

            // 6. Screen Capture Button
            if (!isRtmpSource && !isUvcSource) {
                SourceButton(
                    text = "SCREEN",
                    isActive = isScreenSource,
                    minWidth = 50.dp,
                    onClick = onToggleScreen
                )
            }

            // 7. Monitor Audio Button
            SourceButton(
                text = "MONITOR 🎤",
                isActive = isMonitorAudioOn,
                onClick = onToggleMonitorAudio
            )

            // 8. Bluetooth Mic Toggle Button
            if (showBluetoothToggle) {
                SourceButton(
                    text = "🎤",
                    leadingIcon = R.drawable.ic_bluetooth,
                    isActive = useBluetoothMic,
                    minWidth = 50.dp,
                    onClick = onToggleBluetoothMic
                )
            }

            // 9. Audio Out (System Audio) Button
            if (!isRtmpSource) {
                SourceButton(
                    text = "AUDIO OUT",
                    isActive = useSystemAudioForCamera,
                    onClick = onToggleSystemAudio
                )
            }

            // 10. Audio Settings & Source Drawer Toggle Button
            if (!isRtmpSource) {
                SourceButton(
                    text = "AUDIO",
                    trailingIcon = R.drawable.ic_baseline_settings_24,
                    isActive = isAudioSourceDrawerOpen || isAudioDebugOverlayVisible,
                    onClick = onToggleAudioSourceDrawer
                )
            }

            // 11. SRTLA Stats Button
            if (isSrtlaEndpoint) {
                SourceButton(
                    text = "SRTLA STATS",
                    isActive = isSrtlaStatsVisible,
                    onClick = onToggleSrtlaStats
                )
            }
        }
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

