package com.jhaago.cadagent.ui.navigation

sealed class Destination(val route: String, val label: String) {
    data object Home : Destination("home", "Home")
    data object Jobs : Destination("jobs", "Jobs")
    data object Settings : Destination("settings", "Settings")
    data object NewJob : Destination("new-job", "New Job")

    data object JobDetail : Destination("job/{jobId}", "Job Detail") {
        const val ARGUMENT = "jobId"
        fun route(jobId: String): String = "job/$jobId"
    }
}
