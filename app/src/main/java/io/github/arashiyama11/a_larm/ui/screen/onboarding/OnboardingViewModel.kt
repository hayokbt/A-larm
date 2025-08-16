package io.github.arashiyama11.a_larm.ui.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.arashiyama11.a_larm.PermissionManager
import io.github.arashiyama11.a_larm.domain.LlmApiKeyRepository
import io.github.arashiyama11.a_larm.domain.UserProfileRepository
import io.github.arashiyama11.a_larm.domain.models.Gender
import io.github.arashiyama11.a_larm.domain.models.UserProfile
import io.github.arashiyama11.a_larm.util.NameUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


enum class OnboardingStep {
    LOADING, GRANT_PERMISSIONS, USER_PROFILE, GRANT_API_KEY, EXACT_ALARM, COMPLETED
}

data class OnboardingUiState(
    val apiKey: String = "",
    val isApiKeySaved: Boolean = false,
    val phase: OnboardingStep = OnboardingStep.LOADING,
    val rawName: String = "",
    val displayName: String = "",
    val gender: Gender = Gender.OTHER
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val llmApiKeyRepository: LlmApiKeyRepository,
    private val permissionManager: PermissionManager,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {
    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()

    private val _uiState = MutableStateFlow(initialState())
    val uiState = _uiState.asStateFlow()


    fun onStart() {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = calculateStep()) }
        }
    }

    private fun initialState(): OnboardingUiState {
        val state = OnboardingUiState(
            phase = OnboardingStep.LOADING
        )

        return state
    }

    fun onApiKeyChange(newKey: String) {
        if (newKey.isBlank() || newKey.isEmpty()) {
            _uiState.value = _uiState.value.copy(apiKey = "", isApiKeySaved = false)
            return
        }
        _uiState.value = _uiState.value.copy(apiKey = newKey, isApiKeySaved = false)
    }

    fun onSaveApiKey() {
        if (_uiState.value.apiKey.isBlank() || _uiState.value.apiKey.isEmpty()) {
            _uiState.update { it.copy(isApiKeySaved = false) }
            return
        }
        viewModelScope.launch {
            llmApiKeyRepository.setKey(_uiState.value.apiKey)
            _uiState.update { it.copy(isApiKeySaved = true) }
        }
    }

    fun skipApiKey() {
        viewModelScope.launch {
            llmApiKeyRepository.setKey(null)
            _uiState.update { it.copy(phase = OnboardingStep.COMPLETED) }
        }
    }

    fun requestRuntimePermissions() {
        viewModelScope.launch { _events.emit(UiEvent.RequestRuntimePermissions) }
    }

    fun openExactAlarmSettings() {
        viewModelScope.launch { _events.emit(UiEvent.OpenExactAlarmSettings) }
    }

    private suspend fun calculateStep(): OnboardingStep {
        return when {
            !permissionManager.isMicGranted() || !permissionManager.isNotificationsGranted() -> OnboardingStep.GRANT_PERMISSIONS
            userProfileRepository.getProfile().first() == null -> OnboardingStep.USER_PROFILE
            llmApiKeyRepository.getKey().isNullOrBlank() -> OnboardingStep.GRANT_API_KEY
            !permissionManager.canScheduleExactAlarms() -> OnboardingStep.EXACT_ALARM
            else -> OnboardingStep.COMPLETED
        }
    }

    fun onNameChange(newName: String) {
        val display = NameUtils.sanitizeDisplayName(newName)
        _uiState.update { it.copy(rawName = newName, displayName = display) }
    }

    fun onGenderChange(newGender: Gender) {
        _uiState.update { it.copy(gender = newGender) }
    }

    fun updatePhase() {

        val phase = _uiState.value.phase

        _uiState.update {
            it.copy(phase = OnboardingStep.LOADING)
        }

        when (phase) {
            OnboardingStep.USER_PROFILE -> {
                viewModelScope.launch {
                    userProfileRepository.saveProfile(
                        UserProfile(
                            name = _uiState.value.displayName,
                            gender = _uiState.value.gender
                        )
                    )
                }
            }

            else -> {}
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(phase = calculateStep())
            }
        }
    }


    sealed interface UiEvent {
        object RequestRuntimePermissions : UiEvent
        object OpenExactAlarmSettings : UiEvent
    }
}


private fun validateUserName(name: String): Boolean {
    return name.isNotBlank() && name.length in 1..50
}