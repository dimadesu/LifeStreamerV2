package com.dimadesu.lifestreamer.ui.main.controllers

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dimadesu.lifestreamer.uvc.UvcVideoSource
import com.google.gson.Gson
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.usb.Size
import com.serenegiant.usb.USBMonitor
import io.github.thibaultbee.streampack.core.elements.sources.video.bitmap.BitmapSourceFactory
import io.github.thibaultbee.streampack.core.elements.sources.video.bitmap.IBitmapSource
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSourceFactory
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.ICameraSource
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Controller responsible for UVC (USB camera) source management, USB device attach/detach/open
 * state callbacks, fallback to bitmap on detach, auto-reconnection, and device configuration.
 */
class UvcSourceController(
    private val application: Application,
    private val scope: CoroutineScope,
    private val streamerProvider: () -> SingleStreamer?,
    private val lastUsedCameraIdProvider: () -> String?,
    private val onLastUsedCameraIdChanged: (String?) -> Unit,
    private val defaultCameraIdProvider: () -> String,
    private val testBitmapProvider: () -> Bitmap,
    private val onBeforeSourceSwitch: suspend () -> Unit,
    private val onAfterSourceSwitch: suspend () -> Unit,
    private val onUvcActiveChanged: (Boolean) -> Unit,
    private val onToastMessage: (String) -> Unit,
    private val onErrorMessage: (String) -> Unit
) {
    companion object {
        private const val TAG = "UvcSourceCtrl"
    }

    private var uvcCameraHelper: CameraHelper? = null
    private var uvcReconnectionInProgress = false

    internal val _userToggledUvc = MutableLiveData<Boolean>(false)
    val isUvcSource: LiveData<Boolean> get() = _userToggledUvc

    suspend fun switchToUvcBitmapFallback(streamer: SingleStreamer, bitmap: Bitmap) {
        try {
            delay(300)
            streamer.setVideoSource(BitmapSourceFactory(bitmap))
            Log.i(TAG, "Switched to UVC bitmap fallback (audio unchanged)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set UVC bitmap fallback source: ${e.message}", e)
        }
    }

    fun getSavedUvcVideoConfig(device: UsbDevice): Size? {
        val key = "saved_preview_size_" + USBMonitor.getProductKey(device)
        val prefs = application.getSharedPreferences("uvc_camera_prefs", Context.MODE_PRIVATE)
        val formatJson = prefs.getString(key, null)
        if (formatJson.isNullOrEmpty()) {
            Log.d(TAG, "No saved video format found for device: ${device.deviceName}")
            return null
        }
        return try {
            val gson = Gson()
            val format = gson.fromJson(formatJson, Size::class.java)
            Log.d(TAG, "Loaded saved video format: type=${format.type}, ${format.width}x${format.height}@${format.fps}fps for device: ${device.deviceName}")
            format
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse saved video format: ${e.message}")
            null
        }
    }

    fun toggleUvcSource() {
        val currentStreamer = streamerProvider()
        if (currentStreamer == null) {
            Log.e(TAG, "Streamer service not available for UVC source toggle")
            onErrorMessage("Service not available")
            return
        }

        val videoSource = currentStreamer.videoInput?.sourceFlow?.value

        scope.launch {
            try {
                when (videoSource) {
                    is UvcVideoSource -> {
                        Log.i(TAG, "Switching from UVC to Camera source")
                        _userToggledUvc.postValue(false)
                        onUvcActiveChanged(false)

                        onBeforeSourceSwitch()

                        delay(300)
                        val targetCameraId = lastUsedCameraIdProvider() ?: defaultCameraIdProvider()
                        currentStreamer.setVideoSource(CameraSourceFactory(targetCameraId))

                        uvcCameraHelper?.let { helper ->
                            try {
                                helper.closeCamera()
                                helper.release()
                                Log.d(TAG, "Released CameraHelper after user toggled UVC OFF")
                            } catch (e: Exception) {
                                Log.w(TAG, "Error releasing CameraHelper: ${e.message}")
                            }
                        }
                        uvcCameraHelper = null

                        onAfterSourceSwitch()
                        Log.i(TAG, "Switched to camera source")
                    }
                    is ICameraSource -> {
                        Log.i(TAG, "Switching from Camera to UVC source")
                        onLastUsedCameraIdChanged(videoSource.cameraId)
                        Log.d(TAG, "Saved camera ID: ${videoSource.cameraId}")

                        uvcCameraHelper?.let { oldHelper ->
                            try {
                                oldHelper.closeCamera()
                                oldHelper.release()
                                Log.d(TAG, "Released old CameraHelper before creating new one")
                            } catch (e: Exception) {
                                Log.w(TAG, "Error releasing old CameraHelper: ${e.message}")
                            }
                        }
                        uvcCameraHelper = null

                        val sourceSwitchedSynchronously = AtomicBoolean(false)

                        uvcCameraHelper = CameraHelper().apply {
                            setStateCallback(object : ICameraHelper.StateCallback {
                                override fun onAttach(device: UsbDevice) {
                                    Log.d(TAG, "UVC camera attached: ${device.deviceName}")
                                    val isUvcToggleOn = _userToggledUvc.value ?: false
                                    val currentVideoSource = currentStreamer.videoInput?.sourceFlow?.value
                                    val isOnBitmapFallback = currentVideoSource is IBitmapSource

                                    if (isUvcToggleOn && isOnBitmapFallback && !uvcReconnectionInProgress) {
                                        Log.i(TAG, "UVC camera re-attached while on bitmap fallback - auto-reconnecting")
                                        uvcReconnectionInProgress = true
                                        selectDevice(device)
                                    } else if (uvcReconnectionInProgress) {
                                        Log.d(TAG, "UVC reconnection already in progress, ignoring duplicate attach")
                                    }
                                }

                                override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
                                    val alreadySwitched = sourceSwitchedSynchronously.get()
                                    Log.d(TAG, "UVC device opened (isFirstOpen=$isFirstOpen, alreadySwitched=$alreadySwitched, reconnecting=$uvcReconnectionInProgress)")

                                    val currentVideoSource = currentStreamer.videoInput?.sourceFlow?.value
                                    val isOnBitmapFallback = currentVideoSource is IBitmapSource
                                    val isUvcToggleOn = _userToggledUvc.value ?: false

                                    if (isOnBitmapFallback && isUvcToggleOn && uvcReconnectionInProgress) {
                                        Log.i(TAG, "UVC device reopened while on bitmap fallback - switching back to UVC source")
                                        scope.launch {
                                            try {
                                                onBeforeSourceSwitch()
                                                delay(300)
                                                currentStreamer.setVideoSource(UvcVideoSource.Factory(this@apply))
                                                onAfterSourceSwitch()
                                                Log.i(TAG, "Switched back to UVC source after reconnection")
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error switching back to UVC after reconnection: ${e.message}", e)
                                                onErrorMessage("Failed to reconnect UVC: ${e.message}")
                                            } finally {
                                                uvcReconnectionInProgress = false
                                            }
                                        }
                                    } else if (isFirstOpen && !alreadySwitched && !uvcReconnectionInProgress) {
                                        Log.i(TAG, "First open after permission grant - switching to UVC source")
                                        scope.launch {
                                            try {
                                                _userToggledUvc.postValue(true)
                                                onUvcActiveChanged(true)

                                                onBeforeSourceSwitch()
                                                delay(300)
                                                currentStreamer.setVideoSource(UvcVideoSource.Factory(this@apply))
                                                onAfterSourceSwitch()
                                                Log.i(TAG, "Switched to UVC source after permission grant")
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error switching to UVC after permission: ${e.message}", e)
                                                onErrorMessage("Failed to switch to UVC: ${e.message}")
                                            }
                                        }
                                    } else if (isFirstOpen) {
                                        Log.d(TAG, "Skipping source switch in onDeviceOpen - already switched synchronously")
                                    }

                                    val savedVideoConfig = getSavedUvcVideoConfig(device)
                                    openCamera(savedVideoConfig)
                                }

                                override fun onCameraOpen(device: UsbDevice) {
                                    Log.i(TAG, "UVC camera opened and ready")
                                    val currentSource = currentStreamer.videoInput?.sourceFlow?.value
                                    if (currentSource is UvcVideoSource) {
                                        currentSource.onCameraReady()
                                    } else {
                                        Log.d(TAG, "Video source is not UvcVideoSource, calling startPreview directly")
                                        startPreview()
                                    }
                                }

                                override fun onCameraClose(device: UsbDevice) {
                                    Log.d(TAG, "UVC camera closed")
                                }

                                override fun onDeviceClose(device: UsbDevice) {
                                    Log.d(TAG, "UVC device closed")
                                }

                                override fun onDetach(device: UsbDevice) {
                                    Log.w(TAG, "UVC camera detached - device=${device.deviceName}")
                                    uvcReconnectionInProgress = false

                                    val isUvcActive = _userToggledUvc.value ?: false
                                    if (!isUvcActive) {
                                        Log.d(TAG, "UVC toggle is OFF, ignoring detach event")
                                        return
                                    }

                                    Log.w(TAG, "UVC toggle is ON - switching to fallback (toggle stays ON)")
                                    scope.launch {
                                        try {
                                            switchToUvcBitmapFallback(
                                                currentStreamer,
                                                testBitmapProvider()
                                            )
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error switching to fallback: ${e.message}", e)
                                        }
                                    }
                                }

                                override fun onCancel(device: UsbDevice) {
                                    Log.d(TAG, "UVC camera permission cancelled")
                                    uvcReconnectionInProgress = false
                                    onToastMessage("USB permission denied")
                                }
                            })
                        }

                        val helper = uvcCameraHelper
                        if (helper == null) {
                            onErrorMessage("Failed to initialize UVC camera helper")
                            Log.e(TAG, "Failed to initialize camera helper")
                            return@launch
                        }

                        val deviceList = helper.deviceList
                        if (deviceList.isNullOrEmpty()) {
                            onErrorMessage("No USB camera connected")
                            Log.w(TAG, "No UVC devices found")
                            return@launch
                        }

                        val device = deviceList[0]
                        Log.i(TAG, "Selecting UVC device: ${device.deviceName}")

                        val usbManager = application.getSystemService(Context.USB_SERVICE) as UsbManager
                        if (!usbManager.hasPermission(device)) {
                            Log.d(TAG, "No USB permission yet - requesting permission")
                            onToastMessage("USB permission required")
                            helper.selectDevice(device)
                            return@launch
                        }

                        Log.d(TAG, "USB permission already granted - switching source now")
                        sourceSwitchedSynchronously.set(true)

                        _userToggledUvc.postValue(true)
                        onUvcActiveChanged(true)

                        onBeforeSourceSwitch()
                        delay(300)
                        currentStreamer.setVideoSource(UvcVideoSource.Factory(helper))
                        onAfterSourceSwitch()

                        helper.selectDevice(device)
                        Log.i(TAG, "Switched to UVC source with BT-aware audio")
                    }
                    else -> {
                        Log.i(TAG, "Switching from ${videoSource?.javaClass?.simpleName} to Camera source")
                        _userToggledUvc.postValue(false)
                        onUvcActiveChanged(false)

                        onBeforeSourceSwitch()
                        delay(300)
                        val targetCameraId = lastUsedCameraIdProvider() ?: defaultCameraIdProvider()
                        currentStreamer.setVideoSource(CameraSourceFactory(targetCameraId))
                        onAfterSourceSwitch()
                        Log.i(TAG, "Switched to camera source")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling UVC source: ${e.message}", e)
                onErrorMessage("Failed to toggle UVC source: ${e.message}")
            }
        }
    }

    fun release() {
        uvcCameraHelper?.let { helper ->
            try {
                helper.stopPreview()
                helper.closeCamera()
                helper.release()
                Log.i(TAG, "Released UVC camera helper")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release UVC camera helper: ${e.message}")
            }
        }
        uvcCameraHelper = null
    }
}
