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
        TrashRecordEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun groupDao(): GroupDao
    abstract fun metricDao(): MetricDao
    abstract fun recordDao(): RecordDao
    abstract fun trashDao(): TrashDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "weightnote.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** v2 → v3：新增回收站表 */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trash_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`profileId` INTEGER NOT NULL, " +
                        "`groupId` INTEGER NOT NULL, " +
                        "`groupName` TEXT NOT NULL, " +
                        "`groupColor` INTEGER NOT NULL, " +
                        "`metricId` INTEGER NOT NULL, " +
                        "`metricKey` TEXT NOT NULL, " +
                        "`metricName` TEXT NOT NULL, " +
                        "`metricUnitType` TEXT NOT NULL, " +
                        "`metricBuiltIn` INTEGER NOT NULL, " +
                        "`value` REAL NOT NULL, " +
                        "`inputValue` REAL NOT NULL, " +
                        "`inputUnit` TEXT NOT NULL, " +
                        "`recordedAt` INTEGER NOT NULL, " +
                        "`day` INTEGER NOT NULL, " +
                        "`note` TEXT, " +
                        "`deletedAt` INTEGER NOT NULL, " +
                        "`reason` TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trash_records_profileId` ON `trash_records` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trash_records_deletedAt` ON `trash_records` (`deletedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trash_records_groupId` ON `trash_records` (`groupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trash_records_metricId` ON `trash_records` (`metricId`)")
            }
        }

        /**
         * v1 → v2（表结构不变）：
         * 1. 按时间自动归组改为默认开启，已有身份一并开启
         * 2. 跨午夜时间段（如 18:00–02:00）中午夜之后的记录，归属日期改为前一天
         */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
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
