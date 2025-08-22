package io.github.arashiyama11.a_larm.alarm


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.arashiyama11.a_larm.R
import io.github.arashiyama11.a_larm.domain.TtsGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    @Inject
    lateinit var ttsGateway: TtsGateway


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra("label").orEmpty()

        val fsIntent = Intent(this, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val fsPi = PendingIntent.getActivity(
            this, 10, fsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        ensureChannel() // CH_ID を新しく。例 "alarm.v3"
        val noti = NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(label.ifEmpty { "Alarm" })
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX) // <26 互換
            .setFullScreenIntent(fsPi, true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // 必要ならバイブ/サウンド既定を付けると発火しやすい端末もある
            //.setDefaults(Notification.DEFAULT_ALL)
            .build()

        // 5秒以内に必ず
        startForeground(NOTI_ID, noti)

        // 画面ON時は一部端末で full-screen が出ないため保険で直接起動
        val pm = getSystemService(PowerManager::class.java)
        val screenOn = pm.isInteractive
        if (screenOn) {
            startActivity(fsIntent)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(
            CH_ID, "Alarm", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    companion object {
        private const val CH_ID = "alarm.v0"
        private const val NOTI_ID = 42
    }
}