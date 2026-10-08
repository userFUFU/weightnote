package com.weightnote.domain

import kotlin.math.abs
import kotlin.math.ceil

/** 最小二乘直线拟合：返回 (每天变化量, 截距)，横轴为 epochDay */
fun linearFit(points: List<ChartPoint>): Pair<Double, Double>? {
    if (points.size < 2) return null
    val n = points.size.toDouble()
    // 以第一个点为原点，避免 epochDay 太大带来的精度问题
    val x0 = points.first().day
    val xs = points.map { (it.day - x0).toDouble() }
    val ys = points.map { it.value }
    val mx = xs.sum() / n
    val my = ys.sum() / n
    var sxx = 0.0
    var sxy = 0.0
    for (i in xs.indices) {
        sxx += (xs[i] - mx) * (xs[i] - mx)
        sxy += (xs[i] - mx) * (ys[i] - my)
    }
    if (sxx == 0.0) return null
    val slope = sxy / sxx
    val intercept = my - slope * mx - slope * x0
    return slope to intercept
}

data class GoalForecast(
    /** 预计达到目标的日期（epochDay），无法预测时为 null */
    val day: Long?,
    /** 趋势：每周变化量（与输入同单位） */
    val perWeek: Double,
    /** 无法预测时的原因 */
    val reason: String? = null,
    val reached: Boolean = false,
)

/**
 * 按最近一段时间的趋势预测达到目标的日期。
 * 需要至少 5 个点、跨度至少 7 天，否则返回 null（数据太少，不预测）。
 */
fun forecastGoal(points: List<ChartPoint>, goal: Double, today: Long): GoalForecast? {
    val sorted = points.sortedBy { it.day }
    if (sorted.size < 5 || sorted.last().day - sorted.first().day < 7) return null
    val (slope, intercept) = linearFit(sorted) ?: return null
    val fittedToday = intercept + slope * today
    val remaining = goal - fittedToday
    val perWeek = slope * 7
    if (abs(remaining) < 0.05) return GoalForecast(today, perWeek, reached = true)
    if (abs(slope) < 1e-4 || (remaining > 0) != (slope > 0)) {
        return GoalForecast(null, perWeek, "最近的趋势没有朝目标方向变化")
    }
    val days = remaining / slope
    if (days > 730) return GoalForecast(null, perWeek, "按目前的速度需要两年以上")
    return GoalForecast(today + ceil(days).toLong(), perWeek)
}

/** 某个区间内的平均值，点数不足时返回 null */
fun averageIn(points: List<ChartPoint>, fromDay: Long, toDay: Long, minCount: Int = 3): Double? {
    val inRange = points.filter { it.day in fromDay..toDay }
    if (inRange.size < minCount) return null
    return inRange.sumOf { it.value } / inRange.size
}
