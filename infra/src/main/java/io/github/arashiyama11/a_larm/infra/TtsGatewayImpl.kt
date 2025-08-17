package io.github.arashiyama11.a_larm.infra

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.arashiyama11.a_larm.domain.TtsGateway
import io.github.arashiyama11.a_larm.domain.models.VoiceStyle
import io.github.arashiyama11.a_larm.infra.dto.SpeakRequest
import io.github.arashiyama11.a_larm.infra.dto.SpeakerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
class TtsGatewayImpl @Inject constructor( @ApplicationContext private val context: Context )
    : TtsGateway {
    private val client = OkHttpClient()
    private val baseUrl = "https://settling-ghastly-chamois.ngrok-free.app"
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun speak(text: String, voice: VoiceStyle?) = withContext(Dispatchers.IO) {
        val resolvedStyle = voice ?: VoiceStyle("四国めたん", "ノーマル")
        val speakers = fetchSpeakers()
        val speakerId = resolveSpeakerId(resolvedStyle, speakers)

        Log.d("TtsGatewayImpl", "SpeakRequest text: '$text', speakerId: $speakerId")

        val queryRequestBody = json.encodeToString(SpeakRequest(text, speakerId))
            .toRequestBody("application/json".toMediaType())

        val queryUrl = "$baseUrl/audio_query?text=${URLEncoder.encode(text, "UTF-8")}&speaker=$speakerId"

        val queryResponse = client.newCall(
            Request.Builder()
                .url(queryUrl)
                .post("{}".toRequestBody("application/json".toMediaType())) // 空のJSONボディ
                .header("User-Agent", "okhttp")
                .build()
        ).execute()

        if (queryResponse.code == 422) {
            val errorBody = queryResponse.body?.string()
            Log.e("TtsGatewayImpl", "audio_query failed: $errorBody")
            error("audio_query failed: HTTP ${queryResponse.code}")
        }

        val synthesisRequestBody = queryResponse.body?.string()
            ?.toRequestBody("application/json".toMediaType()) ?: error("Empty query response")

        val synthesisResponse = client.newCall(
            Request.Builder()
                .url("$baseUrl/synthesis?speaker=$speakerId")
                .post(synthesisRequestBody)
                .header("User-Agent", "okhttp")
                .build()
        ).execute()

        if (synthesisResponse.code == 422) {
            val errorBody = synthesisResponse.body?.string()
            Log.e("TtsGatewayImpl", "synthesis failed: $errorBody")
            error("synthesis failed: $errorBody")
        }

        val outputFile = File(context.cacheDir, "output.wav")
        synthesisResponse.body?.byteStream()?.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
                output.flush()
                output.close()
            }
        }

        Log.d("TtsGatewayImpl", "output.wav path: ${outputFile.absolutePath}")
        Log.d("TtsGatewayImpl", "output.wav size: ${outputFile.length()}, readable: ${outputFile.canRead()}")

        val contentType = synthesisResponse.body?.contentType()
        Log.d("TtsGatewayImpl", "synthesis content type: $contentType")

        suspendCancellableCoroutine<Unit> { continuation ->

            if (!outputFile.exists() || outputFile.length() == 0L || !outputFile.canRead()) {
                Log.e("TtsGatewayImpl", "Invalid output file: cannot prepare MediaPlayer")
                continuation.resume(Unit)
                return@suspendCancellableCoroutine
            }

            val player = MediaPlayer()
            try {
                player.setDataSource(outputFile.absolutePath)
                player.setOnCompletionListener {
                    it.release()
                    continuation.resume(Unit)
                }
                player.setOnErrorListener { _, what, extra ->
                    Log.e("TtsGatewayImpl", "MediaPlayer error: what=$what, extra=$extra")
                    player.release()
                    continuation.resume(Unit)
                    true
                }
                player.prepare()
                player.start()
            } catch (e: IOException) {
                Log.e("TtsGatewayImpl", "MediaPlayer prepare failed", e)
                player.release()
                continuation.resumeWithException(e)
            }
        }
    }

    private suspend fun fetchSpeakers(): List<SpeakerResponse> = withContext(Dispatchers.IO) {
        val response = client.newCall(
            Request.Builder()
                .url("$baseUrl/speakers")
                .get()
                .header("User-Agent", "okhttp")
                .build()
        ).execute()

        val body = response.body?.string() ?: error("Empty speaker response")
        json.decodeFromString(body)
    }

    private fun resolveSpeakerId(
        style: VoiceStyle,
        speakers: List<SpeakerResponse>
    ): Int {
        val speaker = speakers.find { it.name == style.speaker }
            ?: error("Speaker '${style.speaker}' not found")

        val matchedStyle = speaker.styles.find { it.name == style.emotion }
            ?: error("Style '${style.emotion}' not found for speaker '${style.emotion}'")

        return matchedStyle.id
    }
}
