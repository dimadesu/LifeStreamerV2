package com.dimadesu.lifestreamer.services

import android.content.Context
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.dimadesu.lifestreamer.models.StreamOrientation
import io.github.thibaultbee.streampack.core.interfaces.IWithVideoRotation
import io.github.thibaultbee.streampack.core.streamers.orientation.IRotationProvider
import io.github.thibaultbee.streampack.core.streamers.orientation.SensorRotationProvider
import io.github.thibaultbee.streampack.core.streamers.single.ISingleStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Tracks device sensor rotation, manages locked rotation during active streaming sessions,
 * and maintains orientation consistency across stream drop reconnections.
 */
class ServiceRotationTracker(
    private val context: Context,
    private val scope: CoroutineScope,
    private val streamerProvider: () -> ISingleStreamer?,
    private val cachedOrientationProvider: () -> StreamOrientation
) {
    companion object {
        private const val TAG = "ServiceRotationTracker"

        fun rotationToString(rotation: Int): String = when (rotation) {
            Surface.ROTATION_0 -> "ROTATION_0 (Portrait)"
            Surface.ROTATION_90 -> "ROTATION_90 (Landscape Left)"
            Surface.ROTATION_180 -> "ROTATION_180 (Portrait Upside Down)"
            Surface.ROTATION_270 -> "ROTATION_270 (Landscape Right)"
            else -> "UNKNOWN ($rotation)"
        }
    }

    var currentRotation: Int = Surface.ROTATION_0
        private set
    var lockedStreamRotation: Int? = null
        private set
    var savedStreamingOrientation: Int? = null
        private set

    private var localRotationProvider: IRotationProvider? = null
    private var localRotationListener: IRotationProvider.Listener? = null

    fun detectCurrentRotation(): Int {
        try {
            @Suppress("DEPRECATION")
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            val rotation = windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            currentRotation = rotation
            Log.d(TAG, "Current rotation detected: ${rotationToString(rotation)}")
            return rotation
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect current rotation: ${e.message}")
            return Surface.ROTATION_0
        }
    }

    fun start() {
        scope.launch(Dispatchers.Default) {
            try {
                val rotationProvider = SensorRotationProvider(context)
                val listener = object : IRotationProvider.Listener {
                    override fun onOrientationChanged(rotation: Int) {
                        if (lockedStreamRotation != null) {
                            Log.i(TAG, "SENSOR: Ignoring rotation change to ${rotationToString(rotation)} - LOCKED to ${rotationToString(lockedStreamRotation!!)}")
                            return
                        }
                        if (cachedOrientationProvider().toSurfaceRotation() != null) {
                            Log.i(TAG, "SENSOR: Ignoring rotation change to ${rotationToString(rotation)} - fixed orientation setting active")
                            return
                        }
                        Log.i(TAG, "SENSOR: Rotation changed to ${rotationToString(rotation)} - NOT LOCKED, applying")

                        try {
                            scope.launch {
                                try {
                                    (streamerProvider() as? IWithVideoRotation)?.setTargetRotation(rotation)
                                    currentRotation = rotation
                                    Log.d(TAG, "Rotation updated to ${rotationToString(rotation)}")
                                } catch (_: Throwable) {}
                            }
                        } catch (_: Throwable) {}
                    }
                }
                rotationProvider.addListener(listener)
                localRotationProvider = rotationProvider
                localRotationListener = listener
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to register rotation provider: ${t.message}")
            }
        }
    }

    fun lockStreamRotation(rotation: Int) {
        val wasLocked = lockedStreamRotation
        lockedStreamRotation = rotation
        savedStreamingOrientation = rotation
        currentRotation = rotation
        Log.i(TAG, "lockStreamRotation: Setting lock from ${if (wasLocked != null) rotationToString(wasLocked) else "null"} to ${rotationToString(rotation)}, saved for reconnection")
    }

    fun unlockStreamRotation() {
        lockedStreamRotation = null
        savedStreamingOrientation = null
        Log.i(TAG, "Stream rotation explicitly unlocked - cleared saved orientation, will follow sensor again")
    }

    fun handleStreamingStart() {
        Log.i(TAG, "handleStreamingStart: lockedStreamRotation = ${if (lockedStreamRotation != null) rotationToString(lockedStreamRotation!!) else "null"}, savedStreamingOrientation = ${if (savedStreamingOrientation != null) rotationToString(savedStreamingOrientation!!) else "null"}")

        if (lockedStreamRotation == null) {
            val fixedRotation = cachedOrientationProvider().toSurfaceRotation()
            if (fixedRotation != null) {
                currentRotation = fixedRotation
                Log.i(TAG, "handleStreamingStart: INITIAL START - Using fixed orientation setting ${rotationToString(fixedRotation)}")
            } else {
                detectCurrentRotation()
            }
            lockedStreamRotation = currentRotation
            savedStreamingOrientation = currentRotation
            Log.i(TAG, "handleStreamingStart: INITIAL START - Locked to ${rotationToString(currentRotation)}, saved for reconnection")
        } else if (savedStreamingOrientation != null) {
            lockedStreamRotation = savedStreamingOrientation
            Log.i(TAG, "handleStreamingStart: RECONNECTION - Restoring saved orientation ${rotationToString(savedStreamingOrientation!!)}")
        } else {
            Log.i(TAG, "handleStreamingStart: LOCK EXISTS - Maintaining ${rotationToString(lockedStreamRotation!!)} through reconnection")
        }
    }

    suspend fun applyPreStreamRotation() {
        val fixedRotation = cachedOrientationProvider().toSurfaceRotation()
        if (fixedRotation != null) {
            currentRotation = fixedRotation
            Log.i(TAG, "applyPreStreamRotation: Using fixed orientation setting ${rotationToString(fixedRotation)}")
        } else if (savedStreamingOrientation != null) {
            currentRotation = savedStreamingOrientation!!
            Log.i(TAG, "applyPreStreamRotation: Reconnecting - Restoring saved orientation ${rotationToString(currentRotation)}")
        } else {
            detectCurrentRotation()
        }

        try {
            (streamerProvider() as? IWithVideoRotation)?.setTargetRotation(currentRotation)
            Log.i(TAG, "applyPreStreamRotation: Applied target rotation ${rotationToString(currentRotation)} to streamer")
        } catch (t: Throwable) {
            Log.w(TAG, "applyPreStreamRotation: Failed to set target rotation: ${t.message}")
        }
        lockStreamRotation(currentRotation)
        Log.i(TAG, "applyPreStreamRotation: Pre-locked stream rotation to ${rotationToString(currentRotation)}")
    }

    suspend fun onOrientationSettingChanged(orientation: StreamOrientation) {
        val fixedRotation = orientation.toSurfaceRotation()
        if (fixedRotation != null && lockedStreamRotation == null) {
            try {
                (streamerProvider() as? IWithVideoRotation)?.setTargetRotation(fixedRotation)
                currentRotation = fixedRotation
                Log.i(TAG, "Applied fixed orientation to streamer: ${rotationToString(fixedRotation)}")
            } catch (_: Throwable) {}
        }
    }

    fun release() {
        try {
            localRotationListener?.let { localRotationProvider?.removeListener(it) }
            localRotationProvider = null
            localRotationListener = null
        } catch (_: Exception) {}
    }
}
