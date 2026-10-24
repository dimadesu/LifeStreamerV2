package com.dimadesu.lifestreamer.ui.main.controllers

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.audiofx.AudioEffect
import android.util.Log
import com.dimadesu.lifestreamer.audio.BluetoothAudioSource
import com.dimadesu.lifestreamer.audio.ConditionalAudioSourceFactory
import com.dimadesu.lifestreamer.data.storage.DataStoreRepository
import com.dimadesu.lifestreamer.models.AudioDebugInfo
import com.dimadesu.lifestreamer.services.CameraStreamerService
import io.github.thibaultbee.streampack.core.elements.sources.IMediaProjectionSource
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Controller responsible for inspecting system audio capture state (sample rate, bit format,
 * channel mask, HAL hardware routing, NS/AEC/AGC effects) and switching input audio source.
 */
class AudioDebugController(
    private val application: Application,
    private val scope: CoroutineScope,
    private val storageRepository: DataStoreRepository,
    private val streamerProvider: () -> SingleStreamer?,
    private val serviceProvider: () -> CameraStreamerService?,
    private val isBluetoothActiveProvider: () -> Boolean,
    private val isSystemAudioActiveProvider: () -> Boolean,
    private val isRtmpActiveProvider: () -> Boolean,
    private val isMonitorAudioOnProvider: () -> Boolean,
    private val onErrorMessage: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "AudioDebug"

        fun getAudioSourceName(source: Int): String = when (source) {
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

    private val _audioDebugInfo = MutableStateFlow<AudioDebugInfo?>(null)
    val audioDebugInfo: StateFlow<AudioDebugInfo?> = _audioDebugInfo.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val _selectedAudioSourceType = MutableStateFlow(MediaRecorder.AudioSource.DEFAULT)
    val selectedAudioSourceType: StateFlow<Int> = _selectedAudioSourceType.asStateFlow()

    private var audioDebugRefreshJob: Job? = null

    fun setSelectedAudioSourceType(sourceType: Int) {
        _selectedAudioSourceType.value = sourceType
    }

    fun toggleAudioDebugOverlay() {
        val newValue = !_isOverlayVisible.value
        _isOverlayVisible.value = newValue
        if (newValue) {
            scope.launch {
                _selectedAudioSourceType.value = storageRepository.audioSourceTypeFlow.first()
                Log.d(TAG, "Loaded audio source from DataStore: source=${_selectedAudioSourceType.value}")
            }

            refreshAudioDebugInfo()
            audioDebugRefreshJob?.cancel()
            audioDebugRefreshJob = scope.launch {
                while (isActive) {
                    delay(2000)
                    refreshAudioDebugInfo()
                }
            }
        } else {
            audioDebugRefreshJob?.cancel()
            audioDebugRefreshJob = null
            _audioDebugInfo.value = null
        }
    }

    fun refreshAudioDebugInfo() {
        scope.launch {
            try {
                val currentStreamer = streamerProvider()
                if (currentStreamer == null) {
                    _audioDebugInfo.value = null
                    Log.d(TAG, "Cannot refresh audio debug info - streamer not available")
                    return@launch
                }

                val audioConfig = currentStreamer.audioConfigFlow.value
                if (audioConfig == null) {
                    _audioDebugInfo.value = null
                    Log.d(TAG, "Cannot refresh audio debug info - audio config not available")
                    return@launch
                }

                val audioSource = currentStreamer.audioInput?.sourceFlow?.value
                val audioSourceType = when {
                    audioSource is BluetoothAudioSource -> "BLUETOOTH"
                    audioSource is IMediaProjectionSource -> "MEDIA PROJECTION"
                    audioSource != null -> "MICROPHONE"
                    else -> "UNKNOWN"
                }

                val actualSystemSource = try {
                    val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val configs = audioManager.activeRecordingConfigurations

                    if (configs.isNotEmpty()) {
                        val ourConfig = configs.firstOrNull { true }
                        ourConfig?.let { config ->
                            val clientSource = config.clientAudioSource
                            val actualSource = config.audioSource
                            val clientName = getAudioSourceName(clientSource)
                            val actualName = getAudioSourceName(actualSource)

                            if (clientSource != actualSource) {
                                "Requested: $clientName → Actual: $actualName"
                            } else {
                                actualName
                            }
                        }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get actual audio source: ${e.message}")
                    null
                }

                val systemAudioInfo = try {
                    val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val configs = audioManager.activeRecordingConfigurations

                    if (configs.isNotEmpty()) {
                        val ourConfig = configs.first()
                        val systemFormat = ourConfig.format
                        val systemSampleRate = systemFormat.sampleRate
                        val systemChannelMask = systemFormat.channelMask
                        val systemEncoding = systemFormat.encoding

                        val effectsToCheck = try {
                            ourConfig.effects
                        } catch (e: Exception) {
                            ourConfig.clientEffects
                        }

                        val ns = effectsToCheck.any { it.type == AudioEffect.EFFECT_TYPE_NS }
                        val aec = effectsToCheck.any { it.type == AudioEffect.EFFECT_TYPE_AEC }
                        val agc = effectsToCheck.any { it.type == AudioEffect.EFFECT_TYPE_AGC }

                        Log.d(TAG, "Audio config from system: SR=$systemSampleRate, CH=$systemChannelMask, FMT=$systemEncoding, Effects: NS=$ns, AEC=$aec, AGC=$agc (from ${effectsToCheck.size} effects)")

                        Triple(ns, aec, agc) to Triple(systemSampleRate, systemChannelMask, systemEncoding) to true
                    } else {
                        Log.d(TAG, "No active recording configurations found")
                        Triple<Boolean?, Boolean?, Boolean?>(null, null, null) to Triple<Int?, Int?, Int?>(null, null, null) to false
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get audio effects and format: ${e.message}")
                    Triple<Boolean?, Boolean?, Boolean?>(null, null, null) to Triple<Int?, Int?, Int?>(null, null, null) to false
                }

                val (effectsAndFormat, hasActiveRecording) = systemAudioInfo
                val (effects, format) = effectsAndFormat
                val (hasNS, hasAEC, hasAGC) = effects
                val (actualSampleRate, actualChannelMask, actualFormat) = format

                val debugInfo = AudioDebugInfo(
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

                _audioDebugInfo.value = debugInfo
                Log.d(TAG, "Audio debug info refreshed: $debugInfo")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to refresh audio debug info: ${e.message}", e)
                _audioDebugInfo.value = null
            }
        }
    }

    fun applySelectedAudioSource() {
        val currentStreamer = streamerProvider()
        if (currentStreamer == null) {
            Log.w(TAG, "Cannot apply audio source - streamer not available")
            return
        }

        scope.launch {
            try {
                val sourceType = _selectedAudioSourceType.value
                Log.i(TAG, "Applying audio source type: ${getAudioSourceName(sourceType)} ($sourceType)")

                storageRepository.saveAudioSourceType(sourceType)
                Log.d(TAG, "Saved audio source type to DataStore")

                val btActive = isBluetoothActiveProvider()
                val sysAudioActive = isSystemAudioActiveProvider()
                val rtmpActive = isRtmpActiveProvider()
                if (btActive || sysAudioActive || rtmpActive) {
                    Log.i(TAG, "Audio source preference saved but not applied — " +
                            "BT=$btActive, SYS=$sysAudioActive, RTMP=$rtmpActive override is active")
                } else {
                    currentStreamer.setAudioSource(ConditionalAudioSourceFactory())
                    Log.i(TAG, "Audio source changed to: ${getAudioSourceName(sourceType)}")
                }

                if (!btActive && !sysAudioActive && !rtmpActive && isMonitorAudioOnProvider()) {
                    Log.i(TAG, "Restarting audio passthrough with new settings")
                    val service = serviceProvider()
                    service?.stopAudioPassthrough()
                    delay(100)
                    service?.startAudioPassthrough()
                }

                if (_isOverlayVisible.value) {
                    delay(200)
                    refreshAudioDebugInfo()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying audio source: ${e.message}", e)
                onErrorMessage("Failed to apply audio source: ${e.message}")
            }
        }
    }

    fun reloadAndApplyAudioSettingsIfChanged() {
        scope.launch {
            try {
                val storedSourceType = storageRepository.audioSourceTypeFlow.first()
                val currentSourceType = _selectedAudioSourceType.value

                if (storedSourceType != currentSourceType) {
                    Log.i(TAG, "Audio source changed externally, reloading and applying. Source: $currentSourceType->$storedSourceType")
                    _selectedAudioSourceType.value = storedSourceType
                    applySelectedAudioSource()
                } else {
                    Log.d(TAG, "Audio settings unchanged, no reload needed")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to reload audio settings: ${e.message}")
            }
        }
    }

    fun cleanup() {
        audioDebugRefreshJob?.cancel()
        audioDebugRefreshJob = null
    }
}
