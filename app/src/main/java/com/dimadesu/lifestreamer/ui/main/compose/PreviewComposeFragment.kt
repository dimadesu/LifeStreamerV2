package com.dimadesu.lifestreamer.ui.main.compose

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.dimadesu.lifestreamer.ApplicationConstants
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.models.StreamOrientation
import com.dimadesu.lifestreamer.models.StreamStatus
import com.dimadesu.lifestreamer.ui.main.ExplanationDialogFragment
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel
import com.dimadesu.lifestreamer.ui.main.PreviewViewModelFactory
import com.dimadesu.lifestreamer.ui.theme.LifeStreamerTheme
import com.dimadesu.lifestreamer.utils.DialogUtils
import com.dimadesu.lifestreamer.utils.PermissionManager
import kotlinx.coroutines.launch

class PreviewComposeFragment : Fragment() {

    private val previewViewModel: PreviewViewModel by viewModels {
        PreviewViewModelFactory(requireActivity().application)
    }

    private var rememberedLockedOrientation: Int? = null
    private var rememberedRotation: Int? = null

    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>
    private lateinit var bluetoothConnectLauncher: ActivityResultLauncher<String>

    private val requestLiveStreamPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val missingPermissions = permissions.toList().filter { !it.second }.map { it.first }
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
        val missingPermissions = permissions.toList().filter { !it.second }.map { it.first }
        if (permissions[Manifest.permission.CAMERA] == true) {
            previewViewModel.initializeVideoSource()
            previewViewModel.loadAvailableCameras()
        }
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            previewViewModel.applyPendingAudioConfig()
        }
        if (missingPermissions.isNotEmpty()) {
            showPermissionError(*missingPermissions.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaProjectionLauncher = previewViewModel.mediaProjectionHelper.registerLauncher(this)

        bluetoothConnectLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                Toast.makeText(requireContext(), "Bluetooth permission granted", Toast.LENGTH_SHORT).show()
                previewViewModel.setUseBluetoothMic(true)
            } else {
                Toast.makeText(requireContext(), "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        setupDialogListeners()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LifeStreamerTheme {
                    MainScreen(
                        viewModel = previewViewModel,
                        onLiveClick = { handleLiveClick() },
                        onRtmpSourceClick = { index -> toggleVideoSourceWithExplanation(index) },
                        onUvcToggleClick = { previewViewModel.toggleUvcSource() },
                        onScreenToggleClick = { previewViewModel.toggleScreenSource(mediaProjectionLauncher) },
                        onSystemAudioToggleClick = { toggleSystemAudioWithExplanation() },
                        onAudioSourceDebugClick = { previewViewModel.toggleAudioDebugOverlay() }
                    )
                }
            }
        }
    }

    private fun setupDialogListeners() {
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
    }

    private fun handleLiveClick() {
        val currentStatus = previewViewModel.streamStatus.value
        Log.d(TAG, "Live button clicked - currentStatus: $currentStatus")

        when (currentStatus) {
            StreamStatus.NOT_STREAMING, StreamStatus.ERROR -> {
                startStreamIfPermissions(previewViewModel.requiredPermissions)
            }
            StreamStatus.STARTING, StreamStatus.CONNECTING, StreamStatus.STREAMING -> {
                stopStream()
            }
        }
    }

    private fun startStreamIfPermissions(permissions: List<String>) {
        if (PermissionManager.hasPermissions(requireContext(), *permissions.toTypedArray())) {
            startStream()
        } else {
            requestLiveStreamPermissionsLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startStream() {
        if (shouldLockOrientation("startStream")) {
            lockOrientation()
        }
        previewViewModel.startStream()
    }

    private fun stopStream() {
        unlockOrientation()
        previewViewModel.stopStream()
    }

    private fun toggleVideoSourceWithExplanation(rtmpIndex: Int = 1) {
        val isCurrentSource = previewViewModel.activeRtmpIndex.value == rtmpIndex
        val isTurningOn = !isCurrentSource

        if (isTurningOn && !previewViewModel.rtmpAudioExplanationShown) {
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

    private fun toggleSystemAudioWithExplanation() {
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
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        requestCameraAndMicrophonePermissions()
    }

    private fun requestCameraAndMicrophonePermissions() {
        val permissionsToCheck = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (PermissionManager.hasPermissions(requireContext(), *permissionsToCheck.toTypedArray())) {
            previewViewModel.initializeVideoSource()
            previewViewModel.loadAvailableCameras()
        } else {
            requestCameraAndMicrophonePermissionsLauncher.launch(permissionsToCheck.toTypedArray())
        }
    }

    private fun shouldLockOrientation(context: String): Boolean {
        val savedRotation = previewViewModel.service?.getSavedStreamingOrientation()
        if (savedRotation != null && rememberedLockedOrientation == null) {
            val orientation = savedRotation.toActivityOrientation()
            requireActivity().requestedOrientation = orientation
            rememberedLockedOrientation = orientation
            rememberedRotation = savedRotation
            return false
        }
        return savedRotation == null && rememberedLockedOrientation == null
    }

    private fun lockOrientation() {
        val fixedRotation = previewViewModel.streamOrientationFlow.value.toSurfaceRotation()
        val rotation = if (fixedRotation != null) {
            fixedRotation
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireContext().display?.rotation ?: android.view.Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            requireActivity().windowManager.defaultDisplay.rotation
        }
        val currentOrientation = rotation.toActivityOrientation()

        rememberedLockedOrientation = currentOrientation
        rememberedRotation = rotation
        requireActivity().requestedOrientation = currentOrientation
        previewViewModel.service?.lockStreamRotation(rotation)
    }

    private fun unlockOrientation() {
        rememberedLockedOrientation = null
        rememberedRotation = null
        val fixedRotation = previewViewModel.streamOrientationFlow.value.toSurfaceRotation()
        if (fixedRotation != null) {
            requireActivity().requestedOrientation = fixedRotation.toActivityOrientation()
        } else {
            requireActivity().requestedOrientation = ApplicationConstants.supportedOrientation
        }
    }

    fun prepareForExit() {
        previewViewModel.prepareForExit()
    }

    private fun showPermissionError(vararg permissions: String) {
        DialogUtils.showPermissionAlertDialog(requireContext())
    }

    private fun Int.toActivityOrientation(): Int = when (this) {
        android.view.Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        android.view.Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        android.view.Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        android.view.Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    companion object {
        private const val TAG = "PreviewComposeFragment"
    }
}
