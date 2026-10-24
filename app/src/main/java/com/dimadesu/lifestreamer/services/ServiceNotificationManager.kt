package com.dimadesu.lifestreamer.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.models.StreamStatus
import com.dimadesu.lifestreamer.services.utils.NotificationUtils
import com.dimadesu.lifestreamer.ui.main.MainActivity
import io.github.thibaultbee.streampack.core.streamers.single.ISingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.IVideoSingleStreamer
import java.util.Locale

/**
 * Manages foreground service notifications, actions, status updates, and lifecycle notification builders.
 */
class ServiceNotificationManager(
    private val service: Service,
    @DrawableRes private val notificationIconResourceId: Int,
    private val streamerProvider: () -> ISingleStreamer?,
    private val streamingStartTimeProvider: () -> Long?,
    private val isCurrentlyMutedProvider: () -> Boolean,
    private val isReconnectingProvider: () -> Boolean,
    val notificationUtils: NotificationUtils = NotificationUtils(service, CHANNEL_ID, NOTIFICATION_ID)
) {
    companion object {
        const val CHANNEL_ID = "camera_streaming_channel"
        const val NOTIFICATION_ID = 1001
    }

    var lastNotificationKey: String? = null

    // Cached PendingIntents
    lateinit var startPendingIntent: PendingIntent
        private set
    lateinit var stopPendingIntent: PendingIntent
        private set
    lateinit var mutePendingIntent: PendingIntent
        private set
    lateinit var exitPendingIntent: PendingIntent
        private set
    lateinit var openPendingIntent: PendingIntent
        private set

    fun initPendingIntents() {
        val startIntent = Intent(service, CameraStreamerService::class.java).apply {
            action = CameraStreamerService.ACTION_START_STREAM
        }
        startPendingIntent = PendingIntent.getService(
            service, 0, startIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(service, CameraStreamerService::class.java).apply {
            action = CameraStreamerService.ACTION_STOP_STREAM
        }
        stopPendingIntent = PendingIntent.getService(
            service, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val muteIntent = Intent(service, CameraStreamerService::class.java).apply {
            action = CameraStreamerService.ACTION_TOGGLE_MUTE
        }
        mutePendingIntent = PendingIntent.getService(
            service, 2, muteIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val exitActivityIntent = Intent(service, MainActivity::class.java).apply {
            action = CameraStreamerService.ACTION_EXIT_APP
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        exitPendingIntent = PendingIntent.getActivity(
            service, 4, exitActivityIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val tapOpenIntent = Intent(service, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = CameraStreamerService.ACTION_OPEN_FROM_NOTIFICATION
        }
        openPendingIntent = PendingIntent.getActivity(
            service, 3, tapOpenIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun createNotificationChannel(
        @StringRes nameResourceId: Int,
        @StringRes descriptionResourceId: Int
    ) {
        notificationUtils.createNotificationChannel(
            nameResourceId = nameResourceId,
            descriptionResourceId = descriptionResourceId
        )
    }

    fun currentMuteLabel(): String {
        return if (isCurrentlyMutedProvider()) {
            service.getString(R.string.service_notification_action_unmute)
        } else {
            service.getString(R.string.service_notification_action_mute)
        }
    }

    fun formatUptime(ms: Long): String {
        if (ms <= 0) return ""
        val totalSeconds = ms / 1000
        val seconds = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val minutes = totalMinutes % 60
        val hours = totalMinutes / 60

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun buildNotificationForStatus(status: StreamStatus): Pair<Notification, String> {
        val title = service.getString(R.string.service_notification_title)

        val statusLabel = when (status) {
            StreamStatus.STREAMING -> service.getString(R.string.status_streaming)
            StreamStatus.STARTING -> service.getString(R.string.status_starting)
            StreamStatus.CONNECTING -> service.getString(R.string.status_connecting)
            StreamStatus.ERROR -> service.getString(R.string.status_error)
            else -> service.getString(R.string.status_not_streaming)
        }

        val content = if (status == StreamStatus.STREAMING) {
            val startTime = streamingStartTimeProvider() ?: System.currentTimeMillis()
            val uptimeMillis = System.currentTimeMillis() - startTime
            val uptime = try { formatUptime(uptimeMillis) } catch (_: Throwable) { "" }
            if (uptime.isNotEmpty()) "$statusLabel • $uptime" else statusLabel
        } else statusLabel

        val muteLabel = currentMuteLabel()
        val showStart = status == StreamStatus.NOT_STREAMING
        val showStop = status == StreamStatus.STREAMING || 
                      status == StreamStatus.CONNECTING || 
                      status == StreamStatus.STARTING

        val videoEncoderRef = if (status == StreamStatus.STREAMING) {
            (streamerProvider() as? IVideoSingleStreamer)?.videoEncoder
        } else null
        val videoBitrate = videoEncoderRef?.bitrate
        val fpsText = try {
            videoEncoderRef?.getStats()?.let { s -> "%.1f fps".format(Locale.US, s.outputFps) }
        } catch (_: Throwable) { null }

        val bitrateText = videoBitrate?.let { b ->
            if (b >= 1_000_000) String.format(Locale.US, "%.2f Mbps", b / 1_000_000.0)
            else String.format(Locale.US, "%d kb/s", b / 1000)
        } ?: ""

        val contentWithBitrate = if (status == StreamStatus.STREAMING) {
            val vb = videoBitrate?.let { b ->
                if (b >= 1_000_000) String.format(Locale.US, "%.2f Mbps", b / 1_000_000.0)
                else String.format(Locale.US, "%d kb/s", b / 1000)
            } ?: ""
            val fpsAppend = fpsText?.let { " • $it" } ?: ""
            "$content • $vb$fpsAppend"
        } else content

        val finalContentText = contentWithBitrate
        val isFg = status == StreamStatus.STREAMING || status == StreamStatus.CONNECTING

        val notification = notificationUtils.createServiceNotification(
            title = title,
            content = finalContentText,
            iconResourceId = notificationIconResourceId,
            isForeground = isFg,
            showStart = showStart,
            showStop = showStop,
            startPending = startPendingIntent,
            stopPending = stopPendingIntent,
            muteLabel = muteLabel,
            mutePending = mutePendingIntent,
            exitPending = exitPendingIntent,
            openPending = openPendingIntent
        )

        val key = listOf(status.name, isCurrentlyMutedProvider(), content, bitrateText, fpsText ?: "", statusLabel).joinToString("|")
        return Pair(notification, key)
    }

    fun createDefaultNotification(content: String): Notification {
        return notificationUtils.createServiceNotification(
            title = service.getString(R.string.service_notification_title),
            content = content,
            iconResourceId = notificationIconResourceId,
            isForeground = true,
            showStart = true,
            showStop = false,
            startPending = startPendingIntent,
            stopPending = stopPendingIntent,
            muteLabel = currentMuteLabel(),
            mutePending = mutePendingIntent,
            exitPending = exitPendingIntent,
            openPending = openPendingIntent
        )
    }

    fun createOpenNotification(): Notification {
        return notificationUtils.createServiceNotification(
            title = service.getString(R.string.service_notification_title),
            content = service.getString(R.string.status_streaming),
            iconResourceId = notificationIconResourceId,
            isForeground = true,
            showStart = false,
            showStop = true,
            startPending = startPendingIntent,
            stopPending = stopPendingIntent,
            muteLabel = currentMuteLabel(),
            mutePending = mutePendingIntent,
            exitPending = exitPendingIntent,
            openPending = openPendingIntent
        )
    }

    fun createErrorNotification(errorMessage: String): Notification {
        return notificationUtils.createServiceNotification(
            title = service.getString(R.string.service_notification_title),
            content = errorMessage,
            iconResourceId = notificationIconResourceId,
            isForeground = false,
            showStart = true,
            showStop = false,
            startPending = startPendingIntent,
            stopPending = stopPendingIntent,
            muteLabel = currentMuteLabel(),
            mutePending = mutePendingIntent,
            exitPending = exitPendingIntent,
            openPending = openPendingIntent
        )
    }

    fun createCloseNotification(): Notification? {
        if (isReconnectingProvider()) {
            return null
        }
        return notificationUtils.createServiceNotification(
            title = service.getString(R.string.service_notification_title),
            content = service.getString(R.string.status_not_streaming),
            iconResourceId = notificationIconResourceId,
            isForeground = false,
            showStart = true,
            showStop = false,
            startPending = startPendingIntent,
            stopPending = stopPendingIntent,
            muteLabel = currentMuteLabel(),
            mutePending = mutePendingIntent,
            exitPending = exitPendingIntent,
            openPending = openPendingIntent
        )
    }

    fun notify(notification: Notification) {
        notificationUtils.notify(notification)
    }
}
