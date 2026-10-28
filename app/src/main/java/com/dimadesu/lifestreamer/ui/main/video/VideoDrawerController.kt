package com.dimadesu.lifestreamer.ui.main.video

import android.app.Application
import android.util.Log
import android.util.Size
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dimadesu.lifestreamer.data.storage.DataStoreRepository
import com.dimadesu.lifestreamer.data.storage.StoredVideoSettings
import com.dimadesu.lifestreamer.models.EndpointType
import com.dimadesu.lifestreamer.models.StreamOrientation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the state and actions behind the video settings drawer:
 * - drawer visibility
 * - video settings (orientation, encoder, resolution, FPS, bitrate, profile, level)
 *
 * Every change is validated against what the encoder/cameras support and saved to DataStore in
 * one write. The stream configuration picks it up from there.
 *
 * Everything that depends on the rest of the streaming screen goes through [Host].
 */
class VideoDrawerController(
    application: Application,
    private val storageRepository: DataStoreRepository,
    private val scope: CoroutineScope,
    private val host: Host
) {
    /**
     * What this controller needs from the screen that owns it.
     */
    interface Host {
        /** Called when the video drawer opens so the host can close conflicting drawers. */
        fun onVideoDrawerOpened()
    }

    private val settingsStateBuilder = VideoSettingsStateBuilder(application)

    private val _isOpen = MutableLiveData(false)
    val isOpen: LiveData<Boolean> = _isOpen

    private val _settingsState = MutableLiveData(VideoSettingsState())
    val settingsState: LiveData<VideoSettingsState> = _settingsState

    /**
     * Starts observing DataStore so the state follows the stored settings and the endpoint type.
     */
    fun start() {
        scope.launch {
            combine(
                storageRepository.storedVideoSettingsFlow,
                storageRepository.endpointTypeFlow
            ) { stored, endpointType ->
                stored to endpointType
            }.collect { (stored, endpointType) ->
                updateSettingsState(stored, endpointType)
            }
        }
    }

    fun toggleDrawer() {
        setDrawerOpen(!(_isOpen.value ?: false))
    }

    fun setDrawerOpen(open: Boolean) {
        _isOpen.value = open
        if (open) {
            host.onVideoDrawerOpened()
            // Camera or encoder capabilities may have changed since the state was last built
            scope.launch {
                updateSettingsState(
                    storageRepository.storedVideoSettingsFlow.first(),
                    storageRepository.endpointTypeFlow.first()
                )
            }
        }
    }

    private suspend fun updateSettingsState(stored: StoredVideoSettings, endpointType: EndpointType) {
        try {
            val result = withContext(Dispatchers.Default) {
                settingsStateBuilder.build(stored, endpointType)
            }
            if (result.shouldPersist) {
                // Persist corrected values so the stored config matches what is shown and used
                storageRepository.saveVideoSettings(result.state.toStored())
            }
            _settingsState.value = result.state
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute video settings state", e)
        }
    }

    private fun update(transform: (StoredVideoSettings) -> StoredVideoSettings) {
        scope.launch {
            try {
                val stored = transform(storageRepository.storedVideoSettingsFlow.first())
                val endpointType = storageRepository.endpointTypeFlow.first()
                val result = withContext(Dispatchers.Default) {
                    settingsStateBuilder.build(stored, endpointType)
                }
                storageRepository.saveVideoSettings(result.state.toStored())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save video settings", e)
            }
        }
    }

    fun setOrientation(orientation: StreamOrientation) = update { it.copy(orientation = orientation) }

    // Profile and level values are only meaningful for one encoder, so they restart from the best ones
    fun setEncoder(encoder: String) = update { it.copy(encoder = encoder, profile = null, level = null) }

    fun setShowHardwareResolutionsOnly(enabled: Boolean) =
        update { it.copy(showHardwareResolutionsOnly = enabled) }

    fun setResolution(resolution: Size) = update { it.copy(resolution = resolution.toString()) }

    fun setCameraFps(fps: Int) = update { it.copy(cameraFps = fps) }

    fun setMatchFps(enabled: Boolean) = update { it.copy(matchFps = enabled) }

    fun setFps(fps: Int) = update { it.copy(fps = fps) }

    fun setBitrateKbps(bitrateKbps: Int) = update { it.copy(bitrateKbps = bitrateKbps) }

    fun setProfile(profile: Int) = update { it.copy(profile = profile, level = null) }

    fun setLevel(level: Int) = update { it.copy(level = level) }

    private companion object {
        const val TAG = "VideoDrawerController"
    }
}
