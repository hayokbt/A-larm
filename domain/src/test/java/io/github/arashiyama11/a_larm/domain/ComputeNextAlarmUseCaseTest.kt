package io.github.arashiyama11.a_larm.domain

import io.github.arashiyama11.a_larm.domain.models.CellKey
import io.github.arashiyama11.a_larm.domain.models.RoutineEntry
import io.github.arashiyama11.a_larm.domain.models.RoutineGrid
import io.github.arashiyama11.a_larm.domain.models.RoutineMode
import io.github.arashiyama11.a_larm.domain.models.RoutineType
import io.github.arashiyama11.a_larm.domain.usecase.ComputeNextAlarmUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class ComputeNextAlarmUseCaseTest {

    @Test
    fun testComputeNextAlarm() = runBlocking {
        val repo = FakeRoutineRepository().apply {
            dayGrid = mapOf(
                // DAILYはdayIndexは無視されるが0..6の範囲でOK
                CellKey(0, 7) to RoutineEntry(
                    type = RoutineType.WAKE,
                    label = "起床",
                    minute = 30
                )
            )
            mode = RoutineMode.DAILY
        }
        val now = LocalDateTime.of(2023, 10, 1, 7, 0)
        val useCase = ComputeNextAlarmUseCase(repo)
        val nextAlarm = useCase.execute(now)
        assertEquals(
            "Expected next alarm at 07:30 but got $nextAlarm",
            LocalDateTime.of(2023, 10, 1, 7, 30, 0, 0),
            nextAlarm
        )
    }

    @Test
    fun `daily mode should return the closest alarm when multiple are available`() = runBlocking {
        val repo = FakeRoutineRepository().apply {
            dayGrid = mapOf(
                CellKey(0, 8) to RoutineEntry(type = RoutineType.WAKE, minute = 0),
                CellKey(0, 7) to RoutineEntry(type = RoutineType.WAKE, minute = 30)
            )
            mode = RoutineMode.DAILY
        }
        val now = LocalDateTime.of(2023, 10, 1, 7, 0)
        val useCase = ComputeNextAlarmUseCase(repo)
        val nextAlarm = useCase.execute(now)
        assertEquals(
            "Expected next alarm to be 07:30",
            now.withHour(7).withMinute(30).withSecond(0).withNano(0),
            nextAlarm
        )
    }

    @Test
    fun `should ignore routine entries that are not of WAKE type`() = runBlocking {
        val repo = FakeRoutineRepository().apply {
            dayGrid = mapOf(
                CellKey(0, 7) to RoutineEntry(type = RoutineType.SLEEP, minute = 30),
                CellKey(0, 8) to RoutineEntry(type = RoutineType.WAKE, minute = 0)
            )
            mode = RoutineMode.DAILY
        }
        val now = LocalDateTime.of(2023, 10, 1, 7, 0)
        val useCase = ComputeNextAlarmUseCase(repo)
        val nextAlarm = useCase.execute(now)
        assertEquals(
            "Expected to ignore SLEEP type and return 08:00 alarm",
            now.withHour(8).withMinute(0).withSecond(0).withNano(0),
            nextAlarm
        )
    }

    @Test
    fun `weekly mode should return upcoming alarm on the same day`() = runBlocking {
        val repo = FakeRoutineRepository().apply {
            weekGrid = mapOf(
                // Monday(0) at 07:30
                CellKey(0, 7) to RoutineEntry(type = RoutineType.WAKE, minute = 30)
            )
            mode = RoutineMode.WEEKLY
        }
        // 2023-10-02 is Monday
        val now = LocalDateTime.of(2023, 10, 2, 7, 0)
        val useCase = ComputeNextAlarmUseCase(repo)
        val nextAlarm = useCase.execute(now)
        assertEquals(
            "Expected next alarm at 07:30 on the same day",
            now.withHour(7).withMinute(30).withSecond(0).withNano(0),
            nextAlarm
        )
    }

    @Test
    fun `weekly mode should return alarm on a later day`() = runBlocking {
        val repo = FakeRoutineRepository().apply {
            weekGrid = mapOf(
                // Monday(0) at 07:30 (already passed)
                CellKey(0, 7) to RoutineEntry(type = RoutineType.WAKE, minute = 30),
                // Tuesday(1) at 06:00
                CellKey(1, 6) to RoutineEntry(type = RoutineType.WAKE, minute = 0)
            )
            mode = RoutineMode.WEEKLY
        }
        // Monday at 08:00
        val now = LocalDateTime.of(2023, 10, 2, 8, 0)
        val useCase = ComputeNextAlarmUseCase(repo)
        val nextAlarm = useCase.execute(now)

        val expected =
            now.toLocalDate().plusDays(1).atTime(6, 0).withSecond(0).withNano(0) // Tue 06:00
        assertEquals(
            "Expected next alarm on Tuesday at 06:00",
            expected,
            nextAlarm
        )
    }

    @Test
    fun `daily mode should return alarm for later hour and earlier minute`() = runBlocking {
        val repo = FakeRoutineRepository().apply {
            dayGrid = mapOf(
                CellKey(0, 8) to RoutineEntry(type = RoutineType.WAKE, minute = 0)
            )
            mode = RoutineMode.DAILY
        }
        val now = LocalDateTime.of(2023, 10, 1, 7, 30)
        val useCase = ComputeNextAlarmUseCase(repo)
        val nextAlarm = useCase.execute(now)
        assertEquals(
            "Expected next alarm at 08:00",
            now.withHour(8).withMinute(0).withSecond(0).withNano(0),
            nextAlarm
        )
    }

    @Test
    fun `weekly mode should wrap around to the next week`() = runBlocking {
        val repo = FakeRoutineRepository().apply {
            weekGrid = mapOf(
                // Saturday(5) at 10:00
                CellKey(5, 10) to RoutineEntry(type = RoutineType.WAKE, minute = 0),
                // Monday(0) at 06:00
                CellKey(0, 6) to RoutineEntry(type = RoutineType.WAKE, minute = 0)
            )
            mode = RoutineMode.WEEKLY
        }
        // 2023-10-07 is Saturday, at 11:00
        val now = LocalDateTime.of(2023, 10, 7, 11, 0)
        val useCase = ComputeNextAlarmUseCase(repo)
        val nextAlarm = useCase.execute(now)

        val expected =
            now.toLocalDate().plusDays(2).atTime(6, 0).withSecond(0).withNano(0) // Mon 06:00
        assertEquals(
            "Expected to wrap to Monday 06:00",
            expected,
            nextAlarm
        )
    }

    @Test
    fun `daily mode should handle overnight alarms correctly`() = runBlocking {
        val repo = FakeRoutineRepository().apply {
            dayGrid = mapOf(
                CellKey(0, 23) to RoutineEntry(type = RoutineType.WAKE, minute = 0),
                CellKey(0, 1) to RoutineEntry(type = RoutineType.WAKE, minute = 0)
            )
            mode = RoutineMode.DAILY
        }
        val now = LocalDateTime.of(2023, 10, 1, 22, 0)
        val useCase = ComputeNextAlarmUseCase(repo)
        val nextAlarm = useCase.execute(now)
        assertEquals(
            "Expected next alarm at 23:00",
            now.withHour(23).withMinute(0).withSecond(0).withNano(0),
            nextAlarm
        )
    }

    @Test
    fun `should return null if no alarms are set`() = runBlocking {
        val repo = FakeRoutineRepository().apply {
            dayGrid = emptyMap()
            weekGrid = emptyMap()
            mode = RoutineMode.DAILY
        }
        val now = LocalDateTime.now()
        val useCase = ComputeNextAlarmUseCase(repo)
        val nextAlarm = useCase.execute(now)
        assertNull("Expected null when no alarms are set", nextAlarm)
    }

    @Test
    fun `should include alarm at the exact current time`() = runBlocking {
        val repo = FakeRoutineRepository().apply {
            dayGrid = mapOf(
                CellKey(0, 7) to RoutineEntry(type = RoutineType.WAKE, minute = 30),
                CellKey(0, 8) to RoutineEntry(type = RoutineType.WAKE, minute = 0)
            )
            mode = RoutineMode.DAILY
        }
        val now = LocalDateTime.of(2023, 10, 1, 7, 30)
        val useCase = ComputeNextAlarmUseCase(repo)
        val nextAlarm = useCase.execute(now)
        assertEquals(
            "Spec says 'now 以降' (inclusive). Should select the 07:30 alarm.",
            now.withSecond(0).withNano(0),
            nextAlarm
        )
    }

    @Test
    fun `weekly mode should correctly wrap from Sunday to Monday`() = runBlocking {
        val repo = FakeRoutineRepository().apply {
            weekGrid = mapOf(
                // Sunday(6) at 10:00 (passed)
                CellKey(6, 10) to RoutineEntry(type = RoutineType.WAKE, minute = 0),
                // Monday(0) at 06:00
                CellKey(0, 6) to RoutineEntry(type = RoutineType.WAKE, minute = 0)
            )
            mode = RoutineMode.WEEKLY
        }
        // 2023-10-08 is Sunday at 11:00
        val now = LocalDateTime.of(2023, 10, 8, 11, 0)
        val useCase = ComputeNextAlarmUseCase(repo)
        val nextAlarm = useCase.execute(now)

        val expected =
            now.toLocalDate().plusDays(1).atTime(6, 0).withSecond(0).withNano(0) // Mon 06:00
        assertEquals(
            "Expected to wrap from Sunday to Monday 06:00",
            expected,
            nextAlarm
        )
    }

    @Test
    fun `daily mode should wrap to next day for overnight alarm`() = runBlocking {
        val repo = FakeRoutineRepository().apply {
            dayGrid = mapOf(
                CellKey(0, 22) to RoutineEntry(type = RoutineType.WAKE, minute = 0), // Passed
                CellKey(0, 6) to RoutineEntry(type = RoutineType.WAKE, minute = 30)  // Next morning
            )
            mode = RoutineMode.DAILY
        }
        val now = LocalDateTime.of(2023, 10, 1, 23, 0)
        val useCase = ComputeNextAlarmUseCase(repo)
        val nextAlarm = useCase.execute(now)

        val expected = now.toLocalDate().plusDays(1).atTime(6, 30).withSecond(0).withNano(0)
        assertEquals(
            "Expected next alarm to be 06:30 next day",
            expected,
            nextAlarm
        )
    }

    @Test
    fun `weekly mode should pick closest day even if not in order`() = runBlocking {
        val repo = FakeRoutineRepository().apply {
            weekGrid = mapOf(
                // Friday(4) at 09:00
                CellKey(4, 9) to RoutineEntry(type = RoutineType.WAKE, minute = 0),
                // Wednesday(2) at 08:00
                CellKey(2, 8) to RoutineEntry(type = RoutineType.WAKE, minute = 0)
            )
            mode = RoutineMode.WEEKLY
        }
        // 2023-10-03 is Tuesday at 12:00
        val now = LocalDateTime.of(2023, 10, 3, 12, 0)
        val useCase = ComputeNextAlarmUseCase(repo)
        val nextAlarm = useCase.execute(now)

        val expected =
            now.toLocalDate().plusDays(1).atTime(8, 0).withSecond(0).withNano(0) // Wed 08:00
        assertEquals(
            "Expected Wednesday 08:00, not Friday 09:00",
            expected,
            nextAlarm
        )
    }

    @Test
    fun `weekly mode should skip same-day-passed-time to next available day`() = runBlocking {
        val repo = FakeRoutineRepository().apply {
            weekGrid = mapOf(
                // Tuesday(1) at 08:00 (passed)
                CellKey(1, 8) to RoutineEntry(type = RoutineType.WAKE, minute = 0),
                // Thursday(3) at 07:00
                CellKey(3, 7) to RoutineEntry(type = RoutineType.WAKE, minute = 0)
            )
            mode = RoutineMode.WEEKLY
        }
        // 2023-10-03 is Tuesday at 12:00
        val now = LocalDateTime.of(2023, 10, 3, 12, 0)
        val usecase = ComputeNextAlarmUseCase(repo)
        val nextAlarm = usecase.execute(now)

        val expected =
            now.toLocalDate().plusDays(2).atTime(7, 0).withSecond(0).withNano(0) // Thu 07:00
        assertEquals(
            "Expected Thursday 07:00 as Tuesday alarm has passed",
            expected,
            nextAlarm
        )
    }
}

class FakeRoutineRepository : RoutineRepository {
    var mode = RoutineMode.DAILY
    var dayGrid: RoutineGrid = emptyMap()
    var weekGrid: RoutineGrid = emptyMap()

    override suspend fun load(mode: RoutineMode): RoutineGrid {
        return when (mode) {
            RoutineMode.DAILY -> dayGrid
            RoutineMode.WEEKLY -> weekGrid
        }
    }

    override suspend fun save(mode: RoutineMode, grid: RoutineGrid) {
        when (mode) {
            RoutineMode.DAILY -> dayGrid = dayGrid + grid
            RoutineMode.WEEKLY -> weekGrid = weekGrid + grid
        }
    }

    override suspend fun setRoutineMode(mode: RoutineMode) {
        this.mode = mode
    }

    override suspend fun getRoutineMode(): Flow<RoutineMode> {
        return flowOf(mode)
    }
}
