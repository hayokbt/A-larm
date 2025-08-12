package io.github.arashiyama11.a_larm.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.arashiyama11.a_larm.ui.screen.fallback.FallbackAlarmScreen
import io.github.arashiyama11.a_larm.ui.screen.fallback.FallbackAlarmViewModel
import io.github.arashiyama11.a_larm.ui.screen.MainPagerScreen
import io.github.arashiyama11.a_larm.ui.screen.apikey.LlmApiKeyScreen
import io.github.arashiyama11.a_larm.ui.screen.onboarding.OnboardingScreen
import io.github.arashiyama11.a_larm.ui.screen.session.SessionScreen
import io.github.arashiyama11.a_larm.ui.screen.session.SessionViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                navigateToHome = { navController.navigate(Screen.Home.route) }
            )
        }
        composable(Screen.Home.route) {
            MainPagerScreen {
                navController.navigate(Screen.ApiKeySetting.route)
            }
        }

        composable(Screen.Session.route) {
            val vm: SessionViewModel = hiltViewModel()
            SessionScreen(state = vm.uiState, onBack = { navController.popBackStack() })
        }

        composable(Screen.FallbackAlarm.route) {
            val vm: FallbackAlarmViewModel = hiltViewModel()
            FallbackAlarmScreen(state = vm.uiState, onBack = { navController.popBackStack() })
        }

        composable(Screen.ApiKeySetting.route) {
            LlmApiKeyScreen {
                navController.popBackStack()
            }
        }
    }
}
