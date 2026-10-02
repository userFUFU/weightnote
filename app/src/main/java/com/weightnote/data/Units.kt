package com.weightnote.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** 指标的单位类型。每种类型有一个“基础单位”，数据库中统一以基础单位保存：质量=kg，长度=cm，百分比=%。 */
enum class UnitType { MASS, LENGTH, PERCENT }

enum class MeasureUnit(
    val symbol: String,
    val label: String,
    val type: UnitType,
    /** 1 个该单位 = 多少个基础单位 */
    val factor: Double,
) {
    KG("kg", "公斤", UnitType.MASS, 1.0),
    JIN("斤", "斤", UnitType.MASS, 0.5),
    LB("lb", "磅", UnitType.MASS, 0.45359237),
    CM("cm", "厘米", UnitType.LENGTH, 1.0),
    INCH("in", "英寸", UnitType.LENGTH, 2.54),
    PERCENT("%", "百分比", UnitType.PERCENT, 1.0);

    fun toBase(value: Double): Double = value * factor
    fun fromBase(base: Double): Double = base / factor

    companion object {
        fun of(name: String?, fallback: MeasureUnit): MeasureUnit =
            entries.firstOrNull { it.name == name } ?: fallback

        fun forType(type: UnitType): List<MeasureUnit> = entries.filter { it.type == type }

        fun baseOf(type: UnitType): MeasureUnit = when (type) {
            UnitType.MASS -> KG
            UnitType.LENGTH -> CM
            UnitType.PERCENT -> PERCENT
        }
    }
}

fun unitTypeOf(name: String): UnitType = UnitType.entries.firstOrNull { it.name == name } ?: UnitType.MASS

/** 统一保留 1 位小数显示 */
fun formatNumber(value: Double, decimals: Int = 1): String {
    val scale = Math.pow(10.0, decimals.toDouble())
    val rounded = (value * scale).roundToLong() / scale
    return String.format(Locale.US, "%.${decimals}f", rounded)
}

/** 带正负号的变化量，如 +0.3 / -1.2 / 0.0 */
fun formatDelta(value: Double, decimals: Int = 1): String {
    val text = formatNumber(abs(value), decimals)
    return when {
        text.toDouble() == 0.0 -> text
        value > 0 -> "+$text"
        else -> "-$text"
    }
}

fun round1(value: Double): Double = (value * 10).roundToLong() / 10.0

// ---------- 日期工具 ----------

val zone: ZoneId get() = ZoneId.systemDefault()

/** 以本地时区计算的“天”编号（epochDay），用于判断“同一天” */
fun dayOf(epochMillis: Long): Long =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toEpochDay()

fun todayDay(): Long = LocalDate.now(zone).toEpochDay()

fun localDateTimeOf(epochMillis: Long): LocalDateTime =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()

fun epochMillisOf(dateTime: LocalDateTime): Long = dateTime.atZone(zone).toInstant().toEpochMilli()

fun minuteOfDay(epochMillis: Long): Int {
    val t = localDateTimeOf(epochMillis)
    return t.hour * 60 + t.minute
}

fun formatMinute(minute: Int): String = String.format(Locale.US, "%02d:%02d", minute / 60, minute % 60)

/** 时间段是否包含某分钟，支持跨午夜（start > end） */
fun minuteInRange(minute: Int, start: Int, end: Int): Boolean =
    if (start <= end) minute in start until end else minute >= start || minute < end
