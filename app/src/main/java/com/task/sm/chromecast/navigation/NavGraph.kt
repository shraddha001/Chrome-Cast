package com.task.sm.chromecast.navigation

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.task.sm.chromecast.screens.HomeScreen

@OptIn(UnstableApi::class)
@Composable
fun NavGraph(
    startDestination: String
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        navigation(
            route = Route.ChromeCastAppNavigation.route,
            startDestination = Route.HomeScreen.route
        ) {
            composable(route = Route.HomeScreen.route) {
                HomeScreen(navController)
            }
        }
    }
}