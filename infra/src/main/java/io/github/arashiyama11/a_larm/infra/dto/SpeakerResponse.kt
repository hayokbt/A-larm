package io.github.arashiyama11.a_larm.infra.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Style(
    val id: Int,
    val name: String
)

@Serializable
data class SpeakerResponse(
    val name: String,
    val styles: List<Style>,
    @SerialName("speaker_uuid")
    val speakerUuid: String,
)

@Serializable
data class SpeakRequest(
    val text: String,
)