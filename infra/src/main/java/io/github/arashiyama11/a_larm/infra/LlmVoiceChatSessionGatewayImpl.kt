package io.github.arashiyama11.a_larm.infra

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.channels.ClosedReceiveChannelException
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
import kotlinx.serialization.json.buildJsonArray
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
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
    val model: String = "models/gemini-2.0-flash-exp",
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig(),
    val systemInstruction: GeminiSystemInstruction? = null,
    //val tools: List<JsonElement> = emptyList()
)

@Serializable
data class GeminiGenerationConfig(
    val response_modalities: List<String> = listOf("TEXT"),
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

class LlmVoiceChatSessionGatewayImpl @Inject constructor(
    @ApplicationContext private val context: Context,
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
            })
        }
        install(Logging) {
            level = LogLevel.ALL
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d(TAG, message)
                }
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    init {
        CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                Log.d(TAG, "Session active: ${isSessionActive.get()} ${webSocketSession?.isActive}")
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

    // リトライ関連
    private var retryCount = 0
    private val maxRetryCount = 1
    private val retryDelayMs = 1000L

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
            webSocketSession = connectWithRetry(apiKey)
            isSessionActive.set(true)

            // システム指示を構築
            val systemInstruction = buildSystemInstruction(persona, brief, history)

            // セットアップメッセージを送信
            val setupMessage = GeminiLiveSetupRequest(
                setup = GeminiLiveSetup(
                    systemInstruction = systemInstruction
                )
            )

            val setupJson = json.encodeToString(setupMessage)
//            val setupSuccess = sendMessageWithRetry(setupJson)
//            if (!setupSuccess) {
//                throw Exception("Failed to send setup message after retries")
//            }
            Log.d(TAG, "Setup message sent successfully")

            // メッセージ処理を開始
            startMessageProcessing()

            // 音声録音を開始
            startAudioRecording()

            _chatState.value = LlmVoiceChatState.USER_SPEAKING
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
                        // 音声データをBase64エンコードしてWebSocketに送信
                        val audioData = buffer.copyOf(bytesRead)
                        audioDataChannel.trySend(audioData)

                        // Gemini Live APIに音声データを送信（最適化されたフォーマット）
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
                    Log.d(TAG, "Received frame: $frame")
                    when (frame) {
                        is Frame.Text -> {
                            val messageText = frame.readText()
                            Log.d(TAG, "Received message: $messageText")

                            try {
                                val jsonElement = json.parseToJsonElement(messageText)
                                processGeminiMessage(jsonElement.jsonObject)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing message: $messageText", e)
                            }
                        }

                        is Frame.Binary -> {
                            val binaryData = frame.readBytes()
                            Log.d(TAG, "Received binary data: ${binaryData.decodeToString()}")
                        }

                        is Frame.Close -> {
                            Log.d(TAG, "WebSocket connection closed")
                            break
                        }

                        else -> {
                            // その他のフレームタイプは無視
                        }
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

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun processGeminiMessage(message: JsonObject) {
        try {
            Log.d(TAG, "Processing message: $message")

            // セットアップ完了の確認
            if (message.containsKey("setupComplete")) {
                Log.d(TAG, "Setup completed")
                return
            }

            // サーバーコンテンツの処理
            val serverContent = message["serverContent"]?.jsonObject
            if (serverContent != null) {
                val modelTurn = serverContent["modelTurn"]?.jsonObject
                if (modelTurn != null) {
                    _chatState.value = LlmVoiceChatState.ASSISTANT_SPEAKING

                    val parts = modelTurn["parts"]
                    if (parts != null) {
                        // 音声データの処理
                        processModelTurnParts(parts)
                    }
                }

                // ターン完了の確認
                val turnComplete = serverContent["turnComplete"]
                if (turnComplete != null) {
                    Log.d(TAG, "Turn completed")
                    _chatState.value = LlmVoiceChatState.USER_SPEAKING
                }
            }

            // 直接のターン完了メッセージ
            if (message.containsKey("turnComplete")) {
                Log.d(TAG, "Turn completed (direct)")
                _chatState.value = LlmVoiceChatState.USER_SPEAKING
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing Gemini message", e)
            _response.tryEmit(VoiceChatResponse.Error("レスポンス処理でエラーが発生しました: ${e.message}"))
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun processModelTurnParts(parts: JsonElement) {
        try {
            when {
                parts.toString().contains("inlineData") -> {
                    // 音声データの処理
                    val audioData = extractAudioDataFromParts(parts)
                    if (audioData.isNotEmpty()) {
                        _response.emit(VoiceChatResponse.Voice(audioData))
                    }
                }

                parts.toString().contains("text") -> {
                    // テキストデータの処理
                    val textContent = extractTextFromParts(parts)
                    if (textContent.isNotEmpty()) {
                        _response.emit(VoiceChatResponse.Text(textContent))
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
    private fun extractAudioDataFromParts(parts: JsonElement): ByteArray {
        return try {
            // Gemini Live APIの音声データ構造に基づいて実装
            // 実際のAPIレスポンス構造に応じて調整が必要
            val jsonObject = parts.jsonObject
            val inlineData = jsonObject["inlineData"]?.jsonObject
            val data = inlineData?.get("data")?.jsonPrimitive?.content

            if (data != null) {
                Base64.decode(data)
            } else {
                ByteArray(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting audio data from parts", e)
            ByteArray(0)
        }
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
            Log.e(TAG, "Error extracting text from parts", e)
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
                Log.e(TAG, "Fallback text extraction failed", fallbackError)
                ""
            }
        }
    }

    @OptIn(InternalAPI::class)
    private suspend fun connectWithRetry(apiKey: String): DefaultClientWebSocketSession {
        var currentRetry = 0
        var lastException: Exception? = null

        val session = httpClient.webSocketSession(GEMINI_WS_URL) {
            parameter("key", apiKey)
            parameter("alt", "ws")
        }


        val receiverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
//                session.incoming.consumeAsFlow().first { frame ->
//                    val text: String = when (frame) {
//                        is Frame.Text -> frame.readText()
//                        is Frame.Binary -> frame.readBytes().toString(Charsets.UTF_8)
//                        else -> return@first false
//                    }
//                    text.contains("setupComplete").also {
//                        if (it) {
//                            Log.d(TAG, "Setup complete message received: $text")
//                        } else {
//                            Log.d(TAG, "Received message: $text")
//                        }
//                    }
//                }
            } catch (e: ClosedReceiveChannelException) {
                // 正常クローズで来ることがある → ログだけ
                Log.d(TAG, "incoming closed: ${session.closeReason.await()}")
            } catch (e: CancellationException) {
                // 親がキャンセルされた／明示的に閉じられた場合
                Log.w(TAG, "receiver cancelled: ${e.message}")
            } catch (t: Throwable) {
                Log.e(TAG, "receiver error", t)
            }
        }

        try {
            // 最初の setup を送る（非同期で安全に送れる）
            session.send(
                Frame.Text("""{"setup": {"model": "models/gemini-2.0-flash-exp","generationConfig": {"response_modalities": ["TEXT"]}}}""")
            )

            // ここで他の send や処理を行う（必要なら別の coroutine で送信し続ける）
            // 例: 何か条件で待つ、または別 job として send を起動

            // 受信が終わるまで待つ (受信ジョブが完了するのを待つ)
            //receiverJob.join()
        } finally {
            // 明示的に閉じる。close は何度呼んでもよい。
//            try {
//                session.close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
//            } catch (t: Throwable) {
//                Log.w(TAG, "close failed", t)
//            }

        }
        webSocketSession = session
        return session
            ?: throw IllegalStateException("WebSocket session is null after connection attempt")

    }

    private suspend fun sendMessageWithRetry(message: String, maxAttempts: Int = 3): Boolean {
        //Log.d(TAG, "Sending message with retry: $message")
        //println(message)
        var attempts = 0

        while (attempts < maxAttempts) {
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
                Log.w(
                    TAG,
                    "Failed to send message (attempt ${attempts + 1}/$maxAttempts): ${e.message}"
                )
                attempts++

                if (attempts < maxAttempts) {
                    delay(500L * attempts) // 500ms, 1s, 1.5s...
                }
            }
        }

        Log.e(TAG, "Failed to send message after $maxAttempts attempts")
        return false
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun sendAudioData(audioData: ByteArray) {
        try {
            // セッションがアクティブかチェック
            if (!isSessionActive.get()) {
                Log.d(TAG, "Session is not active, skipping audio data send")
                return
            }

            // Gemini Live API用の最適化された音声データフォーマット
            val audioMessage = buildJsonObject {
                put("realtimeInput", buildJsonObject {
                    put("audio", buildJsonObject {
                        put("data", Base64.encode(audioData))
                        put("mimeType", "audio/pcm;rate=16000")
                    })
                })
            }

            // リトライ機能付きで送信
            val success = sendMessageWithRetry(audioMessage.toString())
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