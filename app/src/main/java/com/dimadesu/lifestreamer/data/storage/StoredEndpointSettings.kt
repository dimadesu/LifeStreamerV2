package com.dimadesu.lifestreamer.data.storage

import com.dimadesu.lifestreamer.models.EndpointType
import com.dimadesu.lifestreamer.models.LocalRecordingFormat

/**
 * Endpoint settings exactly as stored, with defaults applied for values the user has never chosen.
 *
 * Ports and latencies are kept as text because that is how they are stored. Only the settings of
 * the selected [type] are used when streaming, the rest are remembered for when it is selected again.
 *
 * @property recordToDiskEnabled whether streaming endpoints also record locally to disk.
 * @property recordToDiskFormat stored preference value for [LocalRecordingFormat] (e.g. "ts", "mp4", "flv").
 * @property fileName sanitized file name prefix without timestamp or extension.
 * @property srtRegulatorMode one of the `srt_server_moblin_regulator_mode_value_*` strings
 */
data class StoredEndpointSettings(
    val type: EndpointType = EndpointType.SRT,

    val srtAddress: String = "",
    val srtPort: String = "",
    val srtStreamId: String = "",
    val srtLatency: String = "",
    val srtPassphrase: String = "",

    val rtmpUrl: String = "",

    val srtlaReceiverHost: String = "",
    val srtlaReceiverPort: String = "",
    val srtlaListenPort: String = "",
    val srtlaStreamId: String = "",
    val srtlaLatency: String = "",
    val srtlaPassphrase: String = "",
    val moblinkEnabled: Boolean = false,
    val moblinkPassword: String = "",
    val moblinkPort: String = "",

    val srtAdaptiveBitrate: Boolean = true,
    val srtTargetBitrateKbps: Int = 0,
    val srtMinBitrateKbps: Int = 0,
    val srtRegulatorMode: String = "",

    val rtmpAdaptiveBitrate: Boolean = false,
    val rtmpTargetBitrateKbps: Int = 0,
    val rtmpMinBitrateKbps: Int = 0,

    val recordToDiskEnabled: Boolean = false,
    val recordToDiskFormat: String = LocalRecordingFormat.DEFAULT.prefValue,
    val fileName: String = ""
)
