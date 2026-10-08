package com.weightnote

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.weightnote.data.db.AppDatabase
import com.weightnote.data.epochMillisOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 数据库升级测试：用旧版本表结构写入数据，跑一遍升级，检查结构和数据都正确。
 * 每次改数据库版本都要在这里补一个用例，避免用户升级后 App 打不开。
 */
@RunWith(AndroidJUnit4::class)
// 用普通 Application，避免启动 WeightNoteApp 里的提醒调度等副作用
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private val dbName = "migration-test.db"
    private val oct2 = LocalDate.of(2026, 10, 2).toEpochDay()
    private val oct3 = oct2 + 1
    private val evening0012 = epochMillisOf(LocalDateTime.of(2026, 10, 3, 0, 12))
    private val morning0700 = epochMillisOf(LocalDateTime.of(2026, 10, 3, 7, 0))

    /** 按 v1 的表结构写入一个身份、早晚两个分组、体重指标和两条记录 */
    private fun createV1() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, name, heightCm, gender, birthYear, weightUnit, lengthUnit, goalWeightKg, autoGroupByTime, createdAt) " +
                    "VALUES (1, '我', 170.0, NULL, NULL, 'KG', 'CM', 60.0, 0, 0)",
            )
            db.execSQL("INSERT INTO record_groups (id, profileId, name, color, weightUnit, sortOrder) VALUES (1, 1, '早晨', -1, NULL, 0)")
            db.execSQL("INSERT INTO record_groups (id, profileId, name, color, weightUnit, sortOrder) VALUES (2, 1, '晚上', -1, NULL, 1)")
            db.execSQL("INSERT INTO group_time_rules (id, groupId, startMinute, endMinute) VALUES (1, 1, 240, 660)")
            db.execSQL("INSERT INTO group_time_rules (id, groupId, startMinute, endMinute) VALUES (2, 2, 1080, 120)")
            db.execSQL(
                "INSERT INTO metrics (id, profileId, `key`, name, unitType, builtIn, enabled, sortOrder) " +
                    "VALUES (1, 1, 'weight', '体重', 'MASS', 1, 1, 0)",
            )
            // v1 时按日历日期保存：00:12 的晚上记录被算成 10/3
            db.execSQL(
                "INSERT INTO records (id, profileId, groupId, metricId, value, inputValue, inputUnit, recordedAt, day, note) " +
                    "VALUES (1, 1, 2, 1, 63.4, 63.4, 'KG', $evening0012, $oct3, NULL)",
            )
            db.execSQL(
                "INSERT INTO records (id, profileId, groupId, metricId, value, inputValue, inputUnit, recordedAt, day, note) " +
                    "VALUES (2, 1, 1, 1, 62.5, 62.5, 'KG', $morning0700, $oct3, '空腹')",
            )
        }
    }

    @Test
    fun migrate1To2_enablesAutoGroupAndFixesCrossMidnightDay() {
        createV1()
        val db = helper.runMigrationsAndValidate(dbName, 2, true, AppDatabase.MIGRATION_1_2)

        db.query("SELECT autoGroupByTime FROM profiles WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        db.query("SELECT id, day FROM records ORDER BY id").use { c ->
            c.moveToNext()
            assertEquals("00:12 的晚上记录应归属前一天", oct2, c.getLong(1))
            c.moveToNext()
            assertEquals("早晨记录日期不变", oct3, c.getLong(1))
        }
        db.close()
    }

    @Test
    fun migrate2To3_addsTrashTable() {
        createV1()
        helper.runMigrationsAndValidate(dbName, 2, true, AppDatabase.MIGRATION_1_2).close()
        val db = helper.runMigrationsAndValidate(dbName, 3, true, AppDatabase.MIGRATION_2_3)

        db.execSQL(
            "INSERT INTO trash_records (profileId, groupId, groupName, groupColor, metricId, metricKey, metricName, " +
                "metricUnitType, metricBuiltIn, value, inputValue, inputUnit, recordedAt, day, note, deletedAt, reason) " +
                "VALUES (1, 2, '晚上', -1, 1, 'weight', '体重', 'MASS', 1, 63.0, 63.0, 'KG', 0, 0, NULL, 0, '手动删除')",
        )
        db.query("SELECT COUNT(*) FROM trash_records").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        // 原有记录不受影响
        db.query("SELECT COUNT(*) FROM records").use { c ->
            c.moveToFirst()
            assertEquals(2, c.getInt(0))
        }
        db.close()
    }

    /** 从 v1 一路升级后，用正式的 Room 配置打开，确认 App 能正常读到数据 */
    @Test
    fun migrateAll_thenOpenWithRoom() = runBlocking {
        createV1()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val profiles = room.profileDao().getAll()
            assertEquals(1, profiles.size)
            assertTrue(profiles[0].autoGroupByTime)
            val records = room.recordDao().getByProfile(1)
            assertEquals(2, records.size)
            assertEquals("空腹", records.first { it.id == 2L }.note)
            assertEquals(0, room.trashDao().countAll())
        } finally {
            room.close()
        }
    }
}
