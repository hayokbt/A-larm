package io.github.arashiyama11.a_larm.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.arashiyama11.a_larm.alarm.AlarmScheduler
import io.github.arashiyama11.a_larm.domain.TtsGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val character: String = "Default",
    val language: String = "System",
    val volume: Float = 0.7f,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val ttsGateway: TtsGateway,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    fun ttsTest() {
        viewModelScope.launch {
            ttsGateway.speak("TTS test message。これはTTSのテストメッセージです", null)
        }
    }

    fun setAlarmTest() {
        alarmScheduler.scheduleAlarm(10000, "Test Alarm")
    }
}

