package io.github.arashiyama11.a_larm.ui.screen.session

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class Message(val from: String, val text: String)

data class SessionUiState(
    val messages: List<Message> = listOf(
        Message("AI", "おはよう！今日は何時に起きたい？"),
        Message("You", "7時に起きる予定だよ"),
    ),
    val micActive: Boolean = false,
    val aiTyping: Boolean = false,
)

@HiltViewModel
class SessionViewModel @Inject constructor() : ViewModel() {
    var uiState: SessionUiState = SessionUiState()
        private set
}

