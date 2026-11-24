package com.dimadesu.lifestreamer.uvc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import com.herohan.uvcapp.CameraHelper
import io.github.thibaultbee.streampack.core.elements.processing.video.ISurfaceProcessorInternal
import io.github.thibaultbee.streampack.core.elements.processing.video.source.ISourceInfoProvider
import io.github.thibaultbee.streampack.core.elements.sources.video.AbstractPreviewableSource
import io.github.thibaultbee.streampack.core.elements.sources.video.ISurfaceProcessorAwareSource
import io.github.thibaultbee.streampack.core.elements.sources.video.IVideoSourceInternal
import io.github.thibaultbee.streampack.core.elements.sources.video.SurfaceProcessorPreviewHelper
import io.github.thibaultbee.streampack.core.elements.sources.video.VideoSourceConfig
import io.github.thibaultbee.streampack.core.elements.utils.time.Timebase
import io.github.thibaultbee.streampack.core.utils.InternalStreamPackApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * UVC camera video source for StreamPack.
 *
 * Provides continuous video frames from a USB camera via UVCAndroid library.
 * Routes both streaming and preview through the pipeline's main [ISurfaceProcessorInternal].
 */
@OptIn(InternalStreamPackApi::class)
class UvcVideoSource(
    private val context: Context,
    private val cameraHelper: CameraHelper,
    @Suppress("UNUSED_PARAMETER")
    private val dispatcherProvider: io.github.thibaultbee.streampack.core.pipelines.IVideoDispatcherProvider
) : AbstractPreviewableSource(), IVideoSourceInternal, ISurfaceProcessorAwareSource {

    companion object {
        private const val TAG = "UvcVideoSource"
    }

    // Cached format values from CameraHelper
    private val cachedWidth = AtomicInteger(1920)
    private val cachedHeight = AtomicInteger(1080)

    private fun makeInfoProvider(): ISourceInfoProvider {
        return object : ISourceInfoProvider {
            override fun getSurfaceSize(targetResolution: Size): Size {
                val w = cachedWidth.get()
                val h = cachedHeight.get()
                return Size(w, h)
            }

            override val rotationDegrees: Int = 0
            override val isMirror: Boolean = false
        }
    }

    private val _infoProviderFlow = MutableStateFlow<ISourceInfoProvider>(makeInfoProvider())
    override val infoProviderFlow: StateFlow<ISourceInfoProvider> get() = _infoProviderFlow

    // Streaming state
    private val _isStreamingFlow = MutableStateFlow(false)
    override val isStreamingFlow: StateFlow<Boolean> get() = _isStreamingFlow

    // Preview state
    private val _isPreviewingFlow = MutableStateFlow(false)
    override val isPreviewingFlow: StateFlow<Boolean> get() = _isPreviewingFlow

    // Pipeline processor input surface
    private var outputSurface: Surface? = null

    private val previewHelper = SurfaceProcessorPreviewHelper(
        context = context,
        infoProvider = { infoProviderFlow.value },
        isStreaming = { _isPreviewingFlow.value },
        needMirroring = { false },
        configuredInputSize = { Size(cachedWidth.get(), cachedHeight.get()) }
    )

    // Main thread handler for UVC operations
    private val mainHandler = Handler(Looper.getMainLooper())

    // Pending cleanup management
    private var pendingCleanupRunnable: Runnable? = null

    // Track if this source has been released to avoid calling methods on dead CameraHelper
    @Volatile
    private var isReleased = false

    // Track if camera is ready (opened)
    @Volatile
    private var isCameraReady = false

    override val timebase = Timebase.UPTIME

    init {
        Log.d(TAG, "UvcVideoSource initialized")
        updateCachedFormat()
    }

    override fun setSurfaceProcessor(processor: ISurfaceProcessorInternal) {
        previewHelper.setSurfaceProcessor(processor)
    }

    /**
     * Called when the UVC camera has opened and is ready to stream.
     * This re-adds surfaces and starts the preview to ensure frames flow properly.
     */
    fun onCameraReady() {
        Log.d(TAG, "onCameraReady() called")
        isCameraReady = true
        updateCachedFormat()

        mainHandler.post {
            if (isReleased) return@post
            try {
                outputSurface?.let { surface ->
                    Log.d(TAG, "Re-adding output surface after camera ready")
                    cameraHelper.addSurface(surface, true)
                    cameraHelper.startPreview()
                    Log.i(TAG, "Camera preview started after camera ready")
                } ?: run {
                    Log.w(TAG, "No output surface available in onCameraReady")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onCameraReady: ${e.message}", e)
            }
        }
    }

    override suspend fun startStream() {
        Log.d(TAG, "startStream() called")
        _isStreamingFlow.value = true
        cancelPendingCleanup("startStream")

        mainHandler.post {
            if (isReleased) return@post
            try {
                updateCachedFormat()
                outputSurface?.let { surface ->
                    cameraHelper.addSurface(surface, true)
                    try {
                        cameraHelper.startPreview()
                    } catch (e: Exception) {
                        Log.d(TAG, "startPreview in startStream: ${e.message}")
                    }
                } ?: Log.w(TAG, "No surface available for streaming")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting stream: ${e.message}", e)
                _isStreamingFlow.value = false
            }
        }
    }

    override suspend fun stopStream() {
        Log.d(TAG, "stopStream() called")
        _isStreamingFlow.value = false

        mainHandler.post {
            try {
                if (!_isPreviewingFlow.value) {
                    cameraHelper.stopPreview()
                }
                schedulePendingCleanup(500) {
                    if (!_isStreamingFlow.value && !_isPreviewingFlow.value) {
                        cameraHelper.stopPreview()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping stream: ${e.message}", e)
            }
        }
    }

    override suspend fun configure(config: VideoSourceConfig) {
        Log.d(TAG, "configure() called")
        updateCachedFormat()
        previewHelper.refreshIfAttached()
    }

    override suspend fun release() {
        releaseInternal(keepPreviewOutputOnProcessor = false)
    }

    override suspend fun releaseKeepingPreviewOutput() {
        resetOutputInternal(keepPreviewOutputOnProcessor = true)
        releaseInternal(keepPreviewOutputOnProcessor = true)
    }

    private fun releaseInternal(keepPreviewOutputOnProcessor: Boolean) {
        Log.d(TAG, "release() called")
        isReleased = true
        cancelPendingCleanup("release")
        previewHelper.release(keepOutputOnProcessor = keepPreviewOutputOnProcessor)
        val surfaceToRemove = outputSurface
        outputSurface = null

        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                cameraHelper.stopPreview()
                surfaceToRemove?.let { cameraHelper.removeSurface(it) }
                Log.d(TAG, "UVC video source released on main thread (CameraHelper kept alive)")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing: ${e.message}", e)
            }
        } else {
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                try {
                    cameraHelper.stopPreview()
                    surfaceToRemove?.let { cameraHelper.removeSurface(it) }
                    Log.d(TAG, "UVC video source released (CameraHelper kept alive for reconnection)")
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing: ${e.message}", e)
                } finally {
                    latch.countDown()
                }
            }
            try {
                latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
            }
        }
    }

    override suspend fun getOutput(): Surface? = outputSurface

    override suspend fun setOutput(surface: Surface) {
        Log.d(TAG, "setOutput() called")
        outputSurface = surface
        previewHelper.attachIfReady()

        mainHandler.post {
            if (isReleased) return@post
            try {
                cancelPendingCleanup("setOutput")
                cameraHelper.addSurface(surface, true)
                if (isCameraReady && (_isPreviewingFlow.value || _isStreamingFlow.value)) {
                    try {
                        cameraHelper.startPreview()
                    } catch (_: Exception) {
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting output: ${e.message}", e)
            }
        }
    }

    override suspend fun hasPreview(): Boolean = previewHelper.hasPreview()

    override suspend fun setPreview(surface: Surface) {
        Log.d(TAG, "setPreview() called")
        previewHelper.setPreview(surface) { Size(cachedWidth.get(), cachedHeight.get()) }
        cancelPendingCleanup("setPreview")
    }

    override suspend fun startPreview() {
        Log.d(TAG, "startPreview() called")
        _isPreviewingFlow.value = true
        cancelPendingCleanup("startPreview")

        mainHandler.post {
            if (isReleased) return@post
            try {
                outputSurface?.let { surface ->
                    cameraHelper.addSurface(surface, false)
                    if (isCameraReady) {
                        try {
                            cameraHelper.startPreview()
                        } catch (e: Exception) {
                            Log.d(TAG, "startPreview in startPreview(): ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting preview: ${e.message}", e)
                _isPreviewingFlow.value = false
            }
        }
    }

    override suspend fun startPreview(surface: Surface) {
        setPreview(surface)
        startPreview()
    }

    override suspend fun stopPreview() {
        Log.d(TAG, "stopPreview() called")
        _isPreviewingFlow.value = false
        previewHelper.detachOutput()

        schedulePendingCleanup(500) {
            if (!_isStreamingFlow.value && !_isPreviewingFlow.value) {
                Log.d(TAG, "Full cleanup - stopping camera")
                cameraHelper.stopPreview()
            }
        }
    }

    override fun <T> getPreviewSize(targetSize: Size, targetClass: Class<T>): Size {
        return if (targetSize.width > 0 && targetSize.height > 0) {
            previewHelper.selectPreviewSize(targetSize)
        } else {
            val w = cachedWidth.get().takeIf { it > 0 } ?: 1920
            val h = cachedHeight.get().takeIf { it > 0 } ?: 1080
            previewHelper.selectPreviewSize(Size(w, h))
        }
    }

    override suspend fun resetPreviewImpl() {
        previewHelper.resetPreview()
    }

    override suspend fun resetOutputImpl() {
        resetOutputInternal(keepPreviewOutputOnProcessor = false)
    }

    private fun resetOutputInternal(keepPreviewOutputOnProcessor: Boolean) {
        if (!keepPreviewOutputOnProcessor) {
            previewHelper.detachOutput()
        }
        outputSurface = null
    }

    private fun updateCachedFormat() {
        try {
            val previewSize = cameraHelper.previewSize
            if (previewSize != null) {
                val w = previewSize.width
                val h = previewSize.height
                val prevW = cachedWidth.get()
                val prevH = cachedHeight.get()

                if (prevW != w || prevH != h) {
                    Log.d(TAG, "Format changed: ${prevW}x${prevH} -> ${w}x${h}")
                    cachedWidth.set(w)
                    cachedHeight.set(h)

                    try {
                        _infoProviderFlow.value = makeInfoProvider()
                    } catch (_: Exception) {
                    }
                    previewHelper.refreshIfAttached()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update cached format: ${e.message}")
        }
    }

    private fun cancelPendingCleanup(reason: String? = null) {
        pendingCleanupRunnable?.let {
            Log.d(TAG, "Cancelling pending cleanup${reason?.let { r -> ": $r" } ?: ""}")
            mainHandler.removeCallbacks(it)
            pendingCleanupRunnable = null
        }
    }

    private fun schedulePendingCleanup(delayMs: Long = 500L, action: () -> Unit) {
        cancelPendingCleanup("scheduling new cleanup")
        val runnable = Runnable {
            try {
                if (isReleased) {
                    return@Runnable
                }
                action()
            } catch (e: Exception) {
                Log.e(TAG, "Error in scheduled cleanup: ${e.message}", e)
            } finally {
                pendingCleanupRunnable = null
            }
        }
        pendingCleanupRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    /**
     * Factory for creating UVC video source instances
     */
    @OptIn(InternalStreamPackApi::class)
    class Factory(
        private val cameraHelper: CameraHelper
    ) : ISurfaceProcessorAwareSource.Factory {
        override suspend fun create(
            context: Context,
            dispatcherProvider: io.github.thibaultbee.streampack.core.pipelines.IVideoDispatcherProvider
        ): IVideoSourceInternal {
            return UvcVideoSource(context, cameraHelper, dispatcherProvider)
        }

        override fun isSourceEquals(source: IVideoSourceInternal?): Boolean {
            return source is UvcVideoSource
        }
    }
}
