package io.github.arashiyama11.a_larm.domain.models

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime


data class AlarmRule(
    val id: AlarmId,
    val label: String?,
    val mode: RoutineMode,
    val type: RoutineType,
    val dayIndex: Int, // 0=Mon, 6=Sun
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
)

/** 起床判定のための閾値/方針 */
data class WakeCriteria(
    val minConversationSeconds: Int = 20,
    val okPhrases: Set<String> = setOf("起きた", "起きたよ", "起きました"),
    val minSteps: Int = 10,
    val requireTap: Boolean = false,
    val strategy: WakeStrategy = WakeStrategy.Balanced
) {
    companion object {
        fun default() = WakeCriteria()
    }
}

enum class WakeStrategy { Lenient, Balanced, Strict }

/** 無応答・遅延時のフォールバック/音量エスカレーション */
data class NoResponsePolicy(
    val escalateAfterSeconds: Int = 8,
    val volumeRamp: VolumeRampPolicy = VolumeRampPolicy(),
    val llmTimeoutMs: Long = 3500,
    val ttsTimeoutMs: Long = 2500,
    val fallbackPhrases: List<String> = listOf(
        "おはよう。起きてる？",
        "返事がなければアラーム音に切り替えるね"
    )
)

data class VolumeRampPolicy(
    val startLevel: Int = 3,       // 0..max（infraが実際の範囲に合わせて丸める）
    val maxLevel: Int = 10,
    val step: Int = 1,
    val stepIntervalSeconds: Int = 5
)

/** ベッドタイムの促し */
data class BedtimeNudgePolicy(
    val enabled: Boolean = false,
    val nudgeBeforeMinutes: Int = 30
)

/** アラームセッション（1回の起床試行のライフサイクル） */
data class AlarmSession(
    val id: SessionId,
    val alarmId: AlarmId?,
    val persona: AssistantPersona,
    val startedAt: Instant,
    val state: AlarmState,
    val wakeCriteria: WakeCriteria,
    val noResponsePolicy: NoResponsePolicy
)

/** セッションの状態機械 */
sealed interface AlarmState {
    data object Idle : AlarmState
    data object Ringing : AlarmState
    data class Conversing(
        val since: Instant,
        val conversationSeconds: Int = 0
    ) : AlarmState

    data object AwakeConfirmed : AlarmState
    data object Ended : AlarmState
}

/** 起床シグナル（複数合算して判定） */
sealed interface WakeSignal {
    data object Tap : WakeSignal
    data object Swipe : WakeSignal
    data class VoicePhrase(val text: String) : WakeSignal
    data class ConversationProgress(val seconds: Int) : WakeSignal
    data class Steps(val count: Int) : WakeSignal
    data class Timeout(val seconds: Int) : WakeSignal
}