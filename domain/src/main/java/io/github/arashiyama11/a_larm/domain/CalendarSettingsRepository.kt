package io.github.arashiyama11.a_larm.domain

import io.github.arashiyama11.a_larm.domain.models.CalendarSettings
import kotlinx.coroutines.flow.Flow

/**
 * カレンダー設定リポジトリ
 */
interface CalendarSettingsRepository {
    /**
     * 設定を取得
     */
    fun getSettings(): Flow<CalendarSettings>

    /**
     * 設定を更新
     */
    suspend fun updateSettings(settings: CalendarSettings)

    /**
     * カレンダー機能を有効/無効にする
     */
    suspend fun setCalendarEnabled(enabled: Boolean)

    /**
     * 選択されたカレンダーを設定
     */
    suspend fun setSelectedCalendar(calendarId: Long?)
}
