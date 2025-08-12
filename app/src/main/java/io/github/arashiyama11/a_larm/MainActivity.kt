package io.github.arashiyama11.a_larm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.arashiyama11.a_larm.alarm.AlarmScheduler
import io.github.arashiyama11.a_larm.navigation.AppNavGraph
import io.github.arashiyama11.a_larm.navigation.Screen
import io.github.arashiyama11.a_larm.ui.theme.AlarmTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        alarmScheduler.scheduleAlarm(10000, "Test Alarm")

        setContent {
            AlarmTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val start = if (PermissionManager(this).hasAllRequiredPermissions()) {
                        Screen.Home.route
                    } else {
                        Screen.Onboarding.route
                    }
                    val navController = rememberNavController()
                    AppNavGraph(navController = navController, startDestination = start)
                }
            }
        }
    }
}
