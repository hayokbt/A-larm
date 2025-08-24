package io.github.arashiyama11.a_larm.infra.repository

import io.github.arashiyama11.a_larm.domain.CalendarSettingsRepository
import io.github.arashiyama11.a_larm.domain.models.CalendarSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarSettingsRepositoryImpl @Inject constructor() : CalendarSettingsRepository {

    private val settingsFlow = MutableStateFlow(
        CalendarSettings(
            isEnabled = false,
            selectedCalendarId = null,
            lastSyncTime = null
        )
    )

    override fun getSettings(): Flow<CalendarSettings> = settingsFlow

    override suspend fun updateSettings(settings: CalendarSettings) {
        settingsFlow.value = settings
    }

    override suspend fun setCalendarEnabled(enabled: Boolean) {
        val currentSettings = settingsFlow.value
        settingsFlow.value = currentSettings.copy(isEnabled = enabled)
    }

    override suspend fun setSelectedCalendar(calendarId: Long?) {
        val currentSettings = settingsFlow.value
        settingsFlow.value = currentSettings.copy(
            selectedCalendarId = calendarId,
            lastSyncTime = if (calendarId != null) java.time.LocalDateTime.now() else null
        )
    }
}
