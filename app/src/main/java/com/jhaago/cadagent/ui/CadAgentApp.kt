package com.jhaago.cadagent.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jhaago.cadagent.di.AppContainer
import com.jhaago.cadagent.ui.common.CadAgentViewModelFactory
import com.jhaago.cadagent.ui.home.HomeScreen
import com.jhaago.cadagent.ui.home.HomeViewModel
import com.jhaago.cadagent.ui.jobs.JobsScreen
import com.jhaago.cadagent.ui.jobs.JobsViewModel
import com.jhaago.cadagent.ui.navigation.Destination

@Composable
fun CadAgentApp(container: AppContainer = remember { AppContainer() }) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = listOf(Destination.Home, Destination.Jobs, Destination.Settings)

    MaterialTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    topLevel.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(Destination.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(destination.label.take(1)) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Destination.Home.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(Destination.Home.route) {
                    val homeViewModel: HomeViewModel = viewModel(
                        factory = CadAgentViewModelFactory { HomeViewModel(container.repository) },
                    )
                    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
                    HomeScreen(
                        state = state,
                        onNewJob = { navController.navigate(Destination.NewJob.route) },
                        onJobClick = { navController.navigate(Destination.JobDetail.route(it)) },
                    )
                }
                composable(Destination.Jobs.route) {
                    val jobsViewModel: JobsViewModel = viewModel(
                        factory = CadAgentViewModelFactory { JobsViewModel(container.repository) },
                    )
                    val state by jobsViewModel.uiState.collectAsStateWithLifecycle()
                    JobsScreen(
                        state = state,
                        onJobClick = { navController.navigate(Destination.JobDetail.route(it)) },
                    )
                }
                composable(Destination.Settings.route) {
                    PlaceholderScreen(
                        title = "Settings",
                        body = "Remote connection settings will be added after the secure gateway is designed.",
                    )
                }
                composable(Destination.NewJob.route) {
                    PlaceholderScreen(
                        title = "New CAD Job",
                        body = "Prompt submission is added in the next implementation task.",
                    )
                }
                composable(
                    route = Destination.JobDetail.route,
                    arguments = listOf(navArgument(Destination.JobDetail.ARGUMENT) { type = NavType.StringType }),
                ) { entry ->
                    val jobId = entry.arguments?.getString(Destination.JobDetail.ARGUMENT).orEmpty()
                    PlaceholderScreen(
                        title = "Job Detail",
                        body = "Job $jobId",
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, body: String) {
    Column(Modifier.padding(24.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(body, modifier = Modifier.padding(top = 12.dp))
    }
}
