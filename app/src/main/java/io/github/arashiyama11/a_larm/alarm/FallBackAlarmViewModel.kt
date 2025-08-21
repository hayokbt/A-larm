package io.github.arashiyama11.a_larm.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.arashiyama11.a_larm.domain.AlarmSchedulerGateway
import io.github.arashiyama11.a_larm.domain.SimpleAlarmAudioGateway
import io.github.arashiyama11.a_larm.domain.models.AlarmId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject


private val FALLBACK_ALARM_ID = AlarmId(1L) // 予備アラーム用の固定ID

data class FallbackAlarmUiState(
    val isRinging: Boolean = false,
    val startedAt: LocalDateTime? = null,
    val label: String? = "アラーム"
)

@HiltViewModel
class FallbackAlarmViewModel @Inject constructor(
    private val audio: SimpleAlarmAudioGateway,
    private val scheduler: AlarmSchedulerGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow(FallbackAlarmUiState())
    val uiState: StateFlow<FallbackAlarmUiState> = _uiState

    fun startRinging(label: String? = uiState.value.label) {
        if (uiState.value.isRinging) return
        _uiState.update {
            it.copy(
                isRinging = true,
                startedAt = LocalDateTime.now(),
                label = label
            )
        }
        // ブロック until stop（別コルーチンで実行）
        viewModelScope.launch {
            try {
                audio.playAlarmSound()
            } finally {
                // 停止後
                _uiState.update { it.copy(isRinging = false) }
            }
        }
    }

    fun stopRinging() {
        viewModelScope.launch {
            audio.stopAlarmSound() // play の待機を解除
        }
    }

    fun snooze(minutes: Int) {
        viewModelScope.launch {
            // 音を止めて、minutes 分後に再スケジュール
            audio.stopAlarmSound()
            val next = LocalDateTime.now().plusMinutes(minutes.toLong()).withSecond(0).withNano(0)
            scheduler.scheduleExact(next, FALLBACK_ALARM_ID)
        }
        _uiState.update { it.copy(isRinging = false) }
    }
}