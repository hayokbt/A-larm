package io.github.arashiyama11.a_larm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.Manifest

class PermissionManager(private val context: Context) {

    fun requiredRuntimePermissions(): Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        // POST_NOTIFICATIONS is runtime from API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun isMicGranted(): Boolean = isGranted(Manifest.permission.RECORD_AUDIO)

    fun isNotificationsGranted(): Boolean = isGranted(Manifest.permission.POST_NOTIFICATIONS)

    fun canScheduleExactAlarms(): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return am?.canScheduleExactAlarms() == true
    }

    fun openExactAlarmSettings() {
        // Best-effort: direct to exact alarm permission screen
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasAllRequiredPermissions(): Boolean =
        isMicGranted() && isNotificationsGranted()
}
