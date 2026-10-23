package com.dimadesu.lifestreamer.ui.settings.compose

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
import com.dimadesu.lifestreamer.models.EndpointType
import com.dimadesu.lifestreamer.ui.theme.DarkBackground

@Composable
fun EndpointSettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs by viewModel.preferencesFlow.collectAsState()

    // Dialog state
    var showEndpointTypeDialog by remember { mutableStateOf(false) }
    var activeInputDialog by remember { mutableStateOf<InputDialogConfig?>(null) }
    var showRegulatorDialog by remember { mutableStateOf(false) }

    val endpointTypeId = prefs?.get(stringPreferencesKey(context.getString(R.string.endpoint_type_key)))?.toIntOrNull()
        ?: EndpointType.SRT.id
    val currentEndpoint = EndpointType.fromId(endpointTypeId)

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = stringResource(R.string.endpoint),
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
            // General Endpoint Type Selection
            SettingsCard(title = "Protocol / Output") {
                SettingValuePickerRow(
                    title = stringResource(R.string.endpoint_type),
                    value = stringResource(currentEndpoint.labelResId),
                    onClick = { showEndpointTypeDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Protocol-specific settings
            when (currentEndpoint) {
                EndpointType.SRT -> {
                    val ipKey = context.getString(R.string.srt_server_ip_key)
                    val ip = prefs?.get(stringPreferencesKey(ipKey)) ?: stringResource(R.string.default_srt_server_url)

                    val portKey = context.getString(R.string.srt_server_port_key)
                    val port = prefs?.get(stringPreferencesKey(portKey)) ?: stringResource(R.string.default_srt_server_port)

                    val streamIdKey = context.getString(R.string.srt_server_stream_id_key)
                    val streamId = prefs?.get(stringPreferencesKey(streamIdKey)).orEmpty()

                    val passphraseKey = context.getString(R.string.srt_server_passphrase_key)
                    val passphrase = prefs?.get(stringPreferencesKey(passphraseKey)).orEmpty()

                    val latencyKey = context.getString(R.string.srt_server_latency_key)
                    val latency = prefs?.get(stringPreferencesKey(latencyKey)) ?: stringResource(R.string.default_srt_server_latency)

                    SettingsCard(title = stringResource(R.string.srt_server)) {
                        SettingValuePickerRow(
                            title = "Server IP / Address",
                            value = ip,
                            onClick = {
                                activeInputDialog = InputDialogConfig(
                                    title = "Server IP",
                                    currentValue = ip,
                                    label = "IP or Hostname",
                                    key = ipKey
                                )
                            }
                        )
                        SettingValuePickerRow(
                            title = "Port",
                            value = port,
                            onClick = {
                                activeInputDialog = InputDialogConfig(
                                    title = "Server Port",
                                    currentValue = port,
                                    label = "Port (e.g. 6000)",
                                    isNumeric = true,
                                    key = portKey
                                )
                            }
                        )
                        val streamIdTitle = stringResource(R.string.srt_server_stream_id)
                        SettingValuePickerRow(
                            title = streamIdTitle,
                            value = if (streamId.isEmpty()) "None" else streamId,
                            onClick = {
                                activeInputDialog = InputDialogConfig(
                                    title = streamIdTitle,
                                    currentValue = streamId,
                                    key = streamIdKey
                                )
                            }
                        )

                        val passphraseTitle = stringResource(R.string.srt_server_passphrase)
                        SettingValuePickerRow(
                            title = passphraseTitle,
                            value = if (passphrase.isEmpty()) "None" else "••••••••",
                            onClick = {
                                activeInputDialog = InputDialogConfig(
                                    title = passphraseTitle,
                                    currentValue = passphrase,
                                    key = passphraseKey
                                )
                            }
                        )

                        val latencyTitle = stringResource(R.string.srt_server_latency)
                        SettingValuePickerRow(
                            title = latencyTitle,
                            value = "$latency ms",
                            onClick = {
                                activeInputDialog = InputDialogConfig(
                                    title = latencyTitle,
                                    currentValue = latency,
                                    isNumeric = true,
                                    key = latencyKey
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // SRT Adaptive Bitrate
                    val enableAbrKey = context.getString(R.string.srt_server_enable_bitrate_regulation_key)
                    val enableAbr = prefs?.get(booleanPreferencesKey(enableAbrKey)) ?: true

                    val targetBitrateKey = context.getString(R.string.srt_server_video_target_bitrate_key)
                    val targetBitrate = prefs?.get(intPreferencesKey(targetBitrateKey)) ?: 10000

                    val minBitrateKey = context.getString(R.string.srt_server_video_min_bitrate_key)
                    val minBitrate = prefs?.get(intPreferencesKey(minBitrateKey)) ?: 300

                    val regModeKey = context.getString(R.string.srt_server_moblin_regulator_mode_key)
                    val regMode = prefs?.get(stringPreferencesKey(regModeKey)) ?: stringResource(R.string.srt_server_moblin_regulator_mode_value_belabox)

                    SettingsCard(title = stringResource(R.string.bitrate_regulation)) {
                        SettingSwitchRow(
                            title = stringResource(R.string.srt_server_enable_bitrate_regulation),
                            checked = enableAbr,
                            onCheckedChange = { viewModel.saveBoolean(enableAbrKey, it) }
                        )

                        if (enableAbr) {
                            SettingValuePickerRow(
                                title = stringResource(R.string.srt_server_moblin_regulator_mode),
                                value = when (regMode) {
                                    stringResource(R.string.srt_server_moblin_regulator_mode_value_slow) -> "Moblin Slow"
                                    stringResource(R.string.srt_server_moblin_regulator_mode_value_fast) -> "Moblin Fast"
                                    else -> "Belabox"
                                },
                                onClick = { showRegulatorDialog = true }
                            )

                            SettingSliderRow(
                                title = "Target Bitrate",
                                value = targetBitrate.toFloat(),
                                valueText = "$targetBitrate kbps",
                                valueRange = 500f..20000f,
                                onValueChange = { viewModel.saveInt(targetBitrateKey, it.toInt()) }
                            )

                            SettingSliderRow(
                                title = "Minimum Bitrate",
                                value = minBitrate.toFloat(),
                                valueText = "$minBitrate kbps",
                                valueRange = 100f..5000f,
                                onValueChange = { viewModel.saveInt(minBitrateKey, it.toInt()) }
                            )
                        }
                    }
                }

                EndpointType.SRTLA -> {
                    val hostKey = context.getString(R.string.srtla_receiver_host_key)
                    val host = prefs?.get(stringPreferencesKey(hostKey)) ?: stringResource(R.string.default_srtla_receiver_host)

                    val portKey = context.getString(R.string.srtla_receiver_port_key)
                    val port = prefs?.get(stringPreferencesKey(portKey)) ?: stringResource(R.string.default_srtla_receiver_port)

                    val listenPortKey = context.getString(R.string.srtla_listen_port_key)
                    val listenPort = prefs?.get(stringPreferencesKey(listenPortKey)) ?: stringResource(R.string.default_srtla_listen_port)

                    val latencyKey = context.getString(R.string.srtla_latency_key)
                    val latency = prefs?.get(stringPreferencesKey(latencyKey)) ?: stringResource(R.string.default_srtla_latency)

                    val streamIdKey = context.getString(R.string.srtla_stream_id_key)
                    val streamId = prefs?.get(stringPreferencesKey(streamIdKey)).orEmpty()

                    val passphraseKey = context.getString(R.string.srtla_passphrase_key)
                    val passphrase = prefs?.get(stringPreferencesKey(passphraseKey)).orEmpty()

                    SettingsCard(title = "SRTLA Bonding Server") {
                        SettingValuePickerRow(
                            title = "Receiver Host / IP",
                            value = host,
                            onClick = {
                                activeInputDialog = InputDialogConfig(
                                    title = "SRTLA Receiver Host",
                                    currentValue = host,
                                    key = hostKey
                                )
                            }
                        )
                        SettingValuePickerRow(
                            title = "Receiver Port",
                            value = port,
                            onClick = {
                                activeInputDialog = InputDialogConfig(
                                    title = "Receiver Port",
                                    currentValue = port,
                                    isNumeric = true,
                                    key = portKey
                                )
                            }
                        )
                        SettingValuePickerRow(
                            title = "Local Listen Port",
                            value = listenPort,
                            onClick = {
                                activeInputDialog = InputDialogConfig(
                                    title = "Local Listen Port",
                                    currentValue = listenPort,
                                    isNumeric = true,
                                    key = listenPortKey
                                )
                            }
                        )
                        SettingValuePickerRow(
                            title = "Latency",
                            value = "$latency ms",
                            onClick = {
                                activeInputDialog = InputDialogConfig(
                                    title = "Latency (ms)",
                                    currentValue = latency,
                                    isNumeric = true,
                                    key = latencyKey
                                )
                            }
                        )
                        SettingValuePickerRow(
                            title = "Stream ID",
                            value = if (streamId.isEmpty()) "None" else streamId,
                            onClick = {
                                activeInputDialog = InputDialogConfig(
                                    title = "Stream ID",
                                    currentValue = streamId,
                                    key = streamIdKey
                                )
                            }
                        )
                        SettingValuePickerRow(
                            title = "Passphrase",
                            value = if (passphrase.isEmpty()) "None" else "••••••••",
                            onClick = {
                                activeInputDialog = InputDialogConfig(
                                    title = "Passphrase",
                                    currentValue = passphrase,
                                    key = passphraseKey
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Moblink Relay Server
                    val moblinkEnabledKey = context.getString(R.string.moblink_enabled_key)
                    val moblinkEnabled = prefs?.get(booleanPreferencesKey(moblinkEnabledKey)) ?: false

                    val moblinkPassKey = context.getString(R.string.moblink_password_key)
                    val moblinkPass = prefs?.get(stringPreferencesKey(moblinkPassKey)) ?: stringResource(R.string.default_moblink_password)

                    val moblinkPortKey = context.getString(R.string.moblink_port_key)
                    val moblinkPort = prefs?.get(stringPreferencesKey(moblinkPortKey)) ?: stringResource(R.string.default_moblink_port)

                    SettingsCard(title = "Moblink Relay Server") {
                        SettingSwitchRow(
                            title = stringResource(R.string.moblink_enabled),
                            summary = stringResource(R.string.moblink_summary),
                            checked = moblinkEnabled,
                            onCheckedChange = { viewModel.saveBoolean(moblinkEnabledKey, it) }
                        )

                        if (moblinkEnabled) {
                            SettingValuePickerRow(
                                title = "Server Port",
                                value = moblinkPort,
                                onClick = {
                                    activeInputDialog = InputDialogConfig(
                                        title = "Moblink Port",
                                        currentValue = moblinkPort,
                                        isNumeric = true,
                                        key = moblinkPortKey
                                    )
                                }
                            )
                            SettingValuePickerRow(
                                title = "Server Password",
                                value = moblinkPass,
                                onClick = {
                                    activeInputDialog = InputDialogConfig(
                                        title = "Moblink Password",
                                        currentValue = moblinkPass,
                                        key = moblinkPassKey
                                    )
                                }
                            )
                        }
                    }
                }

                EndpointType.RTMP -> {
                    val urlKey = context.getString(R.string.rtmp_server_url_key)
                    val url = prefs?.get(stringPreferencesKey(urlKey)) ?: stringResource(R.string.default_rtmp_url)

                    val enableAbrKey = context.getString(R.string.rtmp_server_enable_bitrate_regulation_key)
                    val enableAbr = prefs?.get(booleanPreferencesKey(enableAbrKey)) ?: false

                    val targetBitrateKey = context.getString(R.string.rtmp_server_video_target_bitrate_key)
                    val targetBitrate = prefs?.get(intPreferencesKey(targetBitrateKey)) ?: 5000

                    val minBitrateKey = context.getString(R.string.rtmp_server_video_min_bitrate_key)
                    val minBitrate = prefs?.get(intPreferencesKey(minBitrateKey)) ?: 500

                    SettingsCard(title = stringResource(R.string.rtmp_server)) {
                        SettingValuePickerRow(
                            title = "Server URL",
                            value = url,
                            onClick = {
                                activeInputDialog = InputDialogConfig(
                                    title = "RTMP Server URL",
                                    currentValue = url,
                                    label = "rtmp://host:port/app/stream",
                                    key = urlKey
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingsCard(title = stringResource(R.string.bitrate_regulation)) {
                        SettingSwitchRow(
                            title = stringResource(R.string.rtmp_server_enable_bitrate_regulation),
                            checked = enableAbr,
                            onCheckedChange = { viewModel.saveBoolean(enableAbrKey, it) }
                        )

                        if (enableAbr) {
                            SettingSliderRow(
                                title = "Target Bitrate",
                                value = targetBitrate.toFloat(),
                                valueText = "$targetBitrate kbps",
                                valueRange = 500f..15000f,
                                onValueChange = { viewModel.saveInt(targetBitrateKey, it.toInt()) }
                            )
                            SettingSliderRow(
                                title = "Minimum Bitrate",
                                value = minBitrate.toFloat(),
                                valueText = "$minBitrate kbps",
                                valueRange = 200f..5000f,
                                onValueChange = { viewModel.saveInt(minBitrateKey, it.toInt()) }
                            )
                        }
                    }
                }

                else -> {
                    // Local file recording
                    val fileNameKey = context.getString(R.string.file_name_key)
                    val fileName = prefs?.get(stringPreferencesKey(fileNameKey)) ?: stringResource(R.string.default_file_name)

                    SettingsCard(title = "File Recording") {
                        SettingValuePickerRow(
                            title = stringResource(R.string.file_name),
                            value = fileName,
                            onClick = {
                                activeInputDialog = InputDialogConfig(
                                    title = "File Name",
                                    currentValue = fileName,
                                    key = fileNameKey
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // Endpoint selection dialog
    if (showEndpointTypeDialog) {
        val endpointOptions = EndpointType.entries.map {
            it.id.toString() to stringResource(it.labelResId)
        }
        SettingSelectionDialog(
            title = stringResource(R.string.endpoint_type),
            options = endpointOptions,
            selectedKey = endpointTypeId.toString(),
            onSelect = { selectedId ->
                viewModel.saveString(context.getString(R.string.endpoint_type_key), selectedId)
            },
            onDismiss = { showEndpointTypeDialog = false }
        )
    }

    // Regulator mode dialog
    if (showRegulatorDialog) {
        val regOptions = listOf(
            context.getString(R.string.srt_server_moblin_regulator_mode_value_belabox) to "Belabox (Recommended)",
            context.getString(R.string.srt_server_moblin_regulator_mode_value_slow) to "Moblin Slow (Best for bonding)",
            context.getString(R.string.srt_server_moblin_regulator_mode_value_fast) to "Moblin Fast"
        )
        val regModeKey = context.getString(R.string.srt_server_moblin_regulator_mode_key)
        val currentReg = prefs?.get(stringPreferencesKey(regModeKey)) ?: context.getString(R.string.srt_server_moblin_regulator_mode_value_belabox)

        SettingSelectionDialog(
            title = stringResource(R.string.srt_server_moblin_regulator_mode),
            options = regOptions,
            selectedKey = currentReg,
            onSelect = { viewModel.saveString(regModeKey, it) },
            onDismiss = { showRegulatorDialog = false }
        )
    }

    // Input dialog
    activeInputDialog?.let { config ->
        SettingInputDialog(
            title = config.title,
            currentValue = config.currentValue,
            label = config.label,
            placeholder = config.placeholder,
            isNumeric = config.isNumeric,
            onSave = { newValue ->
                viewModel.saveString(config.key, newValue)
            },
            onDismiss = { activeInputDialog = null }
        )
    }
}

private data class InputDialogConfig(
    val title: String,
    val currentValue: String,
    val key: String,
    val label: String? = null,
    val placeholder: String? = null,
    val isNumeric: Boolean = false
)
