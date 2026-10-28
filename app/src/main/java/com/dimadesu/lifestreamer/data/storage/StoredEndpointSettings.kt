package com.dimadesu.lifestreamer.data.storage

import com.dimadesu.lifestreamer.models.EndpointType

/**
 * Endpoint settings exactly as stored, with defaults applied for values the user has never chosen.
 *
 * Ports and latencies are kept as text because that is how they are stored. Only the settings of
 * the selected [type] are used when streaming, the rest are remembered for when it is selected again.
 *
 * @property fileName file name without extension. The extension follows the endpoint type.
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

    val fileName: String = ""
)
