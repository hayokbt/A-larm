package io.github.arashiyama11.a_larm.domain.models

import java.time.LocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

enum class RoutineMode { DAILY, WEEKLY }
enum class RoutineType { NONE, WAKE, SLEEP, TASK }

data class RoutineEntry(
    val type: RoutineType = RoutineType.NONE,
    val label: String = "",
    val minute: Int = 0
)

data class CellKey(val dayIndex: Int, val hour: Int) {
    init {
        require(dayIndex in 0..6) { "dayIndex must be 0..6 (Mon..Sun)" }
        require(hour in 0..23) { "hour must be 0..23" }
    }
}

typealias RoutineGrid = Map<CellKey, RoutineEntry>


/** 当日の状況をまとめてプロンプト文脈に載せるための軽量ブリーフ */
data class DayBrief(
    val date: LocalDateTime?,
    val calendar: List<CalendarEvent> = emptyList(),
    val weather: WeatherBrief? = null,
    val isTraveling: Boolean = false,
    val locationTag: String? = null // "自宅","出張先" 等の粗いタグ
)

data class CalendarEvent(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String? = null
)

data class WeatherBrief(
    val summary: String,           // "晴れのち曇り"
    val tempC: Double?,            // 現在 or 朝の予報
    val precipitationChance: Int?  // %
)

@OptIn(ExperimentalTime::class)
data class ConversationTurn(
    val role: Role,
    val text: String,
    val at: Instant = Clock.System.now()
)
