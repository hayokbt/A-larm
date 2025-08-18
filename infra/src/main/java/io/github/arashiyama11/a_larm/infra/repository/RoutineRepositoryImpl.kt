package io.github.arashiyama11.a_larm.infra.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.arashiyama11.a_larm.domain.RoutineRepository
import io.github.arashiyama11.a_larm.domain.models.CellKey
import io.github.arashiyama11.a_larm.domain.models.RoutineEntry
import io.github.arashiyama11.a_larm.domain.models.RoutineGrid
import io.github.arashiyama11.a_larm.domain.models.RoutineMode
import io.github.arashiyama11.a_larm.domain.models.RoutineType
import io.github.arashiyama11.a_larm.infra.repository.RoutineRepositoryImpl.Companion.STORE_NAME
import io.github.arashiyama11.a_larm.infra.room.RoutineCellEntity
import io.github.arashiyama11.a_larm.infra.room.RoutineDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = STORE_NAME)

@Singleton
class RoutineRepositoryImpl @Inject constructor(
    private val dao: RoutineDao,
    @param:ApplicationContext private val context: Context
) : RoutineRepository {

    private val prefKey = stringPreferencesKey(PREF_NAME)

    override fun load(mode: RoutineMode): Flow<RoutineGrid> {
        return dao.getAll(mode).map {
            it.associate { e ->
                CellKey(dayIndex = e.dayIndex, hour = e.hour) to
                        RoutineEntry(type = e.type, label = e.label, minute = e.minute)
            }
        }
    }

    override suspend fun save(mode: RoutineMode, grid: RoutineGrid) {
        val entities = grid
            // NONE は保存しない（スパース化）※必要ならこのフィルタを外してください
            .filterValues { it.type != RoutineType.NONE }
            .map { (key, entry) ->
                require(entry.minute in 0..59) { "minute must be 0..59" }
                RoutineCellEntity(
                    mode = mode,
                    dayIndex = when (mode) {
                        RoutineMode.DAILY -> 0 // DAILYは曜日に依らないので0固定にしても良い
                        RoutineMode.WEEKLY -> key.dayIndex
                    },
                    hour = key.hour,
                    type = entry.type,
                    label = entry.label,
                    minute = entry.minute
                )
            }
        dao.replaceAll(mode, entities)
    }

    override suspend fun setRoutineMode(mode: RoutineMode) {
        println("setRoutineMode: $mode")
        context.dataStore.edit { prefs ->
            prefs[prefKey] = mode.name
        }
    }

    override fun getRoutineMode(): Flow<RoutineMode> {
        return context.dataStore.data
            .map {
                println("getRoutineMode: ${it[prefKey]}")
                it[prefKey]?.let { name ->
                    RoutineMode.valueOf(name)
                } ?: RoutineMode.DAILY
            }
    }

    companion object {
        internal const val STORE_NAME = "routine_prefs"
        private const val PREF_NAME = "routine_mode"
    }
}