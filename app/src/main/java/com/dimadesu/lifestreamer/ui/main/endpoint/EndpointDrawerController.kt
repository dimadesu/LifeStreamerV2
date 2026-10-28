package com.dimadesu.lifestreamer.ui.main.endpoint

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dimadesu.lifestreamer.data.storage.DataStoreRepository
import com.dimadesu.lifestreamer.data.storage.StoredEndpointSettings
import com.dimadesu.lifestreamer.models.EndpointType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the state and actions behind the endpoint settings drawer:
 * - drawer visibility
 * - endpoint type and the settings of every endpoint (SRT, RTMP, SRTLA, file)
 *
 * Every change is saved to DataStore in one write. The stream configuration picks it up from there.
 *
 * Everything that depends on the rest of the streaming screen goes through [Host].
 */
class EndpointDrawerController(
    private val storageRepository: DataStoreRepository,
    private val scope: CoroutineScope,
    private val host: Host
) {
    /**
     * What this controller needs from the screen that owns it.
     */
    interface Host {
        /** Called when the endpoint drawer opens so the host can close conflicting drawers. */
        fun onEndpointDrawerOpened()
    }

    private val _isOpen = MutableLiveData(false)
    val isOpen: LiveData<Boolean> = _isOpen

    private val _settings = MutableLiveData(StoredEndpointSettings())
    val settings: LiveData<StoredEndpointSettings> = _settings

    // Text fields can save at about the same time (for example on focus loss and on close)
    private val updateMutex = Mutex()

    /**
     * Starts observing DataStore so the state follows the stored settings.
     */
    fun start() {
        scope.launch {
            storageRepository.storedEndpointSettingsFlow.collect { _settings.value = it }
        }
    }

    fun toggleDrawer() {
        setDrawerOpen(!(_isOpen.value ?: false))
    }

    fun setDrawerOpen(open: Boolean) {
        _isOpen.value = open
        if (open) {
            host.onEndpointDrawerOpened()
        }
    }

    private fun update(transform: (StoredEndpointSettings) -> StoredEndpointSettings) {
        scope.launch {
            try {
                updateMutex.withLock {
                    val current = storageRepository.storedEndpointSettingsFlow.first()
                    val updated = transform(current)
                    if (updated != current) {
                        storageRepository.saveEndpointSettings(updated)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save endpoint settings", e)
            }
        }
    }

    fun setType(type: EndpointType) = update { it.copy(type = type) }

    fun setSrtAddress(value: String) = update { it.copy(srtAddress = value.trim()) }

    fun setSrtPort(value: String) = update { it.copy(srtPort = value) }

    fun setSrtStreamId(value: String) = update { it.copy(srtStreamId = value) }

    fun setSrtLatency(value: String) = update { it.copy(srtLatency = value) }

    fun setSrtPassphrase(value: String) = update { it.copy(srtPassphrase = value) }

    fun setRtmpUrl(value: String) = update { it.copy(rtmpUrl = value.trim()) }

    fun setSrtlaReceiverHost(value: String) = update { it.copy(srtlaReceiverHost = value.trim()) }

    fun setSrtlaReceiverPort(value: String) = update { it.copy(srtlaReceiverPort = value) }

    fun setSrtlaListenPort(value: String) = update { it.copy(srtlaListenPort = value) }

    fun setSrtlaStreamId(value: String) = update { it.copy(srtlaStreamId = value) }

    fun setSrtlaLatency(value: String) = update { it.copy(srtlaLatency = value) }

    fun setSrtlaPassphrase(value: String) = update { it.copy(srtlaPassphrase = value) }

    fun setMoblinkEnabled(enabled: Boolean) = update { it.copy(moblinkEnabled = enabled) }

    fun setMoblinkPassword(value: String) = update { it.copy(moblinkPassword = value) }

    fun setMoblinkPort(value: String) = update { it.copy(moblinkPort = value) }

    fun setSrtAdaptiveBitrate(enabled: Boolean) = update { it.copy(srtAdaptiveBitrate = enabled) }

    // The minimum bitrate can never be above the target, so the other one follows
    fun setSrtTargetBitrate(kbps: Int) =
        update { it.copy(srtTargetBitrateKbps = kbps, srtMinBitrateKbps = minOf(it.srtMinBitrateKbps, kbps)) }

    fun setSrtMinBitrate(kbps: Int) =
        update { it.copy(srtMinBitrateKbps = kbps, srtTargetBitrateKbps = maxOf(it.srtTargetBitrateKbps, kbps)) }

    fun setSrtRegulatorMode(mode: String) = update { it.copy(srtRegulatorMode = mode) }

    fun setRtmpAdaptiveBitrate(enabled: Boolean) = update { it.copy(rtmpAdaptiveBitrate = enabled) }

    fun setRtmpTargetBitrate(kbps: Int) =
        update { it.copy(rtmpTargetBitrateKbps = kbps, rtmpMinBitrateKbps = minOf(it.rtmpMinBitrateKbps, kbps)) }

    fun setRtmpMinBitrate(kbps: Int) =
        update { it.copy(rtmpMinBitrateKbps = kbps, rtmpTargetBitrateKbps = maxOf(it.rtmpTargetBitrateKbps, kbps)) }

    fun setFileName(value: String) = update { it.copy(fileName = value.trim()) }

    private companion object {
        const val TAG = "EndpointDrawerController"
    }
}
