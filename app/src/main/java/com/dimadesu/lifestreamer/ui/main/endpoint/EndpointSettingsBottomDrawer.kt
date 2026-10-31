package com.dimadesu.lifestreamer.ui.main.endpoint

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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.data.storage.StoredEndpointSettings
import com.dimadesu.lifestreamer.models.EndpointFactory
import com.dimadesu.lifestreamer.models.EndpointType
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel
import com.dimadesu.lifestreamer.ui.main.compose.SettingsDrawerPanel
import com.dimadesu.lifestreamer.ui.main.compose.SettingsDropdownRow
import com.dimadesu.lifestreamer.ui.main.compose.SettingsSectionTitle
import com.dimadesu.lifestreamer.ui.main.compose.SettingsSwitchRow
import com.dimadesu.lifestreamer.ui.main.compose.SettingsTextFieldRow
import com.dimadesu.lifestreamer.ui.main.compose.SettingsWarningText

/**
 * Stateful Composable for the Endpoint Settings drawer/panel:
 * - Slides in as a bottom drawer (portrait) or side inspector (landscape).
 * - When [endpoint.openForType] is set, shows settings only for that endpoint type (no type dropdown).
 * - When opened generically (openForType == null), shows the type dropdown and all sections.
 */
@Composable
fun EndpointSettingsBottomDrawer(
    viewModel: PreviewViewModel,
    modifier: Modifier = Modifier
) {
    val endpoint = viewModel.endpointDrawer
    val isOpen by endpoint.isOpen.observeAsState(initial = false)
    val isStreaming by viewModel.isStreamingLiveData.observeAsState(initial = false)
    val settings by endpoint.settings.observeAsState(initial = StoredEndpointSettings())
    val openForType by endpoint.openForType.observeAsState(initial = null)

    // Title reflects which type's settings are shown when opened via a cog icon
    val title = if (openForType != null) {
        stringResource(openForType!!.labelResId) + " " + stringResource(R.string.endpoint_settings_title)
    } else {
        stringResource(R.string.endpoint_settings_title)
    }

    SettingsDrawerPanel(
        isOpen = isOpen,
        title = title,
        closeContentDescription = stringResource(R.string.close_endpoint_settings),
        onClose = { endpoint.setDrawerOpen(false) },
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Endpoint type dropdown — hidden for now per user request, code kept for future use.
            val showEndpointTypeDropdown = false
            if (showEndpointTypeDropdown) {
                SettingsDropdownRow(
                    title = stringResource(R.string.endpoint_type),
                    currentValue = settings.type,
                    currentValueName = stringResource(settings.type.labelResId),
                    options = EndpointType.entries.map { it to stringResource(it.labelResId) },
                    onSelectOption = { endpoint.setType(it) }
                )
                Divider()
            }

            // When opened for a specific type, show sections for that type only.
            // When opened generically (no specific type), show sections based on current settings.type.
            val displayType = openForType ?: settings.type
            val capabilities = EndpointFactory(displayType).build()

            if (capabilities.hasSrtCapabilities) {
                SrtSection(settings, endpoint)
            }
            if (capabilities.hasRtmpCapabilities) {
                RtmpSection(settings, endpoint)
            }
            if (capabilities.hasSrtlaCapabilities) {
                SrtlaSection(settings, endpoint)
            }
            if (capabilities.hasFileCapabilities) {
                FileSection(settings, endpoint)
            }

            if (capabilities.hasSrtCapabilities || capabilities.hasSrtlaCapabilities) {
                Divider()
                SrtAdaptiveBitrateSection(settings, endpoint)
            }
            if (capabilities.hasRtmpCapabilities) {
                Divider()
                RtmpAdaptiveBitrateSection(settings, endpoint)
            }

            if (isStreaming) {
                SettingsWarningText(stringResource(R.string.endpoint_settings_apply_after_stream))
                if (capabilities.hasSrtCapabilities || capabilities.hasSrtlaCapabilities || capabilities.hasRtmpCapabilities) {
                    SettingsWarningText(stringResource(R.string.endpoint_settings_adaptive_bitrate_live_update))
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)
}

@Composable
private fun SrtSection(settings: StoredEndpointSettings, endpoint: EndpointDrawerController) {
    SettingsSectionTitle(stringResource(R.string.srt_server))
    SettingsTextFieldRow(
        title = stringResource(R.string.server_ip),
        value = settings.srtAddress,
        onCommit = endpoint::setSrtAddress,
        keyboardType = KeyboardType.Uri,
        isValid = ::isNotBlankText
    )
    SettingsTextFieldRow(
        title = stringResource(R.string.server_port),
        value = settings.srtPort,
        onCommit = endpoint::setSrtPort,
        keyboardType = KeyboardType.Number,
        maxLength = NUMBER_MAX_LENGTH,
        isValid = ::isPort
    )
    SettingsTextFieldRow(
        title = stringResource(R.string.srt_server_stream_id),
        value = settings.srtStreamId,
        onCommit = endpoint::setSrtStreamId
    )
    SettingsTextFieldRow(
        title = stringResource(R.string.srt_server_latency),
        value = settings.srtLatency,
        onCommit = endpoint::setSrtLatency,
        keyboardType = KeyboardType.Number,
        maxLength = NUMBER_MAX_LENGTH,
        isValid = ::isNumber
    )
    SettingsTextFieldRow(
        title = stringResource(R.string.srt_server_passphrase),
        value = settings.srtPassphrase,
        onCommit = endpoint::setSrtPassphrase
    )
}

@Composable
private fun RtmpSection(settings: StoredEndpointSettings, endpoint: EndpointDrawerController) {
    SettingsSectionTitle(stringResource(R.string.rtmp_server))
    SettingsTextFieldRow(
        title = stringResource(R.string.server_url),
        value = settings.rtmpUrl,
        onCommit = endpoint::setRtmpUrl,
        keyboardType = KeyboardType.Uri,
        isValid = ::isNotBlankText
    )
}

@Composable
private fun SrtlaSection(settings: StoredEndpointSettings, endpoint: EndpointDrawerController) {
    SettingsSectionTitle(stringResource(R.string.srtla_settings))
    SettingsTextFieldRow(
        title = stringResource(R.string.srtla_receiver_host),
        value = settings.srtlaReceiverHost,
        onCommit = endpoint::setSrtlaReceiverHost,
        keyboardType = KeyboardType.Uri,
        isValid = ::isNotBlankText
    )
    SettingsTextFieldRow(
        title = stringResource(R.string.srtla_receiver_port),
        value = settings.srtlaReceiverPort,
        onCommit = endpoint::setSrtlaReceiverPort,
        keyboardType = KeyboardType.Number,
        maxLength = NUMBER_MAX_LENGTH,
        isValid = ::isPort
    )
    SettingsTextFieldRow(
        title = stringResource(R.string.srtla_listen_port),
        value = settings.srtlaListenPort,
        onCommit = endpoint::setSrtlaListenPort,
        keyboardType = KeyboardType.Number,
        maxLength = NUMBER_MAX_LENGTH,
        isValid = ::isPort
    )
    SettingsTextFieldRow(
        title = stringResource(R.string.srtla_stream_id),
        value = settings.srtlaStreamId,
        onCommit = endpoint::setSrtlaStreamId
    )
    SettingsTextFieldRow(
        title = stringResource(R.string.srtla_latency),
        value = settings.srtlaLatency,
        onCommit = endpoint::setSrtlaLatency,
        keyboardType = KeyboardType.Number,
        maxLength = NUMBER_MAX_LENGTH,
        isValid = ::isNumber
    )
    SettingsTextFieldRow(
        title = stringResource(R.string.srtla_passphrase),
        value = settings.srtlaPassphrase,
        onCommit = endpoint::setSrtlaPassphrase
    )

    SettingsSwitchRow(
        title = stringResource(R.string.moblink_enabled),
        summary = stringResource(R.string.moblink_summary),
        checked = settings.moblinkEnabled,
        onCheckedChange = endpoint::setMoblinkEnabled
    )
    if (settings.moblinkEnabled) {
        SettingsTextFieldRow(
            title = stringResource(R.string.moblink_password),
            value = settings.moblinkPassword,
            onCommit = endpoint::setMoblinkPassword
        )
        Text(
            text = stringResource(R.string.moblink_password_hint),
            fontSize = 12.sp,
            color = Color(0x99FFFFFF),
            lineHeight = 15.sp
        )
        SettingsTextFieldRow(
            title = stringResource(R.string.moblink_port),
            value = settings.moblinkPort,
            onCommit = endpoint::setMoblinkPort,
            keyboardType = KeyboardType.Number,
            maxLength = NUMBER_MAX_LENGTH,
            isValid = ::isPort
        )
        Text(
            text = stringResource(R.string.moblink_bitrate_note),
            fontSize = 12.sp,
            color = Color(0x99FFFFFF),
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun SrtAdaptiveBitrateSection(settings: StoredEndpointSettings, endpoint: EndpointDrawerController) {
    SettingsSectionTitle(stringResource(R.string.bitrate_regulation))
    SettingsSwitchRow(
        title = stringResource(R.string.srt_server_enable_bitrate_regulation),
        checked = settings.srtAdaptiveBitrate,
        onCheckedChange = endpoint::setSrtAdaptiveBitrate
    )
    if (!settings.srtAdaptiveBitrate) return

    BitrateRows(
        targetKbps = settings.srtTargetBitrateKbps,
        minKbps = settings.srtMinBitrateKbps,
        onSelectTarget = endpoint::setSrtTargetBitrate,
        onSelectMin = endpoint::setSrtMinBitrate
    )

    val modes = stringArrayResource(R.array.moblin_regulator_mode_values)
        .zip(stringArrayResource(R.array.moblin_regulator_mode_entries))
    SettingsDropdownRow(
        title = stringResource(R.string.srt_server_moblin_regulator_mode),
        currentValue = settings.srtRegulatorMode,
        currentValueName = modes.firstOrNull { it.first == settings.srtRegulatorMode }?.second
            ?: settings.srtRegulatorMode,
        options = modes,
        onSelectOption = endpoint::setSrtRegulatorMode
    )
}

@Composable
private fun RtmpAdaptiveBitrateSection(settings: StoredEndpointSettings, endpoint: EndpointDrawerController) {
    SettingsSectionTitle(stringResource(R.string.bitrate_regulation))
    SettingsSwitchRow(
        title = stringResource(R.string.rtmp_server_enable_bitrate_regulation),
        checked = settings.rtmpAdaptiveBitrate,
        onCheckedChange = endpoint::setRtmpAdaptiveBitrate
    )
    if (!settings.rtmpAdaptiveBitrate) return

    BitrateRows(
        targetKbps = settings.rtmpTargetBitrateKbps,
        minKbps = settings.rtmpMinBitrateKbps,
        onSelectTarget = endpoint::setRtmpTargetBitrate,
        onSelectMin = endpoint::setRtmpMinBitrate
    )
}

@Composable
private fun BitrateRows(
    targetKbps: Int,
    minKbps: Int,
    onSelectTarget: (Int) -> Unit,
    onSelectMin: (Int) -> Unit
) {
    SettingsDropdownRow(
        title = stringResource(R.string.srt_server_video_target_bitrate),
        currentValue = targetKbps,
        currentValueName = "$targetKbps Kbps",
        options = bitrateOptions(TARGET_BITRATES_KBPS, targetKbps),
        onSelectOption = onSelectTarget
    )
    SettingsDropdownRow(
        title = stringResource(R.string.srt_server_video_min_bitrate),
        currentValue = minKbps,
        currentValueName = "$minKbps Kbps",
        options = bitrateOptions(MIN_BITRATES_KBPS, minKbps),
        onSelectOption = onSelectMin
    )
}

@Composable
private fun FileSection(settings: StoredEndpointSettings, endpoint: EndpointDrawerController) {
    SettingsSectionTitle(stringResource(R.string.file))
    SettingsTextFieldRow(
        title = stringResource(R.string.file_name),
        value = settings.fileName,
        onCommit = endpoint::setFileName,
        isValid = ::isNotBlankText
    )
    Text(
        text = stringResource(R.string.file_name_extension_note),
        fontSize = 12.sp,
        color = Color(0x99FFFFFF),
        lineHeight = 15.sp
    )
}

// Always offers the current value, even when it is not one of the steps
private fun bitrateOptions(steps: List<Int>, current: Int): List<Pair<Int, String>> =
    (steps + current).distinct().sorted().map { it to "$it Kbps" }

private fun isNotBlankText(text: String) = text.isNotBlank()

private fun isNumber(text: String) = (text.toIntOrNull() ?: -1) >= 0

private fun isPort(text: String) = text.toIntOrNull() in 1..65535

private const val NUMBER_MAX_LENGTH = 5

private val TARGET_BITRATES_KBPS = (500..10000 step 500).toList()
private val MIN_BITRATES_KBPS = (100..500 step 100) + (1000..10000 step 500)
