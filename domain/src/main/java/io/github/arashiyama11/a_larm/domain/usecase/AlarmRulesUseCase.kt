package io.github.arashiyama11.a_larm.domain.usecase

import io.github.arashiyama11.a_larm.domain.AlarmSchedulerGateway
import io.github.arashiyama11.a_larm.domain.RoutineRepository
import io.github.arashiyama11.a_larm.domain.models.AlarmId
import io.github.arashiyama11.a_larm.domain.models.AlarmRule
import io.github.arashiyama11.a_larm.domain.models.RoutineMode
import io.github.arashiyama11.a_larm.domain.models.RoutineType
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRulesUseCase @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val scheduler: AlarmSchedulerGateway,
) {
    //TODO 同じセルに複数のルールがある場合の処理を追加する
    suspend fun upsertAndReschedule(
        rule: AlarmRule,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        val newId: AlarmId = routineRepository.upsert(rule) // 新IDを受け取る前提
        val saved = rule.copy(id = newId)

        if (!saved.enabled || saved.type != RoutineType.WAKE) {
            scheduler.cancel(newId)
            return
        }
        val at = nextOccurrence(now, saved.mode, saved)
        scheduler.scheduleExact(at, newId)
    }

    suspend fun removeAndCancel(id: AlarmId) {
        routineRepository.delete(id)
        scheduler.cancel(id)
    }

    suspend fun rescheduleAll(
        mode: RoutineMode,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        val rules: List<AlarmRule> = routineRepository.load(mode).first()
        for (r in rules) {
            if (r.enabled && r.type == RoutineType.WAKE) {
                scheduler.scheduleExact(nextOccurrence(now, mode, r), r.id)
            } else {
                scheduler.cancel(r.id)
            }
        }
    }

    suspend fun cancelAll(mode: RoutineMode) {
        val rules: List<AlarmRule> = routineRepository.load(mode).first()
        for (r in rules) {
            scheduler.cancel(r.id)
        }
    }

    suspend fun setNextAlarm(now: LocalDateTime = LocalDateTime.now()) {
        val next = nextAlarmRule(now) ?: return
        if (next.enabled && next.type == RoutineType.WAKE) {
            val mode = routineRepository.getRoutineMode().first()
            scheduler.scheduleExact(nextOccurrence(now, mode, next), next.id)
        } else {
            scheduler.cancel(next.id)
        }
    }

    suspend fun nextAlarmRule(now: LocalDateTime = LocalDateTime.now()): AlarmRule? {
        val mode = routineRepository.getRoutineMode().first()
        val rules = routineRepository.load(mode).first()
        return rules.asSequence()
            .filter { it.enabled && it.type == RoutineType.WAKE }
            .minByOrNull { nextOccurrence(now, mode, it) }
    }

    private fun nextOccurrence(now: LocalDateTime, mode: RoutineMode, r: AlarmRule): LocalDateTime {
        val base = when (mode) {
            RoutineMode.DAILY -> now.withHour(r.hour).withMinute(r.minute).withSecond(0).withNano(0)
            RoutineMode.WEEKLY -> {
                val todayIdx = now.dayOfWeek.value - 1 // 0..6
                val dayDiff = (r.dayIndex - todayIdx + 7) % 7
                val date = now.toLocalDate().plusDays(dayDiff.toLong())
                LocalDateTime.of(date, LocalTime.of(r.hour, r.minute)).withSecond(0).withNano(0)
            }
        }
        return if (base.isBefore(now)) base.plusDays(if (mode == RoutineMode.DAILY) 1 else 7) else base
    }
}
