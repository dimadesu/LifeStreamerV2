/*
 * Copyright (C) 2021 Thibault B.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dimadesu.lifestreamer.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.util.SizeF
import kotlin.math.atan
import kotlin.math.sqrt
import kotlin.math.PI
import androidx.appcompat.app.AlertDialog
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.ICameraSource
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSettings
import io.github.thibaultbee.streampack.core.interfaces.setCameraId
import android.app.AppOpsManager
import android.content.Context
import android.media.AudioManager
import android.content.Intent
import android.net.Uri
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.dimadesu.lifestreamer.ApplicationConstants
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.databinding.MainFragmentBinding
import com.dimadesu.lifestreamer.models.StreamOrientation
import com.dimadesu.lifestreamer.models.StreamStatus
import com.dimadesu.lifestreamer.ui.main.compose.CameraControlsBar
import com.dimadesu.lifestreamer.ui.main.compose.CameraControlsBottomDrawer
import com.dimadesu.lifestreamer.ui.main.compose.CameraSliders
import com.dimadesu.lifestreamer.ui.main.compose.LiveButton
import com.dimadesu.lifestreamer.ui.main.compose.SourceControlsBar
import com.dimadesu.lifestreamer.ui.main.compose.TopRightControlsBar
import android.content.ActivityNotFoundException
import com.dimadesu.lifestreamer.utils.DialogUtils
import com.dimadesu.lifestreamer.utils.PermissionManager
import io.github.thibaultbee.streampack.core.interfaces.IStreamer
import io.github.thibaultbee.streampack.core.interfaces.IWithVideoSource
import io.github.thibaultbee.streampack.core.elements.sources.video.IPreviewableSource
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import io.github.thibaultbee.streampack.ui.views.PreviewView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.core.view.children

class PreviewFragment : Fragment(R.layout.main_fragment) {
    private lateinit var binding: MainFragmentBinding

    private val previewViewModel: PreviewViewModel by viewModels {
        PreviewViewModelFactory(requireActivity().application)
    }

    // Remember the orientation AND rotation that was locked when streaming started
    // This allows us to restore the exact same orientation when returning from background
    private var rememberedLockedOrientation: Int? = null
    private var rememberedRotation: Int? = null

    // MediaProjection permission launcher - connects to MediaProjectionHelper
    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>
    // BLUETOOTH_CONNECT permission launcher
    private lateinit var bluetoothConnectLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize MediaProjection launcher with helper
        mediaProjectionLauncher = previewViewModel.mediaProjectionHelper.registerLauncher(this)
        // Initialize BLUETOOTH_CONNECT launcher
        bluetoothConnectLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                Toast.makeText(requireContext(), "Bluetooth permission granted", Toast.LENGTH_SHORT).show()
                // Now that permission is granted, enable the BT toggle
                previewViewModel.setUseBluetoothMic(true)
            } else {
                Toast.makeText(requireContext(), "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = MainFragmentBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = this
        binding.viewmodel = previewViewModel
        bindProperties()
        return binding.root
    }

    @SuppressLint("MissingPermission")
    private fun bindProperties() {
        binding.cameraControlsComposeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CameraControlsBar(viewModel = previewViewModel)
            }
        }

        binding.cameraDrawerComposeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CameraControlsBottomDrawer(viewModel = previewViewModel)
            }
        }

        binding.cameraSlidersComposeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CameraSliders(viewModel = previewViewModel)
            }
        }

        binding.topRightControlsComposeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TopRightControlsBar(viewModel = previewViewModel)
            }
        }

        childFragmentManager.setFragmentResultListener(
            ExplanationDialogFragment.SYS_AUDIO_TAG, viewLifecycleOwner
        ) { _, result ->
            if (result.getBoolean(ExplanationDialogFragment.RESULT_CONFIRMED)) {
                previewViewModel.sysAudioExplanationShown = true
                previewViewModel.toggleSystemAudioForCamera(mediaProjectionLauncher)
            }
        }

        childFragmentManager.setFragmentResultListener(
            ExplanationDialogFragment.RTMP_AUDIO_TAG, viewLifecycleOwner
        ) { _, result ->
            if (result.getBoolean(ExplanationDialogFragment.RESULT_CONFIRMED)) {
                previewViewModel.rtmpAudioExplanationShown = true
                val rtmpIndex = previewViewModel.pendingRtmpIndex ?: return@setFragmentResultListener
                previewViewModel.pendingRtmpIndex = null
                previewViewModel.toggleVideoSource(mediaProjectionLauncher, rtmpIndex = rtmpIndex)
            }
        }

        binding.liveButton.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LiveButton(
                    viewModel = previewViewModel,
                    onClick = {
                        val currentStatus = previewViewModel.streamStatus.value
                        Log.d(TAG, "Live button clicked - currentStatus: $currentStatus")
                        when (currentStatus) {
                            StreamStatus.NOT_STREAMING, StreamStatus.ERROR -> {
                                Log.d(TAG, "Starting stream...")
                                startStreamIfPermissions(previewViewModel.requiredPermissions)
                            }
                            StreamStatus.STARTING, StreamStatus.CONNECTING, StreamStatus.STREAMING -> {
                                Log.d(TAG, "Stopping stream...")
                                stopStream()
                            }
                        }
                    }
                )
            }
        }

        // Commented out along with the switchCameraButton in XML
        /*
        binding.switchCameraButton.setOnClickListener {
            showCameraSelectionDialog()
        }
        */

        binding.sourceControlsComposeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                SourceControlsBar(
                    viewModel = previewViewModel,
                    onSelectCamera = { cameraId ->
                        lifecycleScope.launch {
                            try {
                                (previewViewModel.streamer as? IWithVideoSource)?.setCameraId(cameraId)
                                Log.i(TAG, "Switched to camera: $cameraId")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to switch camera: ${e.message}", e)
                                Toast.makeText(requireContext(), "Failed to switch camera", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onToggleRtmpSource = { rtmpIndex ->
                        toggleVideoSourceWithExplanation(rtmpIndex = rtmpIndex)
                    },
                    onLaunchRtmpServer = {
                        val pkg = "com.dimadesu.mediasrvr"
                        val launchIntent = requireContext().packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        } else {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
                            } catch (e: ActivityNotFoundException) {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
                            }
                        }
                    },
                    onToggleUvc = {
                        previewViewModel.toggleUvcSource()
                    },
                    onLaunchUvcTest = {
                        val intent = Intent(requireContext(), com.dimadesu.lifestreamer.uvc.UvcTestActivity::class.java)
                        startActivity(intent)
                    },
                    onToggleScreen = {
                        previewViewModel.toggleScreenSource(mediaProjectionLauncher)
                    },
                    onToggleMonitorAudio = {
                        previewViewModel.toggleMonitorAudio()
                    },
                    onToggleBluetoothMic = {
                        previewViewModel.setUseBluetoothMic(!(previewViewModel.useBluetoothMic.value ?: false))
                    },
                    onToggleSystemAudio = {
                        val isTurningOn = previewViewModel.useSystemAudioForCameraLiveData.value != true
                        if (isTurningOn && !previewViewModel.sysAudioExplanationShown) {
                            if (childFragmentManager.findFragmentByTag(ExplanationDialogFragment.SYS_AUDIO_TAG) == null) {
                                ExplanationDialogFragment.newInstance(
                                    R.string.audio_out_explanation_title,
                                    R.string.audio_out_explanation_message,
                                    ExplanationDialogFragment.SYS_AUDIO_TAG
                                ).show(childFragmentManager, ExplanationDialogFragment.SYS_AUDIO_TAG)
                            }
                        } else {
                            previewViewModel.toggleSystemAudioForCamera(mediaProjectionLauncher)
                        }
                    },
                    onToggleAudioDebug = {
                        previewViewModel.toggleAudioDebugOverlay()
                    },
                    onToggleSrtlaStats = {
                        previewViewModel.setSrtlaStatsVisible(binding.srtlaStatsScrollView.visibility != View.VISIBLE)
                    }
                )
            }
        }

        previewViewModel.isSrtlaStatsVisible.observe(viewLifecycleOwner) { visible ->
            if (visible) {
                binding.srtlaStatsScrollView.visibility = View.VISIBLE
                binding.srtlaStatsView.startStatsUpdates()
            } else {
                binding.srtlaStatsView.stopStatsUpdates()
                binding.srtlaStatsScrollView.visibility = View.GONE
            }
        }

        // Observe Moblink relay status and push to SrtlaStatsView
        viewLifecycleOwner.lifecycleScope.launch {
            com.dimadesu.lifestreamer.srtla.SrtlaManager.relays.collect { relays ->
                binding.srtlaStatsView.setRelays(relays)
            }
        }

        // Setup audio source spinner
        setupAudioSourceSpinner()

        // Dynamically adjust preview aspect ratio to match the stream resolution.
        // Also called from onConfigurationChanged so rotation re-applies the correct scale.
        previewViewModel.videoConfigLiveData.observe(viewLifecycleOwner) { config ->
            applyPreviewScale(config)
        }

        previewViewModel.streamerErrorLiveData.observe(viewLifecycleOwner) { error ->
            error?.let {
                showError("Oops", it)
                previewViewModel.clearStreamerError() // Clear after showing to prevent re-show on rotation
            }
        }

        previewViewModel.endpointErrorLiveData.observe(viewLifecycleOwner) { error ->
            error?.let {
                showError("Endpoint error", it)
                previewViewModel.clearEndpointError() // Clear after showing to prevent re-show on rotation
            }
        }

        previewViewModel.toastMessageLiveData.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                previewViewModel.clearToastMessage() // Clear after showing to prevent re-show on rotation
            }
        }

        // Observe service requests to ask BLUETOOTH_CONNECT permission
        previewViewModel.bluetoothConnectRequestLiveData.observe(viewLifecycleOwner) { req ->
            req?.let {
                // Launch permission request only if we don't already have it
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val granted = ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        bluetoothConnectLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                    }
                }
                // Clear the event by posting null so it doesn't retrigger
                previewViewModel.clearBluetoothConnectRequest()
            }
        }

        // SCO negotiation state is provided by the Service and exposed via ViewModel.scoStateLiveData
        // It will update the bound `audioScoStatusText` through data binding.

        // Reconnection status is now displayed via data binding in the layout XML
        // No need for manual observer - the TextView will automatically show/hide

        // Lock/unlock orientation based on streaming state and reconnection status
        // Keep orientation locked during STARTING, CONNECTING, and STREAMING
        previewViewModel.isStreamingLiveData.observe(viewLifecycleOwner) { isStreaming ->
            val currentStatus = previewViewModel.streamStatus.value
            val isReconnecting = previewViewModel.isReconnectingLiveData.value ?: false
            Log.d(TAG, "Streaming state changed to: $isStreaming, status: $currentStatus, reconnecting: $isReconnecting")
            if (isStreaming) {
                // Check if Service already has a saved orientation (lifecycle restoration)
                // If so, don't lock again - onStart()/onResume() will restore it
                if (shouldLockOrientation("isStreamingLiveData observer")) {
                    lockOrientation()
                }
            } else {
                // Only unlock if we're truly stopped AND not reconnecting
                val shouldStayLocked = currentStatus == com.dimadesu.lifestreamer.models.StreamStatus.STARTING ||
                                      currentStatus == com.dimadesu.lifestreamer.models.StreamStatus.CONNECTING ||
                                      isReconnecting
                if (!shouldStayLocked) {
                    unlockOrientation()
                } else {
                    Log.d(TAG, "Keeping orientation locked - status: $currentStatus, reconnecting: $isReconnecting")
                }
            }
        }
        
        // Also observe streamStatus to handle orientation during state transitions
        lifecycleScope.launch {
            previewViewModel.streamStatus.collect { status ->
                when (status) {
                    com.dimadesu.lifestreamer.models.StreamStatus.STARTING -> {
                        // Lock orientation as soon as we start attempting to stream
                        // But check Service first - if already saved, don't re-lock
                        if (shouldLockOrientation("STARTING")) {
                            lockOrientation()
                            Log.d(TAG, "Locked orientation during STARTING")
                        }
                    }
                    com.dimadesu.lifestreamer.models.StreamStatus.CONNECTING -> {
                        // Ensure orientation stays locked during reconnection
                        // But check Service first - if already saved, don't re-lock
                        if (shouldLockOrientation("CONNECTING")) {
                            lockOrientation()
                            Log.d(TAG, "Locked orientation during CONNECTING/reconnection")
                        }
                    }
                    com.dimadesu.lifestreamer.models.StreamStatus.NOT_STREAMING,
                    com.dimadesu.lifestreamer.models.StreamStatus.ERROR -> {
                        // Unlock orientation only when truly stopped and not reconnecting
                        val isReconnecting = previewViewModel.isReconnectingLiveData.value ?: false
                        if (previewViewModel.isStreamingLiveData.value == false && !isReconnecting) {
                            unlockOrientation()
                        } else {
                            Log.d(TAG, "Keeping lock despite $status - streaming: ${previewViewModel.isStreamingLiveData.value}, reconnecting: $isReconnecting")
                        }
                    }
                    com.dimadesu.lifestreamer.models.StreamStatus.STREAMING -> {
                        // Orientation should already be locked by isStreamingLiveData observer
                        // This is just a safety check - but DON'T re-lock if we already have one
                        // Check both Service and Fragment to avoid overwriting during lifecycle
                        if (shouldLockOrientation("STREAMING safety check")) {
                            lockOrientation()
                            Log.d(TAG, "Safety lock during STREAMING")
                        }
                    }
                }
            }
        }


        // Apply orientation setting changes in real-time when not streaming
        lifecycleScope.launch {
            previewViewModel.streamOrientationFlow.collect { orientation ->
                val isStreaming = previewViewModel.streamStatus.value.let { status ->
                    status == com.dimadesu.lifestreamer.models.StreamStatus.STARTING ||
                    status == com.dimadesu.lifestreamer.models.StreamStatus.CONNECTING ||
                    status == com.dimadesu.lifestreamer.models.StreamStatus.STREAMING
                }
                if (!isStreaming) {
                    val fixedRotation = orientation.toSurfaceRotation()
                    if (fixedRotation != null) {
                        val activityOrientation = fixedRotation.toActivityOrientation()
                        requireActivity().requestedOrientation = activityOrientation
                        Log.d(TAG, "Orientation setting changed to $orientation, applied: $activityOrientation")
                    } else {
                        requireActivity().requestedOrientation = ApplicationConstants.supportedOrientation
                        Log.d(TAG, "Orientation setting changed to Auto, unlocked")
                    }
                }
            }
        }

        previewViewModel.streamerLiveData.observe(viewLifecycleOwner) { streamer ->
            if (streamer is IStreamer) {
                // TODO: For background streaming, we don't want to automatically stop streaming
                // when the app goes to background. The service should handle this.
                // TODO: Remove this observer when streamer is released
                // lifecycle.addObserver(StreamerViewModelLifeCycleObserver(streamer))
                Log.d(TAG, "Streamer lifecycle observer disabled for background streaming support")
            } else {
                Log.e(TAG, "Streamer is not a ICoroutineStreamer")
            }
            if (streamer is IWithVideoSource) {
                inflateStreamerPreview(streamer)
            } else {
                Log.e(TAG, "Can't start preview, streamer is not a IVideoStreamer")
            }
        }


        // Rebind preview when streaming stops to ensure preview is active
        previewViewModel.isStreamingLiveData.observe(viewLifecycleOwner) { isStreaming ->
            if (isStreaming == false) {
                Log.d(TAG, "Streaming stopped - re-attaching preview if possible")
                try {
                    inflateStreamerPreview()
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to re-attach preview after stop: ${t.message}")
                }
            }
        }
    }

    /**
     * Helper to check if we should lock orientation for a NEW stream.
     * Returns true if orientation should be locked (no saved state exists).
     * If Service has a saved orientation but Fragment doesn't, restores UI to match Service.
     * Logs appropriate message if orientation is already saved.
     */
    private fun shouldLockOrientation(context: String): Boolean {
        val savedRotation = previewViewModel.service?.getSavedStreamingOrientation()
        
        // If Service has saved orientation but Fragment doesn't have it yet, restore UI now
        if (savedRotation != null && rememberedLockedOrientation == null) {
            val orientation = savedRotation.toActivityOrientation()
            requireActivity().requestedOrientation = orientation
            rememberedLockedOrientation = orientation
            rememberedRotation = savedRotation
            Log.d(TAG, "$context: Restored UI orientation from Service saved rotation: $orientation (rotation: $savedRotation)")
            return false // Already handled, don't call lockOrientation()
        }
        
        return if (savedRotation == null && rememberedLockedOrientation == null) {
            true // No saved orientation, proceed with lock
        } else {
            Log.d(TAG, "$context: Already have saved orientation (Service: $savedRotation, Fragment: $rememberedLockedOrientation), not re-locking")
            false
        }
    }

    private fun lockOrientation() {
        // If a fixed orientation is set, use that; otherwise lock to current sensor rotation.
        val fixedRotation = previewViewModel.streamOrientationFlow.value.toSurfaceRotation()
        val rotation = if (fixedRotation != null) {
            fixedRotation
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            requireContext().display?.rotation ?: android.view.Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            requireActivity().windowManager.defaultDisplay.rotation
        }
        val currentOrientation = rotation.toActivityOrientation()

        rememberedLockedOrientation = currentOrientation
        rememberedRotation = rotation
        requireActivity().requestedOrientation = currentOrientation
        Log.d(TAG, "Orientation locked to: $currentOrientation (rotation: $rotation)")

        previewViewModel.service?.lockStreamRotation(rotation)
    }

    private fun unlockOrientation() {
        rememberedLockedOrientation = null
        rememberedRotation = null
        // When a fixed orientation is configured, keep the activity locked to it after
        // streaming stops — don't revert to free sensor rotation.
        val fixedRotation = previewViewModel.streamOrientationFlow.value.toSurfaceRotation()
        if (fixedRotation != null) {
            val fixedActivityOrientation = fixedRotation.toActivityOrientation()
            requireActivity().requestedOrientation = fixedActivityOrientation
            Log.d(TAG, "Orientation kept at fixed setting: $fixedActivityOrientation after stream stop")
        } else {
            requireActivity().requestedOrientation = ApplicationConstants.supportedOrientation
            Log.d(TAG, "Orientation unlocked and remembered orientation cleared")
        }
    }


    private fun startStream() {
        Log.d(TAG, "startStream() called - checking if MediaProjection is required")

        // Check if MediaProjection is required for this streaming setup
        if (previewViewModel.requiresMediaProjection()) {
            Log.d(TAG, "MediaProjection required - using startStreamWithMediaProjection")
            // Use MediaProjection-enabled streaming for RTMP sources
            // Note: Errors are displayed via streamerErrorLiveData observer, no need for onError callback
            previewViewModel.startStreamWithMediaProjection(
                mediaProjectionLauncher,
                onSuccess = {
                    Log.d(TAG, "MediaProjection stream started successfully")
                },
                onError = { error ->
                    // Error already posted to streamerErrorLiveData by ViewModel
                    // Just log it here to avoid double error dialogs
                    Log.e(TAG, "MediaProjection stream failed: $error")
                }
            )
        } else {
            Log.d(TAG, "Regular streaming - using standard startStream")
            // Use the main startStream method for camera sources
            previewViewModel.startStream()
        }
    }

    private fun stopStream() {
        previewViewModel.stopStream()
    }

    /**
     * Unbind the streamer service before [android.content.Context.stopService] so
     * a started+bound CameraStreamerService can actually be destroyed on Quit.
     */
    fun prepareForExit() {
        previewViewModel.prepareForExit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPreviewScale(previewViewModel.videoConfigLiveData.value)
    }

    /**
     * Sizes the PreviewView to exactly match the stream resolution, then visually scales it
     * down (via scaleX/scaleY) so it fits inside the available screen area.
     * Using scale instead of layout resize avoids SurfaceView surface destruction, which
     * would otherwise crash the Samsung Camera HAL during active streams.
     */
    private fun applyPreviewScale(config: io.github.thibaultbee.streampack.core.streamers.single.VideoConfig?) {
        config ?: return
        val width = config.resolution.width
        val height = config.resolution.height
        if (width <= 0 || height <= 0) return

        val isPortrait = resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_PORTRAIT
        val targetW = if (isPortrait) height else width
        val targetH = if (isPortrait) width else height

        // Set the layout size so StreamPack selects the correct camera resolution
        val lp = binding.preview.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        lp.dimensionRatio = null
        lp.width = targetW
        lp.height = targetH
        binding.preview.layoutParams = lp

        // After the layout pass, scale the view down to fit the visible area
        binding.root.post {
            val availableWidth = binding.root.width.toFloat()
            val availableHeight = binding.root.height.toFloat()
            if (availableWidth > 0 && availableHeight > 0) {
                val scale = kotlin.math.min(
                    availableWidth / targetW.toFloat(),
                    availableHeight / targetH.toFloat()
                )
                binding.preview.pivotX = targetW / 2f
                binding.preview.pivotY = targetH / 2f
                binding.preview.scaleX = scale
                binding.preview.scaleY = scale
            }
        }
    }

    private fun showPermissionError(vararg permissions: String) {
        Log.e(TAG, "Permission not granted: ${permissions.joinToString { ", " }}")
        DialogUtils.showPermissionAlertDialog(requireContext())
    }

    private fun showError(title: String, message: String) {
        Log.e(TAG, "Error: $title, $message")
        DialogUtils.showAlertDialog(requireContext(), "Error: $title", message)
    }

    @SuppressLint("MissingPermission")
    override fun onStart() {
        super.onStart()
        
        // Restore orientation lock IMMEDIATELY in onStart() (before onResume()) to prevent
        // the Activity from rotating when returning from background during streaming.
        // Use the Service's saved orientation as the source of truth, since Fragment member
        // variables can be reset during lifecycle transitions.
        val isInStreamingProcess = previewViewModel.streamStatus.value?.let { status ->
            status == com.dimadesu.lifestreamer.models.StreamStatus.STARTING ||
            status == com.dimadesu.lifestreamer.models.StreamStatus.CONNECTING ||
            status == com.dimadesu.lifestreamer.models.StreamStatus.STREAMING
        } ?: false
        
        if (isInStreamingProcess) {
            // Get saved orientation from Service (source of truth)
            val savedRotation = previewViewModel.service?.getSavedStreamingOrientation()
            if (savedRotation != null) {
                val orientation = savedRotation.toActivityOrientation()
                requireActivity().requestedOrientation = orientation
                rememberedLockedOrientation = orientation
                rememberedRotation = savedRotation
                Log.d(TAG, "onStart: Restored orientation from Service: $orientation (rotation: $savedRotation)")
            } else {
                Log.d(TAG, "onStart: No saved orientation in Service, will lock in observer")
            }
        }
        
        // NOTE: Permission request moved to onResume() to fix preview freeze when quickly
        // going to settings and back (StreamPack fix 78f1dc28c)
    }

    override fun onPause() {
        super.onPause()
        previewViewModel.onUiPaused()
        binding.srtlaStatsView.stopStatsUpdates()
        // DO NOT stop streaming when going to background - the service should continue streaming
        // DO NOT stop preview either when the camera is being used for streaming -
        // the camera source is shared between preview and streaming, so stopping preview
        // would also stop the streaming. Instead, let the preview continue running.
        Log.d(TAG, "onPause() - app going to background, keeping both preview and stream active via service")
        
        // Note: We used to stop preview here, but that was causing streaming to stop
        // because the camera source is shared. For background streaming to work properly,
        // we need to keep the camera active.
        // stopStream()
    }

    override fun onResume() {
        super.onResume()
        previewViewModel.onUiResumed()
        if (binding.srtlaStatsView.visibility == View.VISIBLE) {
            binding.srtlaStatsView.startStatsUpdates()
        }
        Log.d(TAG, "onResume() - app returning to foreground, preview should already be active")
        
        // Request permissions in onResume() instead of onStart() to fix preview freeze
        // when quickly going to settings and back (StreamPack fix 78f1dc28c)
        requestCameraAndMicrophonePermissions()

        if (PermissionManager.hasPermissions(requireContext(), Manifest.permission.CAMERA)) {
            // FIRST: Restore orientation lock if streaming, BEFORE restarting preview
            // Get saved orientation from Service (source of truth)
            val currentStatus = previewViewModel.streamStatus.value
            val isInStreamingProcess = currentStatus == com.dimadesu.lifestreamer.models.StreamStatus.STARTING ||
                                       currentStatus == com.dimadesu.lifestreamer.models.StreamStatus.CONNECTING ||
                                       currentStatus == com.dimadesu.lifestreamer.models.StreamStatus.STREAMING
            
            
            if (isInStreamingProcess) {
                Log.d(TAG, "onResume: Secondary check - restoring orientation from Service (status: $currentStatus)")
                
                // Get saved rotation from Service (survives Fragment lifecycle)
                val savedRotation = previewViewModel.service?.getSavedStreamingOrientation()
                if (savedRotation != null) {
                    val orientation = savedRotation.toActivityOrientation()
                    requireActivity().requestedOrientation = orientation
                    rememberedLockedOrientation = orientation
                    rememberedRotation = savedRotation
                    Log.d(TAG, "Restored orientation from Service: $orientation (rotation: $savedRotation)")
                } else {
                    // Fallback to locking current orientation if we don't have a saved one
                    lockOrientation()
                    Log.d(TAG, "No saved orientation in Service, locked to current position")
                }
                
                // SECOND: Give the orientation change time to propagate before restarting preview
                // This prevents the preview from starting in the wrong orientation
                lifecycleScope.launch {
                    delay(100) // Small delay to allow orientation to stabilize
                    try {
                        inflateStreamerPreview()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error while inflating/starting preview on resume: ${e.message}")
                    }
                }
            } else {
                Log.d(TAG, "App returned to foreground - not streaming, applying orientation setting")
                // Not streaming - apply orientation setting (unlock for Auto, keep fixed for others)
                // This also handles the case where stream was stopped from notification while app was in background
                if (rememberedLockedOrientation != null) {
                    unlockOrientation() // respects fixed setting internally
                    Log.d(TAG, "Applied orientation after stream stop")
                } else {
                    // Apply fixed orientation even if we were never locked (e.g. fresh resume)
                    val fixedRotation = previewViewModel.streamOrientationFlow.value.toSurfaceRotation()
                    if (fixedRotation != null) {
                        val fixedActivityOrientation = fixedRotation.toActivityOrientation()
                        requireActivity().requestedOrientation = fixedActivityOrientation
                        Log.d(TAG, "Applied fixed orientation setting on resume: $fixedActivityOrientation")
                    }
                }
                // Start preview immediately
                try {
                    inflateStreamerPreview()
                } catch (e: Exception) {
                    Log.w(TAG, "Error while inflating/starting preview on resume: ${e.message}")
                }
            }

            // THIRD: Handle service foreground recovery if streaming
            if (isInStreamingProcess) {
                previewViewModel.service?.let { service ->
                    try {
                        service.handleForegroundRecovery()
                        Log.d(TAG, "Foreground recovery completed")
                    } catch (t: Throwable) {
                        Log.w(TAG, "Foreground recovery failed: ${t.message}")
                    }
                }
            }
        }
        
        // Reload audio settings from DataStore (may have changed in Settings activity)
        // and apply if they differ from current values
        previewViewModel.reloadAndApplyAudioSettingsIfChanged()
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private fun inflateStreamerPreview() {
        val streamer = previewViewModel.streamerLiveData.value
        if (streamer is IWithVideoSource) {
            inflateStreamerPreview(streamer)
        } else {
            Log.e(TAG, "Can't start preview, streamer is not a video streamer")
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private fun inflateStreamerPreview(streamer: IWithVideoSource) {
        val preview = binding.preview
        // Set camera settings button when camera is started
        preview.listener = object : PreviewView.PreviewListener {
            override fun onPreviewStarted() {
                Log.i(TAG, "Preview started")
            }
        }

        // Keep zoom slider in sync when the user pinch-zooms on the preview.
        // The listener is dispatched on the main thread (PreviewView wraps it with post{}).
        preview.setZoomListener(object : CameraSettings.Zoom.OnZoomChangedListener {
            override fun onZoomChanged(zoomRatio: Float) {
                previewViewModel.onZoomRationOnPinchChanged(zoomRatio)
            }
        })

        // If the preview already uses the same streamer, no need to set it again.
        lifecycleScope.launch {
            try {
                preview.setVideoSourceProvider(streamer)
                Log.d(TAG, "Preview streamer assigned")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set video source provider: ${e.message}")
            }
        }
    }

    private fun startStreamIfPermissions(permissions: List<String>) {
        when {
            PermissionManager.hasPermissions(
                requireContext(), *permissions.toTypedArray()
            ) -> {
                // Log detailed permission status before starting stream
                permissions.forEach { permission ->
                    val isGranted = ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
                    Log.i(TAG, "Permission $permission: granted=$isGranted")
                }
                
                // Special check for RECORD_AUDIO AppOps
                if (permissions.contains(Manifest.permission.RECORD_AUDIO)) {
                    try {
                        val appOpsManager = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            appOpsManager.checkOpNoThrow(
                                AppOpsManager.OPSTR_RECORD_AUDIO,
                                android.os.Process.myUid(),
                                requireContext().packageName
                            )
                        } else {
                            AppOpsManager.MODE_ALLOWED
                        }
                        Log.i(TAG, "RECORD_AUDIO AppOps mode: $mode (${if (mode == AppOpsManager.MODE_ALLOWED) "ALLOWED" else "BLOCKED"})")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to check RECORD_AUDIO AppOps", e)
                    }
                }
                
                startStream()
            }

            else -> {
                Log.w(TAG, "Missing permissions, requesting: ${permissions.joinToString()}")
                requestLiveStreamPermissionsLauncher.launch(
                    permissions.toTypedArray()
                )
            }
        }
    }

    private fun showCameraSelectionDialog() {
        val ctx = requireContext()
        val cameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = try {
            cameraManager.cameraIdList
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to list camera ids: ${t.message}")
            return
        }

        if (cameraIds.isEmpty()) {
            Log.w(TAG, "No camera devices available on this device")
            return
        }

        val items = cameraIds.map { id ->
            try {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facingConst = chars.get(CameraCharacteristics.LENS_FACING)
                val facing = when (facingConst) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_BACK -> "Back"
                    else -> "External"
                }

                // Use available focal lengths only. Sensor physical size isn't always
                // provided on all devices, and focal length values alone are often
                // the most consistently available data we can show.
                val focalArr = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val physicalSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) as? SizeF
                val fovLabel = if (focalArr != null && focalArr.isNotEmpty()) {
                    // Prefer showing diagonal FOV when sensor physical size is available.
                    val firstFocal = focalArr.first()
                    val focalFormatted = "%.1f".format(firstFocal)
                    if (physicalSize != null) {
                        val sensorDiag = sqrt(physicalSize.width * physicalSize.width + physicalSize.height * physicalSize.height)
                        // diagonal FOV in degrees = 2 * atan(sensorDiag / (2 * focal))
                        val fovRad = 2.0 * atan((sensorDiag / (2.0 * firstFocal)).toDouble())
                        val fovDeg = (fovRad * 180.0 / PI).toInt()
                        "${fovDeg}°"
                    } else {
                        // If physical size is missing, show available focal(s)
                        val formattedAll = focalArr.joinToString(",") { "%.1f".format(it) }
                        "f=${formattedAll}mm"
                    }
                } else {
                    "focal unknown"
                }

                "$id $facing $fovLabel"
            } catch (t: Throwable) {
                "Camera $id"
            }
        }.toTypedArray()

        // Determine currently selected camera id if any
        val currentSource = previewViewModel.streamer?.videoInput?.sourceFlow?.value
        val currentCameraId = (currentSource as? ICameraSource)?.cameraId
        val currentIndex = if (currentCameraId != null) cameraIds.indexOf(currentCameraId).coerceAtLeast(0) else -1

        AlertDialog.Builder(ctx)
            .setTitle("Select camera")
            .setSingleChoiceItems(items, if (currentIndex >= 0) currentIndex else -1) { dialog, which ->
                val selectedId = cameraIds[which]
                lifecycleScope.launch {
                    try {
                        (previewViewModel.streamer as? IWithVideoSource)?.setCameraId(selectedId)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to set camera id $selectedId: ${t.message}")
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun requestCameraAndMicrophonePermissions() {
        // Include POST_NOTIFICATIONS on API 33+ so the app asks for it during app open
        val permissionsToCheck = mutableListOf<String>().apply {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }

        when {
            PermissionManager.hasPermissions(
                requireContext(), *permissionsToCheck.toTypedArray()
            ) -> {
                inflateStreamerPreview()
                // Don't call configureAudio() here - it will be handled by service connection
                // when the service is ready and only if not already streaming
                // previewViewModel.configureAudio()
                previewViewModel.initializeVideoSource()
                // Load available cameras for button creation
                previewViewModel.loadAvailableCameras()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                showPermissionError(Manifest.permission.RECORD_AUDIO)
                requestCameraAndMicrophonePermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO
                    )
                )
            }

            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionError(Manifest.permission.CAMERA)
                requestCameraAndMicrophonePermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA
                    )
                )
            }

            else -> {
                requestCameraAndMicrophonePermissionsLauncher.launch(
                    permissionsToCheck.toTypedArray()
                )
            }
        }
    }

    private val requestLiveStreamPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val missingPermissions = permissions.toList().filter {
            !it.second
        }.map { it.first }

        if (missingPermissions.isEmpty()) {
            startStream()
        } else {
            showPermissionError(*missingPermissions.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    private val requestCameraAndMicrophonePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val missingPermissions = permissions.toList().filter {
            !it.second
        }.map { it.first }

        if (permissions[Manifest.permission.CAMERA] == true) {
            inflateStreamerPreview()
            previewViewModel.initializeVideoSource()
            // Load available cameras for button creation
            previewViewModel.loadAvailableCameras()
        }
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            // Apply any pending audio config now that permission is granted
            previewViewModel.applyPendingAudioConfig()
            Log.d(TAG, "RECORD_AUDIO permission granted - applying pending audio config")
        }
        // POST_NOTIFICATIONS is optional for preview; if granted, we can create
        // or update our notification channel. If not granted, continue normally.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] == true
            Log.d(TAG, "POST_NOTIFICATIONS granted=$notifGranted")
            // Optionally create silent notification channel here if granted
            if (notifGranted) {
                try {
                    previewViewModel.service?.let { service ->
                        // Ensure channel exists
                        service.run {
                            // customNotificationUtils exists in the service - calling via reflection
                            // would be heavy; just log for now and allow service to recreate channel when needed
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to ensure notification channel after permission grant: ${t.message}")
                }
            }
        }
        if (missingPermissions.isNotEmpty()) {
            showPermissionError(*missingPermissions.toTypedArray())
        }
    }
    
    private fun setupAudioSourceSpinner() {
        // Read from the same XML arrays as Settings activity
        val entries = resources.getStringArray(R.array.AudioSourceTypeEntries)
        val values = resources.getStringArray(R.array.AudioSourceTypeEntryValues)
        val audioSourceOptions = entries.zip(values.map { it.toInt() })
        
        val adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            audioSourceOptions.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.audioSourceSpinner.adapter = adapter
        
        binding.audioSourceSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            private var isFirstSelection = true
            
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val newSourceType = audioSourceOptions[position].second
                val currentSourceType = previewViewModel.selectedAudioSourceType.value
                
                // Skip first selection (initial setup) and only apply if value changed
                if (isFirstSelection) {
                    isFirstSelection = false
                    previewViewModel.setSelectedAudioSourceType(newSourceType)
                    return
                }
                
                if (newSourceType != currentSourceType) {
                    previewViewModel.setSelectedAudioSourceType(newSourceType)
                    previewViewModel.applySelectedAudioSource()
                }
            }
            
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                // Do nothing
            }
        }
        
        // Observe the selected audio source type and update spinner position
        previewViewModel.selectedAudioSourceType.observe(viewLifecycleOwner) { sourceType ->
            val position = audioSourceOptions.indexOfFirst { it.second == sourceType }
            if (position >= 0 && binding.audioSourceSpinner.selectedItemPosition != position) {
                binding.audioSourceSpinner.setSelection(position)
            }
        }
    }



    /**
     * Toggle RTMP video source, showing an explanation dialog before requesting
     * MediaProjection permission when the user is switching to RTMP for the first time
     * and no MediaProjection token exists yet.
     */
    private fun toggleVideoSourceWithExplanation(rtmpIndex: Int) {
        val isSwitchingTo = previewViewModel.activeRtmpIndex.value != rtmpIndex
        if (isSwitchingTo && !previewViewModel.rtmpAudioExplanationShown && !previewViewModel.hasMediaProjection()) {
            previewViewModel.pendingRtmpIndex = rtmpIndex
            if (childFragmentManager.findFragmentByTag(ExplanationDialogFragment.RTMP_AUDIO_TAG) == null) {
                ExplanationDialogFragment.newInstance(
                    R.string.rtmp_audio_explanation_title,
                    R.string.rtmp_audio_explanation_message,
                    ExplanationDialogFragment.RTMP_AUDIO_TAG
                ).show(childFragmentManager, ExplanationDialogFragment.RTMP_AUDIO_TAG)
            }
        } else {
            previewViewModel.toggleVideoSource(mediaProjectionLauncher, rtmpIndex = rtmpIndex)
        }
    }

    companion object {
        private const val TAG = "PreviewFragment"
    }

    private fun Int.toActivityOrientation(): Int = when (this) {
        android.view.Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        android.view.Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        android.view.Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        android.view.Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

}