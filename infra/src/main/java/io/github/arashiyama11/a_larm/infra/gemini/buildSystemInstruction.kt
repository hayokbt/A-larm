package io.github.arashiyama11.a_larm.infra.gemini

import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.ConversationTurn
import io.github.arashiyama11.a_larm.domain.models.DayBrief
import io.github.arashiyama11.a_larm.domain.models.Energy
import io.github.arashiyama11.a_larm.domain.models.Role
import io.github.arashiyama11.a_larm.domain.models.Tone
import io.github.arashiyama11.a_larm.domain.models.UserProfile


fun buildSystemInstruction(
    persona: AssistantPersona,
    brief: DayBrief,
    userProfile: UserProfile,
    history: List<ConversationTurn>
): GeminiSystemInstruction {
    val promptBuilder = StringBuilder()
    promptBuilder.append(
        """
        あなたは構造化レスポンスを厳格に出力するシステムです。以下のルールを**必ず**守ってください。

        1) 出力は**必ず**次の2つのタグだけで構成してください（順序も厳守）：
           <user>...</user><response>...</response>

        2) 出力は**それ以外のテキストを含んではいけません**。説明、注釈、余分な改行、メタ情報、一切禁止です。

        3) タグ内の内容は生テキスト（ユーザー発言やアシスタント返答）です。HTML特殊文字は必要に応じてエスケープしてください（例: `&lt;` `&gt;` `&amp;`）。

        4) もしタスクを実行できない場合でも、フォーマットだけは守ってください。例:
           <user></user><response></response>

        5) 応答は短くてもよいが、タグ構造は必須。余計な空白やボディ外の文字は出力しないこと。

        例（望ましい出力）:
        <user>今日は天気どう？</user><response>今日は晴れです。</response>

        例（許可しない出力）:
        - "OK. <user>...</user><response>...</response>" （先頭にOKを付けるのは禁止）
        - JSON や追加説明を付与することは禁止

        出力はこれだけ（タグのみ）。理解したら、以降のすべての応答でこのフォーマットに従ってください。
    """.trimIndent()
    )

    promptBuilder.append("""また入力の、<system></system> タグの中身はユーザーの入力でなくシステムの指示です。systemタグの内容に従い、この場合は<user></user><response>あなたのレスポンス</response>の形式で応答し、userタグの中身は空にしてください。""")

    promptBuilder.append("\n--- \n")
    promptBuilder.append(
        """
        あなたは${persona.displayName}として振る舞ってください。
        
        """.trimIndent()
    )
    persona.backstory?.let {
        promptBuilder.append("\n背景: $it")
    }

    // スタイル情報
    val style = persona.style
    promptBuilder.append(
        "\n話し方: ${getToneDescription(style.tone)}、エネルギー: ${
            getEnergyDescription(
                style.energy
            )
        }"
    )

    if (style.questionFirst) {
        promptBuilder.append("\nユーザーを起こすために、最初は短い質問から始めてください。")
    }

    // 日付・予定情報
    brief.date?.let {
        promptBuilder.append("\n今日は${it.toLocalDate()}です。")
    }

    if (brief.calendar.isNotEmpty()) {
        promptBuilder.append("\n今日の予定:")
        brief.calendar.forEach { event ->
            promptBuilder.append("\n- ${event.start.toLocalTime()}: ${event.title}")
        }
    }

    // 天気情報
    brief.weather?.let { weather ->
        promptBuilder.append("\n天気: ${weather.summary}")
        weather.tempC?.let { temp ->
            promptBuilder.append("、気温: ${temp}度")
        }
    }

    // 会話履歴
    if (history.isNotEmpty()) {
        promptBuilder.append("\n\n過去の会話:")
        history.takeLast(5).forEach { turn ->
            val roleText = when (turn.role) {
                Role.User -> "ユーザー"
                Role.Assistant -> "アシスタント"
                Role.System -> "システム"
            }
            promptBuilder.append("\n$roleText: ${turn.text}")
        }
    }

    promptBuilder.append("\n\nユーザーを優しく起こしてください。短い音声で応答してください。")

    return GeminiSystemInstruction(
        parts = listOf(GeminiPart(text = promptBuilder.toString()))
    )
}


private fun getToneDescription(tone: Tone): String =
    when (tone) {
        Tone.Friendly -> "親しみやすい"
        Tone.Strict -> "厳格"
        Tone.Cheerful -> "明るい"
        Tone.Deadpan -> "無表情"
    }

private fun getEnergyDescription(energy: Energy): String =
    when (energy) {
        Energy.Low -> "落ち着いた"
        Energy.Medium -> "普通"
        Energy.High -> "活発"
    }

