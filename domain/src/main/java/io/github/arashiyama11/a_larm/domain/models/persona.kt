package io.github.arashiyama11.a_larm.domain.models

data class AssistantPersona(
    val id: String,
    val displayName: String,
    val description: String,
    val systemPromptTemplate: String,
    val imageUrl: String?,
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
