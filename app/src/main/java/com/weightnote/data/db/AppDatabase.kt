package com.weightnote.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        GroupEntity::class,
        GroupTimeRuleEntity::class,
        MetricEntity::class,
        RecordEntity::class,
        ReminderEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun groupDao(): GroupDao
    abstract fun metricDao(): MetricDao
    abstract fun recordDao(): RecordDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "weightnote.db").build()
    }
}
