package io.github.arashiyama11.a_larm.infra

import android.util.Log
import kotlinx.coroutines.coroutineScope

suspend fun textChunker(text: String, speakerId: Int): List<String> = coroutineScope {
    val rawChunks = text.split(Regex("(?<=[。！？])(?=\\s*[^てがはもでにと])"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    Log.d("TextChunker", "Raw chunks: ${rawChunks.size}")

    val finalChunks = mutableListOf<String>()
    var buffer = ""

    for (chunk in rawChunks) {
        val length = chunk.length

        when {
            length < 10 -> {
                buffer += if (buffer.isEmpty()) chunk else " $chunk"
            }

            length > 40 -> {
                if (buffer.isNotEmpty()) {
                    finalChunks += buffer.trim()
                    buffer = ""
                }
                finalChunks += splitLongChunk(chunk)
            }

            else -> {
                if (buffer.isNotEmpty()) {
                    finalChunks += buffer.trim()
                    buffer = ""
                }
                finalChunks += chunk
            }
        }
    }

    if (buffer.isNotEmpty()) finalChunks += buffer.trim()

    Log.d("TextChunker", "Final chunk count: ${finalChunks.size}")
    return@coroutineScope finalChunks
}

fun splitLongChunk(chunk: String): List<String> {
    if (chunk.length <= 40) return listOf(chunk)

    val commaIndex = findNearestCommaToMiddle(chunk)
    return if (commaIndex != null) {
        val first = chunk.substring(0, commaIndex + 1).trim()
        val second = chunk.substring(commaIndex + 1).trim()
        splitLongChunk(first) + splitLongChunk(second)
    } else {
        listOf(chunk)
    }
}


fun findNearestCommaToMiddle(text: String): Int? {
    val middle = text.length / 2
    val commas = Regex("、").findAll(text).map { it.range.first }.toList()
    return commas.minByOrNull { Math.abs(it - middle) }
}