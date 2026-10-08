package com.weightnote.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.weightnote.MainActivity
import com.weightnote.R
import com.weightnote.WeightNoteApp
import com.weightnote.data.db.AppDatabase
import com.weightnote.data.db.MetricKeys
import com.weightnote.data.db.ReminderEntity
import com.weightnote.data.epochMillisOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** 用 AlarmManager 为每个提醒安排“下一次”触发；触发后再安排下一天 */
class ReminderScheduler(private val context: Context, private val db: AppDatabase) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    suspend fun rescheduleAll() = withContext(Dispatchers.IO) {
        val reminders = db.groupDao().getAllReminders().filter { it.enabled }
        val newIds = reminders.map { it.id }.toSet()
        val oldIds = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
        oldIds.filter { it !in newIds }.forEach { cancel(it) }
        reminders.forEach { schedule(it) }
        prefs.edit().putStringSet(KEY_SCHEDULED, newIds.map { it.toString() }.toSet()).apply()
    }

    fun schedule(reminder: ReminderEntity) {
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(reminder.minuteOfDay / 60, reminder.minuteOfDay % 60)
        if (!at.isAfter(now)) at = at.plusDays(1)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillisOf(at), pendingIntent(reminder.id, snooze = false))
    }

    /** 稍后再提醒一次（不影响每天的固定提醒） */
    fun snooze(reminderId: Long, minutes: Int = SNOOZE_MINUTES) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(reminderId, snooze = true))
    }

    private fun cancel(id: Long) {
        alarmManager.cancel(pendingIntent(id, snooze = false))
        alarmManager.cancel(pendingIntent(id, snooze = true))
    }

    private fun pendingIntent(id: Long, snooze: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context,
        (if (snooze) id + SNOOZE_REQUEST_OFFSET else id).toInt(),
        Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_REMINDER_ID, id)
            .putExtra(EXTRA_SNOOZE, snooze),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val KEY_SCHEDULED = "scheduled_ids"
        private const val SNOOZE_REQUEST_OFFSET = 1_000_000L
        const val SNOOZE_MINUTES = 30
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_SNOOZE = "snooze"
    }
}

object Notifications {
    const val CHANNEL_REMINDER = "reminder"
    const val EXTRA_OPEN_ENTRY = "open_entry"
    const val EXTRA_PROFILE_ID = "open_profile_id"
    const val EXTRA_GROUP_ID = "open_group_id"
    const val EXTRA_NOTIFY_ID = "notify_id"
    const val ACTION_SNOOZE = "com.weightnote.action.SNOOZE"

    fun createChannels(context: Context) {
        val channel = NotificationChannel(CHANNEL_REMINDER, "记录提醒", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "按分组设置的称重提醒" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 打开 App 并弹出记录面板的 Intent（通知、桌面小组件共用）。
     * groupId 为空时按时间规则自动选分组。每个 Intent 带不同的 data，避免 PendingIntent 被系统合并。
     */
    fun entryIntent(context: Context, profileId: Long?, groupId: Long?): Intent =
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("weightnote://entry/${profileId ?: 0}/${groupId ?: 0}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_ENTRY, true)
            .apply {
                profileId?.let { putExtra(EXTRA_PROFILE_ID, it) }
                groupId?.let { putExtra(EXTRA_GROUP_ID, it) }
            }

    fun showReminder(
        context: Context,
        title: String,
        text: String,
        profileId: Long,
        groupId: Long,
        reminderId: Long,
        notifyId: Int,
    ) {
        if (!canPost(context)) return
        val content = PendingIntent.getActivity(
            context,
            notifyId,
            entryIntent(context, profileId, groupId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getBroadcast(
            context,
            notifyId,
            Intent(context, ReminderActionReceiver::class.java)
                .setAction(ACTION_SNOOZE)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_NOTIFY_ID, notifyId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(content)
            .addAction(0, "去记录", content)
            .addAction(0, "${ReminderScheduler.SNOOZE_MINUTES} 分钟后提醒", snooze)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notifyId, notification)
        } catch (_: SecurityException) {
            // 权限在检查后被撤销，忽略
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1)
        if (id < 0) return
        val isSnooze = intent.getBooleanExtra(ReminderScheduler.EXTRA_SNOOZE, false)
        val container = (context.applicationContext as WeightNoteApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = container.database
                val reminder = db.groupDao().getReminder(id) ?: return@launch
                if (!reminder.enabled) return@launch
                // 每天的固定提醒：先安排下一次，避免后续出错导致提醒中断；稍后提醒只响一次
                if (!isSnooze) container.reminderScheduler.schedule(reminder)
                val group = db.groupDao().get(reminder.groupId) ?: return@launch
                val profile = db.profileDao().get(group.profileId) ?: return@launch
                val weight = db.metricDao().getByKey(profile.id, MetricKeys.WEIGHT) ?: return@launch
                val recorded = container.repository.hasRecordToday(group.id, weight.id)
                if (!recorded) {
                    val multiProfile = db.profileDao().getAll().size > 1
                    val who = if (multiProfile) "${profile.name} · " else ""
                    Notifications.showReminder(
                        context,
                        title = "该称体重啦",
                        text = "${who}「${group.name}」今天还没有记录",
                        profileId = profile.id,
                        groupId = group.id,
                        reminderId = reminder.id,
                        notifyId = group.id.toInt(),
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** 通知上的按钮：稍后提醒 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Notifications.ACTION_SNOOZE) return
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1)
        val notifyId = intent.getIntExtra(Notifications.EXTRA_NOTIFY_ID, -1)
        if (notifyId >= 0) NotificationManagerCompat.from(context).cancel(notifyId)
        if (reminderId < 0) return
        (context.applicationContext as WeightNoteApp).container.reminderScheduler.snooze(reminderId)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as WeightNoteApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.reminderScheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
