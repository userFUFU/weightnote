package com.weightnote.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.weightnote.WeightNoteApp
import com.weightnote.data.BackupSettings
import java.util.concurrent.TimeUnit

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as WeightNoteApp).container
        return if (container.backupManager.autoBackup().isSuccess) Result.success() else Result.failure()
    }
}

object BackupScheduler {
    private const val WORK_NAME = "auto_backup"

    fun apply(context: Context, settings: BackupSettings) {
        val wm = WorkManager.getInstance(context)
        if (!settings.autoEnabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(settings.frequency.days, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
