package io.github.arashiyama11.a_larm.infra.room

import androidx.room.Entity
import io.github.arashiyama11.a_larm.domain.models.RoutineMode
import io.github.arashiyama11.a_larm.domain.models.RoutineType


@Entity(
    tableName = "routine_cell",
    primaryKeys = ["mode", "dayIndex", "hour"]
)
data class RoutineCellEntity(
    val mode: RoutineMode,
    val dayIndex: Int,      // 0..6 (Mon..Sun)。DAILYのときは 0 固定でもOK
    val hour: Int,          // 0..23
    val type: RoutineType,
    val label: String,
    val minute: Int
)