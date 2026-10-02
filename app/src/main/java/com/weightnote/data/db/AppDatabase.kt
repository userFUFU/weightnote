package com.weightnote.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.weightnote.domain.logicalDay

@Database(
    entities = [
        ProfileEntity::class,
        GroupEntity::class,
        GroupTimeRuleEntity::class,
        MetricEntity::class,
        RecordEntity::class,
        ReminderEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun groupDao(): GroupDao
    abstract fun metricDao(): MetricDao
    abstract fun recordDao(): RecordDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "weightnote.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /**
         * v1 → v2（表结构不变）：
         * 1. 按时间自动归组改为默认开启，已有身份一并开启
         * 2. 跨午夜时间段（如 18:00–02:00）中午夜之后的记录，归属日期改为前一天
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE profiles SET autoGroupByTime = 1")

                val rulesByGroup = HashMap<Long, MutableList<GroupTimeRuleEntity>>()
                db.query("SELECT id, groupId, startMinute, endMinute FROM group_time_rules").use { c ->
                    while (c.moveToNext()) {
                        val rule = GroupTimeRuleEntity(c.getLong(0), c.getLong(1), c.getInt(2), c.getInt(3))
                        rulesByGroup.getOrPut(rule.groupId) { mutableListOf() } += rule
                    }
                }
                val crossMidnightGroups = rulesByGroup.filterValues { rules -> rules.any { it.startMinute > it.endMinute } }
                crossMidnightGroups.forEach { (groupId, rules) ->
                    val updates = mutableListOf<Pair<Long, Long>>()
                    db.query("SELECT id, recordedAt, day FROM records WHERE groupId = ?", arrayOf<Any>(groupId)).use { c ->
                        while (c.moveToNext()) {
                            val newDay = logicalDay(c.getLong(1), rules)
                            if (newDay != c.getLong(2)) updates += c.getLong(0) to newDay
                        }
                    }
                    updates.forEach { (id, day) ->
                        db.execSQL("UPDATE records SET day = ? WHERE id = ?", arrayOf<Any>(day, id))
                    }
                }
            }
        }
    }
}
