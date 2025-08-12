package io.github.arashiyama11.a_larm

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.arashiyama11.a_larm.alarm.AlarmScheduler
import io.github.arashiyama11.a_larm.domain.AlarmSchedulerGateway

@Module
@InstallIn(SingletonComponent::class)
abstract class ApplicationBindsModule {

    @Binds
    abstract fun bindAlarmScheduler(impl: AlarmScheduler): AlarmSchedulerGateway
}