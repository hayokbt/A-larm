package io.github.arashiyama11.a_larm.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.arashiyama11.a_larm.domain.LlmChatGateway
import io.github.arashiyama11.a_larm.domain.LlmChunk
import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.ConversationTurn
import io.github.arashiyama11.a_larm.domain.models.DayBrief
import io.github.arashiyama11.a_larm.domain.models.PromptStyle
import io.github.arashiyama11.a_larm.domain.models.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.time.ExperimentalTime

data class HomeUiState(
    val nextAlarm: String = "--:--",
    val enabled: Boolean = false,
    val history: List<ConversationTurn> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val llmChatGateway: LlmChatGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    var uiState = _uiState.asStateFlow()

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
