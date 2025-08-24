package io.github.arashiyama11.a_larm.infra

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.arashiyama11.a_larm.domain.AlarmRuleRepository
import io.github.arashiyama11.a_larm.domain.AudioOutputGateway
import io.github.arashiyama11.a_larm.domain.CalendarReadGateway
import io.github.arashiyama11.a_larm.domain.ConversationLogRepository
import io.github.arashiyama11.a_larm.domain.DayBriefGateway
import io.github.arashiyama11.a_larm.domain.LlmApiKeyRepository
import io.github.arashiyama11.a_larm.domain.LlmChatGateway
import io.github.arashiyama11.a_larm.domain.LlmVoiceChatSessionGateway
import io.github.arashiyama11.a_larm.domain.PersonaRepository
import io.github.arashiyama11.a_larm.domain.RoutineRepository
import io.github.arashiyama11.a_larm.domain.SimpleAlarmAudioGateway
import io.github.arashiyama11.a_larm.domain.SttGateway
import io.github.arashiyama11.a_larm.domain.TtsGateway
import io.github.arashiyama11.a_larm.domain.CalendarSettingsRepository
import io.github.arashiyama11.a_larm.domain.UserProfileRepository
import io.github.arashiyama11.a_larm.infra.calendar.LocalCalendarClient
import io.github.arashiyama11.a_larm.infra.gemini.LlmVoiceChatSessionGatewayImpl
import io.github.arashiyama11.a_larm.infra.repository.CalendarSettingsRepositoryImpl
import io.github.arashiyama11.a_larm.infra.repository.LlmApiKeyRepositoryImpl
import io.github.arashiyama11.a_larm.infra.repository.PersonaRepositoryImpl
import io.github.arashiyama11.a_larm.infra.repository.RoutineRepositoryImpl
import io.github.arashiyama11.a_larm.infra.repository.UserProfileRepositoryImpl
import io.github.arashiyama11.a_larm.infra.room.RoutineDao
import io.github.arashiyama11.a_larm.infra.room.RoutineDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DbModule {
    @Provides
    @Singleton
    fun provideDb(@ApplicationContext ctx: Context): RoutineDatabase =
        Room.databaseBuilder(ctx, RoutineDatabase::class.java, "routine.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRoutineDao(db: RoutineDatabase): RoutineDao = db.routineDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ApplicationBindsModule {
    @Singleton
    @Binds
    abstract fun bindRoutineRepository(
        impl: RoutineRepositoryImpl
    ): RoutineRepository

    @Singleton
    @Binds
    abstract fun bindAlarmRuleRepository(
        impl: FakeAlarmRuleRepository
    ): AlarmRuleRepository

    @Singleton
    @Binds
    abstract fun bindPersonaRepository(
        impl: PersonaRepositoryImpl
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

    @Singleton
    @Binds
    abstract fun bindUserProfileRepository(
        impl: UserProfileRepositoryImpl
    ): UserProfileRepository

    @Singleton
    @Binds
    abstract fun bindSimpleAlarmAudioGateway(
        impl: SimpleAlarmAudioGatewayImpl
    ): SimpleAlarmAudioGateway

    @Singleton
    @Binds
    abstract fun bindCalendarSettingsRepository(
        impl: CalendarSettingsRepositoryImpl
    ): CalendarSettingsRepository
}