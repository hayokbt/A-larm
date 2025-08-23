package io.github.arashiyama11.a_larm.infra.gemini

import io.github.arashiyama11.a_larm.domain.models.ConversationTurn
import io.github.arashiyama11.a_larm.domain.models.Role
import kotlin.time.ExperimentalTime


fun isValidUserNotEmptyFormat(input: String): Boolean {
    val re = Regex(
        pattern = "^\\s*<user>\\s*([\\s\\S]*?\\S)\\s*</user>\\s*<response>[\\s\\S]*?</response>\\s*$",
        options = setOf(RegexOption.DOT_MATCHES_ALL)
    )
    return re.matchEntire(input) != null
}

@OptIn(ExperimentalTime::class)
fun parseConversationTurns(input: String): List<ConversationTurn> {
    if (input.isBlank()) return emptyList()

    val text = input.trim()

    val tagToRole = mapOf(
        "user" to Role.User,
        "usr" to Role.User,
        "u" to Role.User,
        "response" to Role.Assistant,
        "assistant" to Role.Assistant,
        "assistant_response" to Role.Assistant,
        "system" to Role.System,
        "sys" to Role.System,
        "s" to Role.System
    )

    // 閉じタグパターン（順序保持）
    val closedTagPattern =
        Regex("(?is)<(user|usr|u|response|assistant|assistant_response|system|sys|s)\\b([^>]*)>(.*?)</\\1>")
    val results = mutableListOf<ConversationTurn>()
    var lastMatchedRangeEnd = 0

    for (m in closedTagPattern.findAll(text)) {
        if (m.range.first > lastMatchedRangeEnd) {
            val inBetween = text.substring(lastMatchedRangeEnd, m.range.first).trim()
            if (inBetween.isNotEmpty()) {
                results += parsePlainFallback(inBetween)
            }
        }

        val tag = m.groupValues[1].lowercase()
        val rawContent = m.groupValues[3]
        val content = unescapeHtml(rawContent).removeAllWhitespace()

        val role = tagToRole[tag] ?: Role.User
        results += ConversationTurn(role, content)

        lastMatchedRangeEnd = m.range.last + 1
    }

    if (lastMatchedRangeEnd < text.length) {
        val tail = text.substring(lastMatchedRangeEnd).trim()
        if (tail.isNotEmpty()) {
            results += parseOpenUntilNextTags(tail, tagToRole)
        }
    }

    if (results.isEmpty()) {
        results += parsePlainFallback(text)
    }

    return results
}

/** 開きタグだけの場合（次のタグまたは EOF までをそのメッセージとする） */
@OptIn(ExperimentalTime::class)
private fun parseOpenUntilNextTags(
    tail: String,
    tagToRole: Map<String, Role>
): List<ConversationTurn> {
    val out = mutableListOf<ConversationTurn>()
    val openTagRegex = Regex(
        "(?is)<(user|usr|u|response|assistant|assistant_response|system|sys|s)\\b([^>]*)>",
        RegexOption.IGNORE_CASE
    )
    var cursor = 0
    val matches = openTagRegex.findAll(tail).toList()

    if (matches.isEmpty()) {
        return parsePlainFallback(tail)
    }

    for ((i, m) in matches.withIndex()) {
        val tag = m.groupValues[1].lowercase()
        val startContent = m.range.last + 1
        val endContent = if (i + 1 < matches.size) matches[i + 1].range.first else tail.length
        if (startContent <= endContent) {
            val rawContent = tail.substring(startContent, endContent)
            val content = unescapeHtml(rawContent).removeAllWhitespace()
            if (content.isNotEmpty()) {
                val role = tagToRole[tag] ?: Role.User
                out += ConversationTurn(role, content)
            }
        }
        cursor = endContent
    }

    val beforeFirst =
        if (matches.first().range.first > 0) tail.substring(0, matches.first().range.first)
            .trim() else ""
    if (beforeFirst.isNotEmpty()) {
        out.addAll(0, parsePlainFallback(beforeFirst))
    }

    if (cursor < tail.length) {
        val lastRest = tail.substring(cursor).trim()
        if (lastRest.isNotEmpty()) out += parsePlainFallback(lastRest)
    }

    return out
}

/** プレーンテキストフォールバック */
@OptIn(ExperimentalTime::class)
private fun parsePlainFallback(text: String): List<ConversationTurn> {
    val roleLineRegex = Regex("(?m)^(User|Assistant|System)[:：]\\s*(.*)$")
    val matches = roleLineRegex.findAll(text).toList()
    if (matches.isEmpty()) {
        // 全体を User として扱うが空白は除去する
        return listOf(ConversationTurn(Role.User, text.removeAllWhitespace()))
    }

    val out = mutableListOf<ConversationTurn>()
    for ((i, m) in matches.withIndex()) {
        val roleRaw = m.groupValues[1]
        val endPos = if (i + 1 < matches.size) matches[i + 1].range.first else text.length

        // m.groupValues[2] は行頭プレフィックスの同じ行の残り
        val firstLineRest = m.groupValues.getOrNull(2)?.trim().orEmpty()

        // マッチ（その先頭行）の末尾の次の位置から endPos までを取得（複数行メッセージ部分）
        val afterFirstLineStart = m.range.last + 1
        val following = if (afterFirstLineStart < endPos) {
            text.substring(afterFirstLineStart, endPos).trim()
        } else {
            ""
        }

        // 最初の行の残り と それ以降のブロックを結合
        val rawContent = listOf(firstLineRest, following)
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()

        val content = unescapeHtml(rawContent).removeAllWhitespace()

        val role = when (roleRaw.lowercase()) {
            "user" -> Role.User
            "assistant" -> Role.Assistant
            "system" -> Role.System
            else -> Role.User
        }

        out += ConversationTurn(role, content.ifEmpty { "" })
    }
    return out
}

/** 簡易 HTML エスケープ解除 */
private fun unescapeHtml(s: String): String {
    return s.replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .trim()
}

/** 空白削除ユーティリティ（半角全角・改行・タブを含めて削除） */
val WS_REGEX = Regex("[\\s\u3000]+")
private fun String.removeAllWhitespace(): String = this.replace(WS_REGEX, "")
