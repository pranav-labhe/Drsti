package com.pranav.drsti.ui.app

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pranav.drsti.di.ServiceLocator
import com.pranav.drsti.ui.screen.PersonScreen
import com.pranav.drsti.ui.screen.PlaceScreen
import com.pranav.drsti.ui.screen.diagnostics.CalibrationScreen
import com.pranav.drsti.ui.screen.diagnostics.DiagnosticsScreen
import com.pranav.drsti.ui.screen.home.HomeScreen
import com.pranav.drsti.ui.screen.onboarding.OnboardingScreen
import com.pranav.drsti.ui.theme.DrshtiTheme
import com.pranav.drsti.ui.viewmodel.DiagnosticsViewModel
import com.pranav.drsti.ui.viewmodel.PersonViewModel
import com.pranav.drsti.ui.viewmodel.PlaceViewModel
import kotlinx.coroutines.launch

/** Application subclass: owns the single ServiceLocator instance for the process lifetime. */
class DrishtiApplication : Application() {
    lateinit var serviceLocator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        serviceLocator = ServiceLocator.getInstance(this)
    }
}

private const val ROUTE_HOME = "home_root"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_PLACES = "places"
private const val ROUTE_DIAGNOSTICS = "diagnostics"
private const val ROUTE_CALIBRATION = "calibration"
private const val ROUTE_ONBOARDING = "onboarding"

/** App-level nav graph: the tabbed Home experience, plus full-screen Profile/Places pushed from Settings. */
@Composable
fun DrishtiApp(serviceLocator: ServiceLocator) {
    DrshtiTheme {
        val navController = rememberNavController()
        val scope = rememberCoroutineScope()
        val onboardingCompleted by serviceLocator.settingsRepository.onboardingCompleted.collectAsState(initial = null)

        val personViewModel = androidx.lifecycle.viewmodel.compose.viewModel<PersonViewModel>(
            factory = PersonViewModel.factory(serviceLocator.personRepository)
        )
        val placeViewModel = androidx.lifecycle.viewmodel.compose.viewModel<PlaceViewModel>(
            factory = PlaceViewModel.factory(serviceLocator.placeRepository)
        )
        val diagnosticsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<DiagnosticsViewModel>(
            factory = DiagnosticsViewModel.factory(serviceLocator.database.aiRequestLogDao())
        )

        // Determine start destination
        val startRoute = when (onboardingCompleted) {
            false -> ROUTE_ONBOARDING
            true -> ROUTE_HOME
            null -> ROUTE_HOME // Loading state, default to home to avoid flicker
        }

        NavHost(navController = navController, startDestination = startRoute) {
            composable(ROUTE_ONBOARDING) {
                OnboardingScreen(
                    onFinish = {
                        scope.launch {
                            serviceLocator.settingsRepository.setOnboardingCompleted(true)
                            navController.navigate(ROUTE_HOME) {
                                popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                            }
                        }
                    }
                )
            }
            composable(ROUTE_HOME) {
                HomeScreen(
                    serviceLocator = serviceLocator,
                    onOpenProfile = { navController.navigate(ROUTE_PROFILE) },
                    onOpenPlaces = { navController.navigate(ROUTE_PLACES) },
                    onOpenDiagnostics = { navController.navigate(ROUTE_DIAGNOSTICS) },
                    onOpenCalibration = { navController.navigate(ROUTE_CALIBRATION) }
                )
            }
            composable(ROUTE_PROFILE) {
                PersonScreen(personViewModel, placeViewModel, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_PLACES) {
                PlaceScreen(placeViewModel, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_DIAGNOSTICS) {
                DiagnosticsScreen(diagnosticsViewModel, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_CALIBRATION) {
                CalibrationScreen(serviceLocator.decisionRepository, onBack = { navController.popBackStack() })
            }
        }
    }
}
