package com.weightnote.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 备份文件格式（JSON）。
 * 记录数量最多，所以记录字段使用短名以减小体积；数值只保存用户输入的原始值和单位，
 * 导入时再换算基础单位。
 */
@Serializable
data class BackupFile(
    val app: String = "WeightNote",
    val version: Int = 1,
    val exportedAt: Long,
    val profiles: List<ProfileBackup>,
)

@Serializable
data class ProfileBackup(
    val name: String,
    val heightCm: Double? = null,
    val gender: String? = null,
    val birthYear: Int? = null,
    val weightUnit: String,
    val lengthUnit: String,
    val goalWeightKg: Double? = null,
    val autoGroupByTime: Boolean = false,
    val createdAt: Long,
    val groups: List<GroupBackup>,
    val metrics: List<MetricBackup>,
    val records: List<RecordBackup>,
)

@Serializable
data class GroupBackup(
    /** 备份内部引用用的 id，导入时会重新分配 */
    val id: Long,
    val name: String,
    val color: Int,
    val weightUnit: String? = null,
    val sortOrder: Int = 0,
    val rules: List<RuleBackup> = emptyList(),
    val reminders: List<ReminderBackup> = emptyList(),
)

@Serializable
data class RuleBackup(val start: Int, val end: Int)

@Serializable
data class ReminderBackup(val minute: Int, val enabled: Boolean = true)

@Serializable
data class MetricBackup(
    val id: Long,
    val key: String,
    val name: String,
    val unitType: String,
    val builtIn: Boolean,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)

@Serializable
data class RecordBackup(
    @SerialName("g") val groupId: Long,
    @SerialName("m") val metricId: Long,
    @SerialName("v") val value: Double,
    @SerialName("u") val unit: String,
    @SerialName("t") val time: Long,
    @SerialName("n") val note: String? = null,
)
