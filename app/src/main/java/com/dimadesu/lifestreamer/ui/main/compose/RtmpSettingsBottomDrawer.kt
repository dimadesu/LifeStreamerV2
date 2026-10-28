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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.data.storage.DataStoreRepository
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel
import kotlin.math.roundToInt

/**
 * Stateful Composable for the RTMP source configuration drawer/panel:
 * - Slides in as a bottom drawer (portrait) or side inspector (landscape).
 * - Displays RTMP SERVER action button.
 * - Dynamic list of RTMP source URLs with add/remove actions.
 * - Toggles for "Restart on disconnect" and "Require audio track".
 * - Slider for player buffer duration in milliseconds.
 */
@Composable
fun RtmpSettingsBottomDrawer(
    viewModel: PreviewViewModel,
    onLaunchRtmpServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOpen by viewModel.isRtmpSettingsDrawerOpen.observeAsState(initial = false)
    val isUvcSource by viewModel.isUvcSource.observeAsState(initial = false)
    val isScreenSource by viewModel.isScreenSource.observeAsState(initial = false)
    val showRtmpControls = !isUvcSource && !isScreenSource

    val rtmpSources by viewModel.rtmpSourceUrlsList.observeAsState(initial = emptyList())
    val restartOnDisconnect by viewModel.rtmpSourceRestartOnDisconnect.observeAsState(initial = true)
    val requireAudioTrack by viewModel.rtmpSourceRequireAudioTrack.observeAsState(initial = true)
    val bufferForPlaybackMs by viewModel.rtmpSourceBufferForPlaybackMs.observeAsState(initial = 2500)

    RtmpSettingsBottomDrawer(
        isOpen = isOpen,
        showRtmpControls = showRtmpControls,
        rtmpSources = rtmpSources,
        onUpdateRtmpUrl = { index, url -> viewModel.setRtmpSourceUrl(index, url) },
        onAddSource = { viewModel.addRtmpSource() },
        onRemoveLastSource = { viewModel.removeLastRtmpSource() },
        restartOnDisconnect = restartOnDisconnect,
        onSetRestartOnDisconnect = { viewModel.setRtmpSourceRestartOnDisconnect(it) },
        requireAudioTrack = requireAudioTrack,
        onSetRequireAudioTrack = { viewModel.setRtmpSourceRequireAudioTrack(it) },
        bufferForPlaybackMs = bufferForPlaybackMs,
        onSetBufferForPlaybackMs = { viewModel.setRtmpSourceBufferForPlaybackMs(it) },
        onLaunchRtmpServer = onLaunchRtmpServer,
        onClose = { viewModel.setRtmpSettingsDrawerOpen(false) },
        modifier = modifier
    )
}

/**
 * Stateless responsive panel for RTMP settings:
 * - Slides in as a side inspector from the right edge in landscape.
 * - Slides in as a bottom drawer above the live button in portrait.
 * - Dynamic max height calculation prevents overflow on all screen sizes.
 * - Integrated vertical scrollbar shows scroll progress when content overflows.
 */
@Composable
fun RtmpSettingsBottomDrawer(
    isOpen: Boolean,
    showRtmpControls: Boolean,
    rtmpSources: List<DataStoreRepository.RtmpSourceUrlEntry>,
    onUpdateRtmpUrl: (Int, String) -> Unit,
    onAddSource: () -> Unit,
    onRemoveLastSource: () -> Unit,
    restartOnDisconnect: Boolean,
    onSetRestartOnDisconnect: (Boolean) -> Unit,
    requireAudioTrack: Boolean,
    onSetRequireAudioTrack: (Boolean) -> Unit,
    bufferForPlaybackMs: Int,
    onSetBufferForPlaybackMs: (Int) -> Unit,
    onLaunchRtmpServer: () -> Unit,
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
            visible = isOpen && showRtmpControls,
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
                                painter = painterResource(id = R.drawable.ic_baseline_settings_24),
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.rtmp_source_category_title).uppercase(),
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
                                contentDescription = stringResource(R.string.close_rtmp_controls),
                                tint = Color(0xB3FFFFFF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)

                    // 1. RTMP SERVER button
                    Button(
                        onClick = onLaunchRtmpServer,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF616161),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.rtmp_server_button),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)

                    // 2. RTMP Source URLs list
                    val sources = if (rtmpSources.isEmpty()) {
                        listOf(DataStoreRepository.RtmpSourceUrlEntry(1, "rtmp://localhost:1935/publish/live"))
                    } else {
                        rtmpSources
                    }

                    sources.forEach { sourceEntry ->
                        RtmpUrlField(
                            index = sourceEntry.index,
                            totalCount = sources.size,
                            url = sourceEntry.url,
                            onUrlChange = { newUrl -> onUpdateRtmpUrl(sourceEntry.index, newUrl) }
                        )
                    }

                    // Add / Remove buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onAddSource,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x224CAF50),
                                contentColor = Color(0xFF81C784)
                            ),
                            border = BorderStroke(1.dp, Color(0x554CAF50)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "+ ${stringResource(R.string.rtmp_source_add_title)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (sources.size > 1) {
                            Button(
                                onClick = onRemoveLastSource,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0x22F44336),
                                    contentColor = Color(0xFFE57373)
                                ),
                                border = BorderStroke(1.dp, Color(0x55F44336)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "− ${stringResource(R.string.rtmp_source_remove_title)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)

                    // 3. Restart SRT stream on disconnect toggle
                    RtmpToggleRow(
                        title = stringResource(R.string.rtmp_source_restart_on_disconnect_title),
                        summary = stringResource(R.string.rtmp_source_restart_on_disconnect_summary),
                        checked = restartOnDisconnect,
                        onCheckedChange = onSetRestartOnDisconnect
                    )

                    // 4. Require audio track toggle
                    RtmpToggleRow(
                        title = stringResource(R.string.rtmp_source_require_audio_track_title),
                        summary = stringResource(R.string.rtmp_source_require_audio_track_summary),
                        checked = requireAudioTrack,
                        onCheckedChange = onSetRequireAudioTrack
                    )

                    HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)

                    // 5. Playback buffer duration slider
                    RtmpBufferSliderBlock(
                        bufferMs = bufferForPlaybackMs,
                        onValueChange = onSetBufferForPlaybackMs
                    )
                }
            }
        }
    }
}

/**
 * Text field for an RTMP source URL with auto-commit on focus change / Done IME action.
 */
@Composable
private fun RtmpUrlField(
    index: Int,
    totalCount: Int,
    url: String,
    onUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(url) { mutableStateOf(url) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(url) {
        if (text != url) {
            text = url
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = if (totalCount == 1 && index == 1) {
                stringResource(R.string.rtmp_source_title)
            } else {
                stringResource(R.string.rtmp_source_url_title_numbered, index)
            },
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        OutlinedTextField(
            value = text,
            onValueChange = { newText ->
                text = newText
                onUrlChange(newText)
            },
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onUrlChange(text)
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4CAF50),
                unfocusedBorderColor = Color(0x44FFFFFF),
                cursorColor = Color(0xFF4CAF50),
                focusedContainerColor = Color(0x22000000),
                unfocusedContainerColor = Color(0x22000000)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        onUrlChange(text)
                    }
                }
        )
    }
}

/**
 * Clean toggle row with title, descriptive summary, and styled green switch.
 */
@Composable
private fun RtmpToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
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
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = summary,
                color = Color(0x80FFFFFF),
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
        Switch(
            checked = checked,
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
 * Playback buffer duration slider (500ms to 6000ms with 500ms step).
 */
@Composable
private fun RtmpBufferSliderBlock(
    bufferMs: Int,
    onValueChange: (Int) -> Unit,
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.rtmp_source_buffer_for_playback_ms_title),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.rtmp_source_buffer_for_playback_ms_summary),
                    color = Color(0x80FFFFFF),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Text(
                text = "${bufferMs}ms",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0x22FFFFFF), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Slider(
            value = bufferMs.toFloat(),
            onValueChange = { floatVal ->
                val rounded = ((floatVal / 500f).roundToInt() * 500).coerceIn(500, 6000)
                onValueChange(rounded)
            },
            valueRange = 500f..6000f,
            steps = ((6000 - 500) / 500) - 1,
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
            Text("500ms", fontSize = 10.sp, color = Color(0x80FFFFFF))
            Text("6000ms", fontSize = 10.sp, color = Color(0x80FFFFFF))
        }
    }
}

@Preview(name = "Side Inspector - Landscape", widthDp = 840, heightDp = 390, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewRtmpSettingsBottomDrawerLandscape() {
    RtmpSettingsBottomDrawer(
        isOpen = true,
        showRtmpControls = true,
        rtmpSources = listOf(
            DataStoreRepository.RtmpSourceUrlEntry(1, "rtmp://localhost:1935/publish/live"),
            DataStoreRepository.RtmpSourceUrlEntry(2, "rtmp://localhost:1935/publish/live2")
        ),
        onUpdateRtmpUrl = { _, _ -> },
        onAddSource = {},
        onRemoveLastSource = {},
        restartOnDisconnect = true,
        onSetRestartOnDisconnect = {},
        requireAudioTrack = true,
        onSetRequireAudioTrack = {},
        bufferForPlaybackMs = 2500,
        onSetBufferForPlaybackMs = {},
        onLaunchRtmpServer = {},
        onClose = {}
    )
}

@Preview(name = "Bottom Drawer - Portrait", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewRtmpSettingsBottomDrawerPortrait() {
    RtmpSettingsBottomDrawer(
        isOpen = true,
        showRtmpControls = true,
        rtmpSources = listOf(
            DataStoreRepository.RtmpSourceUrlEntry(1, "rtmp://localhost:1935/publish/live")
        ),
        onUpdateRtmpUrl = { _, _ -> },
        onAddSource = {},
        onRemoveLastSource = {},
        restartOnDisconnect = true,
        onSetRestartOnDisconnect = {},
        requireAudioTrack = false,
        onSetRequireAudioTrack = {},
        bufferForPlaybackMs = 3000,
        onSetBufferForPlaybackMs = {},
        onLaunchRtmpServer = {},
        onClose = {}
    )
}
