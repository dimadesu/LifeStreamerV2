package com.dimadesu.lifestreamer.services

import android.content.Context
import android.util.Log
import com.dimadesu.lifestreamer.data.storage.DataStoreRepository
import com.dimadesu.lifestreamer.srtla.SrtlaManager
import com.dimadesu.lifestreamer.utils.StreamConfigurationHelper
import io.github.thibaultbee.streampack.core.configuration.mediadescriptor.MediaDescriptor
import io.github.thibaultbee.streampack.core.interfaces.IWithAudioSource
import io.github.thibaultbee.streampack.core.interfaces.IWithVideoRotation
import io.github.thibaultbee.streampack.core.interfaces.IWithVideoSource
import io.github.thibaultbee.streampack.core.streamers.single.IAudioSingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.ISingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.IVideoSingleStreamer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manages the low-level lifecycle of a streaming session:
 * - Validating video and audio sources
 * - Managing SRTLA proxy startup and teardown
 * - Opening the endpoint descriptor with timeout
 * - Waiting for audio/video encoders to initialize
 * - Applying initial safe bitrates and attaching bitrate regulators
 * - Starting and stopping the StreamPack pipeline cleanly
 */
class StreamSessionManager(
    private val context: Context,
    private val storageRepository: DataStoreRepository,
    private val streamConfigurationHelper: StreamConfigurationHelper,
    private val streamerProvider: () -> ISingleStreamer?,
    private val rotationTracker: ServiceRotationTracker
) {
    companion object {
        private const val TAG = "StreamSessionManager"
    }

    /**
     * Validates that both video and audio sources are configured before streaming.
     */
    fun validateSourcesConfigured(): Pair<Boolean, String?> {
        val streamer = streamerProvider() ?: return false to "Streamer is null"
        val videoInput = (streamer as? IWithVideoSource)?.videoInput
        val audioInput = (streamer as? IWithAudioSource)?.audioInput
        val videoSource = videoInput?.sourceFlow?.value
        val audioSource = audioInput?.sourceFlow?.value

        return if (videoSource == null || audioSource == null) {
            val errorMsg = "video source=${videoSource != null}, audio source=${audioSource != null}"
            false to errorMsg
        } else {
            Log.d(TAG, "Sources validated - video: ${videoSource.javaClass.simpleName}, audio: ${audioSource.javaClass.simpleName}")
            true to null
        }
    }

    /**
     * Waits for sources to become available up to timeoutMs (used when starting from notification).
     */
    suspend fun waitForSourcesReady(timeoutMs: Long = 3000): Boolean {
        val streamer = streamerProvider() ?: return false
        val videoInput = (streamer as? IWithVideoSource)?.videoInput
        val audioInput = (streamer as? IWithAudioSource)?.audioInput

        return withTimeoutOrNull(timeoutMs) {
            while (videoInput?.sourceFlow?.value == null || audioInput?.sourceFlow?.value == null) {
                delay(200)
            }
            true
        } ?: false
    }

    /**
     * Opens the stream endpoint and starts streaming.
     * @param descriptor The media descriptor to open. If null, reads from DataStore.
     * @param onPreStart Callback right before calling startStream() on the streamer.
     * @return Result.success if stream started successfully, Result.failure otherwise.
     */
    suspend fun startSession(
        descriptor: MediaDescriptor? = null,
        onPreStart: (() -> Unit)? = null
    ): Result<Unit> {
        val streamer = streamerProvider() ?: return Result.failure(IllegalStateException("Streamer not available"))

        val (valid, sourceError) = validateSourcesConfigured()
        if (!valid) {
            return Result.failure(IllegalStateException("Cannot start stream: $sourceError"))
        }

        val targetDescriptor = descriptor ?: try {
            storageRepository.endpointDescriptorFlow.first()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        Log.i(TAG, "startSession: Opening descriptor $targetDescriptor")

        // 1. Pre-stream rotation
        rotationTracker.applyPreStreamRotation()

        // 2. SRTLA proxy if needed
        val srtlaConfig = storageRepository.srtlaConfigFlow.first()
        if (srtlaConfig != null) {
            Log.i(TAG, "SRTLA mode: starting embedded SRTLA proxy (${srtlaConfig.receiverHost}:${srtlaConfig.receiverPort})")
            SrtlaManager.start(context, srtlaConfig.receiverHost, srtlaConfig.receiverPort, srtlaConfig.listenPort)
        }

        // 3. Open connection with timeout
        try {
            withTimeout(5000) {
                withContext(Dispatchers.IO) {
                    streamer.open(targetDescriptor)
                }
            }
            Log.i(TAG, "startSession: open() completed")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open endpoint descriptor: ${e.message}")
            return Result.failure(e)
        }

        // 4. Wait for encoders to initialize
        val encodersReady = withTimeoutOrNull(5000) {
            while ((streamer as? IVideoSingleStreamer)?.videoEncoder == null ||
                   (streamer as? IAudioSingleStreamer)?.audioEncoder == null) {
                delay(200)
            }
            true
        } ?: false

        if (!encodersReady) {
            val videoEncoderExists = (streamer as? IVideoSingleStreamer)?.videoEncoder != null
            val audioEncoderExists = (streamer as? IAudioSingleStreamer)?.audioEncoder != null
            val errorMsg = "Encoders not ready after open (video=$videoEncoderExists, audio=$audioEncoderExists)"
            Log.e(TAG, "startSession: $errorMsg")
            return Result.failure(IllegalStateException(errorMsg))
        }

        // 5. Apply saved rotation right before startStream()
        rotationTracker.savedStreamingOrientation?.let { savedRotation ->
            try {
                (streamer as? IWithVideoRotation)?.setTargetRotation(savedRotation)
                Log.i(TAG, "startSession: Applied saved rotation $savedRotation to streamer")
            } catch (t: Throwable) {
                Log.w(TAG, "startSession: Failed to set target rotation: ${t.message}")
            }
        }

        onPreStart?.invoke()

        // 6. Start streaming pipeline
        try {
            withContext(Dispatchers.IO) {
                streamer.startStream()

                // Apply initial safe bitrate
                streamConfigurationHelper.applySafeInitialBitrate(
                    streamer as? IVideoSingleStreamer,
                    TAG
                )
            }
            Log.i(TAG, "startSession: startStream() succeeded")

            // 7. Attach bitrate regulator for SRT / RTMP
            try {
                streamConfigurationHelper.attachBitrateRegulator(
                    streamer as? IVideoSingleStreamer,
                    targetDescriptor.type.sinkType,
                    TAG
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to attach bitrate regulator: ${e.message}")
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "startSession: Failed to start stream after open: ${e.message}")
            return Result.failure(e)
        }
    }

    /**
     * Stops the active stream, proxy, and closes the endpoint.
     */
    suspend fun stopSession(closeTimeoutMs: Long = 3000): Boolean {
        return try {
            val streamer = streamerProvider()
            Log.i(TAG, "stopSession: Stopping stream...")
            streamer?.stopStream()

            // Stop SRTLA proxy if running
            if (SrtlaManager.isRunning) {
                Log.i(TAG, "stopSession: Stopping embedded SRTLA proxy")
                withContext(Dispatchers.IO) { SrtlaManager.stop() }
            }

            // Unlock stream rotation
            rotationTracker.unlockStreamRotation()

            // Close endpoint
            try {
                withContext(Dispatchers.IO) {
                    withTimeout(closeTimeoutMs) {
                        streamer?.close()
                    }
                }
                Log.i(TAG, "stopSession: Endpoint closed")
            } catch (e: Exception) {
                Log.w(TAG, "stopSession: Error closing endpoint: ${e.message}")
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "stopSession failed: ${e.message}", e)
            false
        }
    }
}
