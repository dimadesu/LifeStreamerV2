package com.dimadesu.lifestreamer.ui.main.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dimadesu.bondbunny.SrtlaStatsView
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.srtla.SrtlaManager
import com.dimadesu.lifestreamer.ui.main.PreviewViewModel

/**
 * SRTLA connection stats shown in the shared settings drawer chrome:
 * a bottom drawer in portrait and a side inspector in landscape.
 */
@Composable
fun SrtlaStatsBottomDrawer(
    viewModel: PreviewViewModel,
    modifier: Modifier = Modifier
) {
    val isOpen by viewModel.isSrtlaStatsVisible.observeAsState(initial = false)

    SettingsDrawerPanel(
        isOpen = isOpen,
        title = stringResource(R.string.srtla_stats_title),
        closeContentDescription = stringResource(R.string.close_srtla_stats),
        onClose = { viewModel.setSrtlaStatsVisible(false) },
        modifier = modifier
    ) {
        val relays by SrtlaManager.relays.collectAsState()
        val context = LocalContext.current
        val statsView = remember { SrtlaStatsView(context) }

        // Only polls while the drawer content is composed and the screen is resumed.
        LifecycleResumeEffect(Unit) {
            statsView.startStatsUpdates()
            onPauseOrDispose { statsView.stopStatsUpdates() }
        }

        AndroidView(
            factory = { statsView },
            update = { it.setRelays(relays) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
