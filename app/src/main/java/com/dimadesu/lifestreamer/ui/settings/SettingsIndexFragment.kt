package com.dimadesu.lifestreamer.ui.settings

import androidx.preference.Preference
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.models.EndpointType

class SettingsIndexFragment : BaseSettingsFragment() {
    override val preferencesRes = R.xml.settings_index
    override val titleRes = R.string.settings

    override fun onResume() {
        super.onResume()
        updateSummaries()
    }

    private fun updateSummaries() {
        val store = preferenceManager.preferenceDataStore ?: return

        // 1. Endpoint summary
        val endpointPref = findPreferenceSafe<Preference>(R.string.settings_section_endpoint_key)
        if (endpointPref != null) {
            val endpointTypeId = store.getString(getString(R.string.endpoint_type_key), "${EndpointType.SRT.id}")
                ?.toIntOrNull() ?: EndpointType.SRT.id
            val endpointType = EndpointType.fromId(endpointTypeId)
            val typeName = getString(endpointType.labelResId)
            val detail = when (endpointType) {
                EndpointType.SRT -> {
                    val ip = store.getString(getString(R.string.srt_server_ip_key), getString(R.string.default_srt_server_url))
                    val port = store.getString(getString(R.string.srt_server_port_key), getString(R.string.default_srt_server_port))
                    if (!ip.isNullOrBlank()) "$ip:$port" else null
                }
                EndpointType.RTMP -> {
                    store.getString(getString(R.string.rtmp_server_url_key), getString(R.string.default_rtmp_url))
                }
                EndpointType.SRTLA -> {
                    val host = store.getString(getString(R.string.srtla_receiver_host_key), getString(R.string.default_srtla_receiver_host))
                    val port = store.getString(getString(R.string.srtla_receiver_port_key), getString(R.string.default_srtla_receiver_port))
                    if (!host.isNullOrBlank()) "$host:$port" else null
                }
                else -> {
                    store.getString(getString(R.string.file_name_key), getString(R.string.default_file_name))
                }
            }
            endpointPref.summary = if (!detail.isNullOrBlank()) "$typeName · $detail" else typeName
        }

        // 2. Video summary
        val videoPref = findPreferenceSafe<Preference>(R.string.settings_section_video_key)
        if (videoPref != null) {
            val res = store.getString(getString(R.string.video_resolution_key), getString(R.string.default_video_resolution))
            val cameraFps = store.getString(getString(R.string.camera_fps_key), getString(R.string.default_fps))
            val matchFps = store.getBoolean(getString(R.string.match_fps_key), true)
            val fps = if (matchFps) cameraFps else store.getString(getString(R.string.video_fps_key), getString(R.string.default_fps))
            val bitrate = store.getInt(getString(R.string.video_bitrate_key), 1000)
            videoPref.summary = "$res · ${fps} fps · ${bitrate} kbps"
        }

        // 3. Audio summary
        val audioPref = findPreferenceSafe<Preference>(R.string.settings_section_audio_key)
        if (audioPref != null) {
            val encoder = store.getString(getString(R.string.audio_encoder_key), getString(R.string.default_audio_encoder))
            val encoderName = if (encoder?.contains("opus", ignoreCase = true) == true) "Opus" else "AAC"
            val bitrateKbps = (store.getString(getString(R.string.audio_bitrate_key), "128000")?.toIntOrNull() ?: 128000) / 1000
            val sampleRateHz = store.getString(getString(R.string.audio_sample_rate_key), "44100")?.toFloatOrNull() ?: 44100f
            val sampleRateKhz = "%.1f".format(sampleRateHz / 1000f)
            audioPref.summary = "$encoderName · ${bitrateKbps} kbps · ${sampleRateKhz} kHz"
        }

        // 4. RTMP source summary
        val rtmpSourcePref = findPreferenceSafe<Preference>(R.string.settings_section_rtmp_source_key)
        if (rtmpSourcePref != null) {
            val count = store.getInt(getString(R.string.rtmp_source_count_key), 1)
            val primaryUrl = store.getString(getString(R.string.rtmp_source_url_key), getString(R.string.rtmp_source_default_url))
            val countText = if (count == 1) "1 source" else "$count sources"
            rtmpSourcePref.summary = if (!primaryUrl.isNullOrBlank()) "$countText · $primaryUrl" else countText
        }
    }
}
