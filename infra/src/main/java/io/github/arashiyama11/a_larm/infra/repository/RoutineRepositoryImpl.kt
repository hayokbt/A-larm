package io.github.arashiyama11.a_larm.infra.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.arashiyama11.a_larm.domain.RoutineRepository
import io.github.arashiyama11.a_larm.domain.models.AlarmId
import io.github.arashiyama11.a_larm.domain.models.AlarmRule
import io.github.arashiyama11.a_larm.domain.models.RoutineMode
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

    override fun load(mode: RoutineMode): Flow<List<AlarmRule>> {
        return dao.getAll(mode).map {
            it.map {
                it.toDomain()
            }
        }
    }

    override suspend fun replaceAll(alarmRules: List<AlarmRule>) {
        if (alarmRules.isEmpty()) {
            return
        }
        alarmRules.map {
            RoutineCellEntity(
                mode = it.mode,
                dayIndex = when (it.mode) {
                    RoutineMode.DAILY -> 0
                    RoutineMode.WEEKLY -> it.dayIndex
                },
                hour = it.hour,
                type = it.type,
                label = it.label.orEmpty(),
                minute = it.minute,
                id = it.id.value
            )
        }.let { entries ->
            dao.replaceAll(alarmRules[0].mode, entries)
        }
    }

    override suspend fun delete(alarmId: AlarmId) {
        dao.deleteById(alarmId.value)
    }

    override suspend fun upsert(alarmRule: AlarmRule): AlarmId {
        val entity = RoutineCellEntity(
            mode = alarmRule.mode,
            dayIndex = when (alarmRule.mode) {
                RoutineMode.DAILY -> 0
                RoutineMode.WEEKLY -> alarmRule.dayIndex
            },
            hour = alarmRule.hour,
            type = alarmRule.type,
            label = alarmRule.label.orEmpty(),
            minute = alarmRule.minute,
            id = alarmRule.id.value
        )
        val id = dao.upsert(entity)
        return AlarmId(id)
    }

    override suspend fun setRoutineMode(mode: RoutineMode) {
        context.dataStore.edit { prefs ->
            prefs[prefKey] = mode.name
        }
    }

    override fun getRoutineMode(): Flow<RoutineMode> {
        return context.dataStore.data
            .map {
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