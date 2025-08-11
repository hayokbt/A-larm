package io.github.arashiyama11.a_larm.domain

import io.github.arashiyama11.a_larm.domain.models.AlarmId
import io.github.arashiyama11.a_larm.domain.models.AlarmRule
import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.ConversationTurn
import io.github.arashiyama11.a_larm.domain.models.SessionId
import io.github.arashiyama11.a_larm.domain.models.UserId
import io.github.arashiyama11.a_larm.domain.models.WeeklyHabit

interface HabitRepository {
    suspend fun getWeeklyHabit(userId: UserId): WeeklyHabit?
    suspend fun upsertWeeklyHabit(userId: UserId, habit: WeeklyHabit)
}

interface AlarmRuleRepository {
    suspend fun list(userId: UserId): List<AlarmRule>
    suspend fun upsert(userId: UserId, rule: AlarmRule)
    suspend fun remove(userId: UserId, id: AlarmId)
}

interface PersonaRepository {
    suspend fun getCurrent(userId: UserId): AssistantPersona
    suspend fun setCurrent(userId: UserId, persona: AssistantPersona)
}

interface ConversationLogRepository {
    suspend fun append(sessionId: SessionId, turn: ConversationTurn)
    suspend fun history(sessionId: SessionId, limit: Int = 50): List<ConversationTurn>
}
