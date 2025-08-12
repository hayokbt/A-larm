package io.github.arashiyama11.a_larm.ui.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val apiKey: String = "",
    val micGranted: Boolean = false,
    val notificationsGranted: Boolean = true,
    val exactAlarmAllowed: Boolean = true,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor() : ViewModel() {
    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState = _uiState.asStateFlow()

    fun onApiKeyChange(newKey: String) {
        _uiState.value = _uiState.value.copy(apiKey = newKey)
    }

    fun requestRuntimePermissions() {
        viewModelScope.launch { _events.emit(UiEvent.RequestRuntimePermissions) }
    }

    fun openExactAlarmSettings() {
        viewModelScope.launch { _events.emit(UiEvent.OpenExactAlarmSettings) }
    }

    fun onPermissionsResult(result: Map<String, Boolean>) {
        val mic = result.entries.firstOrNull { it.key.contains("RECORD_AUDIO") }?.value
        val noti = result.entries.firstOrNull { it.key.contains("POST_NOTIFICATIONS") }?.value
        _uiState.value = _uiState.value.copy(
            micGranted = mic ?: _uiState.value.micGranted,
            notificationsGranted = noti ?: _uiState.value.notificationsGranted,
        )
    }

    fun refreshExactAlarmState(allowed: Boolean) {
        _uiState.value = _uiState.value.copy(exactAlarmAllowed = allowed)
    }

    sealed interface UiEvent {
        object RequestRuntimePermissions : UiEvent
        object OpenExactAlarmSettings : UiEvent
    }
}
