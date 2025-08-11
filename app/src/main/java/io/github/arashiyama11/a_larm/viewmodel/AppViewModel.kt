package io.github.arashiyama11.a_larm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.arashiyama11.a_larm.domain.LlmChatGateway
import io.github.arashiyama11.a_larm.domain.LlmChunk
import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.DayBrief
import io.github.arashiyama11.a_larm.domain.models.PromptStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.LocalDateTime
import javax.inject.Inject

data class AppUiState(
    val messages: List<String> = emptyList(),
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val llmChatGateway: LlmChatGateway
) : ViewModel(){

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState = _uiState.asStateFlow()

    private var started: Boolean= false

    fun onStart(){
        if (started) return
        started = true

        llmChatGateway.streamReply(
            AssistantPersona(
                "id","displayName", PromptStyle(),
            ),
            DayBrief(
                date = LocalDateTime.now(),

            ),
            emptyList()
        ).onEach {
            val newMessages = _uiState.value.messages + it.toString() + "\n"
            _uiState.value = _uiState.value.copy(messages = newMessages)
        }.launchIn(viewModelScope)
    }

}