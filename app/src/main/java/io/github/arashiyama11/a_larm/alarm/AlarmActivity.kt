package io.github.arashiyama11.a_larm.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.arashiyama11.a_larm.domain.usecase.AlarmRulesUseCase
import io.github.arashiyama11.a_larm.ui.theme.AlarmTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject
    lateinit var alarmRulesUseCase: AlarmRulesUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setShowWhenLocked(true)
        setTurnScreenOn(true)


        setContent {
            AlarmTheme {
                AlarmScreen(finish = ::close)
            }
        }
    }

    private fun close() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                alarmRulesUseCase.setNextAlarm(LocalDateTime.now().plusMinutes(1))
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                finish()
            }
        }
    }
}