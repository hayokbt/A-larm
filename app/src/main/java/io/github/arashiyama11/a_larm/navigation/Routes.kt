package io.github.arashiyama11.a_larm.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")

    data object ApiKeySetting : Screen("api_key_setting")
    data object Session : Screen("session")
    data object FallbackAlarm : Screen("fallback_alarm")
}

