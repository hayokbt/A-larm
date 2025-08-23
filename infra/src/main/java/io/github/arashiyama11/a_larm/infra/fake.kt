package io.github.arashiyama11.a_larm.infra

import io.github.arashiyama11.a_larm.domain.AlarmRuleRepository
import io.github.arashiyama11.a_larm.domain.AudioOutputGateway
import io.github.arashiyama11.a_larm.domain.CalendarReadGateway
import io.github.arashiyama11.a_larm.domain.ConversationLogRepository
import io.github.arashiyama11.a_larm.domain.DayBriefGateway
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
import io.github.arashiyama11.a_larm.domain.models.VolumeRampPolicy
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class FakeAlarmRuleRepository @Inject constructor() : AlarmRuleRepository {
    override suspend fun list(): List<AlarmRule> {
        TODO("Not yet implemented")
    }

    override suspend fun upsert(
        rule: AlarmRule
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun remove(
        id: AlarmId
    ) {
        TODO("Not yet implemented")
    }

}

class FakePersonaRepository @Inject constructor() : PersonaRepository {
    override suspend fun list(): List<AssistantPersona> {
        TODO("Not yet implemented")
    }

    override suspend fun getCurrent(): AssistantPersona {
        TODO("Not yet implemented")
    }

    override suspend fun setCurrent(
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
//
//class FakeAudioOutputGateway @Inject constructor() : AudioOutputGateway {
//    override suspend fun setVolume(level: Int) {
//
//    }
//
//    override suspend fun ramp(policy: VolumeRampPolicy) {
//    }
//
//    override fun supportedRange(): IntRange {
//        return 1..10
//    }
//
//    override suspend fun play(data: ByteArray) {
//    }
//
//}

class FakeSttGateway @Inject constructor() : SttGateway {
    override fun startStreaming(): Flow<SttResult> {
        TODO("Not yet implemented")
    }

    override suspend fun stop() {
        TODO("Not yet implemented")
    }
}

