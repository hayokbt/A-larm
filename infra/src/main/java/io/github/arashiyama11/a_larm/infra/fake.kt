package io.github.arashiyama11.a_larm.infra

import io.github.arashiyama11.a_larm.domain.AlarmRuleRepository
import io.github.arashiyama11.a_larm.domain.AlarmSchedulerGateway
import io.github.arashiyama11.a_larm.domain.AlarmTrigger
import io.github.arashiyama11.a_larm.domain.AudioOutputGateway
import io.github.arashiyama11.a_larm.domain.CalendarReadGateway
import io.github.arashiyama11.a_larm.domain.ConversationLogRepository
import io.github.arashiyama11.a_larm.domain.DayBriefGateway
import io.github.arashiyama11.a_larm.domain.HabitRepository
import io.github.arashiyama11.a_larm.domain.LlmChatGateway
import io.github.arashiyama11.a_larm.domain.LlmChunk
import io.github.arashiyama11.a_larm.domain.PersonaRepository
import io.github.arashiyama11.a_larm.domain.SttGateway
import io.github.arashiyama11.a_larm.domain.SttResult
import io.github.arashiyama11.a_larm.domain.models.AlarmId
import io.github.arashiyama11.a_larm.domain.models.AlarmRule
import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import io.github.arashiyama11.a_larm.domain.models.CalendarEvent
import io.github.arashiyama11.a_larm.domain.models.ConversationTurn
import io.github.arashiyama11.a_larm.domain.models.DayBrief
import io.github.arashiyama11.a_larm.domain.models.SessionId
import io.github.arashiyama11.a_larm.domain.models.UserId
import io.github.arashiyama11.a_larm.domain.models.VolumeRampPolicy
import io.github.arashiyama11.a_larm.domain.models.WeeklyHabit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

class FakeHabitRepository @Inject constructor() : HabitRepository {
    override suspend fun getWeeklyHabit(userId: UserId): WeeklyHabit? {
        TODO("Not yet implemented")
    }

    override suspend fun upsertWeeklyHabit(
        userId: UserId,
        habit: WeeklyHabit
    ) {
        TODO("Not yet implemented")
    }
}

class FakeAlarmRuleRepository @Inject constructor() : AlarmRuleRepository {
    override suspend fun list(userId: UserId): List<AlarmRule> {
        TODO("Not yet implemented")
    }

    override suspend fun upsert(
        userId: UserId,
        rule: AlarmRule
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun remove(
        userId: UserId,
        id: AlarmId
    ) {
        TODO("Not yet implemented")
    }

}

class FakePersonaRepository @Inject constructor() : PersonaRepository {
    override suspend fun getCurrent(userId: UserId): AssistantPersona {
        TODO("Not yet implemented")
    }

    override suspend fun setCurrent(
        userId: UserId,
        persona: AssistantPersona
    ) {
        TODO("Not yet implemented")
    }

}

class FakeConversationLogRepository @Inject constructor() : ConversationLogRepository {
    override suspend fun append(
        sessionId: SessionId,
        turn: ConversationTurn
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun history(
        sessionId: SessionId,
        limit: Int
    ): List<ConversationTurn> {
        TODO("Not yet implemented")
    }

}

class FakeCalendarReadGateway @Inject constructor() : CalendarReadGateway {
    override suspend fun eventsOn(date: LocalDate): List<CalendarEvent> {
        TODO("Not yet implemented")
    }
}


class FakeDayBriefGateway @Inject constructor() : DayBriefGateway {
    override suspend fun buildBrief(forDate: LocalDate): DayBrief {
        TODO("Not yet implemented")
    }
}


class FakeAlarmSchedulerGateway @Inject constructor() : AlarmSchedulerGateway {
    override suspend fun scheduleExact(
        at: LocalDateTime,
        payload: AlarmId?
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun cancel(id: AlarmId) {
        TODO("Not yet implemented")
    }

    override val triggers: Flow<AlarmTrigger>
}


class FakeAudioOutputGateway @Inject constructor() : AudioOutputGateway {
    override suspend fun setVolume(level: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun ramp(policy: VolumeRampPolicy) {
        TODO("Not yet implemented")
    }

    override fun supportedRange(): IntRange {
        TODO("Not yet implemented")
    }

}

class FakeSttGateway @Inject constructor() : SttGateway {
    override fun startStreaming(): Flow<SttResult> {
        TODO("Not yet implemented")
    }

    override suspend fun stop() {
        TODO("Not yet implemented")
    }
}


class FakeLlmChatGateway @Inject constructor() : LlmChatGateway {
    override fun streamReply(
        persona: AssistantPersona,
        brief: DayBrief,
        history: List<ConversationTurn>
    ): Flow<LlmChunk> {
        var i = 0
        return flow {
            while (true) {
                emit(LlmChunk.Text("これはダミーの応答です。${i++}"))
                kotlinx.coroutines.delay(1000) // 1秒ごとにダミーのテキストを送信
            }
        }
    }

}
