package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.dimadesu.lifestreamer.models.StreamStatus
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSettings
import io.github.thibaultbee.streampack.core.interfaces.IWithVideoSource
import io.github.thibaultbee.streampack.ui.views.PreviewView
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: PreviewViewModel,
    onLiveClick: () -> Unit,
    onRtmpSourceClick: (Int) -> Unit,
    onUvcToggleClick: () -> Unit,
    onScreenToggleClick: () -> Unit,
    onSystemAudioToggleClick: () -> Unit,
    onAudioSourceDebugClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    // Observe LiveData from PreviewViewModel
    val streamStatus by viewModel.streamStatus.collectAsState()
    val streamStatusText by viewModel.streamStatusTextLiveData.observeAsState("")
    val bitrateText by viewModel.bitrateLiveData.observeAsState()
    val fpsText by viewModel.encoderStatsLiveData.observeAsState()
    val uptimeText by viewModel.uptimeLiveData.observeAsState()
    val audioSourceText by viewModel.audioSourceIndicatorLiveData.observeAsState()
    val rtmpStatusMessage by viewModel.rtmpStatusLiveData.observeAsState()
    val reconnectionStatusMessage by viewModel.reconnectionStatusLiveData.observeAsState()

    // Sources & States
    val isRtmpSource by viewModel.isRtmpSource.observeAsState(false)
    val activeRtmpIndex by viewModel.activeRtmpIndex.observeAsState(null)
    val rtmpSourceCount by viewModel.rtmpSourceCount.observeAsState(1)
    val isUvcSource by viewModel.isUvcSource.observeAsState(false)
    val isScreenSource by viewModel.isScreenSource.observeAsState(false)
    val isMonitorAudioOn by viewModel.isMonitorAudioOn.observeAsState(false)
    val useBluetoothMic by viewModel.useBluetoothMic.observeAsState(false)
    val showBluetoothToggle by viewModel.showBluetoothToggle.observeAsState(false)
    val isAudioDebugOverlayVisible by viewModel.isAudioDebugOverlayVisible.observeAsState(false)
    val isSrtlaEndpoint by viewModel.isSrtlaEndpoint.observeAsState(false)
    val isSrtlaStatsVisible by viewModel.isSrtlaStatsVisible.observeAsState(false)

    // Camera states
    val isFlashAvailable by viewModel.isFlashAvailable.observeAsState(false)
    val isAutoWhiteBalanceAvailable by viewModel.isAutoWhiteBalanceAvailable.observeAsState(false)
    val isExposureAvailable by viewModel.isExposureCompensationAvailable.observeAsState(false)
    val isZoomAvailable by viewModel.isZoomAvailable.observeAsState(false)
    val isAutoFocusAvailable by viewModel.isAutoFocusModeAvailable.observeAsState(false)
    val showCameraControls by viewModel.showCameraControls.observeAsState(true)
    val showZoomSlider by viewModel.showZoomSlider.observeAsState(false)
    val showExposureSlider by viewModel.showExposureSlider.observeAsState(false)
    val isMuted by viewModel.isMutedLiveData.observeAsState(false)

    val zoomRatio = viewModel.zoomRatio
    val rawZoomRange by viewModel.zoomRatioRange.observeAsState(null)
    val zoomRange = (rawZoomRange?.lower ?: 1f)..(rawZoomRange?.upper ?: 10f)

    val exposureComp = viewModel.exposureCompensation
    val rawExposureRange by viewModel.exposureCompensationRange.observeAsState(null)
    val exposureRange = (rawExposureRange?.lower?.toFloat() ?: -4f)..(rawExposureRange?.upper?.toFloat() ?: 4f)

    // Observe streamer to bind to preview when ready
    LaunchedEffect(viewModel.streamer) {
        val streamer = viewModel.streamer
        val preview = previewViewRef
        if (streamer is IWithVideoSource && preview != null) {
            try {
                preview.setVideoSourceProvider(streamer)
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // LAYER 1: Full-screen StreamPack Video Viewfinder
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    enableZoomOnPinch = true
                    setZoomListener(object : CameraSettings.Zoom.OnZoomChangedListener {
                        override fun onZoomChanged(zoomRatio: Float) {
                            viewModel.onZoomRationOnPinchChanged()
                        }
                    })

                    previewViewRef = this

                    val streamer = viewModel.streamer
                    if (streamer is IWithVideoSource) {
                        coroutineScope.launch {
                            try {
                                setVideoSourceProvider(streamer)
                            } catch (_: Exception) {}
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // LAYER 2: Compose HUD Overlay
        MainHudOverlay(
            streamStatus = streamStatus,
            streamStatusText = streamStatusText,
            bitrateText = bitrateText,
            fpsText = fpsText,
            uptimeText = uptimeText,
            audioSourceText = audioSourceText,
            rtmpStatusMessage = rtmpStatusMessage,
            reconnectionStatusMessage = reconnectionStatusMessage,
            isRtmpSource = isRtmpSource,
            activeRtmpIndex = activeRtmpIndex,
            rtmpSourceCount = rtmpSourceCount,
            isUvcSource = isUvcSource,
            isScreenSource = isScreenSource,
            isMonitorAudioOn = isMonitorAudioOn,
            useBluetoothMic = useBluetoothMic,
            showBluetoothToggle = showBluetoothToggle,
            isAudioDebugOverlayVisible = isAudioDebugOverlayVisible,
            isSrtlaEndpoint = isSrtlaEndpoint,
            isSrtlaStatsVisible = isSrtlaStatsVisible,
            isFlashAvailable = isFlashAvailable,
            isAutoWhiteBalanceAvailable = isAutoWhiteBalanceAvailable,
            isExposureAvailable = isExposureAvailable,
            isZoomAvailable = isZoomAvailable,
            isAutoFocusAvailable = isAutoFocusAvailable,
            showCameraControls = showCameraControls,
            showZoomSlider = showZoomSlider,
            showExposureSlider = showExposureSlider,
            zoomRatio = zoomRatio,
            zoomRange = zoomRange,
            exposureValue = exposureComp,
            exposureRange = exposureRange,
            isMuted = isMuted,
            onLiveToggleClick = onLiveClick,
            onRtmpSourceClick = onRtmpSourceClick,
            onRtmpServerClick = { /* No-op / server dialog */ },
            onUvcToggleClick = onUvcToggleClick,
            onUvcSettingsClick = { /* UVC test */ },
            onScreenToggleClick = onScreenToggleClick,
            onMonitorAudioClick = { viewModel.toggleMonitorAudio() },
            onBluetoothToggleClick = { viewModel.setUseBluetoothMic(!useBluetoothMic) },
            onSystemAudioToggleClick = onSystemAudioToggleClick,
            onAudioSourceDebugClick = onAudioSourceDebugClick,
            onSrtlaStatsClick = { viewModel.setSrtlaStatsVisible(!isSrtlaStatsVisible) },
            onFlashToggleClick = { viewModel.toggleFlash() },
            onWhiteBalanceToggleClick = { viewModel.toggleAutoWhiteBalanceMode() },
            onExposureToggleClick = { viewModel.toggleExposureSlider() },
            onZoomToggleClick = { viewModel.toggleZoomSlider() },
            onAutoFocusToggleClick = { viewModel.toggleAutoFocusMode() },
            onMuteToggleClick = { viewModel.setMute(it) },
            onZoomChange = { viewModel.zoomRatio = it },
            onExposureChange = { viewModel.exposureCompensation = it }
        )
    }
}
