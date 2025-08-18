package io.github.arashiyama11.a_larm.domain

import io.github.arashiyama11.a_larm.domain.models.AlarmId
import io.github.arashiyama11.a_larm.domain.models.AlarmRule
import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.ConversationTurn
import io.github.arashiyama11.a_larm.domain.models.RoutineGrid
import io.github.arashiyama11.a_larm.domain.models.RoutineMode
import io.github.arashiyama11.a_larm.domain.models.SessionId
import io.github.arashiyama11.a_larm.domain.models.UserProfile
import kotlinx.coroutines.flow.Flow


interface RoutineRepository {
    fun load(mode: RoutineMode): Flow<RoutineGrid>
    suspend fun save(mode: RoutineMode, grid: RoutineGrid)

    suspend fun setRoutineMode(mode: RoutineMode)
    fun getRoutineMode(): Flow<RoutineMode>
}
//
//interface HabitRepository {
//    suspend fun getWeeklyHabit(userId: UserId): WeeklyHabit?
//    suspend fun upsertWeeklyHabit(userId: UserId, habit: WeeklyHabit)
//}

interface AlarmRuleRepository {
    suspend fun list(): List<AlarmRule>
    suspend fun upsert(rule: AlarmRule)
    suspend fun remove(id: AlarmId)
}

interface PersonaRepository {
    suspend fun getCurrent(): AssistantPersona
    suspend fun setCurrent(persona: AssistantPersona)
}

interface ConversationLogRepository {
    suspend fun append(sessionId: SessionId, turn: ConversationTurn)
    suspend fun history(sessionId: SessionId, limit: Int = 50): List<ConversationTurn>
}

interface LlmApiKeyRepository {
    suspend fun getKey(): String?
    suspend fun setKey(key: String?)
    suspend fun clearKey()
}

interface UserProfileRepository {
    fun getProfile(): Flow<UserProfile?>
    suspend fun saveProfile(profile: UserProfile)
}
