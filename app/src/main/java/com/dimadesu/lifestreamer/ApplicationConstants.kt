package com.dimadesu.lifestreamer

import android.content.pm.ActivityInfo
import android.media.AudioFormat
import android.media.MediaFormat
import android.util.Range
import android.util.Size
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig

/**
 * Application configuration.
 */
object ApplicationConstants {
    /**
     * Default application orientation when not streaming.
     * Uses SCREEN_ORIENTATION_FULL_SENSOR to follow device rotation naturally in all four orientations
     * (portrait, landscape left, portrait upside down, landscape right).
     * When streaming starts, orientation is locked to current position to prevent
     * disorienting rotations mid-stream.
     */
    const val supportedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

    /**
     * User preferences file name.
     */
    const val userPrefName = "settings"

    /**
     * Video default configuration.
     */
    object Video {
        private val defaultVideoConfig = VideoConfig()

        /**
         * Default video encoder.
         */
        val defaultEncoder = defaultVideoConfig.mimeType

        /**
         * Default FPS for live stream.
         */
        val defaultFps = defaultVideoConfig.fps

        /**
         * Default bitrate for live stream.
         */
        val defaultBitrateInBps = defaultVideoConfig.startBitrate

        /**
         * Default resolution for live stream.
         */
        val defaultResolution = defaultVideoConfig.resolution

        /**
         * Resolutions offered in the video settings when the camera-supported list is not used.
         */
        val standardResolutions = listOf(
            Size(4032, 3024),
            Size(3840, 2160),
            Size(2560, 1440),
            Size(1920, 1440),
            Size(1920, 1080),
            Size(1664, 936),
            Size(1280, 720),
            Size(1024, 768),
            Size(960, 540),
            Size(854, 480),
            Size(640, 360),
            Size(426, 240)
        )

        /**
         * Bitrates offered in the video settings, in kbps.
         */
        val bitrateOptionsInKbps = (500..10_000 step 500).toList()
    }

    /**
     * Audio default configuration.
     */
    object Audio {
        private val defaultAudioConfig = AudioConfig()

        /**
         * Default audio encoder.
         */
        val defaultEncoder = defaultAudioConfig.mimeType

        /**
         * Default channel config.
         */
        val defaultChannelConfig = defaultAudioConfig.channelConfig

        /**
         * Default start bitrate.
         */
        val defaultBitrateInBps = defaultAudioConfig.startBitrate

        /**
         * Default sample rate.
         */
        val defaultSampleRate = defaultAudioConfig.sampleRate

        /**
         * Default byte format.
         */
        val defaultByteFormat = defaultAudioConfig.byteFormat

        /**
         * Default sample rate for [mimeType]. Opus is always 48 kHz.
         */
        fun defaultSampleRateFor(mimeType: String) =
            if (mimeType == MediaFormat.MIMETYPE_AUDIO_OPUS) opusSampleRate else defaultSampleRate

        /**
         * Default and fallback Opus sample rate.
         */
        const val opusSampleRate = 48_000

        /**
         * Sample rates supported by Opus.
         */
        val opusSupportedSampleRates = listOf(8_000, 12_000, 16_000, 24_000, 48_000)

        /**
         * Sample rates to fall back to, in order, when the stored one is not supported.
         */
        val sampleRateFallbackOrder = listOf(44_100, 48_000)

        /**
         * Bitrates offered in the audio settings.
         */
        val bitrateOptions = listOf(64_000, 96_000, 128_000, 160_000, 192_000, 256_000, 320_000)

        /**
         * Bitrates to fall back to, in order, when the stored one is not supported.
         */
        val bitrateFallbackOrder = listOf(defaultBitrateInBps, 96_000, 64_000, 160_000, 192_000)

        /**
         * Bitrate range assumed when the supported range cannot be queried.
         */
        val assumedBitrateRange = Range(32_000, 320_000)

        /**
         * Channel configs offered in the audio settings, with their labels.
         */
        val channelConfigOptions = listOf(
            AudioFormat.CHANNEL_IN_STEREO to "Stereo",
            AudioFormat.CHANNEL_IN_MONO to "Mono"
        )
    }

    object Endpoint {
        const val defaultTargetBitrateKbps = 5000
        const val defaultMinBitrateKbps = 300
    }

    object Connection {
        /**
         * Default latency in milliseconds.
         */
        const val defaultUri = "srt://192.168.1.11:9998"
    }
}
