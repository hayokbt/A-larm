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
            ttsGateway.speak(
                "音声合成技術は、単なる音の生成を超えて、意味の伝達そのものを担う存在へと進化しています。特に、非同期的な生成と再生の並列化が可能になったことで、ユーザー体験は大きく変化しました。これまでのTTSシステムでは、テキストの生成と音声の再生が直列的に行われていたため、待機時間が発生しやすく、意味の流れが断絶することもありました。しかし、現在の設計では、意味のチャンク化と再生時間の予測を組み合わせることで、音声生成を先回りして行うことが可能となり、UXの滑らかさが飛躍的に向上しています。さらに、意味の持続性を保つためには、単なる文字数や文法構造だけでなく、発音時間やイントネーションの変化も考慮する必要があります。これらの要素を統合的に扱うことで、TTSは単なる技術ではなく、意味のオーケストレーションとして機能するようになるのです。",
                null
            )
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
