package com.weightnote

import android.app.Application
import android.content.Context
import com.weightnote.data.Repository
import com.weightnote.data.SettingsStore
import com.weightnote.data.backup.BackupManager
import com.weightnote.data.db.AppDatabase
import com.weightnote.reminder.Notifications
import com.weightnote.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 简单的手动依赖注入容器 */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)
    val settings = SettingsStore(context)
    val reminderScheduler = ReminderScheduler(context, database)
    val repository = Repository(database) { reminderScheduler.rescheduleAll() }
    val backupManager = BackupManager(context, database, settings) { reminderScheduler.rescheduleAll() }
}

class WeightNoteApp : Application() {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        appScope.launch { container.reminderScheduler.rescheduleAll() }
    }
}
