package io.github.arashiyama11.a_larm.infra.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.arashiyama11.a_larm.domain.models.AlarmId
import io.github.arashiyama11.a_larm.domain.models.AlarmRule
import io.github.arashiyama11.a_larm.domain.models.RoutineMode
import io.github.arashiyama11.a_larm.domain.models.RoutineType


@Entity(
    tableName = "routine_cell",
)
data class RoutineCellEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mode: RoutineMode,
    val dayIndex: Int,
    val hour: Int,          // 0..23
    val type: RoutineType,
    val label: String,
    val minute: Int
) {
    fun toDomain(): AlarmRule {
        require(minute in 0..59) { "minute must be 0..59" }
        return AlarmRule(
            id = AlarmId(id),
            type = type,
            label = label,
            minute = minute,
            dayIndex = dayIndex,
            hour = hour,
            mode = mode
        )
    }
}