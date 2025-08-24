package io.github.arashiyama11.a_larm.infra.calendar

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.arashiyama11.a_larm.domain.models.CalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCalendarClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LocalCalendarClient"
        private val CALENDAR_PROJECTION = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_LOCATION
        )
    }

    /**
     * 指定された日付のカレンダーイベントを取得
     */
    suspend fun getEventsForDate(date: LocalDate): List<CalendarEvent> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver

            // 日付の開始と終了をミリ秒に変換
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            // クエリ条件
            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
            val selectionArgs = arrayOf(startOfDay.toString(), endOfDay.toString())

            // ソート順（開始時間順）
            val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

            val cursor: Cursor? = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                CALENDAR_PROJECTION,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val events = mutableListOf<CalendarEvent>()

                while (it.moveToNext()) {
                    try {
                        val event = cursorToCalendarEvent(it)
                        if (event != null) {
                            events.add(event)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "イベントの変換に失敗: ${e.message}")
                    }
                }

                Log.d(TAG, "取得したイベント数: ${events.size}")
                events
            } ?: emptyList()

        } catch (e: Exception) {
            Log.e(TAG, "カレンダーイベント取得エラー", e)
            emptyList()
        }
    }

    /**
     * カレンダーへの接続テスト
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver

            // カレンダー一覧を取得してテスト
            val cursor: Cursor? = contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.VISIBLE
                ),
                "${CalendarContract.Calendars.VISIBLE} = 1", // 表示可能なカレンダーのみ
                null,
                null
            )

            cursor?.use {
                val calendarCount = it.count
                Log.d(TAG, "利用可能なカレンダー数: $calendarCount")
                
                if (calendarCount > 0) {
                    // カレンダーの詳細情報をログ出力
                    while (it.moveToNext()) {
                        val id = it.getLong(0)
                        val name = it.getString(1) ?: "不明"
                        val accountName = it.getString(2) ?: "不明"
                        val displayName = it.getString(3) ?: name
                        val visible = it.getInt(4)
                        
                        Log.d(TAG, "カレンダー情報: ID=$id, 名前=$name, アカウント=$accountName, 表示名=$displayName, 表示=$visible")
                    }
                }
                
                calendarCount > 0
            } ?: false

        } catch (e: Exception) {
            Log.e(TAG, "接続テスト失敗", e)
            false
        }
    }

    /**
     * 利用可能なカレンダー一覧を取得
     */
    suspend fun getAvailableCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver

            val cursor: Cursor? = contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.VISIBLE,
                    CalendarContract.Calendars.ACCOUNT_TYPE
                ),
                "${CalendarContract.Calendars.VISIBLE} = 1", // 表示可能なカレンダーのみ
                null,
                "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val calendars = mutableListOf<CalendarInfo>()

                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val name = it.getString(1) ?: "不明"
                    val accountName = it.getString(2) ?: "不明"
                    val displayName = it.getString(3) ?: name
                    val visible = it.getInt(4)
                    val accountType = it.getString(5) ?: "不明"

                    Log.d(TAG, "カレンダー検出: ID=$id, 名前=$name, アカウント=$accountName, 表示名=$displayName, 表示=$visible, タイプ=$accountType")

                    if (visible == 1) {
                        calendars.add(CalendarInfo(id, name, accountName, displayName))
                    }
                }

                Log.d(TAG, "検出されたカレンダー数: ${calendars.size}")
                calendars
            } ?: emptyList()

        } catch (e: Exception) {
            Log.e(TAG, "カレンダー一覧取得エラー", e)
            emptyList()
        }
    }

    /**
     * カレンダーが利用可能かどうかをチェック
     */
    suspend fun isCalendarAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            
            // 基本的なカレンダーアクセス権限をチェック
            val cursor: Cursor? = contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                null,
                null,
                null
            )
            
            cursor?.use {
                val count = it.count
                Log.d(TAG, "カレンダープロバイダーへのアクセス可能: $count")
                true
            } ?: false
            
        } catch (e: Exception) {
            Log.e(TAG, "カレンダー利用可能性チェック失敗", e)
            false
        }
    }

    /**
     * CursorからCalendarEventオブジェクトに変換
     */
    private fun cursorToCalendarEvent(cursor: Cursor): CalendarEvent? {
        return try {
            val title = cursor.getString(1) ?: "タイトルなし"
            val startTime = cursor.getLong(3)
            val endTime = cursor.getLong(4)
            val location = cursor.getString(6)

            // ミリ秒をLocalDateTimeに変換
            val startDateTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(startTime),
                ZoneId.systemDefault()
            )

            val endDateTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(endTime),
                ZoneId.systemDefault()
            )

            CalendarEvent(
                title = title,
                start = startDateTime,
                end = endDateTime,
                location = location
            )

        } catch (e: Exception) {
            Log.w(TAG, "CalendarEvent変換エラー: ${e.message}")
            null
        }
    }
}

/**
 * カレンダー情報
 */
data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val displayName: String
)
