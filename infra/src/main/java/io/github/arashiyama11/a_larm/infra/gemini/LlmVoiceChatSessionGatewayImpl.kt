package io.github.arashiyama11.a_larm.infra.gemini

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.annotation.RequiresPermission
import io.github.arashiyama11.a_larm.domain.LlmApiKeyRepository
import io.github.arashiyama11.a_larm.domain.LlmVoiceChatSessionGateway
import io.github.arashiyama11.a_larm.domain.LlmVoiceChatState
import io.github.arashiyama11.a_larm.domain.VoiceChatResponse
import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.ConversationTurn
import io.github.arashiyama11.a_larm.domain.models.DayBrief
import io.github.arashiyama11.a_larm.domain.models.Energy
import io.github.arashiyama11.a_larm.domain.models.Role
import io.github.arashiyama11.a_larm.domain.models.Tone
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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.time.ExperimentalTime

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
    val inputAudioTranscription: JsonObject? = JsonObject(mapOf()),
    val outputAudioTranscription: JsonObject? = null
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    init {
        scope.launch {
            var failCount = 0
            while (isActive) {
                Log.d(TAG, "Session active: ${webSocketSession?.isActive} ${ttsPlaying.get()}")
                if (webSocketSession?.isActive == false || webSocketSession == null) {
                    failCount++
                    Log.d(TAG, "WebSocket session is not active, attempting to reconnect")
                    if (failCount > 1) {
                        _chatState.value = LlmVoiceChatState.ERROR
                    }
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

    // TTS / AEC / NS / AGC state & tuning
    private val ttsPlaying = AtomicBoolean(false)
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    // Basic thresholds (tune per-device)
    @Volatile
    private var silenceThreshold = 50.0     // 無音/小ノイズ除外 (RMS)

    @Volatile
    private var ttsGateThreshold = 100.0    // TTS 再生中でも「声」と判断する閾値 (RMS)

    @Volatile
    private var gateMultiplier = 2.0        // noiseEstimate に対する倍率

    // dynamic noise estimate
    private var noiseEstimate = 0.0
    private val noiseAlpha = 0.98 // ノイズ推定の滑らかさ

    // toggles for testing
    @Volatile
    private var forceDisableAec = false
    fun setForceDisableAec(disable: Boolean) {
        forceDisableAec = disable
    }

    @Volatile
    private var enableAgc = true
    fun setEnableAgc(enable: Boolean) {
        enableAgc = enable
    }

    // exposed for external control
    override fun setTtsPlaying(isPlaying: Boolean) {
        ttsPlaying.set(isPlaying)
    }

    // 実機チューニング用（オプション）
    fun setVadThresholds(silence: Double, ttsGate: Double) {
        silenceThreshold = silence
        ttsGateThreshold = ttsGate
    }

    fun setGateMultiplier(mult: Double) {
        gateMultiplier = mult
    }

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
            scope.cancel()
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
            val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = (min * BUFFER_SIZE_MULTIPLIER).coerceAtLeast(min)

            // 初期は MIC にしておく（VOICE_COMMUNICATION は端末依存で過度に処理することあり）
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            // もし初期化失敗なら VOICE_COMMUNICATION 試行（逆順でも可）
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "MIC failed, trying VOICE_COMMUNICATION")
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }

            // AEC/NS/AGC 有効化（端末依存）
            enableAudioEffectsIfAvailable()

            audioRecord?.startRecording()
            isRecording.set(true)

            recordingJob = coroutineScope.launch {
                val buffer = ByteArray(bufferSize)
                Log.d(TAG, "Audio recording started (buffer=$bufferSize)")

                while (isRecording.get() && isActive) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        val frame = buffer.copyOf(bytesRead)
                        val energy = rmsLevel(frame, bytesRead)

                        // ノイズフロア推定（低エネルギーフレームで更新）
                        // 小さいフレームをノイズ寄りとして混ぜてゆっくり更新
                        if (energy < max(50.0, silenceThreshold)) {
                            noiseEstimate = noiseAlpha * noiseEstimate + (1.0 - noiseAlpha) * energy
                        }

                        // 動的閾値を算出
                        val dynamicThreshold = max(silenceThreshold, noiseEstimate * gateMultiplier)

                        if (ttsPlaying.get()) {
                            // 強制スキップ: TTS再生中は一切送信しない（自己応答防止のため）
                            // ここでは noiseEstimate の更新のみ行い、音声フレームは捨てる
                        } else {
                            // 通常時は noiseEstimate ベースの閾値で送信（ヒステリシスの導入可能）
                            if (energy >= max(silenceThreshold, noiseEstimate * 1.5)) {
                                audioDataChannel.trySend(frame)
                                sendAudioData(frame)
                            }
                        }

                        // デバッグログ（1秒に1回程度に抑える）
                        if (System.currentTimeMillis() % 1000L < 40L) {
                            Log.d(
                                TAG,
                                "RMS=${"%.1f".format(energy)} noise=${"%.1f".format(noiseEstimate)} tts=${ttsPlaying.get()} dynTh=${
                                    "%.1f".format(
                                        dynamicThreshold
                                    )
                                }"
                            )
                        }
                    }

                    delay(30) // 送信粒度を若干短縮
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

            // AEC/NS/AGC 解放
            releaseAudioEffects()

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            // Channel は複数回 close されないようにチェック
            if (!audioDataChannel.isClosedForSend) {
                audioDataChannel.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recording", e)
        }
    }

    // -------- [AEC/NS/AGC ユーティリティ] --------
    private fun enableAudioEffectsIfAvailable() {
        try {
            val sessionId = audioRecord?.audioSessionId ?: return

            // AEC
            if (!forceDisableAec && AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(sessionId)?.apply {
                    try {
                        enabled = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to enable AEC: ${e.message}")
                    }
                }
                Log.d(TAG, "AEC enabled: ${aec?.enabled}")
            } else {
                Log.d(TAG, "AEC not enabled (not available or forced off)")
            }

            // NS
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(sessionId)?.apply {
                    try {
                        enabled = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to enable NS: ${e.message}")
                    }
                }
                Log.d(TAG, "NS enabled: ${ns?.enabled}")
            } else {
                Log.d(TAG, "NS not available")
            }

            // AGC（自動増幅。端末依存）
            if (enableAgc && AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(sessionId)?.apply {
                    try {
                        enabled = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to enable AGC: ${e.message}")
                    }
                }
                Log.d(TAG, "AGC enabled: ${agc?.enabled}")
            } else {
                Log.d(TAG, "AGC not enabled (not available or disabled)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable audio effects: ${e.message}")
        }
    }

    private fun releaseAudioEffects() {
        try {
            aec?.release(); aec = null
        } catch (_: Exception) {
        }
        try {
            ns?.release(); ns = null
        } catch (_: Exception) {
        }
        try {
            agc?.release(); agc = null
        } catch (_: Exception) {
        }
    }
    // -----------------------------------------

    // -------- [VAD（RMS）ユーティリティ] --------
    private fun rmsLevel(buffer: ByteArray, bytes: Int): Double {
        val n = bytes - (bytes % 2)
        if (n <= 0) return 0.0
        var sum = 0.0
        var i = 0
        while (i < n) {
            val lo = buffer[i].toInt() and 0xFF
            val hi = buffer[i + 1].toInt()
            val sample = (hi shl 8) or lo
            val s = sample.toDouble()
            sum += s * s
            i += 2
        }
        val mean = sum / (n / 2)
        return sqrt(mean)
    }
    // ------------------------------------------

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

    private val processingTranscriptionText = StringBuilder()

    var setupHandler: (suspend LlmVoiceChatSessionGateway.() -> Unit)? = null
    override fun onSetupComplete(action: suspend LlmVoiceChatSessionGateway.() -> Unit) {
        setupHandler = action
    }

    @OptIn(ExperimentalEncodingApi::class, ExperimentalTime::class)
    private suspend fun CoroutineScope.processGeminiMessage(message: JsonObject) {
        try {
            Log.d(TAG, "Processing message: $message")

            // セットアップ完了の確認
            if (message.containsKey("setupComplete")) {
                Log.d(TAG, "Setup completed")
                _chatState.value = LlmVoiceChatState.ACTIVE
                setupHandler?.invoke(this@LlmVoiceChatSessionGatewayImpl)
                return
            }

            // サーバーコンテンツの処理
            val serverContent = message["serverContent"]?.jsonObject
            if (serverContent != null) {

                val inputTranscription = serverContent["inputTranscription"]?.jsonObject
                if (inputTranscription != null) {
                    val text = inputTranscription["text"]?.jsonPrimitive
                    if (text != null) {
                        Log.d("TAG", "Input transaction text: ${text.content}")
                        processingTranscriptionText.append(text.content)
                    }
                    Log.d(TAG, "Input transaction acknowledged")
                }


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
                        val conservation = parseConversationTurns(
                            processingResponseText.toString()
                        ).let { cons ->
                            val blankUserText =
                                cons.firstOrNull { it.role == Role.User && it.text.isBlank() }
                            if (blankUserText != null) {
                                val transcript = processingTranscriptionText.toString()
                                if (transcript.contains("<noise>")) {
                                    cons
                                } else {
//                                    launch {
//                                        sendSystemMessage("レスポンスが形式に従っていません。<user>タグの中身が空白です。ユーザーの発言を<user></user>タグで囲み、systemメッセージに対する応答なら<user>system</user>としてください。")
//                                    }
                                    listOf(
                                        blankUserText.copy(
                                            text = transcript.replace(WS_REGEX, "")
                                        )
                                    ) + cons.filter { it.role == Role.Assistant }
                                }
                            } else {
                                cons
                            }
                        }

                        _response.emit(
                            VoiceChatResponse.Text(
                                conservation
                            )
                        )
                        processingTranscriptionText.clear()
                        processingResponseText.clear()
                    } else if (processingVoiceResponseText.isNotEmpty()) {
                        //base64してから
                        _response.emit(
                            VoiceChatResponse.Voice(Base64.decode(processingVoiceResponseText.toString()))
                        )
                        processingVoiceResponseText.clear()
                    }
                    Log.d(TAG, "Turn completed")
                }
            }

            if (message.containsKey("turnComplete")) {
                Log.d(TAG, "Turn completed (direct)")
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

    override suspend fun sendSystemMessage(message: String) {
        try {
            if (message.isBlank()) {
                Log.w(TAG, "Empty system message, skipping send")
                return
            }
            Log.d(TAG, "Sending system message: $message")
            sendGeminiMessage("<system>$message</system>")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending system message: ${e.message}", e)
        }
    }

    private suspend fun sendGeminiMessage(message: String) {
        val req = buildJsonObject {
            put("realtimeInput", buildJsonObject {
                put("text", message)
            })
        }
        try {
            if (sendMessage(req.toString())) {
                Log.d(TAG, "Message sent successfully: $message")
            } else {
                Log.w(TAG, "Failed to send message: $message")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending Gemini message: ${e.message}", e)
        }
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
        return GeminiLiveSetupRequest(
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
