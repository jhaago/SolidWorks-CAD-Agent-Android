package com.jhaago.cadagent.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.unit.dp
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
import com.jhaago.cadagent.remote.ui.CadChatScreen
import com.jhaago.cadagent.remote.ui.WorkstationSettingsScreen
import com.jhaago.cadagent.ui.common.CadAgentViewModelFactory
import com.jhaago.cadagent.ui.navigation.Destination
import com.jhaago.cadagent.ui.settings.SettingsScreen
import com.jhaago.cadagent.ui.theme.CadAgentTheme
import com.jhaago.cadagent.ui.theme.CadIcons

@Composable
fun CadAgentApp(container: AppContainer = viewModel<CadAgentAppViewModel>().container) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = listOf(Destination.CadChat, Destination.Remote, Destination.Settings)
    val adapters by container.remoteAdapters.collectAsStateWithLifecycle()
    val remoteViewModel: RemoteViewModel = viewModel(
        key = "remote-${adapters.revision}",
        factory = CadAgentViewModelFactory {
            RemoteViewModel(adapters.session, adapters.ai, adapters.display, adapters.input)
        },
    )
    val state by remoteViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, remoteViewModel, currentRoute) {
        val observer = LifecycleEventObserver { _, _ ->
            remoteViewModel.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && currentRoute != Destination.Settings.route)
        }
        lifecycle.addObserver(observer)
        remoteViewModel.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && currentRoute != Destination.Settings.route)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(currentRoute) { if (currentRoute != Destination.Remote.route) remoteViewModel.cancelInput() }

    CadAgentTheme {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                    topLevel.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(Destination.CadChat.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(when (destination) {
                                Destination.CadChat -> CadIcons.Chat
                                Destination.Remote -> CadIcons.Desktop
                                else -> CadIcons.Settings
                            }, contentDescription = null) },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Destination.CadChat.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(Destination.CadChat.route) { CadChatScreen(state, remoteViewModel, onOpenSettings = { navController.navigate(Destination.Settings.route) { launchSingleTop = true } }) }
                composable(Destination.Remote.route) {
                    RemoteScreen(state, remoteViewModel)
                }
                composable(Destination.Settings.route) {
                    container.remoteSettings?.let { WorkstationSettingsScreen(it) } ?: SettingsScreen()
                }
            }
        }
    }
}
