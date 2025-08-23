package io.github.arashiyama11.a_larm.infra

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import io.github.arashiyama11.a_larm.domain.AudioOutputGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


class AudioOutputGatewayImpl @Inject constructor() : AudioOutputGateway {

    private val sampleRate: Int = 24000
    private val channelConfig: Int = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT

    private var audioTrack: AudioTrack? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // 再生に必要なバッファサイズを計算
    private val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    init {
        // AudioTrackのインスタンスを初期化
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    /**
     * 音量を設定します。
     * levelは 0 から 100 の範囲で指定します。
     */
    override suspend fun setVolume(level: Int) {
        withContext(Dispatchers.IO) {
            val volume = level.coerceIn(0, 100) / 100.0f
            audioTrack?.setVolume(volume)
        }
    }

    /**
     * 指定された音声データを再生します。
     * この関数は再生が完了するまで中断しません（ブロッキングはしません）。
     */
    override suspend fun play(data: ByteArray) {
        coroutineScope.launch {
            audioTrack?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED) {
                    it.play()
                    it.write(data, 0, data.size)
                }
            }
        }.join() // 再生処理が終わるまで待機する場合は join() を使う
    }

    /**
     * サポートしている音量範囲を返します。
     * ここでは0から100の範囲とします。
     */
    override fun supportedRange(): IntRange {
        return 0..100
    }

    /**
     * 再生を停止し、リソースを解放します。
     */
    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
                audioTrack = null
            }
        }
    }
}