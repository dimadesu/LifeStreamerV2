package com.dimadesu.lifestreamer.ui.settings.compose

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.models.EndpointType
import com.dimadesu.lifestreamer.utils.dataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsIndexSummaries(
    val endpointSummary: String = "SRT",
    val videoSummary: String = "1920x1080 · 30 fps",
    val audioSummary: String = "AAC · 128 kbps",
    val rtmpSourceSummary: String = "1 source"
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val dataStore = context.dataStore

    val preferencesFlow: StateFlow<Preferences?> = dataStore.data
        .map { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val summariesFlow: StateFlow<SettingsIndexSummaries> = dataStore.data.map { prefs ->
        // 1. Endpoint
        val endpointTypeId = prefs[stringPreferencesKey(context.getString(R.string.endpoint_type_key))]?.toIntOrNull()
            ?: EndpointType.SRT.id
        val endpointType = EndpointType.fromId(endpointTypeId)
        val endpointTypeName = context.getString(endpointType.labelResId)
        val endpointDetail = when (endpointType) {
            EndpointType.SRT -> {
                val ip = prefs[stringPreferencesKey(context.getString(R.string.srt_server_ip_key))]
                    ?: context.getString(R.string.default_srt_server_url)
                val port = prefs[stringPreferencesKey(context.getString(R.string.srt_server_port_key))]
                    ?: context.getString(R.string.default_srt_server_port)
                if (ip.isNotBlank()) "$ip:$port" else null
            }
            EndpointType.RTMP -> {
                prefs[stringPreferencesKey(context.getString(R.string.rtmp_server_url_key))]
                    ?: context.getString(R.string.default_rtmp_url)
            }
            EndpointType.SRTLA -> {
                val host = prefs[stringPreferencesKey(context.getString(R.string.srtla_receiver_host_key))]
                    ?: context.getString(R.string.default_srtla_receiver_host)
                val port = prefs[stringPreferencesKey(context.getString(R.string.srtla_receiver_port_key))]
                    ?: context.getString(R.string.default_srtla_receiver_port)
                if (host.isNotBlank()) "$host:$port" else null
            }
            else -> {
                prefs[stringPreferencesKey(context.getString(R.string.file_name_key))]
                    ?: context.getString(R.string.default_file_name)
            }
        }
        val endpointSummary = if (!endpointDetail.isNullOrBlank()) "$endpointTypeName · $endpointDetail" else endpointTypeName

        // 2. Video
        val res = prefs[stringPreferencesKey(context.getString(R.string.video_resolution_key))]
            ?: context.getString(R.string.default_video_resolution)
        val cameraFps = prefs[stringPreferencesKey(context.getString(R.string.camera_fps_key))]
            ?: context.getString(R.string.default_fps)
        val matchFps = prefs[booleanPreferencesKey(context.getString(R.string.match_fps_key))] ?: true
        val fps = if (matchFps) cameraFps else prefs[stringPreferencesKey(context.getString(R.string.video_fps_key))]
            ?: context.getString(R.string.default_fps)
        val bitrate = prefs[intPreferencesKey(context.getString(R.string.video_bitrate_key))] ?: 1000
        val videoSummary = "$res · ${fps} fps · ${bitrate} kbps"

        // 3. Audio
        val encoder = prefs[stringPreferencesKey(context.getString(R.string.audio_encoder_key))]
            ?: context.getString(R.string.default_audio_encoder)
        val encoderName = if (encoder.contains("opus", ignoreCase = true)) "Opus" else "AAC"
        val bitrateKbps = (prefs[stringPreferencesKey(context.getString(R.string.audio_bitrate_key))]?.toIntOrNull() ?: 128000) / 1000
        val sampleRateHz = prefs[stringPreferencesKey(context.getString(R.string.audio_sample_rate_key))]?.toFloatOrNull() ?: 44100f
        val sampleRateKhz = "%.1f".format(sampleRateHz / 1000f)
        val audioSummary = "$encoderName · ${bitrateKbps} kbps · ${sampleRateKhz} kHz"

        // 4. RTMP source
        val count = prefs[intPreferencesKey(context.getString(R.string.rtmp_source_count_key))] ?: 1
        val primaryUrl = prefs[stringPreferencesKey(context.getString(R.string.rtmp_source_url_key))]
            ?: context.getString(R.string.rtmp_source_default_url)
        val countText = if (count == 1) "1 source" else "$count sources"
        val rtmpSourceSummary = if (primaryUrl.isNotBlank()) "$countText · $primaryUrl" else countText

        SettingsIndexSummaries(
            endpointSummary = endpointSummary,
            videoSummary = videoSummary,
            audioSummary = audioSummary,
            rtmpSourceSummary = rtmpSourceSummary
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsIndexSummaries())

    fun saveString(key: String, value: String) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey(key)] = value
            }
        }
    }

    fun saveInt(key: String, value: Int) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[intPreferencesKey(key)] = value
            }
        }
    }

    fun saveBoolean(key: String, value: Boolean) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[booleanPreferencesKey(key)] = value
            }
        }
    }

    fun removeKey(key: String) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences.remove(stringPreferencesKey(key))
            }
        }
    }

    fun rtmpSourceUrlKey(index: Int): String {
        val baseKey = context.getString(R.string.rtmp_source_url_key)
        return if (index == 1) baseKey else "${baseKey}_$index"
    }

    fun defaultRtmpSourceUrl(index: Int): String {
        val baseUrl = context.getString(R.string.rtmp_source_default_url)
        return if (index == 1) baseUrl else "$baseUrl$index"
    }
}
