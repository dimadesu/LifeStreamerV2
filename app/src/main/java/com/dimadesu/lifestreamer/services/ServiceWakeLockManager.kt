package com.dimadesu.lifestreamer.services

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * Manages CPU partial wake locks and high-performance network wake locks
 * to ensure uninterrupted background audio recording and video streaming.
 */
class ServiceWakeLockManager(context: Context) {
    companion object {
        private const val TAG = "ServiceWakeLockMgr"
    }

    private val powerManager: PowerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var networkWakeLock: PowerManager.WakeLock? = null

    /**
     * Acquire wake lock to prevent CPU/audio silencing in the background.
     */
    fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "StreamPack::StableBackgroundAudioRecording"
            ).apply {
                acquire()
                Log.i(TAG, "Wake lock acquired for stable background audio recording")
            }
        }
    }

    /**
     * Release CPU wake lock.
     */
    fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
                Log.i(TAG, "Wake lock released")
            }
            wakeLock = null
        }
    }

    /**
     * Acquire network wake lock to prevent throttling during SRT/RTMP upload in the background.
     */
    fun acquireNetworkWakeLock() {
        if (networkWakeLock == null) {
            networkWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LifeStreamer::NetworkUpload"
            ).apply {
                acquire()
                Log.i(TAG, "Network wake lock acquired for SRT/RTMP upload")
            }
        }
    }

    /**
     * Release network wake lock.
     */
    fun releaseNetworkWakeLock() {
        networkWakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
                Log.i(TAG, "Network wake lock released")
            }
            networkWakeLock = null
        }
    }

    /**
     * Release all held wake locks.
     */
    fun releaseAll() {
        releaseWakeLock()
        releaseNetworkWakeLock()
    }
}
