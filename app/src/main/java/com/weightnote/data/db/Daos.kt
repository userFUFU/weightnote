package com.weightnote.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY id")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Delete
    suspend fun delete(profile: ProfileEntity)

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM record_groups WHERE profileId = :profileId ORDER BY sortOrder, id")
    fun observeByProfile(profileId: Long): Flow<List<GroupEntity>>

    @Query("SELECT * FROM record_groups WHERE profileId = :profileId ORDER BY sortOrder, id")
    suspend fun getByProfile(profileId: Long): List<GroupEntity>

    @Query("SELECT * FROM record_groups WHERE id = :id")
    suspend fun get(id: Long): GroupEntity?

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Update
    suspend fun updateAll(groups: List<GroupEntity>)

    @Delete
    suspend fun delete(group: GroupEntity)

    // ---- 时间段规则 ----
    @Query(
        "SELECT r.* FROM group_time_rules r INNER JOIN record_groups g ON r.groupId = g.id " +
            "WHERE g.profileId = :profileId ORDER BY r.startMinute",
    )
    fun observeRules(profileId: Long): Flow<List<GroupTimeRuleEntity>>

    @Query("SELECT * FROM group_time_rules WHERE groupId = :groupId ORDER BY startMinute")
    suspend fun getRules(groupId: Long): List<GroupTimeRuleEntity>

    @Insert
    suspend fun insertRules(rules: List<GroupTimeRuleEntity>)

    @Query("DELETE FROM group_time_rules WHERE groupId = :groupId")
    suspend fun deleteRules(groupId: Long)

    // ---- 提醒 ----
    @Query(
        "SELECT m.* FROM reminders m INNER JOIN record_groups g ON m.groupId = g.id " +
            "WHERE g.profileId = :profileId ORDER BY m.minuteOfDay",
    )
    fun observeReminders(profileId: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE groupId = :groupId ORDER BY minuteOfDay")
    suspend fun getReminders(groupId: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders")
    suspend fun getAllReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminder(id: Long): ReminderEntity?

    @Insert
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Query("DELETE FROM reminders WHERE groupId = :groupId")
    suspend fun deleteReminders(groupId: Long)
}

@Dao
interface MetricDao {
    @Query("SELECT * FROM metrics WHERE profileId = :profileId ORDER BY sortOrder, id")
    fun observeByProfile(profileId: Long): Flow<List<MetricEntity>>

    @Query("SELECT * FROM metrics WHERE profileId = :profileId ORDER BY sortOrder, id")
    suspend fun getByProfile(profileId: Long): List<MetricEntity>

    @Query("SELECT * FROM metrics WHERE profileId = :profileId AND `key` = :key LIMIT 1")
    suspend fun getByKey(profileId: Long, key: String): MetricEntity?

    @Insert
    suspend fun insert(metric: MetricEntity): Long

    @Insert
    suspend fun insertAll(metrics: List<MetricEntity>)

    @Update
    suspend fun update(metric: MetricEntity)

    @Delete
    suspend fun delete(metric: MetricEntity)
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE profileId = :profileId ORDER BY recordedAt DESC, id DESC")
    fun observeByProfile(profileId: Long): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE profileId = :profileId ORDER BY recordedAt, id")
    suspend fun getByProfile(profileId: Long): List<RecordEntity>

    @Query("SELECT * FROM records WHERE groupId = :groupId")
    suspend fun getByGroup(groupId: Long): List<RecordEntity>

    @Query(
        "SELECT * FROM records WHERE profileId = :profileId AND groupId = :groupId " +
            "AND metricId = :metricId AND day = :day AND id != :excludeId ORDER BY recordedAt",
    )
    suspend fun findSameDay(
        profileId: Long,
        groupId: Long,
        metricId: Long,
        day: Long,
        excludeId: Long,
    ): List<RecordEntity>

    @Query(
        "SELECT * FROM records WHERE groupId = :groupId AND metricId = :metricId " +
            "ORDER BY recordedAt DESC, id DESC LIMIT 1",
    )
    suspend fun lastOf(groupId: Long, metricId: Long): RecordEntity?

    @Query(
        "SELECT * FROM records WHERE metricId = :metricId ORDER BY recordedAt DESC, id DESC LIMIT 1",
    )
    suspend fun lastOfMetric(metricId: Long): RecordEntity?

    @Query("SELECT COUNT(*) FROM records WHERE groupId = :groupId AND metricId = :metricId AND day = :day")
    suspend fun countOnDay(groupId: Long, metricId: Long, day: Long): Int

    @Query("SELECT COUNT(*) FROM records WHERE groupId = :groupId")
    suspend fun countByGroup(groupId: Long): Int

    @Query("SELECT COUNT(*) FROM records WHERE metricId = :metricId")
    suspend fun countByMetric(metricId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: RecordEntity): Long

    @Insert
    suspend fun insertAll(records: List<RecordEntity>)

    @Update
    suspend fun update(record: RecordEntity)

    @Update
    suspend fun updateAll(records: List<RecordEntity>)

    @Delete
    suspend fun delete(record: RecordEntity)

    @Query("DELETE FROM records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
