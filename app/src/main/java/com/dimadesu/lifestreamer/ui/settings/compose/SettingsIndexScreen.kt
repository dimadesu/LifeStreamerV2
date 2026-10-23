package com.dimadesu.lifestreamer.ui.settings.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.ui.theme.DarkBackground

@Composable
fun SettingsIndexScreen(
    viewModel: SettingsViewModel,
    onNavigateToEndpoint: () -> Unit,
    onNavigateToVideo: () -> Unit,
    onNavigateToAudio: () -> Unit,
    onNavigateToRtmpSource: () -> Unit,
    onBackClick: () -> Unit
) {
    val summaries by viewModel.summariesFlow.collectAsState()

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = stringResource(R.string.settings),
                onBackClick = onBackClick
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
        ) {
            SettingsCard(title = "Streaming Configuration") {
                SettingNavigationRow(
                    title = stringResource(R.string.endpoint),
                    summary = summaries.endpointSummary,
                    icon = Icons.Default.Sensors,
                    onClick = onNavigateToEndpoint
                )
                SettingNavigationRow(
                    title = stringResource(R.string.video),
                    summary = summaries.videoSummary,
                    icon = Icons.Default.Videocam,
                    onClick = onNavigateToVideo
                )
                SettingNavigationRow(
                    title = stringResource(R.string.audio),
                    summary = summaries.audioSummary,
                    icon = Icons.Default.Audiotrack,
                    onClick = onNavigateToAudio
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsCard(title = "Source Inputs") {
                SettingNavigationRow(
                    title = stringResource(R.string.rtmp_source_category_title),
                    summary = summaries.rtmpSourceSummary,
                    icon = Icons.Default.VideoLibrary,
                    onClick = onNavigateToRtmpSource
                )
            }
        }
    }
}
