package com.dimadesu.lifestreamer.ui.main.audio

import android.app.Application
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dimadesu.lifestreamer.ApplicationConstants
import com.dimadesu.lifestreamer.audio.ConditionalAudioSourceFactory
import com.dimadesu.lifestreamer.data.storage.DataStoreRepository
import com.dimadesu.lifestreamer.models.AudioDebugInfo
import com.dimadesu.lifestreamer.models.EndpointType
import com.dimadesu.lifestreamer.services.CameraStreamerService
import com.dimadesu.lifestreamer.ui.main.audio.AudioDebugInfoReader.Companion.audioSourceName
import com.dimadesu.lifestreamer.utils.StreamerInfoFactory
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the state and actions behind the audio settings / source drawer:
 * - drawer visibility
 * - audio encoder settings (encoder, channels, bitrate, sample rate, profile)
 * - audio source type selection
 * - live audio debug stats while the drawer is open
 *
 * Everything that depends on the rest of the streaming screen goes through [Host].
 */
class AudioDrawerController(
    private val application: Application,
    private val storageRepository: DataStoreRepository,
    private val scope: CoroutineScope,
    private val host: Host
) {
    /**
     * What this controller needs from the screen that owns it.
     */
    interface Host {
        val streamer: SingleStreamer?
        val service: CameraStreamerService?
        val isBluetoothMicActive: Boolean
        val isSystemAudioActive: Boolean
        val isRtmpSourceActive: Boolean
        val isMonitorAudioOn: Boolean

        /** Called when the audio drawer opens so the host can close conflicting drawers. */
        fun onAudioDrawerOpened()

        fun onError(message: String)
    }

    private val settingsStateBuilder = AudioSettingsStateBuilder(application)
    private val debugInfoReader = AudioDebugInfoReader(application)

    private val _isOpen = MutableLiveData(false)
    val isOpen: LiveData<Boolean> = _isOpen

    private val _selectedSourceType = MutableLiveData(MediaRecorder.AudioSource.DEFAULT)
    val selectedSourceType: LiveData<Int> = _selectedSourceType

    private val _settingsState = MutableLiveData<AudioSettingsState>()
    val settingsState: LiveData<AudioSettingsState> = _settingsState

    private val _debugInfo = MutableLiveData<AudioDebugInfo?>()
    val debugInfo: LiveData<AudioDebugInfo?> = _debugInfo

    private var debugRefreshJob: Job? = null

    /**
     * Starts observing DataStore for audio settings changes (e.g. made in the Settings screen).
     * Call once the host is ready to answer [Host] queries.
     */
    fun start() {
        // Apply audio source type changes from DataStore in real time
        scope.launch {
            var isFirstEmission = true
            storageRepository.audioSourceTypeFlow
                .distinctUntilChanged()
                .collect { newSourceType ->
                    if (isFirstEmission) {
                        // First emission is just the initial value - update LiveData but don't apply
                        _selectedSourceType.value = newSourceType
                        Log.d(TAG, "Initial audio source type from DataStore: ${audioSourceName(newSourceType)}")
                        isFirstEmission = false
                    } else if (host.streamer != null) {
                        // Subsequent emissions mean the value changed (e.g., in Settings)
                        val currentValue = _selectedSourceType.value
                        if (currentValue != newSourceType) {
                            Log.i(
                                TAG,
                                "Audio source changed in DataStore: ${audioSourceName(currentValue ?: -1)} -> ${audioSourceName(newSourceType)}"
                            )
                            _selectedSourceType.value = newSourceType
                            applySelectedSource()
                        }
                    }
                }
        }

        scope.launch {
            combine(
                storageRepository.audioConfigFlow,
                storageRepository.endpointTypeFlow
            ) { config, endpointType ->
                config to endpointType
            }.collect { (config, endpointType) ->
                updateSettingsState(config, endpointType)
            }
        }
    }

    fun toggleDrawer() {
        setDrawerOpen(!(_isOpen.value ?: false))
    }

    /**
     * Sets the drawer visibility.
     * When opened, closes any conflicting drawers and starts periodic audio debug stats refresh.
     */
    fun setDrawerOpen(open: Boolean) {
        _isOpen.value = open
        if (open) {
            host.onAudioDrawerOpened()

            // Load current audio source type from DataStore when opening drawer
            scope.launch {
                _selectedSourceType.value = storageRepository.audioSourceTypeFlow.first()
                Log.d(TAG, "Loaded audio source from DataStore: source=${_selectedSourceType.value}")
            }

            // Refresh audio settings state when opening drawer
            scope.launch {
                val config = storageRepository.audioConfigFlow.first()
                val endpointType = storageRepository.endpointTypeFlow.first()
                updateSettingsState(config, endpointType)
            }

            // Refresh immediately when showing the drawer, then every 2 seconds
            refreshDebugInfo()
            debugRefreshJob?.cancel()
            debugRefreshJob = scope.launch {
                while (isActive) {
                    delay(2000)
                    refreshDebugInfo()
                }
            }
        } else {
            debugRefreshJob?.cancel()
            debugRefreshJob = null
            _debugInfo.postValue(null)
        }
    }

    /**
     * Reads current audio configuration from the streamer. Can be called manually when audio
     * configuration changes (e.g., when plugging in a USB mic).
     */
    fun refreshDebugInfo() {
        try {
            val info = debugInfoReader.read(host.streamer)
            _debugInfo.postValue(info)
            Log.d(TAG, "Audio debug info refreshed: $info")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh audio debug info: ${e.message}", e)
            _debugInfo.postValue(null)
        }
    }

    fun setSelectedSourceType(sourceType: Int) {
        _selectedSourceType.value = sourceType
    }

    /**
     * Applies the selected audio source type: saves it to DataStore and recreates the audio
     * source with the selected MediaRecorder.AudioSource constant.
     */
    fun applySelectedSource() {
        val currentStreamer = host.streamer
        if (currentStreamer == null) {
            Log.w(TAG, "Cannot apply audio source - streamer not available")
            return
        }

        scope.launch {
            try {
                val sourceType = _selectedSourceType.value ?: MediaRecorder.AudioSource.CAMCORDER
                Log.i(TAG, "Applying audio source type: ${audioSourceName(sourceType)} ($sourceType)")

                storageRepository.saveAudioSourceType(sourceType)
                Log.d(TAG, "Saved audio source type to DataStore")

                // Only switch to built-in mic if BT mic, SYS AUDIO, and RTMP source are all inactive.
                // When BT, MP, or RTMP is active, the preference is saved and will apply next time
                // ConditionalAudioSourceFactory is used (e.g. after SYS AUDIO is toggled off).
                val btActive = host.isBluetoothMicActive
                val sysAudioActive = host.isSystemAudioActive
                val rtmpActive = host.isRtmpSourceActive
                if (btActive || sysAudioActive || rtmpActive) {
                    Log.i(
                        TAG,
                        "Audio source preference saved but not applied — " +
                            "BT=$btActive, SYS=$sysAudioActive, RTMP=$rtmpActive override is active"
                    )
                } else {
                    currentStreamer.setAudioSource(ConditionalAudioSourceFactory())
                    Log.i(TAG, "Audio source changed to: ${audioSourceName(sourceType)}")
                }

                // If audio monitoring is enabled and we actually switched the source, restart passthrough
                if (!btActive && !sysAudioActive && !rtmpActive && host.isMonitorAudioOn) {
                    Log.i(TAG, "Restarting audio passthrough with new settings")
                    host.service?.stopAudioPassthrough()
                    delay(100) // Small delay to ensure clean restart
                    host.service?.startAudioPassthrough()
                }

                // Refresh debug info only if the drawer is visible
                if (_isOpen.value == true) {
                    delay(200)
                    refreshDebugInfo()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying audio source: ${e.message}", e)
                host.onError("Failed to apply audio source: ${e.message}")
            }
        }
    }

    /**
     * Reloads the audio source type from DataStore and applies it if it differs from the current
     * value. Called on fragment resume to pick up changes made in the Settings activity.
     */
    fun reloadAndApplyIfChanged() {
        scope.launch {
            try {
                val storedSourceType = storageRepository.audioSourceTypeFlow.first()
                val currentSourceType = _selectedSourceType.value

                if (storedSourceType != currentSourceType) {
                    Log.i(
                        TAG,
                        "Audio source changed externally, reloading and applying. " +
                            "Source: $currentSourceType->$storedSourceType"
                    )
                    _selectedSourceType.value = storedSourceType
                    applySelectedSource()
                } else {
                    Log.d(TAG, "Audio settings unchanged, no reload needed")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to reload audio settings: ${e.message}")
            }
        }
    }

    fun updateSettingsState(config: AudioConfig?, endpointType: EndpointType) {
        try {
            val result = settingsStateBuilder.build(config, endpointType)
            if (result.shouldPersist) {
                // Persist corrected values so the stored config matches what is shown and used
                val state = result.state
                scope.launch {
                    storageRepository.saveAudioEncoderAndConfig(
                        encoder = state.encoder,
                        sampleRate = state.sampleRate,
                        bitrate = state.bitrate,
                        profile = state.profile,
                        channelConfig = state.channelConfig
                    )
                }
            }
            _settingsState.postValue(result.state)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute audio settings state", e)
        }
    }

    fun setEncoder(encoder: String) {
        scope.launch {
            try {
                val endpointType = storageRepository.endpointTypeFlow.first()
                val streamerInfo = StreamerInfoFactory(application, endpointType).build()
                val bitrateRange = streamerInfo.audio.getSupportedBitrates(encoder)
                val currentBitrate = _settingsState.value?.bitrate ?: ApplicationConstants.Audio.defaultBitrateInBps
                val targetBitrate = if (!bitrateRange.contains(currentBitrate)) {
                    ApplicationConstants.Audio.bitrateFallbackOrder.firstOrNull { bitrateRange.contains(it) }
                        ?: bitrateRange.lower
                } else {
                    currentBitrate
                }

                val sampleRates = streamerInfo.audio.getSupportedSampleRates(encoder)
                val currentSampleRate = _settingsState.value?.sampleRate
                    ?: ApplicationConstants.Audio.defaultSampleRate
                val targetSampleRate = if (!sampleRates.contains(currentSampleRate)) {
                    when {
                        encoder == MediaFormat.MIMETYPE_AUDIO_OPUS &&
                            sampleRates.contains(ApplicationConstants.Audio.opusSampleRate) ->
                            ApplicationConstants.Audio.opusSampleRate
                        else -> ApplicationConstants.Audio.sampleRateFallbackOrder.firstOrNull { sampleRates.contains(it) }
                            ?: sampleRates.firstOrNull()
                            ?: ApplicationConstants.Audio.defaultSampleRateFor(encoder)
                    }
                } else {
                    currentSampleRate
                }

                val targetProfile = if (encoder == MediaFormat.MIMETYPE_AUDIO_AAC) {
                    val profiles = streamerInfo.audio.getSupportedProfiles(encoder)
                    val currentProfile = _settingsState.value?.profile ?: MediaCodecInfo.CodecProfileLevel.AACObjectLC
                    if (!profiles.contains(currentProfile)) {
                        if (profiles.contains(MediaCodecInfo.CodecProfileLevel.AACObjectLC)) {
                            MediaCodecInfo.CodecProfileLevel.AACObjectLC
                        } else if (profiles.isNotEmpty()) {
                            profiles.first()
                        } else {
                            0
                        }
                    } else {
                        currentProfile
                    }
                } else {
                    0
                }

                storageRepository.saveAudioEncoderAndConfig(
                    encoder = encoder,
                    sampleRate = targetSampleRate,
                    bitrate = targetBitrate,
                    profile = targetProfile
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error validating audio settings for new encoder: ${e.message}")
                storageRepository.saveAudioEncoder(encoder)
            }
        }
    }

    fun setChannelConfig(channelConfig: Int) {
        scope.launch { storageRepository.saveAudioChannelConfig(channelConfig) }
    }

    fun setBitrate(bitrate: Int) {
        scope.launch { storageRepository.saveAudioBitrate(bitrate) }
    }

    fun setSampleRate(sampleRate: Int) {
        scope.launch { storageRepository.saveAudioSampleRate(sampleRate) }
    }

    fun setProfile(profile: Int) {
        scope.launch { storageRepository.saveAudioProfile(profile) }
    }

    private companion object {
        const val TAG = "AudioDrawerController"
    }
}
