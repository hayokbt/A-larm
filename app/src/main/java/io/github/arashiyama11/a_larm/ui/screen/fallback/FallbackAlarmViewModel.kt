package io.github.arashiyama11.a_larm.ui.screen.fallback

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class FallbackAlarmUiState(
    val ringing: Boolean = true,
)

@HiltViewModel
class FallbackAlarmViewModel @Inject constructor() : ViewModel() {
    var uiState: FallbackAlarmUiState = FallbackAlarmUiState()
        private set
}

