package com.dimadesu.lifestreamer.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.os.Build
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Controller managing camera video and optical stabilization:
 * - Detects per-camera capabilities (Off, Optical/OIS, Video/EIS, Preview)
 * - Resets to the best default mode on camera change, like other camera controls
 * - Ensures mutual exclusivity between OIS and EIS
 * - Exposes reactive state for UI controls
 */
class CameraStabilizationController {

    private val _isAvailable = MutableLiveData(false)
    val isAvailable: LiveData<Boolean> = _isAvailable

    private val _availableModes = MutableLiveData<List<Int>>(emptyList())
    val availableModes: LiveData<List<Int>> = _availableModes

    private val _currentMode = MutableLiveData(STABILIZATION_MODE_OFF)
    val currentMode: LiveData<Int> = _currentMode

    var userPreferredMode: Int? = null
        private set

    /**
     * Called when the active camera source changes.
     * Resets the user preference, computes available modes, picks the best default mode,
     * and applies it when active.
     */
    fun onCameraChanged(settings: CameraSettings, scope: CoroutineScope) {
        userPreferredMode = null
        val availModes = getAvailableModes(settings.characteristics)
        _isAvailable.postValue(availModes.size > 1)
        _availableModes.postValue(availModes)

        val targetMode = when {
            userPreferredMode != null && availModes.contains(userPreferredMode) -> {
                userPreferredMode!!
            }
            availModes.contains(STABILIZATION_MODE_OPTICAL) -> {
                STABILIZATION_MODE_OPTICAL
            }
            availModes.contains(STABILIZATION_MODE_VIDEO) -> {
                STABILIZATION_MODE_VIDEO
            }
            else -> {
                STABILIZATION_MODE_OFF
            }
        }
        _currentMode.postValue(targetMode)

        scope.launch {
            try {
                if (!settings.isActiveFlow.value) {
                    withTimeoutOrNull(2000) {
                        settings.isActiveFlow.first { it }
                    }
                }
                if (settings.isActiveFlow.value) {
                    applyModeToCamera(settings, targetMode)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to apply initial stabilization mode: ${t.message}")
            }
        }
    }

    /**
     * Resets stabilization state when switching to a non-camera source.
     */
    fun onSourceReset() {
        _isAvailable.postValue(false)
        _availableModes.postValue(emptyList())
    }

    /**
     * Sets a stabilization mode on the active camera session.
     */
    fun setMode(
        settings: CameraSettings?,
        mode: Int,
        isUserExplicit: Boolean = true,
        scope: CoroutineScope,
        onFeedback: ((String) -> Unit)? = null
    ) {
        if (isUserExplicit) {
            userPreferredMode = mode
        }
        if (settings == null) {
            Log.e(TAG, "Camera settings is not accessible")
            return
        }
        scope.launch {
            try {
                if (settings.isActiveFlow.value) {
                    applyModeToCamera(settings, mode)
                    _currentMode.postValue(mode)
                    if (isUserExplicit) {
                        val modeName = getStabilizationModeName(mode)
                        onFeedback?.invoke("Stabilisation: $modeName")
                    }
                } else {
                    Log.w(TAG, "Cannot set stabilization: camera session is not active")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "setStabilizationMode failed (camera session may be closed): ${t.message}")
            }
        }
    }

    /**
     * Toggles through available stabilization modes sequentially.
     */
    fun toggleMode(
        settings: CameraSettings?,
        scope: CoroutineScope,
        onFeedback: ((String) -> Unit)? = null
    ) {
        val modes = _availableModes.value ?: return
        if (modes.isEmpty()) return
        val current = _currentMode.value ?: STABILIZATION_MODE_OFF
        val currentIndex = modes.indexOf(current)
        val nextMode = modes[(currentIndex + 1) % modes.size]
        setMode(settings, nextMode, isUserExplicit = true, scope = scope, onFeedback = onFeedback)
    }

    /**
     * Discovers supported stabilization modes from [CameraCharacteristics].
     */
    fun getAvailableModes(characteristics: CameraCharacteristics): List<Int> {
        val modes = mutableListOf<Int>()
        modes.add(STABILIZATION_MODE_OFF)

        val oisModes = characteristics[CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION]
        val hasOis = oisModes?.contains(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE_ON) == true
        if (hasOis) {
            modes.add(STABILIZATION_MODE_OPTICAL)
        }

        val videoModes = characteristics[CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES]
        val hasVideo = videoModes?.contains(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true
        if (hasVideo) {
            modes.add(STABILIZATION_MODE_VIDEO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPreview = videoModes?.contains(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION) == true
            if (hasPreview) {
                modes.add(STABILIZATION_MODE_PREVIEW)
            }
        }

        return modes
    }

    /**
     * Applies the stabilization mode to [CameraSettings] ensuring optical and video
     * stabilization are mutually exclusive before submitting to the capture session.
     */
    suspend fun applyModeToCamera(settings: CameraSettings, mode: Int) {
        val isOisAvailable = settings.stabilization.isOpticalAvailable
        when (mode) {
            STABILIZATION_MODE_OFF -> {
                if (isOisAvailable) {
                    settings.set(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        CaptureResult.LENS_OPTICAL_STABILIZATION_MODE_OFF
                    )
                }
                settings.set(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                )
            }
            STABILIZATION_MODE_OPTICAL -> {
                settings.set(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                )
                if (isOisAvailable) {
                    settings.set(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        CaptureResult.LENS_OPTICAL_STABILIZATION_MODE_ON
                    )
                }
            }
            STABILIZATION_MODE_VIDEO -> {
                if (isOisAvailable) {
                    settings.set(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        CaptureResult.LENS_OPTICAL_STABILIZATION_MODE_OFF
                    )
                }
                settings.set(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_ON
                )
            }
            STABILIZATION_MODE_PREVIEW -> {
                if (isOisAvailable) {
                    settings.set(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        CaptureResult.LENS_OPTICAL_STABILIZATION_MODE_OFF
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    settings.set(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                    )
                }
            }
        }
        settings.applyRepeatingSession()
    }

    companion object {
        private const val TAG = "CameraStabilizationCtrl"

        const val STABILIZATION_MODE_OFF = 0
        const val STABILIZATION_MODE_OPTICAL = 1
        const val STABILIZATION_MODE_VIDEO = 2
        const val STABILIZATION_MODE_PREVIEW = 3

        fun getStabilizationModeName(mode: Int): String {
            return when (mode) {
                STABILIZATION_MODE_OFF -> "Off"
                STABILIZATION_MODE_OPTICAL -> "Optical (OIS)"
                STABILIZATION_MODE_VIDEO -> "Video (EIS)"
                STABILIZATION_MODE_PREVIEW -> "Preview"
                else -> "Unknown"
            }
        }
    }
}
