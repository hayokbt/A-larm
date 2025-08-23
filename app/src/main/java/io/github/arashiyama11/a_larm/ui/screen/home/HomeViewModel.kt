package io.github.arashiyama11.a_larm.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.arashiyama11.a_larm.domain.LlmChatGateway
import io.github.arashiyama11.a_larm.domain.LlmChunk
import io.github.arashiyama11.a_larm.domain.RoutineRepository
import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.ConversationTurn
import io.github.arashiyama11.a_larm.domain.models.DayBrief
import io.github.arashiyama11.a_larm.domain.models.PromptStyle
import io.github.arashiyama11.a_larm.domain.models.Role
import io.github.arashiyama11.a_larm.domain.models.RoutineMode
import io.github.arashiyama11.a_larm.domain.usecase.AlarmRulesUseCase
import io.github.arashiyama11.a_larm.infra.TtsGatewayImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

data class HomeUiState(
    val nextAlarm: String = "--:--",
    val enabled: Boolean = false,
    val history: List<ConversationTurn> = emptyList(),
    val mode: RoutineMode = RoutineMode.DAILY
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val llmChatGateway: LlmChatGateway,
    private val routineRepository: RoutineRepository,
    private val alarmRulesUseCase: AlarmRulesUseCase,
    private val ttsGatewayImpl: TtsGatewayImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    var uiState = _uiState.asStateFlow()

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

    private val persona = AssistantPersona(
        id = "id",
        displayName = "Persona Name",
        style = PromptStyle()
    )

    private val brief = DayBrief(
        date = LocalDateTime.now()
    )

    fun onToggleEnabled(newValue: Boolean) {

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


        llmChatGateway.streamReply(persona, brief, uiState.value.history)
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
