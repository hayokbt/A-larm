package io.github.arashiyama11.a_larm.infra.room

import androidx.room.TypeConverter
import io.github.arashiyama11.a_larm.domain.models.RoutineMode
import io.github.arashiyama11.a_larm.domain.models.RoutineType


class RoutineConverters {
    @TypeConverter
    fun fromMode(mode: RoutineMode): String = mode.name
    @TypeConverter
    fun toMode(s: String): RoutineMode = RoutineMode.valueOf(s)

    @TypeConverter
    fun fromType(type: RoutineType): String = type.name
    @TypeConverter
    fun toType(s: String): RoutineType = RoutineType.valueOf(s)
}