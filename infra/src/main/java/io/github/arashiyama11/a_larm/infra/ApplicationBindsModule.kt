package io.github.arashiyama11.a_larm.infra

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.arashiyama11.a_larm.domain.AlarmRuleRepository
import io.github.arashiyama11.a_larm.domain.AlarmSchedulerGateway
import io.github.arashiyama11.a_larm.domain.AudioOutputGateway
import io.github.arashiyama11.a_larm.domain.CalendarReadGateway
import io.github.arashiyama11.a_larm.domain.ConversationLogRepository
import io.github.arashiyama11.a_larm.domain.DayBriefGateway
import io.github.arashiyama11.a_larm.domain.HabitRepository
import io.github.arashiyama11.a_larm.domain.LlmChatGateway
import io.github.arashiyama11.a_larm.domain.PersonaRepository
import io.github.arashiyama11.a_larm.domain.SttGateway
import io.github.arashiyama11.a_larm.domain.TtsGateway

@Module
@InstallIn(SingletonComponent::class)
abstract class ApplicationBindsModule {
    @Binds
    abstract fun bindHabitRepository(
        impl: FakeHabitRepository
    ): HabitRepository


    @Binds
    abstract fun bindAlarmRuleRepository(
        impl: FakeAlarmRuleRepository
    ): AlarmRuleRepository

    @Binds
    abstract fun bindPersonaRepository(
        impl: FakePersonaRepository
    ): PersonaRepository

    @Binds
    abstract fun bindConversationLogRepository(
        impl: FakeConversationLogRepository
    ): ConversationLogRepository

    @Binds
    abstract fun bindCalendarReadGateway(
        impl: FakeCalendarReadGateway
    ): CalendarReadGateway

    @Binds
    abstract fun bindDayBriefGateway(
        impl: FakeDayBriefGateway
    ): DayBriefGateway

    @Binds
    abstract fun bindAlarmSchedulerGateway(
        impl: FakeAlarmSchedulerGateway
    ): AlarmSchedulerGateway

    @Binds
    abstract fun bindAudioOutputGateway(
        impl: FakeAudioOutputGateway
    ): AudioOutputGateway

    @Binds
    abstract fun bindSttGateway(
        impl: FakeSttGateway
    ): SttGateway

    @Binds
    abstract fun bindTtsGateway(
        impl: FakeTtsGateway
    ): TtsGateway

    @Binds
    abstract fun bindLlmChatGateway(
        impl: FakeLlmChatGateway
    ): LlmChatGateway
}