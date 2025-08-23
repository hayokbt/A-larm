package io.github.arashiyama11.a_larm.infra

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.arashiyama11.a_larm.domain.TtsGateway
import io.github.arashiyama11.a_larm.domain.models.VoiceStyle
import io.github.arashiyama11.a_larm.infra.dto.SpeakerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TtsGatewayImpl @Inject constructor( @ApplicationContext private val context: Context )
    : TtsGateway {
    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)       // 全体の処理時間の上限
        .connectTimeout(5, TimeUnit.SECONDS)     // 接続確立までの時間
        .readTimeout(30, TimeUnit.SECONDS)       // サーバーからの応答待ち時間
        .writeTimeout(30, TimeUnit.SECONDS)      // リクエスト送信の猶予時間
        .build()

    private val baseUrl = "https://settling-ghastly-chamois.ngrok-free.app"
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun speak(text: String, voice: VoiceStyle?) {
        coroutineScope {
            val resolvedStyle = voice ?: VoiceStyle("四国めたん", "ノーマル")
            val speakers = fetchSpeakers()
            val speakerId = resolveSpeakerId(resolvedStyle, speakers)

            val chunks = textChunker(text, speakerId)
            val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            val playQueue = Channel<File>(capacity = Channel.UNLIMITED)

            val soundFiles = mutableListOf<File>()
            // 🔸 生成フェーズ（並列）
            launch(dispatcher) {
                val preGenerateLimit = 5
                val shouldBreakEarly = chunks.size > preGenerateLimit

                for ((index, chunk) in chunks.withIndex()) {
                    val file = soundCreate(chunk, speakerId)
                    soundFiles.add(file)
                    playQueue.send(file)
                    Log.d("Speak", "Pre-generated chunk $index: '${chunk.take(30)}...'")

                    if (shouldBreakEarly && index + 1 >= preGenerateLimit) break
                }

                // 残りのチャンクも順次生成
                for (index in soundFiles.size until chunks.size) {
                    val file = soundCreate(chunks[index], speakerId)
                    playQueue.send(file)
                    Log.d("Speak", "Generated chunk $index: '${chunks[index].take(30)}...'")
                }

                playQueue.close()
            }

            // 🔸 再生フェーズ（逐次）
            launch(Dispatchers.IO) {
                var index = 0
                for (file in playQueue) {
                    try {
                        audioPlay(file)
                        Log.d("Speak", "Played chunk $index")
                    } catch (e: Exception) {
                        Log.e("Speak", "Playback failed for chunk $index", e)
                    }
                    index++
                }
            }
        }
    }

    suspend fun soundCreate(text: String, speakerId: Int): File = withContext(Dispatchers.IO) {


        Log.d("TtsGatewayImpl", "SoundCreate text: '$text', speakerId: $speakerId")

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

        val synthesisStart = System.currentTimeMillis()

        val synthesisResponse = client.newCall(
            Request.Builder()
                .url("$baseUrl/synthesis?speaker=$speakerId")
                .post(synthesisRequestBody)
                .header("User-Agent", "okhttp")
                .build()
        ).execute()

        val synthesisDuration = System.currentTimeMillis() - synthesisStart
        Log.d("TtsGatewayImpl", "Synthesis took ${synthesisDuration}ms for text: '${text.take(50)}...'")

        if (synthesisResponse.code == 422) {
            val errorBody = synthesisResponse.body?.string()
            Log.e("TtsGatewayImpl", "synthesis failed: $errorBody")
            error("synthesis failed: $errorBody")
        }

        val timestamp = System.currentTimeMillis()
        val hash = text.hashCode().toString().replace("-", "n") // 簡易識別子
        val outputFile = File(context.cacheDir, "tts_${hash}_$timestamp.wav")
        synthesisResponse.body?.byteStream()?.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
                output.flush()
            }
        }

        Log.d("TtsGatewayImpl", "Generated file: ${outputFile.absolutePath}")
        return@withContext outputFile
    }

    suspend fun audioPlay(file: File) = suspendCancellableCoroutine<Unit> { continuation ->
        if (!file.exists() || file.length() == 0L || !file.canRead()) {
            Log.e("TtsGatewayImpl", "Invalid file: cannot prepare MediaPlayer")
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }

        val player = MediaPlayer()
        var resumed = false // 再生完了の重複防止

        try {
            player.setDataSource(file.absolutePath)

            player.setOnCompletionListener {
                if (!resumed) {
                    resumed = true
                    Log.d("TtsGatewayImpl", "Playback completed: ${file.name}")
                    it.release()
                    continuation.resume(Unit)
                }
            }

            player.setOnErrorListener { _, what, extra ->
                if (!resumed) {
                    resumed = true
                    Log.e("TtsGatewayImpl", "MediaPlayer error: what=$what, extra=$extra")
                    player.release()
                    continuation.resume(Unit)
                }
                true
            }

            player.setOnInfoListener { _, what, extra ->
                Log.w("TtsGatewayImpl", "MediaPlayer info: what=$what, extra=$extra")
                false
            }

            player.prepare()
            player.start()
            Log.d("TtsGatewayImpl", "Playback started: ${file.name}")

            continuation.invokeOnCancellation {
                if (!resumed) {
                    resumed = true
                    Log.w("TtsGatewayImpl", "Playback cancelled: ${file.name}")
                    player.stop()
                    player.release()
                }
            }

        } catch (e: IOException) {
            if (!resumed) {
                resumed = true
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
