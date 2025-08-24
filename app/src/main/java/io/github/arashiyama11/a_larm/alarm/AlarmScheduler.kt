package io.github.arashiyama11.a_larm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.arashiyama11.a_larm.domain.AlarmSchedulerGateway
import io.github.arashiyama11.a_larm.domain.AlarmTrigger
import io.github.arashiyama11.a_larm.domain.models.AlarmId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri
import java.time.format.DateTimeFormatter

@Singleton
class AlarmScheduler @Inject constructor(
    @param:ApplicationContext private val app: Context,
) : AlarmSchedulerGateway {

    override val triggers: Flow<AlarmTrigger> = flowOf()
    private val alarmManager = app.getSystemService(AlarmManager::class.java)

    override suspend fun scheduleExact(at: LocalDateTime, payload: AlarmId?) {
        val timeMillis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        require(timeMillis >= System.currentTimeMillis()) { "Cannot schedule an alarm in the past" }
        val id = requireNotNull(payload) { "AlarmId is required to uniquely schedule/cancel" }

        val label = "Alarm at ${at.format(DateTimeFormatter.ofPattern("HH:mm"))}"
        val req = reqCodeOf(id)
        Log.d(
            "AlarmScheduler",
            "Scheduling alarm at $at with payload=${payload.value} reqCode=$req"
        )
        scheduleAlarm(req, timeMillis, label)
    }

    override suspend fun cancel(id: AlarmId) {
        Log.d("AlarmScheduler", "Cancelling alarm with id=${id.value}")
        val req = reqCodeOf(id)
        val intent = Intent(app, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRE
            data = "a-larm://alarm/$req".toUri()
        }
        val pi = PendingIntent.getBroadcast(
            app, req, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }

    fun scheduleAlarm(reqCode: Int, timeMillis: Long, label: String) {
        val fireIntent = Intent(app, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRE
            data = "a-larm://alarm/$reqCode".toUri() // 一意化
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_REQ_CODE, reqCode)
        }

        val existingPi = PendingIntent.getBroadcast(
            app,
            reqCode,
            fireIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (existingPi != null) {
            // 既存がある場合はキャンセルして入れ替え
            alarmManager.cancel(existingPi)
        }

        val firePi = PendingIntent.getBroadcast(
            app,
            reqCode,
            fireIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(app, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            data = "a-larm://alarm/$reqCode".toUri()
        }
        val showPi = PendingIntent.getActivity(
            app,
            reqCode,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val info = AlarmManager.AlarmClockInfo(timeMillis, showPi)
        alarmManager.setAlarmClock(info, firePi)
    }


    private fun reqCodeOf(id: AlarmId): Int {
        val v = id.value // Long を想定
        return ((v xor (v ushr 32)) and 0x7fffffff).toInt()
    }

    companion object {
        private const val ACTION_ALARM_FIRE = "io.github.arashiyama11.a_larm.ALARM_FIRE"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_REQ_CODE = "req"
    }
}
