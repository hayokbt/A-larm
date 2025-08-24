package io.github.arashiyama11.a_larm.domain.usecase

import io.github.arashiyama11.a_larm.domain.CalendarReadGateway
import io.github.arashiyama11.a_larm.domain.CalendarSettingsRepository
import io.github.arashiyama11.a_larm.domain.models.CalendarEvent
import io.github.arashiyama11.a_larm.domain.models.CalendarSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarUseCase @Inject constructor(
    private val calendarReadGateway: CalendarReadGateway,
    private val calendarSettingsRepository: CalendarSettingsRepository
) {

    /**
     * 今日のカレンダーイベントを取得
     */
    suspend fun getTodayEvents(): List<CalendarEvent> {
        val settings = calendarSettingsRepository.getSettings().first()
        if (!settings.isEnabled) {
            return emptyList()
        }
        return calendarReadGateway.eventsOn(LocalDate.now())
    }

    /**
     * 指定された日付のカレンダーイベントを取得
     */
    suspend fun getEventsForDate(date: LocalDate): List<CalendarEvent> {
        val settings = calendarSettingsRepository.getSettings().first()
        if (!settings.isEnabled) {
            return emptyList()
        }
        return calendarReadGateway.eventsOn(date)
    }

    /**
     * カレンダー設定を取得
     */
    fun getSettings(): Flow<CalendarSettings> = calendarSettingsRepository.getSettings()

    /**
     * カレンダー機能を有効/無効にする
     */
    suspend fun setCalendarEnabled(enabled: Boolean) {
        calendarSettingsRepository.setCalendarEnabled(enabled)
    }

    /**
     * 選択されたカレンダーを設定
     */
    suspend fun setSelectedCalendar(calendarId: Long?) {
        calendarSettingsRepository.setSelectedCalendar(calendarId)
    }
}
