package io.github.arashiyama11.a_larm.domain

import io.github.arashiyama11.a_larm.domain.models.AlarmId
import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.CalendarEvent
import io.github.arashiyama11.a_larm.domain.models.ConversationTurn
import io.github.arashiyama11.a_larm.domain.models.DayBrief
import io.github.arashiyama11.a_larm.domain.models.VoiceStyle
import io.github.arashiyama11.a_larm.domain.models.VolumeRampPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


/** 読み取りだけ（READ_CALENDAR相当は :infra が担保） */
interface CalendarReadGateway {
    suspend fun eventsOn(date: LocalDate): List<CalendarEvent>
}

/** 現在地/天気などの軽量ブリーフ（プライバシ配慮で曖昧/ローカルのみ） */
interface DayBriefGateway {
    suspend fun buildBrief(forDate: LocalDate): DayBrief
}

interface SimpleAlarmAudioGateway {
    /** アラーム音を鳴らす */
    suspend fun playAlarmSound()

    /** アラーム音を停止する */
    suspend fun stopAlarmSound()
}

/** OSアラームスケジューラの抽象化 */
interface AlarmSchedulerGateway {
    suspend fun scheduleExact(at: LocalDateTime, payload: AlarmId?)
    suspend fun cancel(id: AlarmId)
    val triggers: Flow<AlarmTrigger> // OSからの実行トリガ
}

@OptIn(ExperimentalTime::class)
data class AlarmTrigger(val at: Instant, val alarmId: AlarmId?)

/** 音量制御（可能な範囲のみ） */
interface AudioOutputGateway {
    suspend fun setVolume(level: Int)
    suspend fun ramp(policy: VolumeRampPolicy)

    suspend fun play(data: ByteArray)

    fun supportedRange(): IntRange // 例: 0..15

}

/** STT / TTS / LLM —— それぞれ独立したポートに分離 */
// sttは不要かも
interface SttGateway {
    /** セッション開始後、部分/確定結果をストリームで受け取る */
    fun startStreaming(): Flow<SttResult>
    suspend fun stop()
}

sealed interface SttResult {
    data class Partial(val text: String) : SttResult
    data class Final(val text: String) : SttResult
    data class Error(val message: String) : SttResult
}

interface TtsGateway {
    /** 再生完了までサスペンド。タイムアウトは呼び出し側管理 */
    suspend fun speak(text: String, voice: VoiceStyle? = null)
}

interface LlmChatGateway {
    /** ペルソナ＋ブリーフ＋履歴から応答をストリーミング生成 */
    fun streamReply(
        persona: AssistantPersona,
        brief: DayBrief,
        history: List<ConversationTurn>
    ): Flow<LlmChunk>
}

sealed interface LlmChunk {
    data class Text(val delta: String) : LlmChunk
    data class Error(val message: String) : LlmChunk
}

interface LlmVoiceChatSessionGateway {
    suspend fun initialize(
        persona: AssistantPersona,
        brief: DayBrief,
        history: List<ConversationTurn>
    )

    suspend fun stop()

    val chatState: StateFlow<LlmVoiceChatState>
    val response: Flow<VoiceChatResponse>
}

sealed interface VoiceChatResponse {
    data class Voice(val data: ByteArray) : VoiceChatResponse {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Voice

            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }

    data class Text(val text: String) : VoiceChatResponse

    data class Error(val message: String) : VoiceChatResponse
}


enum class LlmVoiceChatState {
    IDLE,
    INITIALIZING,
    USER_SPEAKING,
    ASSISTANT_THINKING,
    ASSISTANT_SPEAKING,
    STOPPING,
    ERROR
}


