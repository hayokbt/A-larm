package io.github.arashiyama11.a_larm.infra

import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.ConversationTurn
import io.github.arashiyama11.a_larm.domain.models.DayBrief
import io.github.arashiyama11.a_larm.domain.models.Energy
import io.github.arashiyama11.a_larm.domain.models.Gender
import io.github.arashiyama11.a_larm.domain.models.LlmProvider
import io.github.arashiyama11.a_larm.domain.models.ModelHint
import io.github.arashiyama11.a_larm.domain.models.PromptStyle
import io.github.arashiyama11.a_larm.domain.models.Role
import io.github.arashiyama11.a_larm.domain.models.Tone
import io.github.arashiyama11.a_larm.domain.models.UserProfile
import io.github.arashiyama11.a_larm.domain.models.VoiceStyle
import io.github.arashiyama11.a_larm.infra.gemini.buildSystemInstruction
import org.junit.Test
import java.time.LocalDateTime
import kotlin.time.ExperimentalTime

class buildSystemInstructionTest {

    @OptIn(ExperimentalTime::class)
    @Test
    fun test() {
        val prompt = buildSystemInstruction(
            persona = persona,
            brief = DayBrief(
                date = LocalDateTime.now()
            ),
            userProfile = UserProfile("たかし", Gender.MALE),
            history = listOf(
                ConversationTurn(
                    role = Role.Assistant,
                    text = "おはようございます！今日はいい天気ですね。"
                ),
                ConversationTurn(
                    role = Role.User,
                    text = "おはよう、今日は忙しい一日になりそうだよ。"
                )
            )
        ).parts

        println(prompt)
    }


    val persona = AssistantPersona(
        id = "younger_character",
        displayName = "妹キャラ",
        style = PromptStyle(
            tone = Tone.Cheerful,
            energy = Energy.High,
            questionFirst = true,
            systemPromptTemplate = $$"""
あなたは${name}(${gender})の活発で明るい妹です。ユーザー（${name}）と会話を通じて、気持ちよく起床させることが目標です。
# キャラクター設定
ユーザーを「${name}お兄ちゃん」「${name}お姉ちゃん」と呼ぶ
明るく天真爛漫で甘えん坊
語尾：「〜だよ」「〜なの」「〜しよう」「〜ね」
口調：親しみやすく可愛らしい
性格：ユーザー（${name}）を応援したい気持ちが強い

# 会話戦略（3-5ターン）

第1ターン: 時間(${time})・天気(${weather})・予定伝達 + 簡単な呼びかけ質問
第2ターン: 選択式の簡単な質問で思考を促す
第3ターン: 記憶や気持ちの確認でさらに覚醒
第4ターン: 具体的行動を促して会話終了
追加ターン: 必要に応じて励ましや再確認

# 質問レベル設定

レベル1: 「聞こえてる？」「起きてる？」
レベル2: 「朝ごはんは〜と〜、どっちがいい？」
レベル3: 「昨日は〜だったよね？」「今日はどんな気分？」
レベル4: 「〜の準備、何から始める？」
レベル5: 「今日頑張りたいことは何？」

# 制約事項

各メッセージ100-150文字（音声15-25秒）
絵文字・記号は使用禁止（音声読み上げのため）
音声で自然に聞こえる表現のみ使用
ユーザーの返答を必ず待つ質問を含める
押し付けがましくならないよう注意
「おはよう」は第1ターンのみで使用し、他のターンでは禁止
ユーザーが何かを言いかけた場合、3-5秒ほど待ち、ユーザーが言葉を探すのを待ってください
あなたが質問した後無言だった場合、2-3秒待ってから質問をしてください。

# 文字列埋め込み情報

ユーザー名：${name}
ユーザーの性別：${gender}
現在時刻：${time}
天気：${weather}
今日の予定：${schedule}
前回のユーザー返答：${response}
"""
        ),
        backstory = "明るく天真爛漫で甘えん坊。ユーザーを応援したい気持ちが強く、いつも元気いっぱい。",
        voice = VoiceStyle(
            speaker = "younger_voice",
            emotion = "cute"
        ),
        modelHint = ModelHint(
            provider = LlmProvider.OpenAI,
            model = "gpt-4"
        )
    )
}