package com.weightnote.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }

enum class BackupFrequency(val label: String, val days: Long) {
    OFF("关闭", 0),
    DAILY("每天", 1),
    WEEKLY("每周", 7),
}

data class BackupSettings(
    val folderUri: String? = null,
    val frequency: BackupFrequency = BackupFrequency.WEEKLY,
    val keepCount: Int = 5,
    val lastBackupAt: Long? = null,
    val lastBackupResult: String? = null,
) {
    val autoEnabled: Boolean get() = folderUri != null && frequency != BackupFrequency.OFF
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val CURRENT_PROFILE = longPreferencesKey("current_profile")
        val THEME = stringPreferencesKey("theme")
        val BACKUP_FOLDER = stringPreferencesKey("backup_folder")
        val BACKUP_FREQUENCY = stringPreferencesKey("backup_frequency")
        val BACKUP_KEEP = intPreferencesKey("backup_keep")
        val BACKUP_LAST_AT = longPreferencesKey("backup_last_at")
        val BACKUP_LAST_RESULT = stringPreferencesKey("backup_last_result")
    }

    val currentProfileId: Flow<Long?> = context.dataStore.data.map { it[Keys.CURRENT_PROFILE] }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME] } ?: ThemeMode.SYSTEM
    }

    val backup: Flow<BackupSettings> = context.dataStore.data.map { prefs ->
        BackupSettings(
            folderUri = prefs[Keys.BACKUP_FOLDER],
            frequency = BackupFrequency.entries.firstOrNull { it.name == prefs[Keys.BACKUP_FREQUENCY] }
                ?: BackupFrequency.WEEKLY,
            keepCount = prefs[Keys.BACKUP_KEEP] ?: 5,
            lastBackupAt = prefs[Keys.BACKUP_LAST_AT],
            lastBackupResult = prefs[Keys.BACKUP_LAST_RESULT],
        )
    }

    suspend fun backupNow(): BackupSettings = backup.first()

    suspend fun setCurrentProfile(id: Long?) {
        context.dataStore.edit {
            if (id == null) it.remove(Keys.CURRENT_PROFILE) else it[Keys.CURRENT_PROFILE] = id
        }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setBackupFolder(uri: String?) {
        context.dataStore.edit {
            if (uri == null) it.remove(Keys.BACKUP_FOLDER) else it[Keys.BACKUP_FOLDER] = uri
        }
    }

    suspend fun setBackupFrequency(frequency: BackupFrequency) {
        context.dataStore.edit { it[Keys.BACKUP_FREQUENCY] = frequency.name }
    }

    suspend fun setBackupKeep(count: Int) {
        context.dataStore.edit { it[Keys.BACKUP_KEEP] = count }
    }

    suspend fun setBackupResult(at: Long, result: String) {
        context.dataStore.edit {
            it[Keys.BACKUP_LAST_AT] = at
            it[Keys.BACKUP_LAST_RESULT] = result
        }
    }
}
