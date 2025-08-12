package io.github.arashiyama11.a_larm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @param:ApplicationContext private val app: Context
) {
    private val alarmManager = app.getSystemService(AlarmManager::class.java)

    /**
     * timeMillis は System.currentTimeMillis() 基準の UTC ミリ秒
     * label はロックスクリーンやシェードに出る文言
     */
    fun scheduleAlarm(timeMillis: Long, label: String) {
        // ブロードキャスト（トリガー）
        val fireIntent = Intent(app, AlarmReceiver::class.java)
            .putExtra("label", label)
        val firePi = PendingIntent.getBroadcast(
            app, REQ_FIRE, fireIntent, piFlags()
        )

        // アラーム一覧やステータスバーから戻る先（表示用）
        val showIntent = app.packageManager
            .getLaunchIntentForPackage(app.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val showPi = PendingIntent.getActivity(
            app, REQ_SHOW, showIntent, piFlags()
        )

        val info = AlarmManager.AlarmClockInfo(timeMillis, showPi)

        // setAlarmClock: Doze中でも保証。システムUIに”次のアラーム”表示。
        alarmManager.setAlarmClock(info, firePi)
    }

    fun cancelAlarm() {
        val fireIntent = Intent(app, AlarmReceiver::class.java)
        val firePi = PendingIntent.getBroadcast(
            app, REQ_FIRE, fireIntent, piFlags()
        )
        alarmManager.cancel(firePi)
    }

    private fun piFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    companion object {
        private const val REQ_FIRE = 1001
        private const val REQ_SHOW = 1002
    }
}
