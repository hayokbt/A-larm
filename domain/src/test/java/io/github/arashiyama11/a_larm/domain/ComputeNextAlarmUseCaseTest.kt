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
import org.junit.Test

class ComputeNextAlarmUseCaseTest {

    @Test
    fun testComputeNextAlarm() = runBlocking {
        val repo = FakeRoutineRepository().apply {
            dayGrid = mapOf(
                CellKey(0, 7) to RoutineEntry(
                    type = RoutineType.WAKE,
                    label = "起床",
                    minute = 30
                )
            )

            mode = RoutineMode.DAILY
        }
        val now = java.time.LocalDateTime.of(2023, 10, 1, 7, 0)
        val useCase = ComputeNextAlarmUseCase(repo)
        val nextAlarm = useCase.execute(now)
        assert(nextAlarm == java.time.LocalDateTime.of(2023, 10, 1, 7, 30)) {
            "Expected next alarm at 07:30 but got $nextAlarm"
        }
    }
}


class FakeRoutineRepository : RoutineRepository {
    var mode = RoutineMode.DAILY
    var dayGrid: RoutineGrid = mutableMapOf()
    var weekGrid: RoutineGrid = mutableMapOf()
    override suspend fun load(mode: RoutineMode): RoutineGrid {
        return when (mode) {
            RoutineMode.DAILY -> dayGrid
            RoutineMode.WEEKLY -> weekGrid
        }
    }

    override suspend fun save(
        mode: RoutineMode,
        grid: RoutineGrid
    ) {
        when (mode) {
            RoutineMode.DAILY -> dayGrid + grid
            RoutineMode.WEEKLY -> weekGrid + grid
        }
    }

    override suspend fun setRoutineMode(mode: RoutineMode) {
        this.mode = mode
    }

    override suspend fun getRoutineMode(): Flow<RoutineMode> {
        return flowOf(mode)
    }
}
