package io.github.arashiyama11.a_larm.infra.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters


@Database(
    entities = [RoutineCellEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoutineConverters::class)
abstract class RoutineDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
}