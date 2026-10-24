package com.dimadesu.lifestreamer.ui.main.controllers

import android.Manifest
import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureResult
import android.util.Log
import android.util.Range
import android.util.Rational
import android.util.SizeF
import androidx.annotation.RequiresPermission
import com.dimadesu.lifestreamer.utils.isEmpty
import com.dimadesu.lifestreamer.utils.setNextCameraId
import com.dimadesu.lifestreamer.utils.switchBackToFront
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSettings
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.ICameraSource
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.atan
import kotlin.math.sqrt

/**
 * Controller responsible for camera hardware features:
 * - Flash / Torch
 * - Auto White Balance (AWB)
 * - Exposure Compensation
 * - Zoom Ratio
 * - Auto Focus (AF) and Manual Lens Distance
 * - Camera device enumeration and switching
 */
class CameraControlsController(
    private val application: Application,
    private val scope: CoroutineScope,
    private val streamerProvider: () -> SingleStreamer?,
    private val videoSourceMutex: Mutex,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onToastMessage: (String) -> Unit = {},
    private val onErrorMessage: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "CameraControls"
    }

    val cameraSettings: CameraSettings?
        get() {
            val videoSource = streamerProvider()?.videoInput?.sourceFlow?.value
            return (videoSource as? ICameraSource)?.settings
        }

    // Available camera list
    private val _availableCameras = MutableStateFlow<List<com.dimadesu.lifestreamer.ui.main.PreviewViewModel.CameraInfo>>(emptyList())
    val availableCameras: StateFlow<List<com.dimadesu.lifestreamer.ui.main.PreviewViewModel.CameraInfo>> = _availableCameras.asStateFlow()

    fun hideSliders() {
        _showZoomSlider.value = false
        _showExposureSlider.value = false
        _showLensDistanceSlider.value = false
    }

    // Flash / Torch
    private val _isFlashAvailable = MutableStateFlow(false)
    val isFlashAvailable: StateFlow<Boolean> = _isFlashAvailable.asStateFlow()

    // Auto White Balance
    private val _isAutoWhiteBalanceAvailable = MutableStateFlow(false)
    val isAutoWhiteBalanceAvailable: StateFlow<Boolean> = _isAutoWhiteBalanceAvailable.asStateFlow()

    // Exposure
    private val _showExposureSlider = MutableStateFlow(false)
    val showExposureSlider: StateFlow<Boolean> = _showExposureSlider.asStateFlow()

    private val _isExposureCompensationAvailable = MutableStateFlow(false)
    val isExposureCompensationAvailable: StateFlow<Boolean> = _isExposureCompensationAvailable.asStateFlow()

    private val _exposureCompensationRange = MutableStateFlow<Range<Int>?>(null)
    val exposureCompensationRange: StateFlow<Range<Int>?> = _exposureCompensationRange.asStateFlow()

    private val _exposureCompensationStep = MutableStateFlow<Rational?>(null)
    val exposureCompensationStep: StateFlow<Rational?> = _exposureCompensationStep.asStateFlow()

    // Zoom
    private val _showZoomSlider = MutableStateFlow(false)
    val showZoomSlider: StateFlow<Boolean> = _showZoomSlider.asStateFlow()

    private val _isZoomAvailable = MutableStateFlow(false)
    val isZoomAvailable: StateFlow<Boolean> = _isZoomAvailable.asStateFlow()

    private val _zoomRatioRange = MutableStateFlow<Range<Float>?>(null)
    val zoomRatioRange: StateFlow<Range<Float>?> = _zoomRatioRange.asStateFlow()

    // Focus & Lens Distance
    private val _isAutoFocusModeAvailable = MutableStateFlow(false)
    val isAutoFocusModeAvailable: StateFlow<Boolean> = _isAutoFocusModeAvailable.asStateFlow()

    private val _showLensDistanceSlider = MutableStateFlow(false)
    val showLensDistanceSlider: StateFlow<Boolean> = _showLensDistanceSlider.asStateFlow()

    private val _lensDistanceRange = MutableStateFlow<Range<Float>?>(null)
    val lensDistanceRange: StateFlow<Range<Float>?> = _lensDistanceRange.asStateFlow()

    // Listeners for data binding property changes in the ViewModel
    var onExposureCompensationChanged: (() -> Unit)? = null
    var onZoomRatioChanged: (() -> Unit)? = null
    var onLensDistanceChanged: (() -> Unit)? = null

    // Exposure value getter/setter
    var exposureCompensation: Float
        get() {
            val settings = cameraSettings
            return if (settings != null && settings.isActiveFlow.value) {
                settings.exposure.compensation * settings.exposure.availableCompensationStep.toFloat()
            } else {
                0f
            }
        }
        set(value) {
            cameraSettings?.let { settings ->
                settings.exposure.let { exposure ->
                    scope.launch {
                        try {
                            if (settings.isActiveFlow.value) {
                                exposure.setCompensation((value / exposure.availableCompensationStep.toFloat()).toInt())
                            }
                            onExposureCompensationChanged?.invoke()
                        } catch (t: Throwable) {
                            Log.w(TAG, "Setting exposure compensation failed (camera session may be closed): ${t.message}")
                        }
                    }
                }
            } ?: Log.e(TAG, "Camera settings is not accessible")
        }

    // Zoom value getter/setter
    var zoomRatio: Float
        get() {
            val settings = cameraSettings
            return if (settings != null && settings.isActiveFlow.value) {
                runBlocking {
                    settings.zoom.getZoomRatio()
                }
            } else {
                1f
            }
        }
        set(value) {
            cameraSettings?.let { settings ->
                scope.launch {
                    try {
                        if (settings.isActiveFlow.value) {
                            settings.zoom.setZoomRatio(value)
                        }
                        onZoomRatioChanged?.invoke()
                    } catch (t: Throwable) {
                        Log.w(TAG, "Setting zoom failed (camera session may be closed): ${t.message}")
                    }
                }
            } ?: Log.e(TAG, "Camera settings is not accessible")
        }

    // Lens distance value getter/setter
    var lensDistance: Float
        get() {
            val settings = cameraSettings
            return if (settings != null && settings.isActiveFlow.value) {
                settings.focus.lensDistance
            } else {
                0f
            }
        }
        set(value) {
            cameraSettings?.let { settings ->
                settings.focus.let { focus ->
                    scope.launch {
                        try {
                            if (settings.isActiveFlow.value) {
                                focus.setLensDistance(value)
                            }
                            onLensDistanceChanged?.invoke()
                        } catch (t: Throwable) {
                            Log.w(TAG, "Setting lens distance failed (camera session may be closed): ${t.message}")
                        }
                    }
                }
            } ?: Log.e(TAG, "Camera settings is not accessible")
        }

    fun onSourceChanged(videoSource: Any?) {
        if (videoSource is ICameraSource) {
            onCameraChanged(videoSource)
        } else {
            _isFlashAvailable.value = false
            _isAutoWhiteBalanceAvailable.value = false
            _isExposureCompensationAvailable.value = false
            _isZoomAvailable.value = false
            _isAutoFocusModeAvailable.value = false
        }
    }

    private fun onCameraChanged(videoSource: ICameraSource) {
        val settings = videoSource.settings
        scope.launch {
            if (settings.isActiveFlow.value) {
                if (settings.stabilization.isOpticalAvailable) {
                    settings.stabilization.setIsEnableOptical(true)
                } else {
                    settings.stabilization.setIsEnableVideo(true)
                }
            }
        }

        // Flash
        _isFlashAvailable.value = settings.flash.isAvailable

        // WB
        _isAutoWhiteBalanceAvailable.value = settings.whiteBalance.availableAutoModes.size > 1

        // Exposure
        val expRange = settings.exposure.availableCompensationRange
        _isExposureCompensationAvailable.value = !expRange.isEmpty
        if (!expRange.isEmpty) {
            val step = settings.exposure.availableCompensationStep
            _exposureCompensationRange.value = Range(
                (expRange.lower * step.toFloat()).toInt(),
                (expRange.upper * step.toFloat()).toInt()
            )
            _exposureCompensationStep.value = step
        } else {
            _exposureCompensationRange.value = null
            _exposureCompensationStep.value = null
        }
        exposureCompensation = 0f

        // Zoom
        val zRange = settings.zoom.availableRatioRange
        _isZoomAvailable.value = !zRange.isEmpty
        _zoomRatioRange.value = if (!zRange.isEmpty) zRange else null
        zoomRatio = 1.0f

        // Focus
        _isAutoFocusModeAvailable.value = settings.focus.availableAutoModes.size > 1

        // Lens distance
        _showLensDistanceSlider.value = false
        _lensDistanceRange.value = settings.focus.availableLensDistanceRange
        lensDistance = 0f
    }

    fun toggleFlash() {
        cameraSettings?.let { settings ->
            scope.launch {
                try {
                    val isCurrentlyEnabled = settings.flash.isEnable
                    settings.flash.setIsEnable(!isCurrentlyEnabled)
                    val message = if (!isCurrentlyEnabled) "Torch: On" else "Torch: Off"
                    onToastMessage(message)
                } catch (t: Throwable) {
                    Log.w(TAG, "toggleFlash failed (camera session may be closed): ${t.message}")
                }
            }
        } ?: Log.e(TAG, "Camera settings is not accessible")
    }

    fun toggleAutoWhiteBalanceMode() {
        cameraSettings?.let { settings ->
            scope.launch {
                try {
                    val awbModes = settings.whiteBalance.availableAutoModes
                    val index = awbModes.indexOf(settings.whiteBalance.autoMode)
                    val newMode = awbModes[(index + 1) % awbModes.size]
                    settings.whiteBalance.setAutoMode(newMode)

                    val modeName = getWhiteBalanceModeName(newMode)
                    onToastMessage("White Balance: $modeName")
                } catch (t: Throwable) {
                    Log.w(TAG, "toggleAutoWhiteBalanceMode failed (camera session may be closed): ${t.message}")
                }
            }
        } ?: Log.e(TAG, "Camera settings is not accessible")
    }

    fun toggleExposureSlider() {
        _showExposureSlider.value = !_showExposureSlider.value
    }

    fun toggleZoomSlider() {
        _showZoomSlider.value = !_showZoomSlider.value
    }

    fun toggleAutoFocusMode() {
        cameraSettings?.let { settings ->
            scope.launch {
                try {
                    val afModes = settings.focus.availableAutoModes
                    val index = afModes.indexOf(settings.focus.autoMode)
                    val newMode = afModes[(index + 1) % afModes.size]
                    settings.focus.setAutoMode(newMode)

                    val modeName = getAutoFocusModeName(newMode)
                    onToastMessage("Focus: $modeName")

                    _showLensDistanceSlider.value = (settings.focus.autoMode == CaptureResult.CONTROL_AF_MODE_OFF)
                } catch (t: Throwable) {
                    Log.w(TAG, "toggleAutoFocusMode failed (camera session may be closed): ${t.message}")
                }
            }
        } ?: Log.e(TAG, "Camera settings is not accessible")
    }

    fun loadAvailableCameras() {
        scope.launch(Dispatchers.IO) {
            try {
                val cameraManager = application.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraIds = cameraManager.cameraIdList

                val cameraList = cameraIds.mapNotNull { id ->
                    try {
                        val characteristics = cameraManager.getCameraCharacteristics(id)
                        val facingConst = characteristics.get(CameraCharacteristics.LENS_FACING)
                        val facing = when (facingConst) {
                            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                            CameraCharacteristics.LENS_FACING_BACK -> "Back"
                            else -> "External"
                        }

                        // Get FOV info
                        val focalArr = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        val physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) as? SizeF
                        val fovLabel = if (focalArr != null && focalArr.isNotEmpty()) {
                            val firstFocal = focalArr.first()
                            if (physicalSize != null) {
                                val sensorDiag = sqrt((physicalSize.width * physicalSize.width + physicalSize.height * physicalSize.height).toDouble())
                                val fovRad = 2.0 * atan(sensorDiag / (2.0 * firstFocal))
                                val fovDeg = (fovRad * 180.0 / Math.PI).toInt()
                                "${fovDeg}°"
                            } else {
                                val formattedAll = focalArr.joinToString(",") { "%.1f".format(it) }
                                "f=${formattedAll}mm"
                            }
                        } else {
                            ""
                        }

                        val displayName = "$facing $fovLabel".trim()
                        com.dimadesu.lifestreamer.ui.main.PreviewViewModel.CameraInfo(id, displayName, facing)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get camera characteristics for $id: ${e.message}")
                        null
                    }
                }

                _availableCameras.value = cameraList
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load cameras: ${e.message}")
                _availableCameras.value = emptyList()
            }
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    fun switchBackToFront(): Boolean {
        val streamer = streamerProvider()
        val videoSource = streamer?.videoInput?.sourceFlow?.value
        if (videoSource is ICameraSource) {
            scope.launch(defaultDispatcher) {
                videoSourceMutex.withLock {
                    streamer.switchBackToFront(application)
                }
            }
        }
        return true
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    fun toggleCamera() {
        val currentStreamer = streamerProvider()
        if (currentStreamer == null) {
            Log.e(TAG, "Streamer service not available for camera toggle")
            onErrorMessage("Service not available")
            return
        }

        val videoSource = currentStreamer.videoInput?.sourceFlow?.value
        if (videoSource is ICameraSource) {
            scope.launch(defaultDispatcher) {
                videoSourceMutex.withLock {
                    try {
                        delay(300)
                        currentStreamer.setNextCameraId(application)
                        Log.i(TAG, "Camera toggled successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to toggle camera", e)
                        onErrorMessage("Camera toggle failed: ${e.message}")
                    }
                }
            }
        } else {
            Log.w(TAG, "Video source is not a camera source, cannot toggle")
        }
    }

    private fun getWhiteBalanceModeName(mode: Int): String {
        return when (mode) {
            CaptureResult.CONTROL_AWB_MODE_OFF -> "Off"
            CaptureResult.CONTROL_AWB_MODE_AUTO -> "Auto"
            CaptureResult.CONTROL_AWB_MODE_INCANDESCENT -> "Incandescent"
            CaptureResult.CONTROL_AWB_MODE_FLUORESCENT -> "Fluorescent"
            CaptureResult.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "Warm Fluorescent"
            CaptureResult.CONTROL_AWB_MODE_DAYLIGHT -> "Daylight"
            CaptureResult.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "Cloudy"
            CaptureResult.CONTROL_AWB_MODE_TWILIGHT -> "Twilight"
            CaptureResult.CONTROL_AWB_MODE_SHADE -> "Shade"
            else -> "Unknown"
        }
    }

    private fun getAutoFocusModeName(mode: Int): String {
        return when (mode) {
            CaptureResult.CONTROL_AF_MODE_OFF -> "Manual"
            CaptureResult.CONTROL_AF_MODE_AUTO -> "Auto"
            CaptureResult.CONTROL_AF_MODE_MACRO -> "Macro"
            CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "Continuous Video"
            CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "Continuous Picture"
            CaptureResult.CONTROL_AF_MODE_EDOF -> "EDOF"
            else -> "Unknown"
        }
    }
}
