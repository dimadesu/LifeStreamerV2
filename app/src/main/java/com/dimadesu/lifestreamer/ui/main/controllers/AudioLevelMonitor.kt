package com.dimadesu.lifestreamer.ui.main.controllers

import android.util.Log
import com.dimadesu.lifestreamer.audio.AudioLevel
import com.dimadesu.lifestreamer.audio.VuMeterEffect
import io.github.thibaultbee.streampack.core.elements.encoders.AudioCodecConfig
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Controller responsible for attaching VuMeterEffect to streamer's audio processor,
 * tracking silence watchdog, and emitting real-time AudioLevel readings.
 */
class AudioLevelMonitor(
    private val scope: CoroutineScope,
    private val streamerProvider: () -> SingleStreamer?,
    private val isUiInForegroundProvider: () -> Boolean
) {
    companion object {
        private const val TAG = "AudioLevelMonitor"
    }

    private val _audioLevelFlow = MutableStateFlow(AudioLevel.SILENT)
    val audioLevelFlow: StateFlow<AudioLevel> = _audioLevelFlow.asStateFlow()

    private var audioConfigObserverJob: Job? = null
    private var startCaptureJob: Job? = null
    private var stopCaptureJob: Job? = null
    private var vuMeterWatchdogJob: Job? = null
    private var vuMeterEffect: VuMeterEffect? = null
    @Volatile private var lastAudioLevelTimeMs = 0L

    val isMonitoringActive: Boolean
        get() = vuMeterEffect != null

    fun setupAudioLevelMonitoring() {
        if (!isUiInForegroundProvider()) {
            Log.i(TAG, "Skipping setupAudioLevelMonitoring because UI is in background")
            return
        }
        val streamer = streamerProvider() ?: return
        val audioProcessor = streamer.audioInput?.processor ?: run {
            Log.w(TAG, "Audio processor not available for level monitoring")
            return
        }

        // Remove any previous effect
        vuMeterEffect?.let { audioProcessor.remove(it) }
        vuMeterWatchdogJob?.cancel()
        vuMeterWatchdogJob = null
        lastAudioLevelTimeMs = 0L

        // Create new VuMeterEffect using IConsumerAudioEffect plugin
        val effect = VuMeterEffect { levels ->
            val isEffectivelySilent = levels.rms < 0.0001f && levels.peak < 0.0001f
                    && levels.rmsRight < 0.0001f && levels.peakRight < 0.0001f
            if (!isEffectivelySilent) {
                lastAudioLevelTimeMs = System.currentTimeMillis()
            }
            _audioLevelFlow.value = levels
        }
        vuMeterEffect = effect
        audioProcessor.add(effect)

        // Watchdog: reset to SILENT if no non-silent audio frames arrive for 1500ms
        vuMeterWatchdogJob = scope.launch {
            while (true) {
                delay(200)
                val lastTime = lastAudioLevelTimeMs
                if (lastTime > 0 && System.currentTimeMillis() - lastTime > 1500) {
                    lastAudioLevelTimeMs = 0L
                    _audioLevelFlow.value = AudioLevel.SILENT
                }
            }
        }

        // Observe streamer's audioConfigFlow for channel count changes
        audioConfigObserverJob?.cancel()
        audioConfigObserverJob = scope.launch {
            streamer.audioConfigFlow.collect { audioConfig ->
                if (audioConfig != null) {
                    try {
                        val channelCount = AudioCodecConfig.getNumberOfChannels(audioConfig.channelConfig)
                        effect.channelCount = channelCount
                        Log.i(TAG, "Audio level monitoring: channelCount=$channelCount")
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to get channel count: ${t.message}")
                        effect.channelCount = 1
                    }
                }
            }
        }

        // Start capture once audio source is ready AND configured
        startCaptureJob?.cancel()
        startCaptureJob = scope.launch {
            try {
                val audioInput = streamer.audioInput ?: return@launch
                audioInput.sourceFlow.filterNotNull().first()
                streamer.audioConfigFlow.filterNotNull().first()
                audioInput.startCapture()
                Log.i(TAG, "Audio capture started for VU meter")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to start audio capture: ${t.message}")
            }
        }

        Log.i(TAG, "Audio level monitoring enabled")
    }

    fun disableAudioLevelMonitoring() {
        try {
            audioConfigObserverJob?.cancel()
            audioConfigObserverJob = null
            startCaptureJob?.cancel()
            startCaptureJob = null
            vuMeterWatchdogJob?.cancel()
            vuMeterWatchdogJob = null
            lastAudioLevelTimeMs = 0L

            val streamer = streamerProvider()
            vuMeterEffect?.let { effect ->
                streamer?.audioInput?.processor?.remove(effect)
                effect.close()
            }
            vuMeterEffect = null
            _audioLevelFlow.value = AudioLevel.SILENT

            val audioInput = streamer?.audioInput
            stopCaptureJob?.cancel()
            stopCaptureJob = scope.launch {
                try {
                    audioInput?.stopCapture()
                    Log.i(TAG, "Audio capture stopped for VU meter")
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to stop audio capture: ${t.message}")
                }
            }
        } catch (_: Throwable) {}
    }
}
