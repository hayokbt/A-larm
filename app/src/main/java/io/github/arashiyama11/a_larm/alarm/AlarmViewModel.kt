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
import io.github.arashiyama11.a_larm.domain.models.ConversationTurn
import io.github.arashiyama11.a_larm.domain.models.DayBrief
import io.github.arashiyama11.a_larm.domain.models.PromptStyle
import io.github.arashiyama11.a_larm.domain.models.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

// 最初は 音を鳴らして、LLMからの応答を待つ
enum class AlarmPhase {
    RINGING, TALKING, SUCCESS, NIDONE, FAILED_RESPONSE, FAILED_TTS, FALLBACK_ALARM
}

data class AlarmUiState(
    val sendingUserVoice: Boolean = false,
    val phase: AlarmPhase = AlarmPhase.RINGING,
    val chatState: LlmVoiceChatState = LlmVoiceChatState.IDLE,
    val startAt: LocalDateTime? = null,
    val assistantTalk: List<ConversationTurn> = emptyList(),
    val closeButtonEnabled: Boolean = false,
)

sealed interface AlarmUiAction {
    data object Start : AlarmUiAction
    data object Stop : AlarmUiAction
}

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val ttsGateway: TtsGateway,
    private val llmVoiceChatSessionGatewayProvider: Provider<LlmVoiceChatSessionGateway>,
    private val audioOutputGateway: AudioOutputGateway,
    private val simpleAlarmAudioGateway: SimpleAlarmAudioGateway
) : ViewModel() {
    private val _uiState = MutableStateFlow(AlarmUiState())
    lateinit var llmVoiceChatSessionGateway: LlmVoiceChatSessionGateway
    val uiState = _uiState.asStateFlow()
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

    @OptIn(ExperimentalTime::class)
    fun onStart() {
        if (started) return
        started = true
        llmVoiceChatSessionGateway = llmVoiceChatSessionGatewayProvider.get()

        llmVoiceChatSessionGateway.onSetupComplete {
            delay(5.seconds)
            llmVoiceChatSessionGateway.sendSystemMessage("ユーザーに起床を促してください")
        }

        viewModelScope.launch {
            while (isActive) {
                val isUserWakeUp = _uiState.value.assistantTalk.filter {
                    Clock.System.now().epochSeconds - it.at.epochSeconds < 30 && it.role == Role.User
                }.let {
                    Log.d("AlarmViewModel", "Recent user talks: $it")
                    it.all { it.text.isNotBlank() } && it.isNotEmpty()
                }
                Log.d("AlarmViewModel", "isUserWakeUp: $isUserWakeUp")
                if (isUserWakeUp && _uiState.value.phase != AlarmPhase.SUCCESS) {
                    _uiState.update {
                        it.copy(
                            phase = AlarmPhase.SUCCESS,
                            sendingUserVoice = false,
                            closeButtonEnabled = true
                        )
                    }
                    break
                }
                delay(1000)
            }
        }

        viewModelScope.launch {
            llmVoiceChatSessionGateway.chatState.collect {
                Log.d("AlarmViewModel", "Chat state updated: $it")
                _uiState.update { currentState ->
                    if (it == LlmVoiceChatState.ERROR) {
                        currentState.copy(
                            phase = AlarmPhase.FALLBACK_ALARM,
                            sendingUserVoice = false
                        )
                    } else if (it == LlmVoiceChatState.ACTIVE) {
                        currentState.copy(
                            phase = AlarmPhase.TALKING,
                            sendingUserVoice = false,
                            chatState = it
                        )
                    } else {
                        currentState.copy(chatState = it)
                    }
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            llmVoiceChatSessionGateway.initialize(persona, brief, emptyList())
        }
        addCloseable {
            runBlocking {
                llmVoiceChatSessionGateway.stop()
            }

            runBlocking {
                simpleAlarmAudioGateway.stopAlarmSound()
            }
        }
        simpleAlarmJob = viewModelScope.launch(Dispatchers.IO) {
            simpleAlarmAudioGateway.playAlarmSound()
        }

        viewModelScope.launch {
            var inActiveCount = 0
            llmVoiceChatSessionGateway.response.onEach {
                Log.d("AlarmViewModel", "Response received: $it")
            }.collectWithInactivityTimeout(viewModelScope, 10_000, onInactive = {
                if (inActiveCount++ > 3) {
                    Log.d("AlarmViewModel", "No response from user, switching to fallback alarm")
                    _uiState.update {
                        it.copy(
                            phase = AlarmPhase.FALLBACK_ALARM,
                            sendingUserVoice = false
                        )
                    }

                    llmVoiceChatSessionGateway.stop()
                }
                if (inActiveCount == 2) {
                    // マシンガン
                    repeat(5) {
                        llmVoiceChatSessionGateway.sendSystemMessage("ユーザーに起床を促してください")
                        delay(1000)
                    }
                } else {
                    llmVoiceChatSessionGateway.sendSystemMessage("ユーザーが${inActiveCount * 10}秒応答していません。さらに起床を促してください")
                }

            }) { res ->
                coroutineScope {
                    if (uiState.value.phase == AlarmPhase.RINGING || simpleAlarmJob?.isActive == true) {
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
                                ttsGateway.speak(res.texts.mapNotNull { if (it.role == Role.Assistant) it.text else null }
                                    .joinToString("\n"))
                                delay(500)
                                llmVoiceChatSessionGateway.setTtsPlaying(false)
                            }
                            _uiState.update {
                                it.copy(
                                    assistantTalk = it.assistantTalk + res.texts,
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
            }
        }
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

suspend fun <T> Flow<T>.collectWithInactivityTimeout(
    scope: CoroutineScope,
    inactivityMs: Long = 10_000L,
    onInactive: suspend () -> Unit,
    onNext: suspend (T) -> Unit,
) {
    val channel = this.produceIn(scope) // ReceiveChannel<T>
    try {
        while (scope.isActive) {
            // 次の要素を最大 inactivityMs 待つ
            val value = withTimeoutOrNull(inactivityMs) {
                channel.receive()
            }
            if (value == null) {
                onInactive()
            } else {
                onNext(value)
            }
        }
    } finally {
        channel.cancel()
    }
}
