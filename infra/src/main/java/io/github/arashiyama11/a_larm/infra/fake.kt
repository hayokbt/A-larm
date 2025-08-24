package io.github.arashiyama11.a_larm.infra

import io.github.arashiyama11.a_larm.domain.AlarmRuleRepository
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
import io.github.arashiyama11.a_larm.infra.calendar.LocalCalendarClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
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

class FakeCalendarReadGateway @Inject constructor(
    private val localCalendarClient: LocalCalendarClient
) : CalendarReadGateway {
    override suspend fun eventsOn(date: LocalDate): List<CalendarEvent> {
        return localCalendarClient.getEventsForDate(date)
        // テスト用のダミーデータ
        val now = LocalDateTime.now()
        return listOf(
            CalendarEvent(
                title = "テストイベント1",
                start = now.withHour(9).withMinute(0).withSecond(0),
                end = now.withHour(10).withMinute(0).withSecond(0),
                location = "会議室A"
            ),
            CalendarEvent(
                title = "テストイベント2",
                start = now.withHour(15).withMinute(30).withSecond(0),
                end = now.withHour(16).withMinute(30).withSecond(0),
                location = "オフィス"
            )
        )
    }
}


class FakeDayBriefGateway @Inject constructor(
    private val calendarReadGateway: CalendarReadGateway
) : DayBriefGateway {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
    }

    override suspend fun buildBrief(forDate: LocalDate): DayBrief {
        val events = calendarReadGateway.eventsOn(forDate)
        return DayBrief(
            date = LocalDateTime.now(),
            weather = null,
            calendar = events
        )
    }

    companion object {
        private const val ENDPOINT = "https://weather.tsukumijima.net/api/forecast/city/130010"
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

