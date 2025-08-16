package io.github.arashiyama11.a_larm.util

import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale


object NameUtils {
    // 削除対象: 制御文字・ゼロ幅・BIDI 等
    private val removeRegex = Regex("[\\p{C}\\u200B-\\u200F\\u202A-\\u202E]")

    // 許可文字（保守的）: 漢字/ひらがな/カタカナ/ASCII 英数字/基本句読点/スペース
    private val allowRegex =
        Regex("""[\p{IsHan}\p{IsHiragana}\p{IsKatakana}A-Za-z0-9 \-_,.\u3000\u3001\u3002]""")

    private fun graphemeCount(s: String, locale: Locale = Locale.getDefault()): Int {
        val it = BreakIterator.getCharacterInstance(locale).also { it.setText(s) }
        var count = 0
        var pos = it.first()
        while (pos != BreakIterator.DONE) {
            val next = it.next()
            if (next != BreakIterator.DONE) count++
            pos = next
        }
        return count
    }

    private fun trimGraphemes(s: String, max: Int, locale: Locale = Locale.getDefault()): String {
        if (max <= 0) return ""
        val it = BreakIterator.getCharacterInstance(locale).also { it.setText(s) }
        val sb = StringBuilder()
        var pos = it.first()
        var count = 0
        while (pos != BreakIterator.DONE && count < max) {
            val next = it.next()
            if (next == BreakIterator.DONE) break
            sb.append(s.substring(pos, next))
            pos = next
            count++
        }
        return sb.toString()
    }

    // サニタイズして safeName を作る（表示用）
    fun sanitizeDisplayName(
        raw: String,
        maxJapaneseGraphemes: Int = 10,
        maxAsciiGraphemes: Int = 20
    ): String {
        var s = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        s = removeRegex.replace(s, "")
        s = s.replace(Regex("\\s+"), " ").trim()

        // 許可文字以外を削る（保守的）
        s = s.mapNotNull { ch ->
            val ss = ch.toString()
            if (allowRegex.matches(ss)) ss else null
        }.joinToString("")

        val isAsciiOnly = s.matches(Regex("^[A-Za-z0-9 \\-_,\\.]*$"))
        val trimmed = if (isAsciiOnly) trimGraphemes(s, maxAsciiGraphemes, Locale.ENGLISH)
        else trimGraphemes(s, maxJapaneseGraphemes, Locale.JAPANESE)

        return trimmed
    }

    // speakAs を作る（簡易 ASCII 化）。 ICU4J があれば Transliterator に差し替えると良い。
    fun makeSpeakAs(sanitizedName: String, maxAscii: Int = 20): String {
        // NFKC 正規化 -> 非 ASCII を除去 -> 英数字/スペース/ハイフンのみ残す
        var s = Normalizer.normalize(sanitizedName, Normalizer.Form.NFKC)
        s = s.replace(Regex("[^\\p{ASCII}]"), "")
        s = s.replace(Regex("[^A-Za-z0-9 \\-]"), " ").replace(Regex("\\s+"), " ").trim()
        if (s.isEmpty()) return "user" // 最低限のフォールバック
        return if (s.length <= maxAscii) s else s.substring(0, maxAscii).trim()
    }

    // 簡易チェック: ルールに沿うか（例: 空でない、長さ、禁止文字が無い等）
    data class Validation(val isAcceptable: Boolean, val reason: String? = null)

    fun validateSanitized(safeName: String): Validation {
        if (safeName.isBlank()) return Validation(false, "名前が空になりました")
        // 追加チェック例: 最低 1 文字以上、グラフェム数チェックなど
        val g = graphemeCount(safeName)
        if (g > 50) return Validation(false, "名前が長すぎます")
        // さらに禁止語句チェック等を入れても良い
        return Validation(true, null)
    }
}