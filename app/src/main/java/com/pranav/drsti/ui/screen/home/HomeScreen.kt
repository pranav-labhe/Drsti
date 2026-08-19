package com.pranav.drsti.ui.screen.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pranav.drsti.di.ServiceLocator
import com.pranav.drsti.ui.screen.chat.ChatScreen
import com.pranav.drsti.ui.screen.decisions.DecisionScreen
import com.pranav.drsti.ui.screen.kundali.KundaliScreen
import com.pranav.drsti.ui.screen.panchang.PanchangScreen
import com.pranav.drsti.ui.screen.settings.SettingsScreen
import com.pranav.drsti.ui.viewmodel.*

private sealed class HomeTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Chat : HomeTab("chat", "Chat", Icons.Filled.Chat)
    data object Decisions : HomeTab("decisions", "Decisions", Icons.Filled.AddChart)
    data object Kundali : HomeTab("kundali", "Kundali", Icons.Filled.AutoAwesome)
    data object Panchang : HomeTab("panchang", "Panchang", Icons.Filled.CalendarMonth)
    data object Settings : HomeTab("settings", "Settings", Icons.Filled.Settings)
}

private val tabs = listOf(HomeTab.Chat, HomeTab.Decisions, HomeTab.Kundali, HomeTab.Panchang, HomeTab.Settings)

/**
 * Hosts the primary bottom-navigation experience (spec §4, §53). Chat is the
 * default/start tab — Kundali, Panchang, Dasha info and Settings remain one
 * tap away as supporting screens rather than the primary flow.
 */
@Composable
fun HomeScreen(
    serviceLocator: ServiceLocator,
    onOpenProfile: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(
            serviceLocator.chatRepository,
            serviceLocator.personRepository,
            serviceLocator.database.panchangDao(),
            serviceLocator.database.kundaliDao(),
            serviceLocator.database.dashaDao(),
            serviceLocator.database.planetaryPositionDao(),
            serviceLocator.database.decisionDao(),
            serviceLocator.database.decisionAnalysisDao(),
            { serviceLocator.aiService.value }
        )
    )
    val kundaliViewModel: KundaliViewModel = viewModel(
        factory = KundaliViewModel.factory(
            serviceLocator.personRepository, serviceLocator.database.kundaliDao(), serviceLocator.database.dashaDao()
        ) { serviceLocator.aiService.value }
    )
    val transitViewModel: TransitViewModel = viewModel(
        factory = TransitViewModel.factory(
            serviceLocator.personRepository,
            serviceLocator.database.kundaliDao(),
            serviceLocator.database.planetaryPositionDao(),
            serviceLocator.database.transitAnalysisDao(),
            serviceLocator.settingsRepository,
            { serviceLocator.aiService.value }
        )
    )
    val panchangViewModel: PanchangViewModel = viewModel(
        factory = PanchangViewModel.factory(
            serviceLocator.personRepository, serviceLocator.database.panchangDao()
        ) { serviceLocator.aiService.value }
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(serviceLocator.settingsRepository, serviceLocator.secureStorage, serviceLocator)
    )
    val decisionViewModel: DecisionViewModel = viewModel(
        factory = DecisionViewModel.factory(
            serviceLocator.decisionRepository,
            serviceLocator.personRepository,
            serviceLocator.database.panchangDao(),
            serviceLocator.database.kundaliDao(),
            serviceLocator.database.dashaDao(),
            serviceLocator.database.planetaryPositionDao(),
            { serviceLocator.aiService.value }
        )
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = false
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        // Use bottom padding from scaffold to ensure child screens stay above the navigation bar
        NavHost(
            navController = navController,
            startDestination = HomeTab.Chat.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            composable(HomeTab.Chat.route) {
                ChatScreen(
                    viewModel = chatViewModel,
                    onNavigateToSettings = { navController.navigate(HomeTab.Settings.route) },
                    onNavigateToProfile = onOpenProfile,
                    onNavigateToKundali = { navController.navigate(HomeTab.Kundali.route) },
                    onNavigateToPanchang = { navController.navigate(HomeTab.Panchang.route) },
                    onNavigateToDecisions = { navController.navigate(HomeTab.Decisions.route) }
                )
            }
            composable(HomeTab.Decisions.route) { DecisionScreen(decisionViewModel) }
            composable(HomeTab.Kundali.route) { 
                KundaliScreen(kundaliViewModel, transitViewModel) 
            }
            composable(HomeTab.Panchang.route) { PanchangScreen(panchangViewModel) }
            composable(HomeTab.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    database = serviceLocator.database,
                    onOpenProfile = onOpenProfile,
                    onOpenPlaces = onOpenPlaces,
                    onOpenDiagnostics = onOpenDiagnostics
                )
            }
        }
    }
}

@Composable
private inline fun <reified VM : androidx.lifecycle.ViewModel> viewModel(factory: androidx.lifecycle.ViewModelProvider.Factory): VM =
    androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
