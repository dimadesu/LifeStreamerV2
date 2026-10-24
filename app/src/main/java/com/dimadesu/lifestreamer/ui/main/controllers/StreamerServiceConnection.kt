package com.dimadesu.lifestreamer.ui.main.controllers

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.dimadesu.lifestreamer.audio.BluetoothAudioManager
import com.dimadesu.lifestreamer.models.StreamStatus
import com.dimadesu.lifestreamer.services.CameraStreamerService
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Manages foreground service binding, connection callbacks, and flow observation
 * between PreviewViewModel and CameraStreamerService.
 */
class StreamerServiceConnection(
    private val application: Application,
    private val scope: CoroutineScope,
    private val onConnected: (service: CameraStreamerService, binder: CameraStreamerService.CameraStreamerServiceBinder, streamer: SingleStreamer) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onReconnectionStatus: (String?) -> Unit,
    private val onBitrateUpdated: (String) -> Unit,
    private val onEncoderStatsUpdated: (String?) -> Unit,
    private val onPassthroughStateChanged: (Boolean) -> Unit,
    private val onStreamStatusChanged: (StreamStatus) -> Unit,
    private val onMutedChanged: (Boolean) -> Unit,
    private val onScoStateChanged: (BluetoothAudioManager.ScoState) -> Unit,
    private val onBluetoothConnectRequested: () -> Unit,
    private val onUptimeUpdated: (String?) -> Unit
) {
    companion object {
        private const val TAG = "StreamerServiceConn"
    }

    var streamerService: CameraStreamerService? = null
        private set

    var serviceBinder: CameraStreamerService.CameraStreamerServiceBinder? = null
        private set

    var serviceStreamer: SingleStreamer? = null
        private set

    private val _isServiceReady = MutableStateFlow(false)
    val isServiceReady: StateFlow<Boolean> = _isServiceReady.asStateFlow()

    private var activeConnection: ServiceConnection? = null
    private val serviceObservationJobs = mutableListOf<Job>()

    @RequiresApi(Build.VERSION_CODES.O)
    fun bind() {
        Log.i(TAG, "Binding to CameraStreamerService...")

        val serviceIntent = Intent(application, CameraStreamerService::class.java)
        application.startForegroundService(serviceIntent)
        Log.i(TAG, "Started/reconnected to CameraStreamerService as independent foreground service")

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder is CameraStreamerService.CameraStreamerServiceBinder) {
                    val service = binder.getService()
                    streamerService = service
                    serviceBinder = binder
                    val streamer = binder.streamer as SingleStreamer
                    serviceStreamer = streamer

                    clearObservationJobs()

                    // Observe centralized reconnection status message from service
                    serviceObservationJobs += scope.launch {
                        try {
                            service.reconnectionStatusMessage.collect { message ->
                                onReconnectionStatus(message)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to observe reconnection state: ${t.message}")
                        }
                    }

                    // Collect bitrate flow from service binder
                    serviceObservationJobs += scope.launch {
                        try {
                            service.currentBitrateFlow.collect { bits ->
                                val text = bits?.let { b ->
                                    if (b >= 1_000_000) String.format(Locale.US, "%.2f Mbps", b / 1_000_000.0)
                                    else String.format(Locale.US, "%d kb/s", b / 1000)
                                } ?: ""
                                onBitrateUpdated(text)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to collect bitrate: ${t.message}")
                        }
                    }

                    // Collect encoder stats flow from service
                    serviceObservationJobs += scope.launch {
                        try {
                            service.encoderStatsFlow.collect { statsText ->
                                onEncoderStatsUpdated(statsText)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to collect encoder stats: ${t.message}")
                        }
                    }

                    // Observe passthrough running state
                    try {
                        val snapshot = service.isPassthroughRunning.value
                        onPassthroughStateChanged(snapshot)
                    } catch (_: Throwable) {}

                    serviceObservationJobs += scope.launch {
                        try {
                            service.isPassthroughRunning.collect { running ->
                                onPassthroughStateChanged(running)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to observe passthrough state: ${t.message}")
                        }
                    }

                    _isServiceReady.value = true

                    // Collect service-provided stream status
                    serviceObservationJobs += scope.launch {
                        try {
                            binder.serviceStreamStatus().collect { svcStatus ->
                                onStreamStatusChanged(svcStatus)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to collect service status: ${t.message}")
                        }
                    }

                    // Collect isMuted flow
                    serviceObservationJobs += scope.launch {
                        try {
                            binder.isMutedFlow().collect { muted ->
                                onMutedChanged(muted)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to collect isMuted flow: ${t.message}")
                        }
                    }

                    // Collect SCO state flow
                    serviceObservationJobs += scope.launch {
                        try {
                            binder.scoStateFlow().collect { state ->
                                onScoStateChanged(state)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to collect SCO state flow: ${t.message}")
                        }
                    }

                    // Collect BLUETOOTH_CONNECT permission requests
                    serviceObservationJobs += scope.launch {
                        try {
                            binder.bluetoothConnectPermissionRequests().collect {
                                onBluetoothConnectRequested()
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to collect BLUETOOTH_CONNECT requests: ${t.message}")
                        }
                    }

                    // Collect uptime flow
                    serviceObservationJobs += scope.launch {
                        try {
                            binder.uptimeFlow().collect { uptime ->
                                onUptimeUpdated(uptime)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to collect uptime flow: ${t.message}")
                        }
                    }

                    onConnected(service, binder, streamer)
                    Log.i(TAG, "CameraStreamerService connected and ready - streaming state: ${streamer.isStreamingFlow.value}")
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.w(TAG, "CameraStreamerService disconnected: $name")
                clearObservationJobs()
                streamerService = null
                serviceBinder = null
                serviceStreamer = null
                _isServiceReady.value = false
                onDisconnected()
            }
        }

        application.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        activeConnection = connection
    }

    fun unbind() {
        activeConnection?.let { connection ->
            try {
                application.unbindService(connection)
                Log.i(TAG, "Unbound from CameraStreamerService")
            } catch (e: Exception) {
                Log.w(TAG, "unbindService failed: ${e.message}")
            }
        }
        activeConnection = null
        clearObservationJobs()
        streamerService = null
        serviceBinder = null
        serviceStreamer = null
        _isServiceReady.value = false
        onDisconnected()
    }

    fun setUseBluetoothMic(enabled: Boolean, forcePassthroughRestart: Boolean = false, skipPassthroughRestart: Boolean = false) {
        try {
            serviceBinder?.setUseBluetoothMic(enabled, forcePassthroughRestart, skipPassthroughRestart)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to setUseBluetoothMic: ${t.message}")
        }
    }

    fun refreshNotification() {
        try {
            serviceBinder?.refreshNotification()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to refreshNotification: ${t.message}")
        }
    }

    fun markUserStoppedManually() {
        try {
            streamerService?.markUserStoppedManually()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to markUserStoppedManually: ${t.message}")
        }
    }

    private fun clearObservationJobs() {
        serviceObservationJobs.forEach { it.cancel() }
        serviceObservationJobs.clear()
    }
}
