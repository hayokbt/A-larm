package io.github.arashiyama11.a_larm.domain.usecase

import io.github.arashiyama11.a_larm.domain.RoutineRepository
import io.github.arashiyama11.a_larm.domain.models.CellKey
import io.github.arashiyama11.a_larm.domain.models.RoutineEntry
import io.github.arashiyama11.a_larm.domain.models.RoutineMode
import io.github.arashiyama11.a_larm.domain.models.RoutineType
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComputeNextAlarmUseCase @Inject constructor(
    private val routineRepository: RoutineRepository
) {
    /**
     * 「now以降で最も近い起床時刻」を返す
     */
    suspend fun execute(now: LocalDateTime): LocalDateTime? {
        val mode = routineRepository.getRoutineMode().first()
        val grid = routineRepository.load(mode)

        return grid.asSequence()
            .filter { it.value.type == RoutineType.WAKE }
            .map { (key, entry) -> nextOccurrence(now, mode, key, entry) }
            .minOrNull()
    }

    private fun nextOccurrence(
        now: LocalDateTime,
        mode: RoutineMode,
        key: CellKey,
        entry: RoutineEntry
    ): LocalDateTime {
        val base = when (mode) {
            RoutineMode.DAILY -> {
                now.withHour(key.hour)
                    .withMinute(entry.minute)
                    .withSecond(0)
                    .withNano(0)
            }

            RoutineMode.WEEKLY -> {
                // LocalDateTime.dayOfWeek.value: 1..7 (Mon..Sun) -> 0..6 に補正
                val todayIdx = now.dayOfWeek.value - 1
                val dayDiff = (key.dayIndex - todayIdx + 7) % 7
                val date = now.toLocalDate().plusDays(dayDiff.toLong())
                LocalDateTime.of(date, LocalTime.of(key.hour, entry.minute))
                    .withSecond(0)
                    .withNano(0)
            }
        }

        return when (mode) {
            RoutineMode.DAILY ->
                if (base.isBefore(now)) base.plusDays(1) else base

            RoutineMode.WEEKLY ->
                if (base.isBefore(now)) base.plusDays(7) else base
        }
    }
}