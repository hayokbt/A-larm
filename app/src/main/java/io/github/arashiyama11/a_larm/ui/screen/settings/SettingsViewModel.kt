package io.github.arashiyama11.a_larm.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.arashiyama11.a_larm.domain.AlarmSchedulerGateway
import io.github.arashiyama11.a_larm.domain.TtsGateway
import io.github.arashiyama11.a_larm.domain.models.AlarmId
import io.github.arashiyama11.a_larm.domain.UserProfileRepository
import io.github.arashiyama11.a_larm.domain.models.Gender
import io.github.arashiyama11.a_larm.domain.models.UserProfile
import io.github.arashiyama11.a_larm.util.NameUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class SettingsUiState(
    val character: String = "Default",
    val language: String = "System",
    val volume: Float = 0.7f,
    val rawName: String = "",
    val displayName: String = "",
    val gender: Gender = Gender.OTHER,
    val isSaving: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val ttsGateway: TtsGateway,
    private val alarmScheduler: AlarmSchedulerGateway,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userProfileRepository.getProfile().collect { profile ->
                if (profile != null) {
                    _uiState.value = _uiState.value.copy(
                        rawName = profile.name,
                        displayName = profile.name,
                        gender = profile.gender
                    )
                }
            }
        }
    }

    fun ttsTest() {
        viewModelScope.launch {
            ttsGateway.speak("TTS test message。これはTTSのテストメッセージです", null)
        }
    }

    fun setAlarmTest() {
        val after10Sec = LocalDateTime.now().plus(10, ChronoUnit.SECONDS)

        viewModelScope.launch {
            alarmScheduler.scheduleExact(after10Sec, AlarmId(42))
        }
    }

    fun onNameChange(newName: String) {
        val display = NameUtils.sanitizeDisplayName(newName)
        _uiState.value = _uiState.value.copy(rawName = newName, displayName = display)
    }

    fun onGenderChange(newGender: Gender) {
        _uiState.value = _uiState.value.copy(gender = newGender)
    }

    fun saveProfile() {
        val name = _uiState.value.displayName
        val gender = _uiState.value.gender
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            userProfileRepository.saveProfile(UserProfile(name = name, gender = gender))
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }
}
