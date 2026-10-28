package com.dimadesu.lifestreamer.ui.main.audio

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.util.Log
import com.dimadesu.lifestreamer.audio.BluetoothAudioSource
import com.dimadesu.lifestreamer.models.AudioDebugInfo
import io.github.thibaultbee.streampack.core.elements.sources.IMediaProjectionSource
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer

/**
 * Reads the live audio configuration from the streamer and from the system's active recording
 * configurations (what the HAL is really using).
 */
class AudioDebugInfoReader(private val application: Application) {

    /**
     * @return the current debug info, or null when the streamer or its audio config is unavailable.
     */
    fun read(streamer: SingleStreamer?): AudioDebugInfo? {
        if (streamer == null) {
            Log.d(TAG, "Cannot refresh audio debug info - streamer not available")
            return null
        }

        if (streamer.audioConfigFlow.value == null) {
            Log.d(TAG, "Cannot refresh audio debug info - audio config not available")
            return null
        }

        // Determine audio source type using type checking instead of class names
        val audioSource = streamer.audioInput?.sourceFlow?.value
        val audioSourceType = when {
            audioSource is BluetoothAudioSource -> "BLUETOOTH"
            audioSource is IMediaProjectionSource -> "MEDIA PROJECTION"
            audioSource != null -> "MICROPHONE" // Any other audio source is microphone-based
            else -> "UNKNOWN"
        }

        val configs = try {
            val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.activeRecordingConfigurations
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get active recording configurations: ${e.message}")
            emptyList()
        }
        // The first active recording is assumed to be this app's session
        val ourConfig = configs.firstOrNull()

        val actualSystemSource = ourConfig?.let { config ->
            val clientName = audioSourceName(config.clientAudioSource)
            val actualName = audioSourceName(config.audioSource)
            if (config.clientAudioSource != config.audioSource) {
                "Requested: $clientName → Actual: $actualName"
            } else {
                actualName
            }
        }

        var hasNS: Boolean? = null
        var hasAEC: Boolean? = null
        var hasAGC: Boolean? = null
        var actualSampleRate: Int? = null
        var actualChannelMask: Int? = null
        var actualFormat: Int? = null
        var hasActiveRecording = false

        if (ourConfig != null) {
            try {
                val systemFormat = ourConfig.format
                actualSampleRate = systemFormat.sampleRate
                actualChannelMask = systemFormat.channelMask
                actualFormat = systemFormat.encoding

                // Prefer effects (what's actually running), fall back to clientEffects (requested)
                val effectsToCheck = try {
                    ourConfig.effects
                } catch (e: Exception) {
                    ourConfig.clientEffects
                }
                hasNS = effectsToCheck.any { it.type == AudioEffect.EFFECT_TYPE_NS }
                hasAEC = effectsToCheck.any { it.type == AudioEffect.EFFECT_TYPE_AEC }
                hasAGC = effectsToCheck.any { it.type == AudioEffect.EFFECT_TYPE_AGC }
                hasActiveRecording = true

                Log.d(
                    TAG,
                    "Audio config from system: SR=$actualSampleRate, CH=$actualChannelMask, " +
                        "FMT=$actualFormat, Effects: NS=$hasNS, AEC=$hasAEC, AGC=$hasAGC " +
                        "(from ${effectsToCheck.size} effects)"
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get audio effects and format: ${e.message}")
                hasNS = null
                hasAEC = null
                hasAGC = null
                actualSampleRate = null
                actualChannelMask = null
                actualFormat = null
                hasActiveRecording = false
            }
        } else {
            Log.d(TAG, "No active recording configurations found")
        }

        return AudioDebugInfo(
            audioSource = audioSourceType,
            actualSystemSource = actualSystemSource,
            sampleRate = actualSampleRate,
            bitFormat = actualFormat,
            channelConfig = actualChannelMask,
            noiseSuppression = hasNS,
            acousticEchoCanceler = hasAEC,
            automaticGainControl = hasAGC,
            hasActiveRecording = hasActiveRecording
        )
    }

    companion object {
        private const val TAG = "AudioDebugInfoReader"

        /**
         * Converts a MediaRecorder.AudioSource constant to a readable name.
         */
        fun audioSourceName(source: Int): String = when (source) {
            0 -> "DEFAULT"
            1 -> "MIC"
            5 -> "CAMCORDER"
            6 -> "VOICE_RECOGNITION"
            7 -> "VOICE_COMMUNICATION"
            9 -> "UNPROCESSED"
            10 -> "VOICE_PERFORMANCE"
            else -> "UNKNOWN ($source)"
        }
    }
}
