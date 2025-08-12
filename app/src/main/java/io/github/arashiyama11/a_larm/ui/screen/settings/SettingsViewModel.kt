package io.github.arashiyama11.a_larm.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.arashiyama11.a_larm.domain.AlarmSchedulerGateway
import io.github.arashiyama11.a_larm.domain.TtsGateway
import io.github.arashiyama11.a_larm.domain.models.AlarmId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class SettingsUiState(
    val character: String = "Default",
    val language: String = "System",
    val volume: Float = 0.7f,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val ttsGateway: TtsGateway,
    private val alarmScheduler: AlarmSchedulerGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    fun ttsTest() {
        viewModelScope.launch {
            ttsGateway.speak("TTS test message。これはTTSのテストメッセージです", null)
        }
    }

    fun setAlarmTest() {
        val after10Sec = LocalDateTime.now().plus(10, ChronoUnit.SECONDS)

        viewModelScope.launch {
            alarmScheduler.scheduleExact(after10Sec, AlarmId("Test Alarm"))
        }
    }
}

