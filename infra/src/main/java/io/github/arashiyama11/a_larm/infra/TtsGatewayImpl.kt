package io.github.arashiyama11.a_larm.infra

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH
import androidx.media3.common.C.USAGE_MEDIA
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.arashiyama11.a_larm.domain.TtsGateway
import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.VoiceStyle
import io.github.arashiyama11.a_larm.infra.dto.SpeakerResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TtsGatewayImpl @Inject constructor(@ApplicationContext private val context: Context) :
    TtsGateway {
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000 // 30 seconds
            connectTimeoutMillis = 30_000 // 10 seconds
            socketTimeoutMillis = 30_000 // 10 seconds
        }

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
    }
    private val baseUrl = "${BuildConfig.SERVER_URL}:${BuildConfig.SERVER_PORT}"
    private val json = Json { ignoreUnknownKeys = true }

    private val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            // 音声用のAudioAttributes（任意）
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(USAGE_MEDIA)
                    .setContentType(AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true
            )
        }
    }

    override suspend fun speak(text: String, assistantPersona: AssistantPersona): Unit =
        withContext(Dispatchers.IO) {
            // 1) fetch
            val response = client.post("$baseUrl/api/prompt/${assistantPersona.id}") {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(mapOf("text" to text))
            }

            if (!response.status.isSuccess()) {
                // ログは省略
                return@withContext
            }

            // 2) write to file
            val outputFile = File(context.cacheDir, "output.wav")
            outputFile.outputStream().use { os ->
                os.write(response.bodyAsBytes())
                os.flush()
            }

            // 3) play on Main
            withContext(Dispatchers.Main) {
                // safety checks
                if (!outputFile.exists() || outputFile.length() == 0L || !outputFile.canRead()) {
                    // ログ出すなど
                    return@withContext
                }

                suspendCancellableCoroutine<Unit> { cont ->
                    val uri: Uri = outputFile.toUri()
                    val mediaItem = MediaItem.fromUri(uri)

                    // 前の再生が残ってたら止めてクリア
                    try {
                        if (player.isPlaying) {
                            player.stop()
                        }
                    } catch (_: Exception) { /* ignore */
                    }
                    player.clearMediaItems()

                    // リスナー（終了/エラーを監視）
                    val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) {
                                player.removeListener(this)
                                // 続きの処理を続ける
                                if (cont.isActive) cont.resume(Unit)
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            player.removeListener(this)
                            if (cont.isActive) cont.resumeWithException(error)
                        }
                    }

                    player.addListener(listener)

                    // セットして再生
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()

                    // キャンセル時の後始末
                    cont.invokeOnCancellation {
                        // Mainスレッドで安全に停止・リスナー削除
                        try {
                            player.removeListener(listener)
                            player.stop()
                            player.clearMediaItems()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }

//    private suspend fun fetchSpeakers(): List<SpeakerResponse> = withContext(Dispatchers.IO) {
//        val response = client.newCall(
//            Request.Builder()
//                .url("$baseUrl/speakers")
//                .get()
//                .header("User-Agent", "okhttp")
//                .build()
//        ).execute()
//
//        val body = response.body?.string() ?: error("Empty speaker response")
//        json.decodeFromString(body)
//    }

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
