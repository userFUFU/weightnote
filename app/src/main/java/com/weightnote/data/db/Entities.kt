package com.weightnote.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.weightnote.data.MeasureUnit

/** 身份：每个身份的数据完全独立 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val heightCm: Double? = null,
    /** MALE / FEMALE / null */
    val gender: String? = null,
    val birthYear: Int? = null,
    val weightUnit: String = MeasureUnit.KG.name,
    val lengthUnit: String = MeasureUnit.CM.name,
    val goalWeightKg: Double? = null,
    /** 是否按分组的时间段规则自动选择分组 */
    val autoGroupByTime: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val weightUnitEnum: MeasureUnit get() = MeasureUnit.of(weightUnit, MeasureUnit.KG)
    val lengthUnitEnum: MeasureUnit get() = MeasureUnit.of(lengthUnit, MeasureUnit.CM)
}

/** 分组：每条记录属于一个分组，每个分组在图上是一条曲线 */
@Entity(
    tableName = "record_groups",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    /** ARGB */
    val color: Int,
    /** 该分组的体重输入单位，null 表示跟随身份 */
    val weightUnit: String? = null,
    val sortOrder: Int = 0,
)

/** 分组的时间段规则（分钟，0..1439），start > end 表示跨午夜 */
@Entity(
    tableName = "group_time_rules",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class GroupTimeRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val startMinute: Int,
    val endMinute: Int,
)

/** 指标：体重、体脂率、各项围度 */
@Entity(
    tableName = "metrics",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class MetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    /** 内置指标的固定 key（见 MetricKeys），自定义指标为 custom_xxx */
    val key: String,
    val name: String,
    /** UnitType.name */
    val unitType: String,
    val builtIn: Boolean,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MetricEntity::class,
            parentColumns = ["id"],
            childColumns = ["metricId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("groupId"),
        Index("metricId"),
        Index(value = ["profileId", "metricId", "day"]),
    ],
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val groupId: Long,
    val metricId: Long,
    /** 换算到基础单位后的值（kg / cm / %） */
    val value: Double,
    /** 用户输入的原始值和单位，用于原样展示，避免来回换算的舍入误差 */
    val inputValue: Double,
    val inputUnit: String,
    val recordedAt: Long,
    /** 本地日期的 epochDay，用于“同一天同组”冲突判断 */
    val day: Long,
    val note: String? = null,
) {
    val inputUnitEnum: MeasureUnit get() = MeasureUnit.of(inputUnit, MeasureUnit.KG)
}

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val minuteOfDay: Int,
    val enabled: Boolean = true,
)

object MetricKeys {
    const val WEIGHT = "weight"
    const val BODY_FAT = "body_fat"
    const val WAIST = "waist"
    const val HIP = "hip"
    const val CHEST = "chest"
    const val THIGH = "thigh"
    const val ARM = "arm"

    /** BMI 不存储，由体重和身高实时计算，仅在图表中作为虚拟指标使用 */
    const val BMI = "bmi"
}
