package com.dimadesu.lifestreamer.ui.settings

import android.media.AudioFormat
import android.media.MediaCodecInfo
import android.media.MediaFormat
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreference
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.utils.ProfileLevelDisplay
import com.dimadesu.lifestreamer.utils.StreamerInfoFactory
import io.github.thibaultbee.streampack.core.streamers.infos.CameraStreamerConfigurationInfo
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig

class AudioSettingsFragment : BaseSettingsFragment() {
    override val preferencesRes = R.xml.settings_audio
    override val titleRes = R.string.audio

    private lateinit var streamerInfo: CameraStreamerConfigurationInfo
    private val profileLevelDisplay by lazy { ProfileLevelDisplay(requireContext()) }

    private val audioEnablePreference: SwitchPreference by lazy {
        findPreferenceSafe<SwitchPreference>(R.string.audio_enable_key) ?: error("audio_enable_key not found")
    }

    private val audioSettingsCategory: PreferenceCategory by lazy {
        findPreferenceSafe<PreferenceCategory>(R.string.audio_settings_key) ?: error("audio_settings_key not found")
    }

    private val audioEncoderListPreference: ListPreference by lazy {
        findPreferenceSafe<ListPreference>(R.string.audio_encoder_key) ?: error("audio_encoder_key not found")
    }

    private val audioChannelConfigListPreference: ListPreference by lazy {
        findPreferenceSafe<ListPreference>(R.string.audio_channel_config_key) ?: error("audio_channel_config_key not found")
    }

    private val audioBitrateListPreference: ListPreference by lazy {
        findPreferenceSafe<ListPreference>(R.string.audio_bitrate_key) ?: error("audio_bitrate_key not found")
    }

    private val audioSampleRateListPreference: ListPreference by lazy {
        findPreferenceSafe<ListPreference>(R.string.audio_sample_rate_key) ?: error("audio_sample_rate_key not found")
    }

    private val audioByteFormatListPreference: ListPreference by lazy {
        findPreferenceSafe<ListPreference>(R.string.audio_byte_format_key) ?: error("audio_byte_format_key not found")
    }

    private val audioProfileListPreference: ListPreference by lazy {
        findPreferenceSafe<ListPreference>(R.string.audio_profile_key) ?: error("audio_profile_key not found")
    }

    override fun onPreferencesInflated() {
        streamerInfo = StreamerInfoFactory(requireContext(), storedEndpointType()).build()
        loadAudioSettings()
    }

    private fun loadAudioSettings() {
        val supportedAudioEncoderName = mapOf(
            MediaFormat.MIMETYPE_AUDIO_AAC to getString(R.string.audio_encoder_aac),
            MediaFormat.MIMETYPE_AUDIO_OPUS to getString(R.string.audio_encoder_opus)
        )

        val supportedAudioEncoder = streamerInfo.audio.supportedEncoders
        val defaultAudioEncoder =
            if (supportedAudioEncoder.contains(MediaFormat.MIMETYPE_AUDIO_AAC)) {
                MediaFormat.MIMETYPE_AUDIO_AAC
            } else {
                supportedAudioEncoder.first()
            }
        audioEncoderListPreference.entryValues = supportedAudioEncoder.toTypedArray()
        audioEncoderListPreference.entries =
            supportedAudioEncoder.map { supportedAudioEncoderName[it] }.toTypedArray()
        if (audioEncoderListPreference.entry == null) {
            audioEncoderListPreference.value = defaultAudioEncoder
        }
        audioEncoderListPreference.setOnPreferenceChangeListener { _, newValue ->
            loadAudioSettings(newValue as String)
            true
        }

        loadAudioSettings(audioEncoderListPreference.value)
    }

    private fun loadAudioSettings(encoder: String) {
        audioSettingsCategory.isVisible = audioEnablePreference.isChecked
        audioEnablePreference.setOnPreferenceChangeListener { _, newValue ->
            audioSettingsCategory.isVisible = newValue as Boolean
            true
        }

        val inputChannelRange = streamerInfo.audio.getSupportedInputChannelRange(encoder)
        audioChannelConfigListPreference.entryValues.filter {
            inputChannelRange.contains(AudioConfig.getNumberOfChannels(it.toString().toInt()))
        }.toTypedArray().run {
            audioChannelConfigListPreference.entryValues = this
        }

        val bitrateRange = streamerInfo.audio.getSupportedBitrates(encoder)
        audioBitrateListPreference.entryValues.filter {
            bitrateRange.contains(
                it.toString().toInt()
            )
        }.toTypedArray().run {
            audioBitrateListPreference.entries =
                this.map { "${it.toString().toInt() / 1000} Kbps" }.toTypedArray()
            audioBitrateListPreference.entryValues = this
        }
        if (audioBitrateListPreference.entry == null) {
            audioBitrateListPreference.value = "128000"
        }

        val sampleRates = streamerInfo.audio.getSupportedSampleRates(encoder)
        audioSampleRateListPreference.entries =
            sampleRates.map { "${"%.1f".format(it.toString().toFloat() / 1000)} kHz" }
                .toTypedArray()
        audioSampleRateListPreference.entryValues = sampleRates.map { "$it" }.toTypedArray()
        if (audioSampleRateListPreference.entry == null) {
            audioSampleRateListPreference.value = when {
                sampleRates.contains(44100) -> "44100"
                sampleRates.contains(48000) -> "48000"
                else -> "${sampleRates.first()}"
            }
        }

        val supportedByteFormatName = mapOf(
            AudioFormat.ENCODING_PCM_8BIT to getString(R.string.audio_byte_format_8bit),
            AudioFormat.ENCODING_PCM_16BIT to getString(R.string.audio_byte_format_16bit),
            AudioFormat.ENCODING_PCM_FLOAT to getString(R.string.audio_byte_format_float)
        )
        val byteFormats = streamerInfo.audio.getSupportedByteFormats()
        audioByteFormatListPreference.entries =
            byteFormats.map { supportedByteFormatName[it] }.toTypedArray()
        audioByteFormatListPreference.entryValues = byteFormats.map { "$it" }.toTypedArray()
        if (audioByteFormatListPreference.entry == null) {
            audioByteFormatListPreference.value = "${AudioFormat.ENCODING_PCM_16BIT}"
        }

        audioProfileListPreference.isVisible = encoder == MediaFormat.MIMETYPE_AUDIO_AAC
        val profiles = streamerInfo.audio.getSupportedProfiles(encoder)
        audioProfileListPreference.entries = profiles.map {
            profileLevelDisplay.getProfileName(
                encoder, it
            )
        }.toTypedArray()
        audioProfileListPreference.entryValues = profiles.map { "$it" }.toTypedArray()
        if (audioProfileListPreference.entry == null) {
            val value = if (profiles.contains(MediaCodecInfo.CodecProfileLevel.AACObjectLC)) {
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            } else if (profiles.isNotEmpty()) {
                profiles.first()
            } else {
                0
            }
            audioProfileListPreference.value = value.toString()
        }
    }
}
