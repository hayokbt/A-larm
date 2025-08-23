package io.github.arashiyama11.a_larm.domain.models

data class AssistantPersona(
    val id: String,
    val displayName: String,
    val style: PromptStyle,
    val backstory: String? = null,
    val voice: VoiceStyle? = null,
    val locale: String = "ja-JP",
    val modelHint: ModelHint? = null
)


data class PromptStyle(
    val tone: Tone = Tone.Friendly,
    val energy: Energy = Energy.Medium,
    val questionFirst: Boolean = true,
    val systemPromptTemplate: String // e.g. "{backstory}\n今日の予定: {agenda}\nユーザーを起こす短い質問から開始"
)

enum class Tone { Friendly, Strict, Cheerful, Deadpan }
enum class Energy { Low, Medium, High }


data class VoiceStyle(
    val speaker: String,
    val emotion: String
)

enum class LlmProvider { OpenAI, Google, Grok, Local }
data class ModelHint(
    val provider: LlmProvider,
    val model: String
)
