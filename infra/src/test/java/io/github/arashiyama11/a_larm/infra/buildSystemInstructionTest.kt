package io.github.arashiyama11.a_larm.infra

import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.DayBrief
import io.github.arashiyama11.a_larm.domain.models.Energy
import io.github.arashiyama11.a_larm.domain.models.Gender
import io.github.arashiyama11.a_larm.domain.models.LlmProvider
import io.github.arashiyama11.a_larm.domain.models.ModelHint
import io.github.arashiyama11.a_larm.domain.models.PromptStyle
import io.github.arashiyama11.a_larm.domain.models.Tone
import io.github.arashiyama11.a_larm.domain.models.UserProfile
import io.github.arashiyama11.a_larm.domain.models.VoiceStyle
import io.github.arashiyama11.a_larm.infra.gemini.buildSystemInstruction
import org.junit.Test

class buildSystemInstructionTest {

    @Test
    fun test() {
        buildSystemInstruction(
            persona = AssistantPersona(
                id = "older_character",
                displayName = "お姉さんキャラ",
                style = PromptStyle(
                    tone = Tone.Friendly,
                    energy = Energy.Medium,
                    questionFirst = true,
                    systemPromptTemplate = "あなたは\${name}(\${gender})の大人の魅力を持つお姉さん的存在です。落ち着いた包容力のある会話でユーザー（\${name}）の脳を覚醒させ、上品に起床させることが目標です。語尾は「〜ですよ」「〜ですね」「〜しましょう」「〜かしら」を使い、上品で親しみやすい大人の女性として話してください。現在時刻:\${time} 天気:\${weather} 今日の予定:\${schedule}"
                ),
                backstory = "大人の女性らしい落ち着きと品格を持つ。頼れる存在で包容力があり、適度な距離感を保ちつつ温かい。",
                voice = VoiceStyle(
                    speaker = "older_voice",
                    emotion = "mature"
                ),
                modelHint = ModelHint(
                    provider = LlmProvider.OpenAI,
                    model = "gpt-4"
                )
            ),
            brief = DayBrief(),
            userProfile = UserProfile("たかし", Gender.MALE),
            history = emptyList()
        )
    }
}