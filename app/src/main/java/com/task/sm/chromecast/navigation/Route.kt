package com.task.sm.chromecast.navigation

import androidx.navigation.NamedNavArgument

sealed class Route(
    val route: String,
    val arguments: List<NamedNavArgument> = emptyList()
) {
    object AppStartNavigation : Route(route = "AppStartNavigation")

    object ChromeCastAppNavigation : Route(route = "ChromeCastAppNavigation")
    object HomeScreen : Route(route = "HomeScreen")
}