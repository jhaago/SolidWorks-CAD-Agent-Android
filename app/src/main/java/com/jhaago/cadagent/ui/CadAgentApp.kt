package com.jhaago.cadagent.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jhaago.cadagent.di.AppContainer
import com.jhaago.cadagent.remote.ui.RemoteViewModel
import com.jhaago.cadagent.remote.ui.RemoteScreen
import com.jhaago.cadagent.remote.ui.WorkstationSettingsScreen
import com.jhaago.cadagent.ui.common.CadAgentViewModelFactory
import com.jhaago.cadagent.ui.navigation.Destination
import com.jhaago.cadagent.ui.settings.SettingsScreen
import com.jhaago.cadagent.ui.theme.CadAgentTheme

@Composable
fun CadAgentApp(container: AppContainer = viewModel<CadAgentAppViewModel>().container) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = listOf(Destination.Remote, Destination.Settings)

    CadAgentTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    topLevel.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(Destination.Remote.route) { saveState = true }
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
                startDestination = Destination.Remote.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(Destination.Remote.route) {
                    val adapters by container.remoteAdapters.collectAsStateWithLifecycle()
                    val remoteViewModel: RemoteViewModel = viewModel(
                        key = "remote-${adapters.revision}",
                        factory = CadAgentViewModelFactory {
                            RemoteViewModel(adapters.session, adapters.ai, adapters.display, adapters.input)
                        },
                    )
                    val state by remoteViewModel.uiState.collectAsStateWithLifecycle()
                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    DisposableEffect(lifecycle, remoteViewModel) {
                        val observer = LifecycleEventObserver { _, _ -> remoteViewModel.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
                        lifecycle.addObserver(observer)
                        remoteViewModel.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                        onDispose { lifecycle.removeObserver(observer); remoteViewModel.setForeground(false) }
                    }
                    RemoteScreen(state, remoteViewModel)
                }
                composable(Destination.Settings.route) {
                    container.remoteSettings?.let { WorkstationSettingsScreen(it) } ?: SettingsScreen()
                }
            }
        }
    }
}
