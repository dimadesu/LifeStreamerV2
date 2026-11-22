package com.dimadesu.lifestreamer.ui.main.endpoint

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.data.storage.StoredEndpointSettings
import com.dimadesu.lifestreamer.models.EndpointType
import com.dimadesu.lifestreamer.models.FileExtension
import com.dimadesu.lifestreamer.models.LocalRecordingFormat
import com.dimadesu.lifestreamer.ui.main.compose.SettingsDropdownRow
import com.dimadesu.lifestreamer.ui.main.compose.SettingsSectionTitle
import com.dimadesu.lifestreamer.ui.main.compose.SettingsSwitchRow
import com.dimadesu.lifestreamer.ui.main.compose.SettingsTextFieldRow
import com.dimadesu.lifestreamer.utils.LocalRecordingCompatibilityValidator
import com.dimadesu.lifestreamer.utils.RecordingFileNameFormatter

/**
 * Scope for checking local recording codec compatibility warnings across settings drawers.
 */
enum class LocalRecordingWarningScope {
    ALL,
    VIDEO_ONLY,
    AUDIO_ONLY
}

/**
 * Evaluates whether the active video/audio encoders are incompatible with the configured local
 * recording container for [endpointType], returning an actionable warning or `null` when
 * compatible (or when local recording is inactive / endpoint is a standalone file).
 */
fun computeLocalRecordingCompatibilityWarning(
    endpointType: EndpointType,
    recordToDiskEnabled: Boolean,
    recordToDiskFormatPref: String?,
    videoMimeType: String?,
    audioMimeType: String?,
    scope: LocalRecordingWarningScope = LocalRecordingWarningScope.ALL
): String? {
    if (!endpointType.isStreaming || !recordToDiskEnabled) {
        return null
    }

    val format = LocalRecordingFormat.fromPrefValue(recordToDiskFormatPref, endpointType)
    val container = format.extension.containerType

    val relevantIncompatible = when (scope) {
        LocalRecordingWarningScope.ALL -> true
        LocalRecordingWarningScope.VIDEO_ONLY ->
            videoMimeType != null && !LocalRecordingCompatibilityValidator.isVideoSupported(container, videoMimeType)
        LocalRecordingWarningScope.AUDIO_ONLY ->
            audioMimeType != null && !LocalRecordingCompatibilityValidator.isAudioSupported(container, audioMimeType)
    }
    if (!relevantIncompatible) {
        return null
    }

    return LocalRecordingCompatibilityValidator.validate(
        extension = format.extension,
        videoMimeType = videoMimeType,
        audioMimeType = audioMimeType,
        endpointType = endpointType
    )
}

/**
 * Modular local recording settings section reused by both standalone file endpoints
 * (TS/MP4/FLV/WebM/OGG/3GP) and streaming endpoints (SRT/SRTLA/RTMP).
 */
@Composable
fun LocalRecordingSection(
    endpointType: EndpointType,
    settings: StoredEndpointSettings,
    videoMimeType: String?,
    audioMimeType: String?,
    onSetRecordToDiskEnabled: (Boolean) -> Unit,
    onSetRecordToDiskFormat: (LocalRecordingFormat) -> Unit,
    onSetFileName: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        if (endpointType.isLocalFile) {
            val fileExtension = FileExtension.fromEndpointType(endpointType)
            SettingsSectionTitle(stringResource(R.string.file))
            FileNamePrefixField(
                value = settings.fileName,
                extension = fileExtension,
                onCommit = onSetFileName
            )
        } else {
            SettingsSectionTitle(stringResource(R.string.local_recording))
            SettingsSwitchRow(
                title = stringResource(R.string.record_to_disk_enable_title),
                summary = stringResource(R.string.record_to_disk_enable_summary),
                checked = settings.recordToDiskEnabled,
                onCheckedChange = onSetRecordToDiskEnabled
            )

            if (settings.recordToDiskEnabled) {
                val supportedFormats = LocalRecordingFormat.supportedFor(endpointType)
                val effectiveFormat = LocalRecordingFormat.fromPrefValue(
                    settings.recordToDiskFormat,
                    endpointType
                )

                SettingsDropdownRow(
                    title = stringResource(R.string.record_to_disk_format_title),
                    currentValue = effectiveFormat,
                    currentValueName = "${effectiveFormat.extension.containerType.name} (${effectiveFormat.extension.extension})",
                    options = supportedFormats.map { it to stringResource(it.labelResId) },
                    onSelectOption = onSetRecordToDiskFormat
                )

                FileNamePrefixField(
                    value = settings.fileName,
                    extension = effectiveFormat.extension,
                    onCommit = onSetFileName
                )

                val compatibilityWarning = computeLocalRecordingCompatibilityWarning(
                    endpointType = endpointType,
                    recordToDiskEnabled = settings.recordToDiskEnabled,
                    recordToDiskFormatPref = settings.recordToDiskFormat,
                    videoMimeType = videoMimeType,
                    audioMimeType = audioMimeType,
                    scope = LocalRecordingWarningScope.ALL
                )
                if (compatibilityWarning != null) {
                    LocalRecordingCompatibilityWarningCard(message = compatibilityWarning)
                }
            }
        }
    }
}

@Composable
private fun FileNamePrefixField(
    value: String,
    extension: FileExtension,
    onCommit: (String) -> Unit
) {
    SettingsTextFieldRow(
        title = stringResource(R.string.file_name),
        value = value,
        onCommit = onCommit,
        sanitize = RecordingFileNameFormatter::sanitizePrefix,
        summaryProvider = { draft ->
            val preview = RecordingFileNameFormatter.formatPreview(draft, extension)
            "Movies/LifeStreamer/$preview"
        }
    )
}

/**
 * Inline red warning banner displayed when the active video/audio encoder is incompatible with
 * the chosen local recording container format.
 */
@Composable
fun LocalRecordingCompatibilityWarningCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0x26EF5350),
        border = BorderStroke(1.dp, Color(0x88EF5350)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.local_recording_warning_title),
                color = Color(0xFFEF5350),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 16.sp
            )
            Text(
                text = message,
                color = Color(0xE6FFFFFF),
                fontSize = 12.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Preview(name = "Local Recording - Streaming with Warning", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun PreviewLocalRecordingStreamingWarning() {
    LocalRecordingSection(
        endpointType = EndpointType.SRT,
        settings = StoredEndpointSettings(
            type = EndpointType.SRT,
            recordToDiskEnabled = true,
            recordToDiskFormat = LocalRecordingFormat.MP4.prefValue,
            fileName = "LifeStreamer"
        ),
        videoMimeType = "video/avc",
        audioMimeType = "audio/opus",
        onSetRecordToDiskEnabled = {},
        onSetRecordToDiskFormat = {},
        onSetFileName = {},
        modifier = Modifier.padding(16.dp)
    )
}

@Preview(name = "Local Recording - Standalone File", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun PreviewLocalRecordingStandaloneFile() {
    LocalRecordingSection(
        endpointType = EndpointType.TS_FILE,
        settings = StoredEndpointSettings(
            type = EndpointType.TS_FILE,
            fileName = "MyStream"
        ),
        videoMimeType = "video/avc",
        audioMimeType = "audio/mp4a-latm",
        onSetRecordToDiskEnabled = {},
        onSetRecordToDiskFormat = {},
        onSetFileName = {},
        modifier = Modifier.padding(16.dp)
    )
}
