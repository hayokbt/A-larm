package io.github.arashiyama11.a_larm.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.arashiyama11.a_larm.domain.LlmChatGateway
import io.github.arashiyama11.a_larm.domain.LlmChunk
import io.github.arashiyama11.a_larm.domain.PersonaRepository
import io.github.arashiyama11.a_larm.domain.RoutineRepository
import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.ConversationTurn
import io.github.arashiyama11.a_larm.domain.models.DayBrief
import io.github.arashiyama11.a_larm.domain.models.PromptStyle
import io.github.arashiyama11.a_larm.domain.models.Role
import io.github.arashiyama11.a_larm.domain.models.RoutineMode
import io.github.arashiyama11.a_larm.domain.usecase.AlarmRulesUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

data class HomeUiState(
    val nextAlarm: String = "--:--",
    val enabled: Boolean = true,
    val history: List<ConversationTurn> = emptyList(),
    val mode: RoutineMode = RoutineMode.DAILY,
    val availablePersonas: List<AssistantPersona> = emptyList(),
    val selectedPersona: AssistantPersona? = null,
    val customAlarmTime: LocalTime? = null,
    val isOneTimeAlarm: Boolean = false,
    val isLoading: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val llmChatGateway: LlmChatGateway,
    private val routineRepository: RoutineRepository,
    private val alarmRulesUseCase: AlarmRulesUseCase,
    private val personaRepository: PersonaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    var uiState = _uiState.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    init {
        loadPersonas()
        loadCurrentPersona()
    }

    fun onStart() {
        routineRepository.getRoutineMode().onEach {
            _uiState.update { currentState ->
                currentState.copy(mode = it)
            }
        }.launchIn(viewModelScope)
        viewModelScope.launch {
            while (isActive) {
                val job = launch {
                    // 次のアラームを計算してUIに反映

                    val nextAlarm = alarmRulesUseCase.nextAlarmRule()
                    if (nextAlarm != null) {
                        _uiState.update {
                            it.copy(nextAlarm = formatTime(nextAlarm.hour, nextAlarm.minute))
                        }
                    }
                }
                delay(30.seconds)
                job.cancel()
            }
        }
    }

    private fun loadPersonas() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val personas = personaRepository.list()
                _uiState.update {
                    it.copy(
                        availablePersonas = personas,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadCurrentPersona() {
        viewModelScope.launch {
            try {
                val currentPersona = personaRepository.getCurrent()
                _uiState.update { it.copy(selectedPersona = currentPersona) }
            } catch (e: Exception) {
                // エラーハンドリング
            }
        }
    }

    private val brief = DayBrief(
        date = LocalDateTime.now()
    )

    fun onToggleEnabled(newValue: Boolean) {
        _uiState.update { it.copy(enabled = newValue) }
        // 実際の実装では AlarmScheduler を使ってアラームの有効/無効を切り替え
    }

    fun onSelectPersona(persona: AssistantPersona) {
        viewModelScope.launch {
            try {
                personaRepository.setCurrent(persona)
                _uiState.update { it.copy(selectedPersona = persona) }
            } catch (e: Exception) {
                // エラーハンドリング
            }
        }
    }

    fun onSetCustomAlarmTime(time: LocalTime) {
        _uiState.update {
            it.copy(
                customAlarmTime = time,
                nextAlarm = time.format(timeFormatter)
            )
        }
        // 実際の実装では AlarmScheduler を使って一回限りのアラームを設定
    }

    fun onToggleOneTimeAlarm(isOneTime: Boolean) {
        _uiState.update { it.copy(isOneTimeAlarm = isOneTime) }
    }

    fun onSkipNextAlarm() {
        // 次のアラームをスキップする処理
        // 実際の実装では AlarmScheduler を使って次回のアラームを無効化
    }

    fun onResetToDefaultAlarm() {
        _uiState.update {
            it.copy(
                customAlarmTime = null,
                isOneTimeAlarm = false
            )
        }
        // デフォルトのアラーム時刻を再計算
    }

    @OptIn(ExperimentalTime::class)
    fun sendMessage(message: String) {
        // ここでメッセージを送信するロジックを実装
        // 例えば、LlmChatGatewayを使ってAIと対話するなど
        _uiState.update {
            it.copy(
                history = it.history + ConversationTurn(
                    role = Role.User,
                    text = message
                )
            )
        }


        val currentPersona = uiState.value.selectedPersona ?: return

        llmChatGateway.streamReply(currentPersona, brief, uiState.value.history)
            .onEach { chunk ->
                // チャンクを受け取ったら、履歴に追加
                val res = when (chunk) {
                    is LlmChunk.Text -> chunk.delta
                    is LlmChunk.Error -> chunk.message
                }
                _uiState.update { currentState ->
                    if (currentState.history.lastOrNull()?.role == Role.Assistant) {
                        // 直前のアシスタントの応答があれば、更新
                        val last = currentState.history.last()
                        currentState.copy(
                            history = currentState.history.dropLast(1) + ConversationTurn(
                                role = Role.Assistant,
                                text = last.text + res
                            )
                        )
                    } else {
                        currentState.copy(
                            history = currentState.history + ConversationTurn(
                                role = Role.Assistant,
                                text = res
                            )
                        )
                    }
                }
            }.launchIn(viewModelScope)
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    return String.format(Locale.JAPAN, "%02d:%02d", hour, minute)
}
