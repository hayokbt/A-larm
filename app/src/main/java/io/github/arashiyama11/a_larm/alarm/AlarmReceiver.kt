package io.github.arashiyama11.a_larm.alarm


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("label").orEmpty()
        val svc = Intent(context, AlarmService::class.java)
            .putExtra("label", label)
        // O+ は startForegroundService
        context.startForegroundService(svc)
    }
}