package com.jhaago.cadagent.ui.navigation

sealed class Destination(val route: String, val label: String) {
    data object CadChat : Destination("cad-chat", "CAD Chat")
    data object Remote : Destination("remote", "Remote")
    data object Settings : Destination("settings", "Settings")
}
