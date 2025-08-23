package io.github.arashiyama11.a_larm.infra

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import android.media.RingtoneManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.arashiyama11.a_larm.domain.SimpleAlarmAudioGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class SimpleAlarmAudioGatewayImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SimpleAlarmAudioGateway {

    private val TAG = "SimpleAlarmAudioGateway"
    private val mutex = Mutex()
    private var mediaPlayer: MediaPlayer? = null
    private var playbackFinished: CompletableDeferred<Unit>? = null

    // ← 追加：prepareAsync の完了を待つためのゲート
    private var prepareGate: CompletableDeferred<Unit>? = null

    private var audioFocusRequest: AudioFocusRequest? = null
    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { /* log if needed */ }

    override suspend fun playAlarmSound() {
        val deferredToAwait: CompletableDeferred<Unit> = mutex.withLock {
            if (mediaPlayer?.isPlaying == true) {
                playbackFinished ?: CompletableDeferred<Unit>().also { playbackFinished = it }
            } else {
                val newDeferred = CompletableDeferred<Unit>()
                playbackFinished = newDeferred

                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val focusGranted = requestAudioFocus(am)

                val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: Settings.System.DEFAULT_ALARM_ALERT_URI

                try {
                    withContext(Dispatchers.Main) {
                        val mp = MediaPlayer()
                        mediaPlayer = mp

                        val attrs = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                        mp.setAudioAttributes(attrs)
                        mp.isLooping = true

                        // ← ここから：準備完了ゲート
                        val gate = CompletableDeferred<Unit>().also { prepareGate = it }
                        mp.setOnPreparedListener { if (!gate.isCompleted) gate.complete(Unit) }
                        mp.setOnErrorListener { _, what, extra ->
                            if (!gate.isCompleted) gate.completeExceptionally(
                                RuntimeException("MP error $what/$extra")
                            )
                            true
                        }

                        mp.setDataSource(context, uri)
                        mp.prepareAsync()

                        // 準備完了 or 停止で解除
                        try {
                            gate.await()
                        } finally {
                            if (prepareGate === gate) prepareGate = null
                        }

                        // stop 中に解放されていたら起動しない
                        if (mediaPlayer !== mp) return@withContext

                        mp.start()
                        Log.d(TAG, "Alarm sound started (focusGranted=$focusGranted) uri=$uri")
                    }
                } catch (e: Exception) {
                    // 準備中キャンセル/エラー含む
                    withContext(Dispatchers.Main) {
                        try {
                            mediaPlayer?.let {
                                if (it.isPlaying) try {
                                    it.stop()
                                } catch (_: Exception) {
                                }
                                it.reset(); it.release()
                            }
                        } catch (_: Exception) {
                        }
                        mediaPlayer = null
                    }
                    if (focusGranted) {
                        try {
                            val am2 =
                                context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                            abandonAudioFocus(am2)
                        } catch (_: Exception) {
                        }
                    }
                    // 再生フェーズまで行っていないので newDeferred は待たせない
                    playbackFinished = null
                    throw e
                }

                newDeferred
            }
        }

        try {
            // 再生中は stop までブロック
            deferredToAwait.await()
        } catch (ex: Throwable) {
            try {
                stopAlarmSound()
            } catch (_: Throwable) {
            }
            throw ex
        }
    }

    override suspend fun stopAlarmSound() {
        Log.d(TAG, "Stopping alarm sound")
        // 参照を原子的に退避
        val mpRef: MediaPlayer?
        val deferred: CompletableDeferred<Unit>?
        val gate: CompletableDeferred<Unit>?
        mutex.withLock {
            mpRef = mediaPlayer
            deferred = playbackFinished
            gate = prepareGate
            mediaPlayer = null
            playbackFinished = null
            prepareGate = null
        }

        // 準備待ちが進行中なら解除（例：prepareAsync 中に stop が来たケース）
        gate?.let {
            if (it.isActive && !it.isCompleted) {
                it.completeExceptionally(CancellationException("stopped"))
            }
        }

        // 実際に停止・解放
        if (mpRef != null) {
            withContext(Dispatchers.Main) {
                try {
                    try {
                        if (mpRef.isPlaying) mpRef.stop()
                    } catch (_: Exception) {
                    }
                    try {
                        mpRef.reset()
                    } catch (_: Exception) {
                    }
                    try {
                        mpRef.release()
                    } catch (_: Exception) {
                    }
                } finally {
                    // AudioFocus 解放
                    try {
                        val am = context.getSystemService(AudioManager::class.java)
                        abandonAudioFocus(am)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to abandon audio focus", e)
                    }
                }
            }
        }

        // play 側の await を解除（再生フェーズに入っている場合）
        try {
            deferred?.complete(Unit)
        } catch (_: Exception) {
        }
    }

    // ---- Audio focus helpers ----
    private fun requestAudioFocus(am: AudioManager): Boolean = try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val afr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(afChangeListener)
                .build()
            val res = am.requestAudioFocus(afr)
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                audioFocusRequest = afr; true
            } else false
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                afChangeListener, AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    } catch (e: Exception) {
        Log.w(TAG, "requestAudioFocus failed", e); false
    }

    private fun abandonAudioFocus(am: AudioManager) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it); audioFocusRequest = null }
            } else {
                @Suppress("DEPRECATION") am.abandonAudioFocus(afChangeListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "abandonAudioFocus failed", e)
        }
    }
}
