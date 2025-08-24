package io.github.arashiyama11.a_larm.ui.screen.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.arashiyama11.a_larm.domain.usecase.CalendarUseCase
import io.github.arashiyama11.a_larm.domain.models.CalendarSettings
import io.github.arashiyama11.a_larm.infra.calendar.CalendarInfo
import io.github.arashiyama11.a_larm.infra.calendar.LocalCalendarClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalendarSettingsViewModel @Inject constructor(
    private val calendarUseCase: CalendarUseCase,
    private val localCalendarClient: LocalCalendarClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarSettingsUiState())
    val uiState: StateFlow<CalendarSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadAvailableCalendars()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            calendarUseCase.getSettings().collect { settings ->
                _uiState.value = _uiState.value.copy(
                    settings = settings
                )
            }
        }
    }

    private fun loadAvailableCalendars() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                // まずカレンダーが利用可能かチェック
                val isAvailable = localCalendarClient.isCalendarAvailable()
                if (!isAvailable) {
                    _uiState.value = _uiState.value.copy(
                        availableCalendars = emptyList(),
                        isLoading = false,
                        error = "カレンダープロバイダーにアクセスできません。権限を確認してください。"
                    )
                    return@launch
                }
                
                val calendars = localCalendarClient.getAvailableCalendars()
                _uiState.value = _uiState.value.copy(
                    availableCalendars = calendars,
                    isLoading = false,
                    error = null
                )
                
                if (calendars.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        error = "利用可能なカレンダーが見つかりません。Googleアカウントが同期されているか確認してください。"
                    )
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "カレンダー一覧の取得に失敗しました: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun setCalendarEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                calendarUseCase.setCalendarEnabled(enabled)
                if (enabled) {
                    // 有効にした場合は、カレンダー一覧を再読み込み
                    loadAvailableCalendars()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "設定の更新に失敗しました: ${e.message}"
                )
            }
        }
    }

    fun setSelectedCalendar(calendarId: Long?) {
        viewModelScope.launch {
            try {
                calendarUseCase.setSelectedCalendar(calendarId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "カレンダーの選択に失敗しました: ${e.message}"
                )
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = true,
                    error = null
                )
                
                // まずカレンダーが利用可能かチェック
                val isAvailable = localCalendarClient.isCalendarAvailable()
                if (!isAvailable) {
                    _uiState.value = _uiState.value.copy(
                        connectionTestResult = false,
                        isTestingConnection = false,
                        error = "カレンダープロバイダーにアクセスできません。権限を確認してください。"
                    )
                    return@launch
                }
                
                val result = localCalendarClient.testConnection()
                _uiState.value = _uiState.value.copy(
                    connectionTestResult = result,
                    isTestingConnection = false,
                    error = null
                )
                
                if (!result) {
                    _uiState.value = _uiState.value.copy(
                        error = "カレンダーへの接続に失敗しました。Googleアカウントが同期されているか確認してください。"
                    )
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "接続テストに失敗しました: ${e.message}",
                    isTestingConnection = false,
                    connectionTestResult = false
                )
            }
        }
    }

    fun refreshCalendars() {
        loadAvailableCalendars()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun onPermissionDenied() {
        _uiState.value = _uiState.value.copy(
            error = "カレンダーへのアクセス権限が必要です。設定から権限を許可してください。"
        )
    }
}

data class CalendarSettingsUiState(
    val settings: CalendarSettings = CalendarSettings(),
    val availableCalendars: List<CalendarInfo> = emptyList(),
    val isLoading: Boolean = true,
    val isTestingConnection: Boolean = false,
    val connectionTestResult: Boolean? = null,
    val error: String? = null
)
