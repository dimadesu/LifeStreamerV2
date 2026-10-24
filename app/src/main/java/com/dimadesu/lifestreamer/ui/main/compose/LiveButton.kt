package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.models.StreamStatus
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel

/**
 * Stateful Live/Stop stream button matching original ToggleButton behavior.
 */
@Composable
fun LiveButton(
    viewModel: PreviewViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status by viewModel.streamStatus.collectAsState(initial = StreamStatus.NOT_STREAMING)
    val isReconnecting by viewModel.isReconnectingLiveData.observeAsState(initial = false)

    LiveButton(
        status = status,
        isReconnecting = isReconnecting,
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * Stateless Live/Stop stream button.
 * Toggles purely between "LIVE" and "STOP" text based on streaming state,
 * matching original ToggleButton behavior without animations or custom status text.
 */
@Composable
fun LiveButton(
    status: StreamStatus,
    isReconnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isChecked = when (status) {
        StreamStatus.STARTING, StreamStatus.CONNECTING, StreamStatus.STREAMING -> true
        StreamStatus.ERROR, StreamStatus.NOT_STREAMING -> isReconnecting
    }

    Button(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(
            text = if (isChecked) stringResource(R.string.stop) else stringResource(R.string.live)
        )
    }
}

@Preview(name = "Not Streaming (LIVE)")
@Composable
private fun PreviewLiveButtonNotStreaming() {
    LiveButton(status = StreamStatus.NOT_STREAMING, isReconnecting = false, onClick = {})
}

@Preview(name = "Streaming (STOP)")
@Composable
private fun PreviewLiveButtonStreaming() {
    LiveButton(status = StreamStatus.STREAMING, isReconnecting = false, onClick = {})
}
