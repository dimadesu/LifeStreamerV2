package com.dimadesu.lifestreamer.ui.main.audio

import android.app.Application
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Range
import com.dimadesu.lifestreamer.ApplicationConstants
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.models.EndpointType
import com.dimadesu.lifestreamer.utils.ProfileLevelDisplay
import com.dimadesu.lifestreamer.utils.StreamerInfoFactory
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig
import java.util.Locale

/**
 * Audio settings currently selected plus the options the active encoder/endpoint supports.
 */
data class AudioSettingsState(
    val encoder: String = ApplicationConstants.Audio.defaultEncoder,
    val channelConfig: Int = ApplicationConstants.Audio.defaultChannelConfig,
    val bitrate: Int = ApplicationConstants.Audio.defaultBitrateInBps,
    val sampleRate: Int = ApplicationConstants.Audio.defaultSampleRate,
    val profile: Int = MediaCodecInfo.CodecProfileLevel.AACObjectLC,
    val availableEncoders: List<Pair<String, String>> = emptyList(),
    val availableChannelConfigs: List<Pair<Int, String>> = emptyList(),
    val availableBitrates: List<Pair<Int, String>> = emptyList(),
    val availableSampleRates: List<Pair<Int, String>> = emptyList(),
    val availableProfiles: List<Pair<Int, String>> = emptyList(),
    val isProfileVisible: Boolean = true
)

/**
 * Builds an [AudioSettingsState] from the stored [AudioConfig], correcting values the encoder
 * does not support.
 */
class AudioSettingsStateBuilder(private val application: Application) {

    private val profileLevelDisplay by lazy { ProfileLevelDisplay(application) }

    /**
     * @property shouldPersist true when the stored values were corrected and should be written back.
     */
    class Result(val state: AudioSettingsState, val shouldPersist: Boolean)

    fun build(config: AudioConfig?, endpointType: EndpointType): Result {
        val streamerInfo = StreamerInfoFactory(application, endpointType).build()
        val encoder = config?.mimeType ?: ApplicationConstants.Audio.defaultEncoder
        val storedChannelConfig = config?.channelConfig ?: ApplicationConstants.Audio.defaultChannelConfig
        val storedBitrate = config?.startBitrate ?: ApplicationConstants.Audio.defaultBitrateInBps
        val defaultSampleRate = ApplicationConstants.Audio.defaultSampleRateFor(encoder)
        val sampleRate = config?.sampleRate ?: defaultSampleRate
        val storedProfile = config?.profile ?: MediaCodecInfo.CodecProfileLevel.AACObjectLC
        // True when a supported-values query failed and guessed values are used instead
        var usedFallback = false

        val supportedEncoders = streamerInfo.audio.supportedEncoders
        val encoderNameMap = mapOf(
            MediaFormat.MIMETYPE_AUDIO_AAC to application.getString(R.string.audio_encoder_aac),
            MediaFormat.MIMETYPE_AUDIO_OPUS to application.getString(R.string.audio_encoder_opus)
        )
        val availableEncoders = supportedEncoders.map { it to (encoderNameMap[it] ?: it) }

        val inputChannelRange: Range<Int> = try {
            streamerInfo.audio.getSupportedInputChannelRange(encoder)
        } catch (_: Throwable) {
            usedFallback = true
            Range(1, 2)
        }
        val candidateChannels = ApplicationConstants.Audio.channelConfigOptions
        val availableChannelConfigs = candidateChannels.filter { (cfg, _) ->
            inputChannelRange.contains(AudioConfig.getNumberOfChannels(cfg))
        }.ifEmpty { candidateChannels }
        val supportedChannelConfigs = availableChannelConfigs.map { it.first }
        val channelConfig = if (supportedChannelConfigs.contains(storedChannelConfig)) {
            storedChannelConfig
        } else {
            supportedChannelConfigs.first()
        }

        val bitrateRange: Range<Int> = try {
            streamerInfo.audio.getSupportedBitrates(encoder)
        } catch (_: Throwable) {
            usedFallback = true
            ApplicationConstants.Audio.assumedBitrateRange
        }
        val candidateBitrates = ApplicationConstants.Audio.bitrateOptions
        val supportedBitrates = candidateBitrates.filter { bitrateRange.contains(it) }
            .ifEmpty { candidateBitrates }
        val availableBitrates = supportedBitrates.map { it to "${it / 1000} Kbps" }
        val bitrate = when {
            supportedBitrates.contains(storedBitrate) -> storedBitrate
            supportedBitrates.contains(ApplicationConstants.Audio.defaultBitrateInBps) ->
                ApplicationConstants.Audio.defaultBitrateInBps
            else -> supportedBitrates.first()
        }

        val sampleRates = try {
            streamerInfo.audio.getSupportedSampleRates(encoder)
        } catch (_: Throwable) {
            usedFallback = true
            if (encoder == MediaFormat.MIMETYPE_AUDIO_OPUS) listOf(ApplicationConstants.Audio.opusSampleRate)
            else ApplicationConstants.Audio.sampleRateFallbackOrder
        }
        val availableSampleRates = sampleRates.map {
            it to "${"%.1f".format(Locale.US, it.toFloat() / 1000)} kHz"
        }
        val actualSampleRate = if (sampleRates.contains(sampleRate)) {
            sampleRate
        } else {
            ApplicationConstants.Audio.sampleRateFallbackOrder.firstOrNull { sampleRates.contains(it) }
                ?: sampleRates.firstOrNull()
                ?: defaultSampleRate
        }

        val isProfileVisible = encoder == MediaFormat.MIMETYPE_AUDIO_AAC
        val supportedProfiles = if (isProfileVisible) {
            try {
                streamerInfo.audio.getSupportedProfiles(encoder)
            } catch (_: Throwable) {
                usedFallback = true
                listOf(MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
        } else {
            emptyList()
        }
        val availableProfiles = supportedProfiles.map { it to profileLevelDisplay.getProfileName(encoder, it) }
        val profile = when {
            !isProfileVisible || supportedProfiles.contains(storedProfile) -> storedProfile
            supportedProfiles.contains(MediaCodecInfo.CodecProfileLevel.AACObjectLC) ->
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            else -> supportedProfiles.firstOrNull() ?: 0
        }

        val shouldPersist = config != null && !usedFallback &&
            (bitrate != storedBitrate || actualSampleRate != config.sampleRate ||
                profile != storedProfile || channelConfig != storedChannelConfig)

        return Result(
            state = AudioSettingsState(
                encoder = encoder,
                channelConfig = channelConfig,
                bitrate = bitrate,
                sampleRate = actualSampleRate,
                profile = profile,
                availableEncoders = availableEncoders,
                availableChannelConfigs = availableChannelConfigs,
                availableBitrates = availableBitrates,
                availableSampleRates = availableSampleRates,
                availableProfiles = availableProfiles,
                isProfileVisible = isProfileVisible
            ),
            shouldPersist = shouldPersist
        )
    }
}
