package io.github.arashiyama11.a_larm.infra

import android.media.AudioManager
import android.media.ToneGenerator
import io.github.arashiyama11.a_larm.domain.TtsGateway
import io.github.arashiyama11.a_larm.domain.models.VoiceStyle
import javax.inject.Inject

class TtsGatewayImpl @Inject constructor() : TtsGateway {
    override suspend fun speak(
        text: String,
        voice: VoiceStyle?
    ) {
        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
    }
}