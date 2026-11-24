package com.dimadesu.lifestreamer.rtmp.video

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import io.github.thibaultbee.streampack.core.elements.processing.video.ISurfaceProcessorInternal
import io.github.thibaultbee.streampack.core.elements.processing.video.source.ISourceInfoProvider
import io.github.thibaultbee.streampack.core.elements.sources.video.AbstractPreviewableSource
import io.github.thibaultbee.streampack.core.elements.sources.video.ISurfaceProcessorAwareSource
import io.github.thibaultbee.streampack.core.elements.sources.video.IVideoSourceInternal
import io.github.thibaultbee.streampack.core.elements.sources.video.SurfaceProcessorPreviewHelper
import io.github.thibaultbee.streampack.core.elements.sources.video.VideoSourceConfig
import io.github.thibaultbee.streampack.core.elements.utils.time.Timebase
import io.github.thibaultbee.streampack.core.utils.InternalStreamPackApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

@OptIn(InternalStreamPackApi::class)
class RTMPVideoSource(
    private val context: Context,
    private val exoPlayer: ExoPlayer,
    private val dispatcherProvider: io.github.thibaultbee.streampack.core.pipelines.IVideoDispatcherProvider
) : AbstractPreviewableSource(), IVideoSourceInternal, ISurfaceProcessorAwareSource {
    companion object {
        private const val TAG = "RTMPVideoSource"
    }

    override val timebase = Timebase.UPTIME

    private val cachedFormatWidth = AtomicInteger(0)
    private val cachedFormatHeight = AtomicInteger(0)
    private val cachedRotation = AtomicInteger(0)

    private fun makeInfoProvider(): ISourceInfoProvider {
        return object : ISourceInfoProvider {
            override fun getSurfaceSize(targetResolution: Size): Size {
                val w = cachedFormatWidth.get().takeIf { it > 0 } ?: targetResolution.width
                val h = cachedFormatHeight.get().takeIf { it > 0 } ?: targetResolution.height
                return Size(w, h)
            }

            override fun getRelativeRotationDegrees(targetRotation: Int, requiredMirroring: Boolean): Int = 0

            override val rotationDegrees: Int get() = 0

            override val isMirror: Boolean = false
        }
    }

    private val _infoProviderFlow = MutableStateFlow<ISourceInfoProvider>(makeInfoProvider())
    override val infoProviderFlow: StateFlow<ISourceInfoProvider> get() = _infoProviderFlow

    private val _isStreamingFlow = MutableStateFlow(false)
    override val isStreamingFlow: StateFlow<Boolean> get() = _isStreamingFlow

    private val _isPreviewingFlow = MutableStateFlow(false)
    override val isPreviewingFlow: StateFlow<Boolean> get() = _isPreviewingFlow

    private var outputSurface: Surface? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var playerListener: Player.Listener? = null
    private var isFormatListenerRegistered = false

    private val previewHelper = SurfaceProcessorPreviewHelper(
        context = context,
        infoProvider = { infoProviderFlow.value },
        isStreaming = { _isPreviewingFlow.value },
        needMirroring = { false },
        configuredInputSize = {
            val w = cachedFormatWidth.get()
            val h = cachedFormatHeight.get()
            if (w > 0 && h > 0) Size(w, h) else null
        }
    )

    private val formatListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            updateCachedFormat()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateCachedFormat()
        }
    }

    override fun setSurfaceProcessor(processor: ISurfaceProcessorInternal) {
        previewHelper.setSurfaceProcessor(processor)
    }

    private fun ensurePlayerStartedOnMain() {
        try {
            if (!isFormatListenerRegistered) {
                exoPlayer.addListener(formatListener)
                isFormatListenerRegistered = true
            }
            updateCachedFormat()
        } catch (_: Exception) {
        }

        outputSurface?.let { surface ->
            exoPlayer.setVideoSurface(surface)
        }

        try {
            if (exoPlayer.mediaItemCount > 0) {
                if (exoPlayer.playbackState == Player.STATE_IDLE) {
                    exoPlayer.prepare()
                }
                exoPlayer.playWhenReady = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "ExoPlayer preparation failed, but streaming can continue: ${e.message}")
        }

        if (playerListener == null) {
            playerListener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    Log.d(TAG, "Playback state changed to: $playbackState")
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "ExoPlayer error: ${error.message}", error)
                }
            }
            exoPlayer.addListener(playerListener!!)
        }
    }

    override suspend fun startStream() {
        _isStreamingFlow.value = true
        mainHandler.post {
            try {
                if (outputSurface == null) {
                    Log.w(TAG, "No output surface set - cannot start streaming")
                    _isStreamingFlow.value = false
                    return@post
                }
                ensurePlayerStartedOnMain()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting stream: ${e.message}", e)
                _isStreamingFlow.value = false
            }
        }
    }

    override suspend fun stopStream() {
        Log.d(TAG, "stopStream() called - streaming: ${_isStreamingFlow.value}")
        _isStreamingFlow.value = false
    }

    override suspend fun configure(config: VideoSourceConfig) {
        Log.d(TAG, "configure: resolution=${config.resolution}")
        withContext(Dispatchers.Main) {
            if (!isFormatListenerRegistered) {
                try {
                    exoPlayer.addListener(formatListener)
                    isFormatListenerRegistered = true
                    updateCachedFormat()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to register format listener in configure(): ${e.message}")
                }
            }
        }
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
        previewHelper.release(keepOutputOnProcessor = keepPreviewOutputOnProcessor)
        mainHandler.post {
            try {
                exoPlayer.setVideoSurface(null)
                if (exoPlayer.playbackState != Player.STATE_IDLE) {
                    exoPlayer.stop()
                }
                playerListener?.let { listener ->
                    try {
                        exoPlayer.removeListener(listener)
                    } catch (_: Exception) {
                    }
                    playerListener = null
                }
                if (isFormatListenerRegistered) {
                    try {
                        exoPlayer.removeListener(formatListener)
                    } catch (_: Exception) {
                    }
                    isFormatListenerRegistered = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing ExoPlayer: ${e.message}", e)
            }
        }
    }

    private fun updateCachedFormat() {
        try {
            val format = exoPlayer.videoFormat
            val w = format?.width ?: 0
            val h = format?.height ?: 0
            val rot = format?.rotationDegrees ?: 0
            val prevW = cachedFormatWidth.get()
            val prevH = cachedFormatHeight.get()
            val prevRot = cachedRotation.get()
            if (prevW != w || prevH != h || prevRot != rot) {
                Log.d(TAG, "Format changed: ${prevW}x${prevH} -> ${w}x${h}, rotation: $prevRot -> $rot")
                cachedFormatWidth.set(w)
                cachedFormatHeight.set(h)
                cachedRotation.set(rot)
                try {
                    _infoProviderFlow.value = makeInfoProvider()
                } catch (_: Exception) {
                }
                previewHelper.refreshIfAttached()
            }
        } catch (_: Exception) {
        }
    }

    override suspend fun getOutput(): Surface? = outputSurface

    override suspend fun setOutput(surface: Surface) {
        outputSurface = surface
        previewHelper.attachIfReady()
        mainHandler.post {
            try {
                exoPlayer.setVideoSurface(surface)
                Log.d(TAG, "Set ExoPlayer surface to main processor input surface")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting output surface: ${e.message}", e)
            }
        }
    }

    override suspend fun hasPreview(): Boolean = previewHelper.hasPreview()

    override suspend fun setPreview(surface: Surface) {
        previewHelper.setPreview(surface) {
            val w = cachedFormatWidth.get().takeIf { it > 0 } ?: 1920
            val h = cachedFormatHeight.get().takeIf { it > 0 } ?: 1080
            Size(w, h)
        }
    }

    override suspend fun startPreview() {
        _isPreviewingFlow.value = true
        mainHandler.post {
            try {
                ensurePlayerStartedOnMain()
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
        _isPreviewingFlow.value = false
        previewHelper.detachOutput()
    }

    override fun <T> getPreviewSize(targetSize: Size, targetClass: Class<T>): Size {
        if (cachedFormatWidth.get() <= 0 || cachedFormatHeight.get() <= 0) {
            mainHandler.post {
                updateCachedFormat()
            }
        }
        return if (targetSize.width > 0 && targetSize.height > 0) {
            previewHelper.selectPreviewSize(targetSize)
        } else {
            val w = cachedFormatWidth.get().takeIf { it > 0 } ?: 1920
            val h = cachedFormatHeight.get().takeIf { it > 0 } ?: 1080
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

    @OptIn(InternalStreamPackApi::class)
    class Factory(
        private val exoPlayer: ExoPlayer,
    ) : ISurfaceProcessorAwareSource.Factory {
        override suspend fun create(
            context: Context,
            dispatcherProvider: io.github.thibaultbee.streampack.core.pipelines.IVideoDispatcherProvider
        ): IVideoSourceInternal {
            return RTMPVideoSource(context, exoPlayer, dispatcherProvider)
        }

        override fun isSourceEquals(source: IVideoSourceInternal?): Boolean {
            return source is RTMPVideoSource
        }
    }
}
