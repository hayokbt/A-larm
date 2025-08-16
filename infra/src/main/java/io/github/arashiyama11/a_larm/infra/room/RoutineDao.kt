package io.github.arashiyama11.a_larm.infra.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.github.arashiyama11.a_larm.domain.models.RoutineMode

@Dao
interface RoutineDao {

    @Query("SELECT * FROM routine_cell WHERE mode = :mode")
    suspend fun getAll(mode: RoutineMode): List<RoutineCellEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RoutineCellEntity>)

    @Query("DELETE FROM routine_cell WHERE mode = :mode")
    suspend fun deleteAllByMode(mode: RoutineMode)

    @Transaction
    suspend fun replaceAll(mode: RoutineMode, items: List<RoutineCellEntity>) {
        deleteAllByMode(mode)
        if (items.isNotEmpty()) upsertAll(items)
    }
}
