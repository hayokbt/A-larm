package io.github.arashiyama11.a_larm.infra

import io.github.arashiyama11.a_larm.domain.LlmApiKeyRepository
import io.github.arashiyama11.a_larm.domain.LlmChatGateway
import io.github.arashiyama11.a_larm.domain.LlmChunk
import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.ConversationTurn
import io.github.arashiyama11.a_larm.domain.models.DayBrief
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class LlmChatGatewayImpl @Inject constructor(
    private val llmApiKeyRepositoryImpl: LlmApiKeyRepository
) : LlmChatGateway {
    override fun streamReply(
        persona: AssistantPersona,
        brief: DayBrief,
        history: List<ConversationTurn>
    ): Flow<LlmChunk> {

        return flow {
            emit(LlmChunk.Text("これはダミーの応答です。\n"))
            delay(500)
            emit(LlmChunk.Text("さらに続けて応答します。"))
        }
    }
}