package com.dimadesu.lifestreamer.ui.main.controllers

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.Player
import com.dimadesu.lifestreamer.audio.BluetoothAudioConfig
import com.dimadesu.lifestreamer.audio.ConditionalAudioSourceFactory
import com.dimadesu.lifestreamer.rtmp.audio.MediaProjectionAudioSourceFactory
import com.dimadesu.lifestreamer.rtmp.video.RTMPVideoSource
import com.dimadesu.lifestreamer.services.CameraStreamerService
import io.github.thibaultbee.streampack.core.elements.sources.IMediaProjectionSource
import io.github.thibaultbee.streampack.core.elements.sources.video.bitmap.IBitmapSource
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Manages audio source routing, Bluetooth mic vs System Audio mutual exclusivity,
 * and audio monitoring/passthrough.
 */
class AudioRoutingController(
    private val application: Application,
    private val scope: CoroutineScope,
    private val streamerProvider: () -> SingleStreamer?,
    private val serviceProvider: () -> CameraStreamerService?,
    private val serviceBinderProvider: () -> CameraStreamerService.CameraStreamerServiceBinder?,
    private val mediaProjectionTokenStore: MediaProjectionTokenStore,
    private val userToggledUvcProvider: () -> Boolean,
    private val currentRtmpPlayerProvider: () -> Player?,
    private val onRequestBluetoothPermission: () -> Unit
) {
    companion object {
        private const val TAG = "AudioRoutingCtrl"
    }

    // UI toggle: Use Bluetooth mic when available
    private val _useBluetoothMic = MutableLiveData<Boolean>(BluetoothAudioConfig.isEnabled())
    val useBluetoothMic: LiveData<Boolean> get() = _useBluetoothMic

    // Toggle: camera/UVC sources use full-phone system audio instead of mic
    private var _useSystemAudioForCamera = false
    val useSystemAudioForCamera: Boolean get() = _useSystemAudioForCamera
    private val _useSystemAudioForCameraLiveData = MutableLiveData(false)
    val useSystemAudioForCameraLiveData: LiveData<Boolean> get() = _useSystemAudioForCameraLiveData

    // Audio monitoring state
    private val _isMonitorAudioOn = MutableLiveData<Boolean>(false)
    val isMonitorAudioOn: LiveData<Boolean> get() = _isMonitorAudioOn

    var suppressPassthroughObserver = false

    fun setUseBluetoothMic(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val granted = ContextCompat.checkSelfPermission(
                application,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                onRequestBluetoothPermission()
                return
            }
        }

        // Mutual exclusivity: enabling BT mic turns off SYS AUDIO
        val sysAudioWasOn = enabled && _useSystemAudioForCamera
        if (sysAudioWasOn) {
            Log.i(TAG, "BT mic on - turning off SYS AUDIO (mutual exclusivity)")
            _useSystemAudioForCamera = false
            _useSystemAudioForCameraLiveData.postValue(false)
        }

        _useBluetoothMic.postValue(enabled)
        try {
            BluetoothAudioConfig.setEnabled(enabled)
        } catch (_: Throwable) {}

        if (sysAudioWasOn) {
            scope.launch {
                setAudioSourceBasedOnVideoSource()
                val monitorWasOn = _isMonitorAudioOn.value == true
                try {
                    serviceBinderProvider()?.setUseBluetoothMic(true, forcePassthroughRestart = monitorWasOn)
                } catch (_: Throwable) {}
            }
        } else {
            try {
                serviceBinderProvider()?.setUseBluetoothMic(enabled)
            } catch (_: Throwable) {}
        }
    }

    fun handleScoStateFailedOrDisconnected() {
        _useBluetoothMic.postValue(false)
        try {
            serviceBinderProvider()?.setUseBluetoothMic(false)
        } catch (_: Throwable) {}
    }

    fun toggleSystemAudioForCamera(mediaProjectionLauncher: ActivityResultLauncher<Intent>) {
        val alreadyHasProjection = mediaProjectionTokenStore.hasProjection()
        if (!alreadyHasProjection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.i(TAG, "Requesting MediaProjection for SYS AUDIO toggle")
            mediaProjectionTokenStore.requestProjection(mediaProjectionLauncher) { projection ->
                if (projection == null) {
                    Log.w(TAG, "MediaProjection denied — SYS AUDIO toggle cancelled")
                    return@requestProjection
                }
                doToggleSystemAudioForCamera()
            }
        } else {
            doToggleSystemAudioForCamera()
        }
    }

    private fun doToggleSystemAudioForCamera() {
        _useSystemAudioForCamera = !_useSystemAudioForCamera
        _useSystemAudioForCameraLiveData.postValue(_useSystemAudioForCamera)
        Log.i(TAG, "System audio for camera toggled: $_useSystemAudioForCamera")

        // Mutual exclusivity: enabling SYS AUDIO turns off BT mic
        if (_useSystemAudioForCamera && _useBluetoothMic.value == true) {
            Log.i(TAG, "SYS AUDIO on - turning off BT mic (mutual exclusivity)")
            _useBluetoothMic.postValue(false)
            try { BluetoothAudioConfig.setEnabled(false) } catch (_: Throwable) {}
            try { serviceBinderProvider()?.setUseBluetoothMic(false, skipPassthroughRestart = true) } catch (_: Throwable) {}
        }

        scope.launch {
            setAudioSourceBasedOnVideoSource()
            if (_useSystemAudioForCamera) {
                try { serviceBinderProvider()?.refreshNotification() } catch (_: Throwable) {}
                if (_isMonitorAudioOn.value == true) {
                    Log.i(TAG, "SYS AUDIO on - stopping mic passthrough (stream uses phone audio)")
                    serviceProvider()?.stopAudioPassthrough()
                }
            } else {
                if (BluetoothAudioConfig.isEnabled()) {
                    Log.i(TAG, "SYS AUDIO off - re-applying BT policy to restore BT mic")
                    try { serviceBinderProvider()?.setUseBluetoothMic(true) } catch (_: Throwable) {}
                }
                if (_isMonitorAudioOn.value == true) {
                    Log.i(TAG, "SYS AUDIO off - restarting audio monitoring")
                    applyMonitorAudioState()
                }
            }
        }
    }

    suspend fun setAudioSourceBasedOnVideoSource() {
        val currentStreamer = streamerProvider() ?: return
        val currentVideoSource = currentStreamer.videoInput?.sourceFlow?.value
        val isUvcBitmapFallback = currentVideoSource is IBitmapSource && userToggledUvcProvider()
        val isRtmpOrBitmap = currentVideoSource is RTMPVideoSource || (currentVideoSource is IBitmapSource && !isUvcBitmapFallback)

        if (isRtmpOrBitmap) {
            val projection = mediaProjectionTokenStore.streamingMediaProjection ?: mediaProjectionTokenStore.helper.getMediaProjection()
            if (projection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    currentStreamer.setAudioSource(MediaProjectionAudioSourceFactory(projection))
                    Log.i(TAG, "Set MediaProjection audio for RTMP/Bitmap video")
                } catch (e: Exception) {
                    Log.w(TAG, "MediaProjection audio failed, using conditional source: ${e.message}")
                    currentStreamer.setAudioSource(ConditionalAudioSourceFactory())
                }
            } else {
                Log.i(TAG, "No MediaProjection available for RTMP/Bitmap, using conditional source")
                currentStreamer.setAudioSource(ConditionalAudioSourceFactory())
            }
        } else if (_useSystemAudioForCamera && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = mediaProjectionTokenStore.currentProjection
            if (projection != null) {
                try {
                    currentStreamer.setAudioSource(MediaProjectionAudioSourceFactory(projection, captureFullPhone = true))
                    Log.i(TAG, "Set full-phone system audio for camera/UVC source")
                } catch (e: Exception) {
                    Log.w(TAG, "Full-phone audio failed, falling back to mic: ${e.message}")
                    currentStreamer.setAudioSource(ConditionalAudioSourceFactory())
                }
            } else {
                Log.w(TAG, "No MediaProjection for system audio toggle, using mic")
                currentStreamer.setAudioSource(ConditionalAudioSourceFactory())
            }
        } else {
            Log.i(TAG, "Camera/UVC video detected, using ConditionalAudioSourceFactory (BT-aware)")
            currentStreamer.setAudioSource(ConditionalAudioSourceFactory())
        }
    }

    fun toggleMonitorAudio() {
        val newState = !(_isMonitorAudioOn.value ?: false)
        _isMonitorAudioOn.value = newState
        if (newState) {
            applyMonitorAudioState()
        } else {
            stopAllMonitoring()
        }
    }

    fun setMonitorUiState(state: Boolean) {
        _isMonitorAudioOn.value = state
    }

    fun applyMonitorAudioState() {
        val videoSource = streamerProvider()?.videoInput?.sourceFlow?.value
        val audioSource = streamerProvider()?.audioInput?.sourceFlow?.value

        when {
            videoSource is RTMPVideoSource -> {
                currentRtmpPlayerProvider()?.let { player ->
                    Handler(Looper.getMainLooper()).post {
                        try {
                            player.volume = 1.0f
                            Log.i(TAG, "Audio monitor ON - RTMP audio (ExoPlayer volume: ${player.volume})")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to set ExoPlayer volume: ${e.message}", e)
                        }
                    }
                }
                serviceProvider()?.stopAudioPassthrough()
            }

            audioSource != null && audioSource !is IMediaProjectionSource -> {
                currentRtmpPlayerProvider()?.let { player ->
                    Handler(Looper.getMainLooper()).post {
                        try {
                            player.volume = 0f
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to mute ExoPlayer: ${e.message}", e)
                        }
                    }
                }
                serviceProvider()?.startAudioPassthrough()
                Log.i(TAG, "Audio monitor ON - Microphone passthrough")
            }

            else -> {
                stopAllMonitoring()
                Log.i(TAG, "Audio monitor OFF - MediaProjection audio detected (cannot monitor without echo)")
            }
        }
    }

    fun stopAllMonitoring() {
        currentRtmpPlayerProvider()?.let { player ->
            Handler(Looper.getMainLooper()).post {
                try {
                    player.volume = 0f
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to mute ExoPlayer: ${e.message}", e)
                }
            }
        }
        serviceProvider()?.stopAudioPassthrough()
        Log.i(TAG, "Audio monitor OFF")
    }

    fun onPassthroughServiceStateChanged(running: Boolean, isRtmpSource: Boolean) {
        if (suppressPassthroughObserver) {
            Log.d(TAG, "Suppressing passthrough state change during source switch (running=$running)")
            return
        }
        if (!isRtmpSource && !_useSystemAudioForCamera) {
            _isMonitorAudioOn.postValue(running)
            Log.i(TAG, "Service passthrough running state observed: $running")
        } else if (_useSystemAudioForCamera) {
            Log.d(TAG, "Ignoring passthrough state change while SYS AUDIO is active (running=$running)")
        } else {
            Log.d(TAG, "Ignoring passthrough state change while on RTMP source (running=$running)")
        }
    }
}
