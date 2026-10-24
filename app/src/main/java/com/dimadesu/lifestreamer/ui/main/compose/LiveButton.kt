package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
 * Uses standard grey button styling with 4dp rounded corners and ToggleButton indicator,
 * matching the original AppCompat ToggleButton appearance.
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
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF616161),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        modifier = modifier.defaultMinSize(minWidth = 88.dp, minHeight = 40.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isChecked) stringResource(R.string.stop) else stringResource(R.string.live)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(3.dp)
                    .background(
                        if (isChecked) Color(0xFF03DAC5) else Color(0x66FFFFFF),
                        RoundedCornerShape(1.dp)
                    )
            )
        }
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
