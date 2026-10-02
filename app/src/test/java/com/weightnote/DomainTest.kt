package com.weightnote

import com.weightnote.data.MeasureUnit
import com.weightnote.data.db.GroupEntity
import com.weightnote.data.db.GroupTimeRuleEntity
import com.weightnote.data.epochMillisOf
import com.weightnote.domain.logicalDay
import com.weightnote.domain.suggestGroup
import java.time.LocalDate
import java.time.LocalDateTime
import com.weightnote.data.formatDelta
import com.weightnote.data.formatNumber
import com.weightnote.data.minuteInRange
import com.weightnote.data.db.RecordEntity
import com.weightnote.domain.ChartPoint
import com.weightnote.domain.difference
import com.weightnote.domain.ignoredRecordIds
import com.weightnote.domain.latestPerDay
import com.weightnote.domain.movingAverage
import com.weightnote.domain.rangesOverlap
import com.weightnote.data.TimeRange
import com.weightnote.ui.record.KeypadKey
import com.weightnote.ui.record.applyKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {

    private fun rec(id: Long, group: Long, day: Long, at: Long, value: Double) = RecordEntity(
        id = id, profileId = 1, groupId = group, metricId = 1, value = value,
        inputValue = value, inputUnit = "KG", recordedAt = at, day = day,
    )

    @Test
    fun unitConversion_roundTrips() {
        assertEquals(62.3, MeasureUnit.JIN.toBase(124.6), 1e-9)
        assertEquals("150.2", formatNumber(MeasureUnit.LB.fromBase(MeasureUnit.LB.toBase(150.2))))
        assertEquals(2.54, MeasureUnit.INCH.toBase(1.0), 1e-9)
    }

    @Test
    fun formatDelta_signs() {
        assertEquals("+0.3", formatDelta(0.3))
        assertEquals("-1.2", formatDelta(-1.2))
        assertEquals("0.0", formatDelta(-0.01))
    }

    @Test
    fun latestPerDay_keepsNewestOfSameDayAndGroup() {
        val records = listOf(
            rec(1, group = 1, day = 10, at = 100, value = 60.0),
            rec(2, group = 1, day = 10, at = 200, value = 61.0),
            rec(3, group = 2, day = 10, at = 150, value = 62.0),
        )
        val latest = latestPerDay(records).associateBy { it.groupId }
        assertEquals(61.0, latest[1]!!.value, 0.0)
        assertEquals(62.0, latest[2]!!.value, 0.0)
        assertEquals(setOf(1L), ignoredRecordIds(records))
    }

    @Test
    fun movingAverage_usesTimeWindow() {
        val pts = listOf(ChartPoint(1, 10.0), ChartPoint(2, 20.0), ChartPoint(9, 30.0))
        val ma = movingAverage(pts, windowDays = 7)
        assertEquals(15.0, ma[1].value, 1e-9)
        // day 9 的窗口是 (2, 9]，只包含自己
        assertEquals(30.0, ma[2].value, 1e-9)
    }

    @Test
    fun difference_onlyCommonDays() {
        val a = listOf(ChartPoint(1, 60.0), ChartPoint(2, 60.5))
        val b = listOf(ChartPoint(1, 61.0), ChartPoint(3, 61.5))
        val d = difference(a, b)
        assertEquals(1, d.size)
        assertEquals(1.0, d[0].value, 1e-9)
    }

    @Test
    fun timeRange_crossMidnight() {
        assertTrue(minuteInRange(23 * 60, 18 * 60, 2 * 60))
        assertTrue(minuteInRange(60, 18 * 60, 2 * 60))
        assertFalse(minuteInRange(3 * 60, 18 * 60, 2 * 60))
        assertTrue(rangesOverlap(TimeRange(18 * 60, 2 * 60), TimeRange(60, 3 * 60)))
        assertFalse(rangesOverlap(TimeRange(4 * 60, 11 * 60), TimeRange(18 * 60, 2 * 60)))
    }

    @Test
    fun logicalDay_afterMidnightBelongsToPreviousDay() {
        val evening = listOf(GroupTimeRuleEntity(groupId = 2, startMinute = 18 * 60, endMinute = 2 * 60))
        val morning = listOf(GroupTimeRuleEntity(groupId = 1, startMinute = 4 * 60, endMinute = 11 * 60))
        val at0012 = epochMillisOf(LocalDateTime.of(2026, 10, 3, 0, 12))
        val oct2 = LocalDate.of(2026, 10, 2).toEpochDay()
        assertEquals(oct2, logicalDay(at0012, evening))
        assertEquals(oct2 + 1, logicalDay(at0012, morning))
        assertEquals(oct2, logicalDay(epochMillisOf(LocalDateTime.of(2026, 10, 2, 23, 30)), evening))
        // 自动归组：00:12 应匹配到「晚上」
        val groups = listOf(
            GroupEntity(id = 1, profileId = 1, name = "早晨", color = 0, sortOrder = 0),
            GroupEntity(id = 2, profileId = 1, name = "晚上", color = 0, sortOrder = 1),
        )
        assertEquals(2L, suggestGroup(groups, morning + evening, at0012)?.id)
    }

    @Test
    fun keypad_replacesPrefilledThenAppends() {
        var t = applyKey("62.3", fresh = true, key = KeypadKey.Digit('6'))
        assertEquals("6", t)
        t = applyKey(t, false, KeypadKey.Digit('1'))
        t = applyKey(t, false, KeypadKey.Dot)
        t = applyKey(t, false, KeypadKey.Digit('8'))
        t = applyKey(t, false, KeypadKey.Digit('9')) // 只允许 1 位小数
        assertEquals("61.8", t)
        assertEquals("61.", applyKey(t, false, KeypadKey.Backspace))
        assertEquals("", applyKey("62.3", true, KeypadKey.Backspace))
        assertEquals("0.", applyKey("", false, KeypadKey.Dot))
    }
}
