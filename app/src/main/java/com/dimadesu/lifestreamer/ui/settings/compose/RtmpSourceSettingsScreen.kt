package com.dimadesu.lifestreamer.ui.settings.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.ui.theme.AccentBlue
import com.dimadesu.lifestreamer.ui.theme.DarkBackground
import com.dimadesu.lifestreamer.ui.theme.LiveRed

@Composable
fun RtmpSourceSettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs by viewModel.preferencesFlow.collectAsState()

    var activeInputDialog by remember { mutableStateOf<RtmpInputDialogConfig?>(null) }

    val restartKey = context.getString(R.string.rtmp_source_restart_on_disconnect_key)
    val restartOnDisconnect = prefs?.get(booleanPreferencesKey(restartKey)) ?: true

    val requireAudioKey = context.getString(R.string.rtmp_source_require_audio_track_key)
    val requireAudioTrack = prefs?.get(booleanPreferencesKey(requireAudioKey)) ?: true

    val bufferKey = context.getString(R.string.rtmp_source_buffer_for_playback_ms_key)
    val bufferMs = prefs?.get(intPreferencesKey(bufferKey))
        ?: context.getString(R.string.default_rtmp_buffer_for_playback_ms).toInt()

    val countKey = context.getString(R.string.rtmp_source_count_key)
    val sourceCount = prefs?.get(intPreferencesKey(countKey)) ?: 1

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = stringResource(R.string.rtmp_source_category_title),
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
            SettingsCard(title = "Playback Behavior") {
                SettingSwitchRow(
                    title = stringResource(R.string.rtmp_source_restart_on_disconnect_title),
                    summary = stringResource(R.string.rtmp_source_restart_on_disconnect_summary),
                    checked = restartOnDisconnect,
                    onCheckedChange = { viewModel.saveBoolean(restartKey, it) }
                )

                SettingSwitchRow(
                    title = stringResource(R.string.rtmp_source_require_audio_track_title),
                    summary = stringResource(R.string.rtmp_source_require_audio_track_summary),
                    checked = requireAudioTrack,
                    onCheckedChange = { viewModel.saveBoolean(requireAudioKey, it) }
                )

                SettingValuePickerRow(
                    title = stringResource(R.string.rtmp_source_buffer_for_playback_ms_title),
                    summary = stringResource(R.string.rtmp_source_buffer_for_playback_ms_summary),
                    value = "$bufferMs ms",
                    onClick = {
                        activeInputDialog = RtmpInputDialogConfig(
                            title = "Player Buffer",
                            currentValue = bufferMs.toString(),
                            isNumeric = true,
                            key = bufferKey
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            SettingsCard(title = "RTMP Source URLs ($sourceCount configured)") {
                for (i in 1..sourceCount) {
                    val urlKey = viewModel.rtmpSourceUrlKey(i)
                    val url = prefs?.get(stringPreferencesKey(urlKey)) ?: viewModel.defaultRtmpSourceUrl(i)
                    val title = if (i == 1) "Primary RTMP Source URL" else "RTMP Source URL $i"

                    SettingValuePickerRow(
                        title = title,
                        value = url,
                        onClick = {
                            activeInputDialog = RtmpInputDialogConfig(
                                title = title,
                                currentValue = url,
                                key = urlKey
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val newCount = sourceCount + 1
                            viewModel.saveInt(countKey, newCount)
                            val newKey = viewModel.rtmpSourceUrlKey(newCount)
                            viewModel.saveString(newKey, viewModel.defaultRtmpSourceUrl(newCount))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Source")
                    }

                    if (sourceCount > 1) {
                        OutlinedButton(
                            onClick = {
                                val removeIndex = sourceCount
                                val removeKey = viewModel.rtmpSourceUrlKey(removeIndex)
                                viewModel.removeKey(removeKey)
                                viewModel.saveInt(countKey, sourceCount - 1)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveRed),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Remove Last")
                        }
                    }
                }
            }
        }
    }

    activeInputDialog?.let { config ->
        SettingInputDialog(
            title = config.title,
            currentValue = config.currentValue,
            label = "URL or Value",
            isNumeric = config.isNumeric,
            onSave = { newValue ->
                if (config.isNumeric) {
                    newValue.toIntOrNull()?.let { viewModel.saveInt(config.key, it) }
                } else {
                    viewModel.saveString(config.key, newValue)
                }
            },
            onDismiss = { activeInputDialog = null }
        )
    }
}

private data class RtmpInputDialogConfig(
    val title: String,
    val currentValue: String,
    val key: String,
    val isNumeric: Boolean = false
)
