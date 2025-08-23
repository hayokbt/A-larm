package io.github.arashiyama11.a_larm.infra

import io.github.arashiyama11.a_larm.infra.gemini.parseConversationTurns
import org.junit.Test

class parseConversationTurnsTest {
    @Test
    fun test() {
        val text = "<user>ユーザーの発言</user><response>アシスタントの発言</response>"
        val result = parseConversationTurns(text)
        assert(result.size == 2)
        assert(result[0].role.name == "User")
        assert(result[0].text == "ユーザーの発言")
        assert(result[1].role.name == "Assistant")
        assert(result[1].text == "アシスタントの発言")
    }
}