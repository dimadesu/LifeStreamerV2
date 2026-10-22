package com.dimadesu.lifestreamer.ui.settings

import android.text.InputFilter
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreference
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.models.EndpointFactory
import com.dimadesu.lifestreamer.models.EndpointType
import com.dimadesu.lifestreamer.models.FileExtension
import java.io.IOException

class EndpointSettingsFragment : BaseSettingsFragment() {
    override val preferencesRes = R.xml.settings_endpoint
    override val titleRes = R.string.endpoint

    private val endpointTypePreference: ListPreference by lazy {
        findPreferenceSafe<ListPreference>(R.string.endpoint_type_key) ?: error("endpoint_type_key not found")
    }

    private val srtEndpointPreference: PreferenceCategory by lazy {
        findPreferenceSafe<PreferenceCategory>(R.string.srt_server_key) ?: error("srt_server_key not found")
    }

    private val rtmpEndpointPreference: PreferenceCategory by lazy {
        findPreferenceSafe<PreferenceCategory>(R.string.rtmp_server_key) ?: error("rtmp_server_key not found")
    }

    private val fileEndpointPreference: PreferenceCategory by lazy {
        findPreferenceSafe<PreferenceCategory>(R.string.file_endpoint_key) ?: error("file_endpoint_key not found")
    }

    private val srtlaEndpointPreference: PreferenceCategory by lazy {
        findPreferenceSafe<PreferenceCategory>(R.string.srtla_key) ?: error("srtla_key not found")
    }

    private val bitrateRegulationPreference: PreferenceCategory by lazy {
        findPreferenceSafe<PreferenceCategory>(R.string.bitrate_regulation_key) ?: error("bitrate_regulation_key not found")
    }

    private val serverIpPreference: EditTextPreference by lazy {
        findPreferenceSafe<EditTextPreference>(R.string.srt_server_ip_key) ?: error("srt_server_ip_key not found")
    }

    private val serverPortPreference: EditTextPreference by lazy {
        findPreferenceSafe<EditTextPreference>(R.string.srt_server_port_key) ?: error("srt_server_port_key not found")
    }

    private val serverLatencyPreference: EditTextPreference by lazy {
        findPreferenceSafe<EditTextPreference>(R.string.srt_server_latency_key) ?: error("srt_server_latency_key not found")
    }

    private val serverEnableBitrateRegulationPreference: SwitchPreference by lazy {
        findPreferenceSafe<SwitchPreference>(R.string.srt_server_enable_bitrate_regulation_key) ?: error("srt_server_enable_bitrate_regulation_key not found")
    }

    private val serverTargetVideoBitratePreference: SeekBarPreference by lazy {
        findPreferenceSafe<SeekBarPreference>(R.string.srt_server_video_target_bitrate_key) ?: error("srt_server_video_target_bitrate_key not found")
    }

    private val serverMinVideoBitratePreference: SeekBarPreference by lazy {
        findPreferenceSafe<SeekBarPreference>(R.string.srt_server_video_min_bitrate_key) ?: error("srt_server_video_min_bitrate_key not found")
    }

    private val rtmpServerEnableBitrateRegulationPreference: SwitchPreference by lazy {
        findPreferenceSafe<SwitchPreference>(R.string.rtmp_server_enable_bitrate_regulation_key) ?: error("rtmp_server_enable_bitrate_regulation_key not found")
    }

    private val rtmpServerTargetVideoBitratePreference: SeekBarPreference by lazy {
        findPreferenceSafe<SeekBarPreference>(R.string.rtmp_server_video_target_bitrate_key) ?: error("rtmp_server_video_target_bitrate_key not found")
    }

    private val rtmpServerMinVideoBitratePreference: SeekBarPreference by lazy {
        findPreferenceSafe<SeekBarPreference>(R.string.rtmp_server_video_min_bitrate_key) ?: error("rtmp_server_video_min_bitrate_key not found")
    }

    private val regulatorModePreference: ListPreference by lazy {
        findPreferenceSafe<ListPreference>(R.string.srt_server_moblin_regulator_mode_key) ?: error("srt_server_moblin_regulator_mode_key not found")
    }

    private val fileNamePreference: EditTextPreference by lazy {
        findPreferenceSafe<EditTextPreference>(R.string.file_name_key) ?: error("file_name_key not found")
    }

    override fun onPreferencesInflated() {
        loadEndpoint()
    }

    private fun loadEndpoint() {
        val supportedEndpoint = EndpointType.entries.map { "${it.id}" }.toTypedArray()
        endpointTypePreference.entryValues = supportedEndpoint
        endpointTypePreference.entries =
            EndpointType.entries.map { getString(it.labelResId) }.toTypedArray()

        if (endpointTypePreference.value.isNullOrEmpty()) {
            endpointTypePreference.value = "${EndpointType.SRT.id}"
        }
        endpointTypePreference.value?.toIntOrNull()?.let { setEndpointType(it) }
        endpointTypePreference.setOnPreferenceChangeListener { _, newValue ->
            (newValue as? String)?.toIntOrNull()?.let { setEndpointType(it) }
            true
        }

        serverIpPreference.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
        }

        serverPortPreference.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
            editText.filters = arrayOf(InputFilter.LengthFilter(5))
        }

        serverLatencyPreference.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
            editText.filters = arrayOf(InputFilter.LengthFilter(5))
        }

        findPreferenceSafe<EditTextPreference>(R.string.srtla_receiver_host_key)
            ?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
            }
        findPreferenceSafe<EditTextPreference>(R.string.srtla_receiver_port_key)
            ?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
                editText.filters = arrayOf(InputFilter.LengthFilter(5))
            }
        findPreferenceSafe<EditTextPreference>(R.string.srtla_listen_port_key)
            ?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
                editText.filters = arrayOf(InputFilter.LengthFilter(5))
            }
        findPreferenceSafe<EditTextPreference>(R.string.srtla_latency_key)
            ?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
                editText.filters = arrayOf(InputFilter.LengthFilter(5))
            }

        val currentEndpointType = EndpointType.fromId(endpointTypePreference.value?.toInt() ?: 0)
        val currentEndpoint = EndpointFactory(currentEndpointType).build()

        val isSrtInitial = currentEndpoint.hasSrtCapabilities || currentEndpoint.hasSrtlaCapabilities
        val isRtmpInitial = currentEndpoint.hasRtmpCapabilities

        serverEnableBitrateRegulationPreference.isVisible = isSrtInitial
        serverTargetVideoBitratePreference.isVisible = serverEnableBitrateRegulationPreference.isChecked && isSrtInitial
        serverMinVideoBitratePreference.isVisible =
            serverEnableBitrateRegulationPreference.isChecked && isSrtInitial
        regulatorModePreference.isVisible = serverEnableBitrateRegulationPreference.isChecked && isSrtInitial

        rtmpServerEnableBitrateRegulationPreference.isVisible = isRtmpInitial
        rtmpServerTargetVideoBitratePreference.isVisible = rtmpServerEnableBitrateRegulationPreference.isChecked && isRtmpInitial
        rtmpServerMinVideoBitratePreference.isVisible = rtmpServerEnableBitrateRegulationPreference.isChecked && isRtmpInitial

        serverEnableBitrateRegulationPreference.setOnPreferenceChangeListener { _, newValue ->
            val isEnabled = newValue as Boolean
            val endpoint = EndpointFactory(EndpointType.fromId(endpointTypePreference.value?.toInt() ?: 0)).build()
            val isSrt = endpoint.hasSrtCapabilities || endpoint.hasSrtlaCapabilities

            serverTargetVideoBitratePreference.isVisible = isEnabled && isSrt
            serverMinVideoBitratePreference.isVisible = isEnabled && isSrt
            regulatorModePreference.isVisible = isEnabled && isSrt
            true
        }

        serverTargetVideoBitratePreference.setOnPreferenceChangeListener { _, newValue ->
            val rounded = roundBitrate(newValue as Int)
            if (rounded < serverMinVideoBitratePreference.value) {
                serverMinVideoBitratePreference.value = rounded
            }
            if (rounded != newValue) {
                serverTargetVideoBitratePreference.value = rounded
                false
            } else {
                true
            }
        }

        serverMinVideoBitratePreference.setOnPreferenceChangeListener { _, newValue ->
            val rounded = roundBitrate(newValue as Int)
            if (rounded > serverTargetVideoBitratePreference.value) {
                serverTargetVideoBitratePreference.value = rounded
            }
            if (rounded != newValue) {
                serverMinVideoBitratePreference.value = rounded
                false
            } else {
                true
            }
        }

        rtmpServerEnableBitrateRegulationPreference.setOnPreferenceChangeListener { _, newValue ->
            val isEnabled = newValue as Boolean
            val endpoint = EndpointFactory(EndpointType.fromId(endpointTypePreference.value?.toInt() ?: 0)).build()
            val isRtmp = endpoint.hasRtmpCapabilities
            rtmpServerTargetVideoBitratePreference.isVisible = isEnabled && isRtmp
            rtmpServerMinVideoBitratePreference.isVisible = isEnabled && isRtmp
            true
        }

        rtmpServerTargetVideoBitratePreference.setOnPreferenceChangeListener { _, newValue ->
            val rounded = roundBitrate(newValue as Int)
            if (rounded < rtmpServerMinVideoBitratePreference.value) {
                rtmpServerMinVideoBitratePreference.value = rounded
            }
            if (rounded != newValue) {
                rtmpServerTargetVideoBitratePreference.value = rounded
                false
            } else {
                true
            }
        }

        rtmpServerMinVideoBitratePreference.setOnPreferenceChangeListener { _, newValue ->
            val rounded = roundBitrate(newValue as Int)
            if (rounded > rtmpServerTargetVideoBitratePreference.value) {
                rtmpServerTargetVideoBitratePreference.value = rounded
            }
            if (rounded != newValue) {
                rtmpServerMinVideoBitratePreference.value = rounded
                false
            } else {
                true
            }
        }
    }

    private fun setEndpointType(id: Int) {
        val endpointType = EndpointType.fromId(id)
        val endpoint = EndpointFactory(endpointType).build()
        srtEndpointPreference.isVisible = endpoint.hasSrtCapabilities
        rtmpEndpointPreference.isVisible = endpoint.hasRtmpCapabilities
        srtlaEndpointPreference.isVisible = endpoint.hasSrtlaCapabilities
        fileEndpointPreference.isVisible = endpoint.hasFileCapabilities
        bitrateRegulationPreference.isVisible = endpoint.hasSrtCapabilities || endpoint.hasSrtlaCapabilities || endpoint.hasRtmpCapabilities

        val isSrt = endpoint.hasSrtCapabilities || endpoint.hasSrtlaCapabilities
        val isRtmp = endpoint.hasRtmpCapabilities

        serverEnableBitrateRegulationPreference.isVisible = isSrt
        serverTargetVideoBitratePreference.isVisible = isSrt && serverEnableBitrateRegulationPreference.isChecked
        regulatorModePreference.isVisible = serverEnableBitrateRegulationPreference.isChecked && isSrt
        serverMinVideoBitratePreference.isVisible = isSrt && serverEnableBitrateRegulationPreference.isChecked

        rtmpServerEnableBitrateRegulationPreference.isVisible = isRtmp
        rtmpServerTargetVideoBitratePreference.isVisible = isRtmp && rtmpServerEnableBitrateRegulationPreference.isChecked
        rtmpServerMinVideoBitratePreference.isVisible = isRtmp && rtmpServerEnableBitrateRegulationPreference.isChecked

        // Update file extension
        if (endpoint.hasFileCapabilities) {
            // Remove previous extension (only once)
            fileNamePreference.text = fileNamePreference.text?.substringBeforeLast(".") ?: "output"
            // Add correct extension
            fileNamePreference.text += when {
                endpoint.hasFLVCapabilities -> FileExtension.FLV.extension
                endpoint.hasTSCapabilities -> FileExtension.TS.extension
                endpoint.hasMP4Capabilities -> FileExtension.MP4.extension
                endpointType == EndpointType.WEBM_FILE -> FileExtension.WEBM.extension
                endpointType == EndpointType.OGG_FILE -> FileExtension.OGG.extension
                endpointType == EndpointType.THREEGP_FILE -> FileExtension.THREEGP.extension
                else -> throw IOException("Unknown file type")
            }
        }
    }
}
