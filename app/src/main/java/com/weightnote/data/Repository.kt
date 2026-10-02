package com.weightnote.data

import androidx.room.withTransaction
import com.weightnote.data.db.AppDatabase
import com.weightnote.data.db.GroupEntity
import com.weightnote.data.db.GroupTimeRuleEntity
import com.weightnote.data.db.MetricEntity
import com.weightnote.data.db.MetricKeys
import com.weightnote.data.db.ProfileEntity
import com.weightnote.data.db.RecordEntity
import com.weightnote.data.db.ReminderEntity
import com.weightnote.domain.logicalDay
import kotlinx.coroutines.flow.Flow

/** 一条待保存的记录（新建或编辑） */
data class RecordDraft(
    /** 0 表示新建，否则为被编辑记录的 id */
    val editingId: Long = 0,
    val groupId: Long,
    val metricId: Long,
    val inputValue: Double,
    val inputUnit: MeasureUnit,
    val recordedAt: Long,
    val note: String? = null,
)

/** 分组的时间段（分钟） */
data class TimeRange(val start: Int, val end: Int)

/** 预设分组配色 */
val GroupPalette: List<Int> = listOf(
    0xFFF28C38.toInt(), // 橙
    0xFF5C6BC0.toInt(), // 靛蓝
    0xFF26A69A.toInt(), // 青绿
    0xFFEC407A.toInt(), // 粉
    0xFF8D6E63.toInt(), // 棕
    0xFF42A5F5.toInt(), // 蓝
    0xFF9CCC65.toInt(), // 草绿
    0xFFAB47BC.toInt(), // 紫
    0xFFFFCA28.toInt(), // 黄
    0xFF78909C.toInt(), // 灰蓝
)

class Repository(
    private val db: AppDatabase,
    /** 提醒相关数据变化后回调，用于重新安排闹钟 */
    private val onRemindersChanged: suspend () -> Unit,
) {
    private val profileDao = db.profileDao()
    private val groupDao = db.groupDao()
    private val metricDao = db.metricDao()
    private val recordDao = db.recordDao()

    // ---------------- 观察 ----------------

    fun observeProfiles(): Flow<List<ProfileEntity>> = profileDao.observeAll()
    fun observeGroups(profileId: Long): Flow<List<GroupEntity>> = groupDao.observeByProfile(profileId)
    fun observeRules(profileId: Long): Flow<List<GroupTimeRuleEntity>> = groupDao.observeRules(profileId)
    fun observeReminders(profileId: Long): Flow<List<ReminderEntity>> = groupDao.observeReminders(profileId)
    fun observeMetrics(profileId: Long): Flow<List<MetricEntity>> = metricDao.observeByProfile(profileId)
    fun observeRecords(profileId: Long): Flow<List<RecordEntity>> = recordDao.observeByProfile(profileId)

    // ---------------- 身份 ----------------

    /** 创建身份，同时预设“早晨/晚上”分组（时间规则默认关闭）和内置指标 */
    suspend fun createProfile(profile: ProfileEntity): Long = db.withTransaction {
        val id = profileDao.insert(profile.copy(id = 0))
        val morning = groupDao.insert(
            GroupEntity(profileId = id, name = "早晨", color = GroupPalette[0], sortOrder = 0),
        )
        val evening = groupDao.insert(
            GroupEntity(profileId = id, name = "晚上", color = GroupPalette[1], sortOrder = 1),
        )
        groupDao.insertRules(
            listOf(
                GroupTimeRuleEntity(groupId = morning, startMinute = 4 * 60, endMinute = 11 * 60),
                GroupTimeRuleEntity(groupId = evening, startMinute = 18 * 60, endMinute = 2 * 60),
            ),
        )
        metricDao.insertAll(defaultMetrics(id))
        id
    }

    private fun defaultMetrics(profileId: Long): List<MetricEntity> {
        fun m(key: String, name: String, type: UnitType, order: Int) = MetricEntity(
            profileId = profileId, key = key, name = name, unitType = type.name,
            builtIn = true, enabled = true, sortOrder = order,
        )
        return listOf(
            m(MetricKeys.WEIGHT, "体重", UnitType.MASS, 0),
            m(MetricKeys.BODY_FAT, "体脂率", UnitType.PERCENT, 1),
            m(MetricKeys.WAIST, "腰围", UnitType.LENGTH, 2),
            m(MetricKeys.HIP, "臀围", UnitType.LENGTH, 3),
            m(MetricKeys.CHEST, "胸围", UnitType.LENGTH, 4),
            m(MetricKeys.THIGH, "大腿围", UnitType.LENGTH, 5),
            m(MetricKeys.ARM, "上臂围", UnitType.LENGTH, 6),
        )
    }

    suspend fun updateProfile(profile: ProfileEntity) = profileDao.update(profile)

    suspend fun deleteProfile(profile: ProfileEntity) {
        profileDao.delete(profile)
        onRemindersChanged()
    }

    // ---------------- 分组 ----------------

    suspend fun saveGroup(
        group: GroupEntity,
        rules: List<TimeRange>,
        reminders: List<ReminderEntity>,
    ): Long {
        val id = db.withTransaction {
            val groupId = if (group.id == 0L) {
                val order = (groupDao.getByProfile(group.profileId).maxOfOrNull { it.sortOrder } ?: -1) + 1
                groupDao.insert(group.copy(sortOrder = order))
            } else {
                groupDao.update(group)
                group.id
            }
            groupDao.deleteRules(groupId)
            groupDao.insertRules(
                rules.map { GroupTimeRuleEntity(groupId = groupId, startMinute = it.start, endMinute = it.end) },
            )
            groupDao.deleteReminders(groupId)
            groupDao.insertReminders(reminders.map { it.copy(id = 0, groupId = groupId) })
            // 时间段变化可能影响跨午夜记录的归属日期，重新计算
            val newRules = groupDao.getRules(groupId)
            val changed = recordDao.getByGroup(groupId).mapNotNull { r ->
                val day = logicalDay(r.recordedAt, newRules)
                if (day != r.day) r.copy(day = day) else null
            }
            if (changed.isNotEmpty()) recordDao.updateAll(changed)
            groupId
        }
        onRemindersChanged()
        return id
    }

    suspend fun deleteGroup(group: GroupEntity) {
        groupDao.delete(group)
        onRemindersChanged()
    }

    /** 调整分组顺序，delta = -1 上移，+1 下移 */
    suspend fun moveGroup(profileId: Long, groupId: Long, delta: Int) = db.withTransaction {
        val list = groupDao.getByProfile(profileId).toMutableList()
        val index = list.indexOfFirst { it.id == groupId }
        val target = index + delta
        if (index < 0 || target !in list.indices) return@withTransaction
        val item = list.removeAt(index)
        list.add(target, item)
        groupDao.updateAll(list.mapIndexed { i, g -> g.copy(sortOrder = i) })
    }

    suspend fun getRules(groupId: Long) = groupDao.getRules(groupId)
    suspend fun getReminders(groupId: Long) = groupDao.getReminders(groupId)
    suspend fun countRecordsOfGroup(groupId: Long) = recordDao.countByGroup(groupId)

    // ---------------- 指标 ----------------

    suspend fun updateMetric(metric: MetricEntity) = metricDao.update(metric)

    suspend fun addCustomMetric(profileId: Long, name: String, type: UnitType = UnitType.LENGTH) {
        val order = (metricDao.getByProfile(profileId).maxOfOrNull { it.sortOrder } ?: -1) + 1
        metricDao.insert(
            MetricEntity(
                profileId = profileId,
                key = "custom_${System.currentTimeMillis()}",
                name = name,
                unitType = type.name,
                builtIn = false,
                enabled = true,
                sortOrder = order,
            ),
        )
    }

    suspend fun deleteMetric(metric: MetricEntity) {
        if (!metric.builtIn) metricDao.delete(metric)
    }

    suspend fun countRecordsOfMetric(metricId: Long) = recordDao.countByMetric(metricId)

    // ---------------- 记录 ----------------

    suspend fun lastRecord(groupId: Long, metricId: Long): RecordEntity? = recordDao.lastOf(groupId, metricId)

    suspend fun lastRecordOfMetric(metricId: Long): RecordEntity? = recordDao.lastOfMetric(metricId)

    /** 记录归属的逻辑日期（考虑分组的跨午夜时间段） */
    private suspend fun dayFor(groupId: Long, epochMillis: Long): Long =
        logicalDay(epochMillis, groupDao.getRules(groupId))

    /** 查找与草稿“同一天、同分组、同指标”的已有记录 */
    suspend fun findConflicts(profileId: Long, drafts: List<RecordDraft>): List<RecordEntity> =
        drafts.flatMap {
            recordDao.findSameDay(profileId, it.groupId, it.metricId, dayFor(it.groupId, it.recordedAt), it.editingId)
        }.distinctBy { it.id }

    /**
     * 保存草稿。
     * @param overwrite true 时删除同一天同组同指标的旧记录（默认行为）；false 时保留两条，图表取最新
     */
    suspend fun saveDrafts(profileId: Long, drafts: List<RecordDraft>, overwrite: Boolean) =
        db.withTransaction {
            if (overwrite) {
                val conflicts = findConflicts(profileId, drafts)
                if (conflicts.isNotEmpty()) recordDao.deleteByIds(conflicts.map { it.id })
            }
            drafts.forEach { d ->
                val entity = RecordEntity(
                    id = d.editingId,
                    profileId = profileId,
                    groupId = d.groupId,
                    metricId = d.metricId,
                    value = d.inputUnit.toBase(d.inputValue),
                    inputValue = d.inputValue,
                    inputUnit = d.inputUnit.name,
                    recordedAt = d.recordedAt,
                    day = dayFor(d.groupId, d.recordedAt),
                    note = d.note?.trim()?.takeIf { it.isNotEmpty() },
                )
                if (d.editingId == 0L) recordDao.insert(entity) else recordDao.update(entity)
            }
        }

    suspend fun deleteRecord(record: RecordEntity) = recordDao.delete(record)

    /** 撤销删除：按原 id 重新插入 */
    suspend fun restoreRecord(record: RecordEntity) {
        recordDao.insert(record)
    }

    suspend fun hasRecordToday(groupId: Long, metricId: Long): Boolean =
        recordDao.countOnDay(groupId, metricId, dayFor(groupId, System.currentTimeMillis())) > 0
}
