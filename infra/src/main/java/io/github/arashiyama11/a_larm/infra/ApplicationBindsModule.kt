package io.github.arashiyama11.a_larm.infra

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.arashiyama11.a_larm.domain.AlarmRuleRepository
import io.github.arashiyama11.a_larm.domain.AudioOutputGateway
import io.github.arashiyama11.a_larm.domain.CalendarReadGateway
import io.github.arashiyama11.a_larm.domain.ConversationLogRepository
import io.github.arashiyama11.a_larm.domain.DayBriefGateway
import io.github.arashiyama11.a_larm.domain.HabitRepository
import io.github.arashiyama11.a_larm.domain.LlmApiKeyRepository
import io.github.arashiyama11.a_larm.domain.LlmChatGateway
import io.github.arashiyama11.a_larm.domain.LlmVoiceChatSessionGateway
import io.github.arashiyama11.a_larm.domain.PersonaRepository
import io.github.arashiyama11.a_larm.domain.SttGateway
import io.github.arashiyama11.a_larm.domain.TtsGateway
import io.github.arashiyama11.a_larm.infra.repository.LlmApiKeyRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ApplicationBindsModule {
    @Singleton
    @Binds
    abstract fun bindHabitRepository(
        impl: FakeHabitRepository
    ): HabitRepository

    @Singleton
    @Binds
    abstract fun bindAlarmRuleRepository(
        impl: FakeAlarmRuleRepository
    ): AlarmRuleRepository

    @Singleton
    @Binds
    abstract fun bindPersonaRepository(
        impl: FakePersonaRepository
    ): PersonaRepository

    @Singleton
    @Binds
    abstract fun bindConversationLogRepository(
        impl: FakeConversationLogRepository
    ): ConversationLogRepository

    @Singleton
    @Binds
    abstract fun bindCalendarReadGateway(
        impl: FakeCalendarReadGateway
    ): CalendarReadGateway

    @Singleton
    @Binds
    abstract fun bindDayBriefGateway(
        impl: FakeDayBriefGateway
    ): DayBriefGateway

    @Singleton
    @Binds
    abstract fun bindAudioOutputGateway(
        impl: AudioOutputGatewayImpl
    ): AudioOutputGateway

    @Singleton
    @Binds
    abstract fun bindSttGateway(
        impl: FakeSttGateway
    ): SttGateway

    @Singleton
    @Binds
    abstract fun bindTtsGateway(
        impl: TtsGatewayImpl
    ): TtsGateway

    @Singleton
    @Binds
    abstract fun bindLlmChatGateway(
        impl: LlmChatGatewayImpl
    ): LlmChatGateway

    @Singleton
    @Binds
    abstract fun bindLlmApiKeyRepository(
        impl: LlmApiKeyRepositoryImpl
    ): LlmApiKeyRepository

    @Binds
    abstract fun bindLlmVoiceChatSessionGateway(
        impl: LlmVoiceChatSessionGatewayImpl
    ): LlmVoiceChatSessionGateway
}