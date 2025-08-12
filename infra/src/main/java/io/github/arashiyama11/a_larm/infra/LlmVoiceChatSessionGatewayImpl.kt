package io.github.arashiyama11.a_larm.infra

import io.github.arashiyama11.a_larm.domain.LlmVoiceChatSessionGateway
import io.github.arashiyama11.a_larm.domain.LlmVoiceChatState
import io.github.arashiyama11.a_larm.domain.VoiceChatResponse
import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.ConversationTurn
import io.github.arashiyama11.a_larm.domain.models.DayBrief
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class LlmVoiceChatSessionGatewayImpl @Inject constructor() : LlmVoiceChatSessionGateway {
    private val _chatState = MutableStateFlow(LlmVoiceChatState.IDLE)
    override val chatState: StateFlow<LlmVoiceChatState>
        get() = _chatState

    private val _response = MutableSharedFlow<VoiceChatResponse>(extraBufferCapacity = 10)
    override val response: Flow<VoiceChatResponse>
        get() = MutableSharedFlow()

    override suspend fun initialize(
        persona: AssistantPersona,
        brief: DayBrief,
        history: List<ConversationTurn>
    ) {
        _chatState.value = LlmVoiceChatState.INITIALIZING
        repeat(5) {
            delay(50)
            _chatState.value = LlmVoiceChatState.ASSISTANT_THINKING
            delay(1000)
            _chatState.value = LlmVoiceChatState.ASSISTANT_SPEAKING
            _response.emit(
                VoiceChatResponse.Text("Hello, how can I assist you today?")
            )
            delay(1000)
            _chatState.value = LlmVoiceChatState.IDLE
            delay(100)
            _chatState.value = LlmVoiceChatState.USER_SPEAKING
            delay(5000)
            _chatState.value = LlmVoiceChatState.ASSISTANT_THINKING
            delay(1000)
            _chatState.value = LlmVoiceChatState.ASSISTANT_SPEAKING
            _response.emit(
                VoiceChatResponse.Text("I am here to help you with your tasks.")
            )
        }
    }

    override suspend fun stop() {
    }
}