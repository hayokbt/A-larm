package io.github.arashiyama11.a_larm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent


class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // TODO
            // 永続層(Room/Datastore等)から次回アラームを読み直して schedule
            // 例:
            // val repo = ...
            // val next = repo.loadNextAlarm()
            // AlarmScheduler(context).scheduleAlarm(next.timeMillis, next.label)
        }
    }
}