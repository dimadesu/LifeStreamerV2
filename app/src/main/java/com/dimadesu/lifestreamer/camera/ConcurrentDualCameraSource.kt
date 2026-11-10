package com.dimadesu.lifestreamer.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresPermission
import io.github.thibaultbee.streampack.core.elements.processing.video.IInputResolver
import io.github.thibaultbee.streampack.core.elements.processing.video.ISurfaceProcessorInternal
import io.github.thibaultbee.streampack.core.elements.processing.video.outputs.SurfaceOutput
import io.github.thibaultbee.streampack.core.elements.processing.video.source.ISourceInfoProvider
import io.github.thibaultbee.streampack.core.elements.sources.video.AbstractPreviewableSource
import io.github.thibaultbee.streampack.core.elements.sources.video.ISurfaceProcessorAwareSource
import io.github.thibaultbee.streampack.core.elements.sources.video.ISurfaceSourceInternal
import io.github.thibaultbee.streampack.core.elements.sources.video.IVideoSourceInternal
import io.github.thibaultbee.streampack.core.elements.sources.video.VideoSourceConfig
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSettings
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSourceFactory
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.ICameraSource
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.ICameraSourceInternal
import io.github.thibaultbee.streampack.core.elements.utils.extensions.displayRotation
import io.github.thibaultbee.streampack.core.elements.utils.extensions.rotate
import io.github.thibaultbee.streampack.core.logger.Logger
import io.github.thibaultbee.streampack.core.pipelines.IVideoDispatcherProvider
import io.github.thibaultbee.streampack.core.utils.InternalStreamPackApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A video source that captures video from two cameras concurrently (e.g. front and rear)
 * and composites them into a single frame using OpenGL.
 */
@OptIn(InternalStreamPackApi::class)
class ConcurrentDualCameraSource internal constructor(
    private val context: Context,
    internal val primaryCameraSource: AbstractPreviewableSource,
    internal val secondaryCameraSource: AbstractPreviewableSource,
    @Volatile var compositionMode: CompositionMode = CompositionMode.PIP_TOP_RIGHT
) : ICameraSource,
    ICameraSourceInternal,
    ISurfaceSourceInternal,
    ISurfaceProcessorAwareSource,
    IInputResolver,
    AbstractPreviewableSource() {

    private val primaryCamera = primaryCameraSource as ICameraSource
    private val secondaryCamera = secondaryCameraSource as ICameraSource

    override val cameraId: String
        get() = primaryCamera.cameraId

    val primaryCameraId: String
        get() = primaryCamera.cameraId

    val secondaryCameraId: String
        get() = secondaryCamera.cameraId

    override val settings: CameraSettings
        get() = primaryCamera.settings

    val secondarySettings: CameraSettings
        get() = secondaryCamera.settings

    override val timebase
        get() = primaryCameraSource.timebase

    override val infoProviderFlow
        get() = primaryCameraSource.infoProviderFlow

    override fun getInfoProvider(inputSurface: Surface): ISourceInfoProvider {
        return if (isSecondaryInput(inputSurface)) {
            secondaryCameraSource.infoProviderFlow.value
        } else {
            primaryCameraSource.infoProviderFlow.value
        }
    }

    override fun isSecondaryInput(inputSurface: Surface) = inputSurface == secondarySurface

    override fun getTargetRect(inputSurface: Surface): RectF? {
        val hasSecondary = secondarySurface != null && _secondaryCameraErrorFlow.value == null
        val rects = compositionMode.getNormalizedRects(if (hasSecondary) 2 else 1)
        return when (inputSurface) {
            primarySurface -> rects.getOrNull(0)
            secondarySurface -> rects.getOrNull(1)
            else -> null
        }
    }

    private var surfaceProcessor: ISurfaceProcessorInternal? = null
    private var primarySurface: Surface? = null
    private var secondarySurface: Surface? = null

    private val _isStreamingFlow = MutableStateFlow(false)
    override val isStreamingFlow: StateFlow<Boolean> = _isStreamingFlow.asStateFlow()

    private val _isPreviewingFlow = MutableStateFlow(false)
    override val isPreviewingFlow: StateFlow<Boolean> = _isPreviewingFlow.asStateFlow()

    private var savedConfig: VideoSourceConfig? = null

    /**
     * The preview shows the composited frame rendered by the surface processor, not the raw
     * primary camera frames. It is attached to the processor as an additional output.
     */
    private var previewSurface: Surface? = null
    private var previewOutput: SurfaceOutput? = null
    private var previewSize: Size? = null

    // Cameras must feed the processor while previewing, even when not streaming.
    private val camerasMutex = Mutex()
    private var isStreamRequested = false
    private var isPreviewRequested = false
    private var areCamerasActive = false
    private var isReleased = false

    private val _secondaryCameraErrorFlow = MutableStateFlow<Throwable?>(null)

    /**
     * Last failure to start the secondary (PiP) camera, `null` when it started fine.
     * When set, the source keeps streaming the primary camera only.
     */
    val secondaryCameraErrorFlow: StateFlow<Throwable?> = _secondaryCameraErrorFlow.asStateFlow()

    private fun computeSecondaryResolution(primaryResolution: Size, mode: CompositionMode): Size {
        val desired = when (mode) {
            CompositionMode.SIDE_BY_SIDE, CompositionMode.TOP_BOTTOM -> {
                if (primaryResolution.width > 1280) Size(1280, 720) else primaryResolution
            }
            else -> {
                if (primaryResolution.width >= 1920) Size(1280, 720) else Size(854, 480)
            }
        }
        return closestSupportedSize(secondaryCamera.cameraId, desired)
    }

    /**
     * Returns [desired] if the camera supports it, otherwise the supported size with the same
     * aspect ratio and the closest area. The camera would otherwise pick an arbitrary size with
     * another aspect ratio and the frame would be distorted.
     */
    private fun closestSupportedSize(cameraId: String, desired: Size): Size {
        return try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val sizes = manager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java)
                ?.toList()
                .orEmpty()
            if (sizes.isEmpty() || sizes.contains(desired)) {
                return desired
            }
            val desiredRatio = desired.width.toFloat() / desired.height
            val desiredArea = desired.width * desired.height
            sizes.minWith(
                compareBy<Size> { abs(it.width.toFloat() / it.height - desiredRatio) }
                    .thenBy { abs(it.width * it.height - desiredArea) }
            )
        } catch (t: Throwable) {
            Logger.w(TAG, "Failed to query sizes for camera $cameraId: ${t.message}")
            desired
        }
    }

    override fun setSurfaceProcessor(processor: ISurfaceProcessorInternal) {
        this.surfaceProcessor = processor
        processor.inputResolver = this
        attachPreviewOutput()
    }

    private fun detachPreviewOutput() {
        previewOutput?.let { surfaceProcessor?.removeOutputSurface(it) }
        previewOutput = null
    }

    private fun attachPreviewOutput() {
        val processor = surfaceProcessor ?: return
        val surface = previewSurface ?: return
        val size = previewSize ?: return

        val rotation = context.displayRotation
        if (previewOutput != null &&
            previewOutput?.targetResolution == size &&
            previewOutput?.targetRotation == rotation
        ) {
            return
        }

        // The processor drops its outputs when the input resolution changes: always re-add.
        detachPreviewOutput()
        try {
            val baseProvider = infoProviderFlow.value
            // The composited frame is already upright and sized as the preview: no letterboxing.
            val previewInfoProvider = object : ISourceInfoProvider by baseProvider {
                override fun getSurfaceSize(targetResolution: Size) =
                    targetResolution.rotate(baseProvider.getRelativeRotationDegrees(rotation, false))
            }
            val output = SurfaceOutput(
                targetSurface = surface,
                targetResolution = size,
                targetRotation = rotation,
                isStreaming = { true },
                sourceResolution = size,
                // Front camera selfie view in the preview only; the stream is not mirrored.
                needMirroring = true,
                sourceInfoProvider = previewInfoProvider
            )
            processor.addOutputSurface(output)
            previewOutput = output
        } catch (t: Throwable) {
            Logger.w(TAG, "Failed to attach preview output: ${t.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun updateCamerasState() = camerasMutex.withLock {
        val isNeeded = isStreamRequested || isPreviewRequested
        if (isNeeded && !areCamerasActive) {
            primaryCameraSource.startStream()
            try {
                secondaryCameraSource.startStream()
                _secondaryCameraErrorFlow.value = null
            } catch (t: Throwable) {
                Logger.e(TAG, "Failed to start secondary camera stream in concurrent mode", t)
                _secondaryCameraErrorFlow.value = t
            }
            areCamerasActive = true
        } else if (!isNeeded && areCamerasActive) {
            primaryCameraSource.stopStream()
            secondaryCameraSource.stopStream()
            areCamerasActive = false
        }
    }

    override suspend fun getOutput(): Surface? = primarySurface

    @RequiresPermission(Manifest.permission.CAMERA)
    override suspend fun setOutput(surface: Surface) {
        primarySurface = surface
        primaryCameraSource.setOutput(surface)

        val processor = surfaceProcessor
        if (processor != null) {
            processor.inputResolver = this
            val primaryRes = savedConfig?.resolution ?: Size(1920, 1080)
            val secondarySize = computeSecondaryResolution(primaryRes, compositionMode)
            val secSurface = processor.createInputSurface(
                secondarySize,
                secondaryCameraSource.timebase,
                isSecondary = true
            )
            this.secondarySurface = secSurface
            secondaryCameraSource.setOutput(secSurface)
        } else {
            Logger.w(TAG, "SurfaceProcessor not set on ConcurrentDualCameraSource when setting output")
        }
        attachPreviewOutput()
    }

    @SuppressLint("MissingPermission")
    override suspend fun resetOutputImpl() {
        detachPreviewOutput()
        primaryCameraSource.resetOutput()
        secondaryCameraSource.resetOutput()
        secondarySurface?.let {
            surfaceProcessor?.removeInputSurface(it)
            secondarySurface = null
        }
        primarySurface = null
        clearProcessorReference()
    }

    // The processor outlives this source: it must not keep pointing at a released dual source.
    private fun clearProcessorReference() {
        surfaceProcessor?.let {
            if (it.inputResolver === this) it.inputResolver = null
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    override suspend fun configure(config: VideoSourceConfig) {
        this.savedConfig = config
        primaryCameraSource.configure(config)
        val secResolution = computeSecondaryResolution(config.resolution, compositionMode)
        secondaryCameraSource.configure(config.copy(resolution = secResolution))
    }

    /**
     * The preview is the composited frame, already rotated upright: returns a size with the
     * orientation of [targetSize] instead of a sensor size.
     */
    override fun <T> getPreviewSize(targetSize: Size, targetClass: Class<T>): Size {
        val scale = minOf(1f, MAX_PREVIEW_DIMENSION.toFloat() / max(targetSize.width, targetSize.height))
        val size = Size(
            (targetSize.width * scale).roundToInt().coerceAtLeast(2) and 1.inv(),
            (targetSize.height * scale).roundToInt().coerceAtLeast(2) and 1.inv()
        )
        // Only remembered: the output is attached by setPreview, once the new surface exists.
        previewSize = size
        return size
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    override suspend fun startStream() {
        isStreamRequested = true
        updateCamerasState()
        _isStreamingFlow.value = true
    }

    override suspend fun stopStream() {
        isStreamRequested = false
        updateCamerasState()
        _isStreamingFlow.value = false
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    override suspend fun startPreview() {
        isPreviewRequested = true
        try {
            updateCamerasState()
        } catch (t: Throwable) {
            Logger.w(TAG, "Failed to start cameras for preview: ${t.message}")
        }
        _isPreviewingFlow.value = true
    }

    override suspend fun stopPreview() {
        isPreviewRequested = false
        try {
            updateCamerasState()
        } catch (t: Throwable) {
            Logger.w(TAG, "Failed to stop cameras after preview: ${t.message}")
        }
        _isPreviewingFlow.value = false
    }

    override suspend fun hasPreview(): Boolean = previewSurface != null

    @RequiresPermission(Manifest.permission.CAMERA)
    override suspend fun setPreview(surface: Surface) {
        previewSurface = surface
        if (previewSize == null) {
            previewSize = savedConfig?.resolution
        }
        attachPreviewOutput()
    }

    @SuppressLint("MissingPermission")
    override suspend fun resetPreviewImpl() {
        detachPreviewOutput()
        previewSurface = null
    }

    override suspend fun setOnFirstFrameListener(listener: () -> Unit) {
        primaryCameraSource.setOnFirstFrameListener(listener)
    }

    override suspend fun release() {
        if (isReleased) return
        isReleased = true
        isStreamRequested = false
        isPreviewRequested = false
        areCamerasActive = false
        _isStreamingFlow.value = false
        _isPreviewingFlow.value = false

        try {
            primaryCameraSource.release()
        } catch (_: Throwable) {}
        try {
            secondaryCameraSource.release()
        } catch (_: Throwable) {}

        resetOutputImpl()
    }

    companion object {
        private const val TAG = "ConcurrentDualCameraSource"
        private const val MAX_PREVIEW_DIMENSION = 1280
    }
}

/**
 * A factory to create a [ConcurrentDualCameraSource].
 *
 * @param primaryCameraId the primary camera ID (e.g. rear)
 * @param secondaryCameraId the secondary camera ID (e.g. front)
 * @param compositionMode the initial composition layout mode
 */
@OptIn(InternalStreamPackApi::class)
class ConcurrentDualCameraSourceFactory(
    val primaryCameraId: String,
    val secondaryCameraId: String,
    val compositionMode: CompositionMode = CompositionMode.PIP_TOP_RIGHT
) : ISurfaceProcessorAwareSource.Factory {
    @RequiresPermission(Manifest.permission.CAMERA)
    override suspend fun create(
        context: Context,
        dispatcherProvider: IVideoDispatcherProvider
    ): IVideoSourceInternal {
        val primaryCameraSource =
            CameraSourceFactory(primaryCameraId).create(context, dispatcherProvider) as AbstractPreviewableSource
        val secondaryCameraSource =
            CameraSourceFactory(secondaryCameraId).create(context, dispatcherProvider) as AbstractPreviewableSource

        return ConcurrentDualCameraSource(
            context,
            primaryCameraSource,
            secondaryCameraSource,
            compositionMode
        )
    }

    override fun isSourceEquals(source: IVideoSourceInternal?): Boolean {
        return source is ConcurrentDualCameraSource &&
                source.primaryCameraId == primaryCameraId &&
                source.secondaryCameraId == secondaryCameraId
    }

    override fun toString(): String {
        return "ConcurrentDualCameraSourceFactory(primary=$primaryCameraId, secondary=$secondaryCameraId, mode=$compositionMode)"
    }
}

