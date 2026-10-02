package com.weightnote.domain

import com.weightnote.data.TimeRange
import com.weightnote.data.db.GroupEntity
import com.weightnote.data.db.GroupTimeRuleEntity
import com.weightnote.data.db.RecordEntity
import com.weightnote.data.minuteInRange
import com.weightnote.data.minuteOfDay

data class ChartPoint(val day: Long, val value: Double)

private val latestFirst = compareBy<RecordEntity>({ it.recordedAt }, { it.id })

/** 同一天、同分组、同指标有多条时，只保留最新的一条（图表和统计都用这个规则） */
fun latestPerDay(records: List<RecordEntity>): List<RecordEntity> =
    records.groupBy { Triple(it.groupId, it.metricId, it.day) }
        .values
        .map { list -> list.maxWith(latestFirst) }

/** 被“同一天同组取最新”规则忽略掉的记录 id，用于在列表中灰显 */
fun ignoredRecordIds(records: List<RecordEntity>): Set<Long> =
    records.groupBy { Triple(it.groupId, it.metricId, it.day) }
        .values
        .filter { it.size > 1 }
        .flatMap { list ->
            val keep = list.maxWith(latestFirst)
            list.filter { it.id != keep.id }.map { it.id }
        }
        .toSet()

/** 按天的移动平均：每个点取 (day - window, day] 内所有点的平均值 */
fun movingAverage(points: List<ChartPoint>, windowDays: Int = 7): List<ChartPoint> {
    if (points.isEmpty()) return emptyList()
    val sorted = points.sortedBy { it.day }
    val result = ArrayList<ChartPoint>(sorted.size)
    var startIndex = 0
    var sum = 0.0
    sorted.forEachIndexed { i, p ->
        sum += p.value
        while (sorted[startIndex].day <= p.day - windowDays) {
            sum -= sorted[startIndex].value
            startIndex++
        }
        result += ChartPoint(p.day, sum / (i - startIndex + 1))
    }
    return result
}

/** 差值曲线：两组都有数据的日期上，计算 b - a */
fun difference(a: List<ChartPoint>, b: List<ChartPoint>): List<ChartPoint> {
    val aMap = a.associate { it.day to it.value }
    return b.mapNotNull { p -> aMap[p.day]?.let { ChartPoint(p.day, p.value - it) } }.sortedBy { it.day }
}

data class SeriesStats(
    val first: ChartPoint,
    val last: ChartPoint,
    val min: ChartPoint,
    val max: ChartPoint,
    val average: Double,
    val count: Int,
) {
    val change: Double get() = last.value - first.value
}

fun statsOf(points: List<ChartPoint>): SeriesStats? {
    if (points.isEmpty()) return null
    val sorted = points.sortedBy { it.day }
    return SeriesStats(
        first = sorted.first(),
        last = sorted.last(),
        min = sorted.minBy { it.value },
        max = sorted.maxBy { it.value },
        average = sorted.sumOf { it.value } / sorted.size,
        count = sorted.size,
    )
}

// ---------------- BMI ----------------

fun bmiOf(weightKg: Double, heightCm: Double?): Double? {
    if (heightCm == null || heightCm <= 0) return null
    val m = heightCm / 100.0
    return weightKg / (m * m)
}

/** 中国成人 BMI 标准 */
fun bmiCategory(bmi: Double): String = when {
    bmi < 18.5 -> "偏瘦"
    bmi < 24.0 -> "正常"
    bmi < 28.0 -> "超重"
    else -> "肥胖"
}

// ---------------- 分组时间规则 ----------------

/** 按时间段规则推荐分组：返回排序最靠前、且规则包含该时间的分组 */
fun suggestGroup(
    groups: List<GroupEntity>,
    rules: List<GroupTimeRuleEntity>,
    epochMillis: Long,
): GroupEntity? {
    val minute = minuteOfDay(epochMillis)
    return groups.firstOrNull { g ->
        rules.any { it.groupId == g.id && minuteInRange(minute, it.startMinute, it.endMinute) }
    }
}

fun rangesOverlap(a: TimeRange, b: TimeRange): Boolean =
    (0 until 24 * 60).any { minuteInRange(it, a.start, a.end) && minuteInRange(it, b.start, b.end) }
