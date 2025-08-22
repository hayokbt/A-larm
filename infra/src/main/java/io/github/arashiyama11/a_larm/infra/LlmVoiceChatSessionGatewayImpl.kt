package io.github.arashiyama11.a_larm.infra

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import io.github.arashiyama11.a_larm.domain.LlmApiKeyRepository
import io.github.arashiyama11.a_larm.domain.LlmVoiceChatSessionGateway
import io.github.arashiyama11.a_larm.domain.LlmVoiceChatState
import io.github.arashiyama11.a_larm.domain.VoiceChatResponse
import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.ConversationTurn
import io.github.arashiyama11.a_larm.domain.models.DayBrief
import io.github.arashiyama11.a_larm.domain.models.Role
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.InternalAPI
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
data class GeminiLiveMessage(
    val type: String,
    val data: JsonElement? = null
)

@Serializable
data class GeminiLiveSetupRequest(
    val setup: GeminiLiveSetup
)

@Serializable
data class GeminiLiveSetup(
    val model: String = "models/gemini-2.5-flash-preview-native-audio-dialog",//"models/gemini-2.0-flash-exp",
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig(),
    val systemInstruction: GeminiSystemInstruction? = null,
    //val tools: List<JsonElement> = emptyList()
)

@Serializable
data class GeminiGenerationConfig(
    val response_modalities: List<String> = listOf(
        "AUDIO",
        "TEXT"
    ), //listOf("TEXT"), 実際にはlistにできなそう
    val speech_config: GeminiSpeechConfig = GeminiSpeechConfig()
)

@Serializable
data class GeminiSpeechConfig(
    val voice_config: GeminiVoiceConfig = GeminiVoiceConfig()
)

@Serializable
data class GeminiVoiceConfig(
    val prebuilt_voice_config: GeminiPrebuiltVoiceConfig = GeminiPrebuiltVoiceConfig()
)

@Serializable
data class GeminiPrebuiltVoiceConfig(
    val voice_name: String = "Aoede"
)

@Serializable
data class GeminiSystemInstruction(
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String
)


@Singleton
class LlmVoiceChatSessionGatewayImpl @Inject constructor(
    private val llmApiKeyRepository: LlmApiKeyRepository
) : LlmVoiceChatSessionGateway {

    companion object {
        private const val TAG = "LlmVoiceChatSession"
        private const val GEMINI_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 4
    }

    private val _chatState = MutableStateFlow(LlmVoiceChatState.IDLE)
    override val chatState: StateFlow<LlmVoiceChatState> = _chatState

    private val _response = MutableSharedFlow<VoiceChatResponse>(extraBufferCapacity = 10)
    override val response: Flow<VoiceChatResponse> = _response

    private val httpClient = HttpClient(CIO) {
        install(WebSockets)
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
        install(Logging) {

        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    init {
        Json.Default
        CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                Log.d(TAG, "Session active: ${isSessionActive.get()} ${webSocketSession?.isActive}")
                if (webSocketSession?.isActive == false || webSocketSession == null) {
                    Log.d(TAG, "WebSocket session is not active, attempting to reconnect")
                    _chatState.value = LlmVoiceChatState.ERROR
                }
                delay(1000)
            }
        }
    }

    private var webSocketSession: DefaultClientWebSocketSession? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var messageProcessingJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    private val isSessionActive = AtomicBoolean(false)
    private val audioDataChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun initialize(
        persona: AssistantPersona,
        brief: DayBrief,
        history: List<ConversationTurn>
    ) {
        try {
            _chatState.value = LlmVoiceChatState.INITIALIZING
            Log.d(TAG, "Initializing Gemini Live API session")

            // APIキーを取得
            val apiKey = llmApiKeyRepository.getKey()
            if (apiKey.isNullOrBlank()) {
                Log.e(TAG, "API key is not available")
                _response.emit(VoiceChatResponse.Error("APIキーが設定されていません"))
                _chatState.value = LlmVoiceChatState.ERROR
                return
            }

            // WebSocket接続を確立（リトライ機能付き）
            webSocketSession = connect(apiKey)
            isSessionActive.set(true)

            // セットアップメッセージを送信
            val setupMessage = buildSetupRequest(
                responseAudio = false,
                persona = persona,
                brief = brief,
                history = history
            )

            val setupSuccess = sendMessage(json.encodeToString(setupMessage))

            if (!setupSuccess) {
                throw Exception("Failed to send setup message after retries")
            }

            Log.d(TAG, "Setup message sent successfully")

            startMessageProcessing()
            startAudioRecording()

            _chatState.value = LlmVoiceChatState.ACTIVE
            Log.d(TAG, "Gemini Live API session initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize session", e)
            // エラー時はセッションを非アクティブにする
            isSessionActive.set(false)
            webSocketSession?.close()
            webSocketSession = null
            _response.emit(VoiceChatResponse.Error("セッションの初期化に失敗しました: ${e.message}"))
            _chatState.value = LlmVoiceChatState.ERROR
        }
    }

    override suspend fun stop() {
        try {
            _chatState.value = LlmVoiceChatState.STOPPING
            Log.d(TAG, "Stopping session")

            // セッションを非アクティブにマーク
            isSessionActive.set(false)

            // 録音を停止
            stopAudioRecording()

            // ジョブをキャンセル（WebSocket接続を閉じる前に）
            recordingJob?.cancel()
            messageProcessingJob?.cancel()

            // WebSocket接続を閉じる
            webSocketSession?.close()
            webSocketSession = null

            _chatState.value = LlmVoiceChatState.IDLE
            Log.d(TAG, "Session stopped")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping session", e)
            _chatState.value = LlmVoiceChatState.ERROR
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @OptIn(ExperimentalEncodingApi::class)
    private fun startAudioRecording() {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ) * BUFFER_SIZE_MULTIPLIER

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)

            recordingJob = coroutineScope.launch {
                val buffer = ByteArray(bufferSize)
                Log.d(TAG, "Audio recording started")

                while (isRecording.get() && isActive) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        val audioData = buffer.copyOf(bytesRead)
                        audioDataChannel.trySend(audioData)
                        sendAudioData(audioData)
                    }

                    delay(50) // 50msごとに音声データを送信
                }

                Log.d(TAG, "Audio recording stopped")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording", e)
            _response.tryEmit(VoiceChatResponse.Error("音声録音の開始に失敗しました: ${e.message}"))
        }
    }

    private fun stopAudioRecording() {
        try {
            isRecording.set(false)
            recordingJob?.cancel()
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            audioDataChannel.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recording", e)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun startMessageProcessing() {
        messageProcessingJob = coroutineScope.launch {
            try {
                for (frame in webSocketSession!!.incoming) {
                    val messageText = when (frame) {
                        is Frame.Text -> frame.readText()
                        is Frame.Binary -> frame.readBytes().decodeToString()
                        is Frame.Close -> {
                            Log.d(TAG, "WebSocket connection closed")
                            isSessionActive.set(false)
                            _chatState.value = LlmVoiceChatState.IDLE
                            break
                        }

                        else -> {
                            return@launch
                        }
                    }
                    Log.d(TAG, "Processing frame: $messageText".take(100))
                    try {
                        val jsonElement = json.parseToJsonElement(messageText)
                        processGeminiMessage(jsonElement.jsonObject)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing message: $messageText", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in message processing", e)
                if (e !is CancellationException) {
                    _response.tryEmit(VoiceChatResponse.Error("メッセージ処理でエラーが発生しました: ${e.message}"))
                    _chatState.value = LlmVoiceChatState.ERROR
                }
            }
        }
    }

    private val processingResponseText = StringBuilder()

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun processGeminiMessage(message: JsonObject) {
        try {
            Log.d(TAG, "Processing message: $message")

            // セットアップ完了の確認
            if (message.containsKey("setupComplete")) {
                Log.d(TAG, "Setup completed")
                _chatState.value = LlmVoiceChatState.ACTIVE
                return
            }

            // サーバーコンテンツの処理
            val serverContent = message["serverContent"]?.jsonObject
            if (serverContent != null) {
                val modelTurn = serverContent["modelTurn"]?.jsonObject
                if (modelTurn != null) {
                    _chatState.value = LlmVoiceChatState.ACTIVE

                    val parts = modelTurn["parts"]
                    if (parts != null) {
                        // 音声データの処理
                        processModelTurnParts(parts)
                    }
                }

                // ユーザーが喋って中断された
                val interrupted = serverContent["interrupted"]
                if (interrupted != null) {
                    if (processingResponseText.isNotEmpty()) {
                        processingResponseText.clear()
                    } else if (processingVoiceResponseText.isNotEmpty()) {
                        processingVoiceResponseText.clear()
                    }
                }
                // ターン完了の確認
                val turnComplete = serverContent["turnComplete"]
                if (turnComplete != null) {
                    if (processingResponseText.isNotEmpty()) {
                        _response.emit(VoiceChatResponse.Text(processingResponseText.toString()))
                        processingResponseText.clear()
                    } else if (processingVoiceResponseText.isNotEmpty()) {
                        //base64してから
                        _response.emit(
                            VoiceChatResponse.Voice(Base64.decode(processingVoiceResponseText.toString()))
                        )
                        processingVoiceResponseText.clear()
                    }
                    Log.d(TAG, "Turn completed")
                    //_chatState.value = LlmVoiceChatState.USER_SPEAKING
                }
            }

            // 直接のターン完了メッセージ
            if (message.containsKey("turnComplete")) {
                Log.d(TAG, "Turn completed (direct)")
                //_chatState.value = LlmVoiceChatState.
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing Gemini message", e)
            _response.tryEmit(VoiceChatResponse.Error("レスポンス処理でエラーが発生しました: ${e.message}"))
        }
    }

    private val processingVoiceResponseText = StringBuilder()

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun processModelTurnParts(parts: JsonElement) {
        try {
            when {
                parts.toString().contains("inlineData") -> {
                    // 音声データの処理
                    val audioData = extractAudioDataFromParts(parts)
                    if (!audioData.isNullOrBlank()) {
                        processingVoiceResponseText.append(audioData)
                        // _response.emit(VoiceChatResponse.Voice(audioData))
                    }
                }

                parts.toString().contains("text") -> {
                    // テキストデータの処理
                    val textContent = extractTextFromParts(parts)
                    if (textContent.isNotEmpty()) {
                        processingResponseText.append(textContent)
                    }
                }

                else -> {
                    Log.d(TAG, "Unknown part type: $parts")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing model turn parts", e)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun extractAudioDataFromParts(parts: JsonElement): String? {
        return try {
            val jsonObject = parts.jsonArray[0].jsonObject
            val inlineData = jsonObject["inlineData"]?.jsonObject
            val data = inlineData?.get("data")?.jsonPrimitive?.content
            data
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting audio data from parts", e)
            null
        }
    }


    @OptIn(InternalAPI::class)
    private suspend fun connect(apiKey: String): DefaultClientWebSocketSession {
        val session = httpClient.webSocketSession(GEMINI_WS_URL) {
            parameter("key", apiKey)
            parameter("alt", "ws")
        }
        return session
    }

    private suspend fun sendMessage(message: String): Boolean {
        try {
            // セッションがアクティブかチェック
            if (!isSessionActive.get()) {
                Log.w(TAG, "Session is not active, cannot send message")
                return false
            }

            val session = webSocketSession
            if (session == null || session.isActive.not()) {
                Log.w(TAG, "WebSocket session is null or not active: $session")
                return false
            }

            session.send(Frame.Text(message))
            return true
        } catch (e: CancellationException) {
            Log.d(TAG, "Message sending was cancelled")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message: ${e.message}", e)
        }

        return false
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun sendAudioData(audioData: ByteArray) {
        try {
            // セッションがアクティブかチェック
            if (!isSessionActive.get()) {
                Log.d(TAG, "Session is not active, skipping audio data send")
                _chatState.value = LlmVoiceChatState.ERROR
                return
            }

            // Gemini Live API用の最適化された音声データフォーマット
            val audioMessage = buildAudioMessage(audioData)

            val success = sendMessage(audioMessage)
            if (!success) {
                Log.w(TAG, "Failed to send audio data after retries")
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Audio data sending was cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio data", e)
        }
    }
}


private fun buildSetupRequest(
    responseAudio: Boolean = false,
    persona: AssistantPersona,
    brief: DayBrief,
    history: List<ConversationTurn>
): GeminiLiveSetupRequest {
    if (responseAudio) {
        GeminiLiveSetupRequest(
            setup = GeminiLiveSetup(
                model = "models/gemini-2.5-flash-preview-native-audio-dialog",
                generationConfig = GeminiGenerationConfig(
                    response_modalities = listOf("AUDIO"),
                    speech_config = GeminiSpeechConfig(
                        voice_config = GeminiVoiceConfig(
                            prebuilt_voice_config = GeminiPrebuiltVoiceConfig(
                                voice_name = "Aoede"
                            )
                        )
                    )
                ),
                systemInstruction = buildSystemInstruction(
                    persona, brief, history
                )
            )
        )
    }
    return GeminiLiveSetupRequest(
        setup = GeminiLiveSetup(
            model = "models/gemini-2.0-flash-exp",
            systemInstruction = buildSystemInstruction(persona, brief, history),
            generationConfig = GeminiGenerationConfig(
                response_modalities = listOf("TEXT"),
            )
        )
    )
}

@OptIn(ExperimentalEncodingApi::class)
private fun buildAudioMessage(audioData: ByteArray): String {
    return buildJsonObject {
        put("realtimeInput", buildJsonObject {
            put("audio", buildJsonObject {
                put("data", Base64.encode(audioData))
                put("mimeType", "audio/pcm;rate=16000")
            })
        })
    }.toString()
}


private fun buildSystemInstruction(
    persona: AssistantPersona,
    brief: DayBrief,
    history: List<ConversationTurn>
): GeminiSystemInstruction {
    val promptBuilder = StringBuilder()

    // ペルソナ情報
    promptBuilder.append("あなたは${persona.displayName}として振る舞ってください。")
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


private fun getToneDescription(tone: io.github.arashiyama11.a_larm.domain.models.Tone): String =
    when (tone) {
        io.github.arashiyama11.a_larm.domain.models.Tone.Friendly -> "親しみやすい"
        io.github.arashiyama11.a_larm.domain.models.Tone.Strict -> "厳格"
        io.github.arashiyama11.a_larm.domain.models.Tone.Cheerful -> "明るい"
        io.github.arashiyama11.a_larm.domain.models.Tone.Deadpan -> "無表情"
    }

private fun getEnergyDescription(energy: io.github.arashiyama11.a_larm.domain.models.Energy): String =
    when (energy) {
        io.github.arashiyama11.a_larm.domain.models.Energy.Low -> "落ち着いた"
        io.github.arashiyama11.a_larm.domain.models.Energy.Medium -> "普通"
        io.github.arashiyama11.a_larm.domain.models.Energy.High -> "活発"
    }


private fun extractTextFromParts(parts: JsonElement): String {
    return try {
        // Gemini Live APIのテキストレスポンス構造に基づいて実装
        val jsonArray = parts.jsonArray
        val textParts = mutableListOf<String>()

        for (part in jsonArray) {
            val partObject = part.jsonObject
            val text = partObject["text"]?.jsonPrimitive?.content
            if (text != null) {
                textParts.add(text)
            }
        }

        textParts.joinToString("")
    } catch (e: Exception) {
        Log.e("Llm", "Error extracting text from parts", e)
        // フォールバック：単純な文字列変換
        try {
            parts.toString().let { raw ->
                // JSONから簡単にテキストを抽出する試み
                if (raw.contains("\"text\":")) {
                    val regex = "\"text\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    regex.findAll(raw).map { it.groupValues[1] }.joinToString("")
                } else {
                    ""
                }
            }
        } catch (fallbackError: Exception) {
            Log.e("Llm", "Fallback text extraction failed", fallbackError)
            ""
        }
    }
}