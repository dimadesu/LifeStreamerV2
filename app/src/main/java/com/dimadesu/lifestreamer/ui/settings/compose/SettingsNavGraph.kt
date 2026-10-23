package com.dimadesu.lifestreamer.ui.settings.compose

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

object SettingsDestinations {
    const val INDEX = "settings_index"
    const val ENDPOINT = "settings_endpoint"
    const val VIDEO = "settings_video"
    const val AUDIO = "settings_audio"
    const val RTMP_SOURCE = "settings_rtmp_source"
}

@Composable
fun SettingsNavGraph(
    navController: NavHostController = rememberNavController(),
    viewModel: SettingsViewModel = viewModel(),
    onFinish: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = SettingsDestinations.INDEX,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(280))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(280))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(280))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(280))
        }
    ) {
        composable(SettingsDestinations.INDEX) {
            SettingsIndexScreen(
                viewModel = viewModel,
                onNavigateToEndpoint = { navController.navigate(SettingsDestinations.ENDPOINT) },
                onNavigateToVideo = { navController.navigate(SettingsDestinations.VIDEO) },
                onNavigateToAudio = { navController.navigate(SettingsDestinations.AUDIO) },
                onNavigateToRtmpSource = { navController.navigate(SettingsDestinations.RTMP_SOURCE) },
                onBackClick = onFinish
            )
        }

        composable(SettingsDestinations.ENDPOINT) {
            EndpointSettingsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(SettingsDestinations.VIDEO) {
            VideoSettingsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(SettingsDestinations.AUDIO) {
            AudioSettingsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(SettingsDestinations.RTMP_SOURCE) {
            RtmpSourceSettingsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
