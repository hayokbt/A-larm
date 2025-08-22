package io.github.arashiyama11.a_larm.alarm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.arashiyama11.a_larm.domain.AudioOutputGateway
import io.github.arashiyama11.a_larm.domain.LlmVoiceChatSessionGateway
import io.github.arashiyama11.a_larm.domain.LlmVoiceChatState
import io.github.arashiyama11.a_larm.domain.SimpleAlarmAudioGateway
import io.github.arashiyama11.a_larm.domain.TtsGateway
import io.github.arashiyama11.a_larm.domain.VoiceChatResponse
import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.DayBrief
import io.github.arashiyama11.a_larm.domain.models.PromptStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

// 最初は 音を鳴らして、LLMからの応答を待つ
enum class AlarmPhase {
    RINGING, TALKING, SUCCESS, NIDONE, FAILED_RESPONSE, FAILED_TTS,
}

data class AlarmUiState(
    val sendingUserVoice: Boolean = false,
    val phase: AlarmPhase = AlarmPhase.RINGING,
    val chatState: LlmVoiceChatState = LlmVoiceChatState.IDLE,
    val startAt: LocalDateTime? = null,
    val assistantTalk: List<String> = emptyList()
)

sealed interface AlarmUiAction {
    data object Start : AlarmUiAction
    data object Stop : AlarmUiAction
}

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val ttsGateway: TtsGateway,
    private val llmVoiceChatSessionGateway: LlmVoiceChatSessionGateway,
    private val audioOutputGateway: AudioOutputGateway,
    private val simpleAlarmAudioGateway: SimpleAlarmAudioGateway
) : ViewModel() {
    private val _uiState = MutableStateFlow(AlarmUiState())
    val uiState = combine(_uiState, llmVoiceChatSessionGateway.chatState) { uiState, chatState ->
        uiState.copy(chatState = chatState)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AlarmUiState())
    private var started: Boolean = false

    private val persona = AssistantPersona(
        id = "id",
        displayName = "Persona Name",
        style = PromptStyle()
    )

    private val brief = DayBrief(
        date = LocalDateTime.now()
    )

    private var simpleAlarmJob: Job? = null

    fun onStart() {
        if (started) return
        started = true
        viewModelScope.launch(Dispatchers.IO) {
            llmVoiceChatSessionGateway.initialize(persona, brief, emptyList())
        }
        simpleAlarmJob = viewModelScope.launch(Dispatchers.IO) {
            simpleAlarmAudioGateway.playAlarmSound()
        }

        llmVoiceChatSessionGateway.response.onEach {
            Log.d("AlarmViewModel", "Response received: $it")
        }.onEach { res ->
            coroutineScope {
                if (uiState.value.phase == AlarmPhase.RINGING) {
                    try {
                        simpleAlarmAudioGateway.stopAlarmSound()
                    } finally {
                        simpleAlarmJob?.cancel()
                        simpleAlarmJob = null
                    }
                    _uiState.update {
                        it.copy(
                            phase = AlarmPhase.TALKING,
                            sendingUserVoice = false,
                            startAt = LocalDateTime.now()
                        )
                    }
                }

                when (res) {
                    is VoiceChatResponse.Text -> {
                        launch {
                            llmVoiceChatSessionGateway.setTtsPlaying(true)
                            ttsGateway.speak(res.text)
                            llmVoiceChatSessionGateway.setTtsPlaying(false)
                        }
                        _uiState.update {
                            it.copy(
                                assistantTalk = it.assistantTalk + res.text,
                            )
                        }
                    }

                    is VoiceChatResponse.Voice -> {
                        audioOutputGateway.play(res.data)
                    }

                    is VoiceChatResponse.Error -> {
                        _uiState.update {
                            it.copy(
                                phase = AlarmPhase.FAILED_RESPONSE,
                                sendingUserVoice = false
                            )
                        }
                    }
                }
            }
        }.launchIn(viewModelScope)
    }


    fun reduce(action: AlarmUiAction) {
        when (action) {
            is AlarmUiAction.Start -> {
                if (!started) {
                    onStart()
                }
            }

            is AlarmUiAction.Stop -> {
                viewModelScope.launch(Dispatchers.IO) {
                    llmVoiceChatSessionGateway.stop()
                    _uiState.update {
                        it.copy(
                            phase = AlarmPhase.SUCCESS,
                            sendingUserVoice = false
                        )
                    }
                }
            }
        }
    }
}
