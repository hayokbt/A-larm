package io.github.arashiyama11.a_larm.domain.models

/**
 * カレンダー設定
 */
data class CalendarSettings(
    /** カレンダー機能が有効かどうか */
    val isEnabled: Boolean = false,

    /** 選択されたカレンダーID */
    val selectedCalendarId: Long? = null,

    /** 最後に同期した日時 */
    val lastSyncTime: java.time.LocalDateTime? = null
)
