package com.weightnote.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.weightnote.data.formatDelta
import com.weightnote.data.formatNumber
import com.weightnote.domain.ChartPoint
import com.weightnote.ui.components.formatShortDate
import com.weightnote.ui.components.weekdayName
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class ChartSeries(
    val key: String,
    val name: String,
    val color: Color,
    /** 按 day 升序 */
    val points: List<ChartPoint>,
    val strokeWidth: Dp = 2.dp,
    val lineAlpha: Float = 1f,
    val showDots: Boolean = true,
    val dashed: Boolean = false,
    val inTooltip: Boolean = true,
)

/**
 * 自绘折线图：
 * - 多条曲线叠加，横轴为日期（每天一个位置，方便对齐早晚等不同分组）
 * - 单指左右拖动、双指缩放、单击查看当天数值、双击还原
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    startDay: Long,
    endDay: Long,
    unitLabel: String,
    modifier: Modifier = Modifier,
    goal: Double? = null,
    showZeroLine: Boolean = false,
    signedValues: Boolean = false,
    interactive: Boolean = true,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val tooltipStyle = MaterialTheme.typography.bodySmall.copy(color = colors.inverseOnSurface)
    val emptyStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurfaceVariant)

    val fullStart = startDay - 0.5f
    val fullEnd = endDay + 0.5f
    val fullSpan = fullEnd - fullStart
    var viewStart by remember(startDay, endDay) { mutableFloatStateOf(fullStart) }
    var viewEnd by remember(startDay, endDay) { mutableFloatStateOf(fullEnd) }
    var selectedDay by remember(startDay, endDay, series) { mutableStateOf<Long?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val leftPad = with(density) { 44.dp.toPx() }
    val rightPad = with(density) { 12.dp.toPx() }
    val topPad = with(density) { 20.dp.toPx() }
    val bottomPad = with(density) { 24.dp.toPx() }

    fun plotWidth() = (canvasSize.width - leftPad - rightPad).coerceAtLeast(1f)

    fun setView(start: Float, span: Float) {
        val s = span.coerceIn(min(4f, fullSpan), fullSpan)
        val st = start.coerceIn(fullStart, fullEnd - s)
        viewStart = st
        viewEnd = st + s
    }

    val gestureModifier = if (!interactive) Modifier else Modifier
        .pointerInput(startDay, endDay) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var panning = false
                var drag = Offset.Zero
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val centroid = event.calculateCentroid()
                        val span = viewEnd - viewStart
                        val pxPerDay = plotWidth() / span
                        val anchorRatio = ((centroid.x - leftPad) / plotWidth()).coerceIn(0f, 1f)
                        val anchorDay = viewStart + anchorRatio * span - pan.x / pxPerDay
                        val newSpan = span / zoom
                        setView(anchorDay - anchorRatio * newSpan, newSpan)
                        event.changes.forEach { it.consume() }
                        panning = true
                    } else if (pressed.size == 1) {
                        val change = pressed[0]
                        val delta = change.positionChange()
                        if (!panning) {
                            drag += delta
                            if (abs(drag.x) > viewConfiguration.touchSlop && abs(drag.x) > abs(drag.y) * 1.2f) {
                                panning = true
                            }
                        }
                        if (panning) {
                            val span = viewEnd - viewStart
                            setView(viewStart - delta.x / (plotWidth() / span), span)
                            change.consume()
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .pointerInput(startDay, endDay, series) {
            detectTapGestures(
                onDoubleTap = { setView(fullStart, fullSpan) },
                onTap = { pos ->
                    val span = viewEnd - viewStart
                    val day = viewStart + (pos.x - leftPad) / plotWidth() * span
                    val nearest = series.asSequence()
                        .flatMap { it.points.asSequence() }
                        .filter { it.day >= viewStart && it.day <= viewEnd }
                        .minByOrNull { abs(it.day - day) }
                        ?.day
                    selectedDay = if (nearest == selectedDay) null else nearest
                },
            )
        }

    Canvas(
        modifier
            .onSizeChanged { canvasSize = it }
            .then(gestureModifier),
    ) {
        val plotW = size.width - leftPad - rightPad
        val plotH = size.height - topPad - bottomPad
        if (plotW <= 0f || plotH <= 0f) return@Canvas

        val visible = series.flatMap { s -> s.points.filter { it.day >= viewStart - 1 && it.day <= viewEnd + 1 } }
        if (visible.isEmpty()) {
            val layout = textMeasurer.measure("所选范围内没有数据", emptyStyle)
            drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f))
            return@Canvas
        }

        // ---- Y 轴范围 ----
        var minV = visible.minOf { it.value }
        var maxV = visible.maxOf { it.value }
        goal?.let { minV = min(minV, it); maxV = max(maxV, it) }
        if (showZeroLine) { minV = min(minV, 0.0); maxV = max(maxV, 0.0) }
        if (maxV - minV < 0.6) {
            val c = (maxV + minV) / 2
            minV = c - 0.5
            maxV = c + 0.5
        }
        val padV = (maxV - minV) * 0.08
        minV -= padV
        maxV += padV
        val step = niceStep(maxV - minV)
        val yMin = floor(minV / step) * step
        val yMax = ceil(maxV / step) * step
        val decimals = when {
            step >= 1 -> 0
            step >= 0.1 -> 1
            else -> 2
        }

        val span = viewEnd - viewStart
        fun x(day: Double) = leftPad + ((day - viewStart) / span * plotW).toFloat()
        fun y(v: Double) = topPad + ((yMax - v) / (yMax - yMin) * plotH).toFloat()

        // ---- 网格与 Y 轴标签 ----
        drawLabel(textMeasurer, unitLabel, labelStyle, Offset(0f, 0f))
        var tick = yMin
        while (tick <= yMax + step / 2) {
            val ty = y(tick)
            drawLine(colors.outlineVariant.copy(alpha = 0.6f), Offset(leftPad, ty), Offset(size.width - rightPad, ty), 1f)
            val text = formatNumber(tick, decimals)
            val layout = textMeasurer.measure(text, labelStyle)
            drawText(layout, topLeft = Offset(leftPad - layout.size.width - 6.dp.toPx(), ty - layout.size.height / 2f))
            tick += step
        }

        // ---- X 轴标签 ----
        xTicks(viewStart, viewEnd).forEach { (day, label) ->
            val tx = x(day.toDouble())
            if (tx < leftPad - 1 || tx > size.width - rightPad + 1) return@forEach
            drawLine(colors.outlineVariant.copy(alpha = 0.35f), Offset(tx, topPad), Offset(tx, topPad + plotH), 1f)
            val layout = textMeasurer.measure(label, labelStyle)
            val lx = (tx - layout.size.width / 2f).coerceIn(leftPad - layout.size.width / 2f, size.width - layout.size.width.toFloat())
            drawText(layout, topLeft = Offset(lx, topPad + plotH + 4.dp.toPx()))
        }

        // ---- 零线 / 目标线 ----
        if (showZeroLine && 0.0 in yMin..yMax) {
            drawLine(colors.outline, Offset(leftPad, y(0.0)), Offset(size.width - rightPad, y(0.0)), 1.5f)
        }
        goal?.let { g ->
            val gy = y(g)
            drawLine(
                colors.tertiary,
                Offset(leftPad, gy),
                Offset(size.width - rightPad, gy),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
            val layout = textMeasurer.measure("目标 ${formatNumber(g)}", labelStyle.copy(color = colors.tertiary))
            drawText(layout, topLeft = Offset(size.width - rightPad - layout.size.width, gy - layout.size.height - 2f))
        }

        // ---- 曲线 ----
        clipRect(leftPad - 6.dp.toPx(), topPad - 6.dp.toPx(), size.width - rightPad + 6.dp.toPx(), topPad + plotH + 6.dp.toPx()) {
            series.forEach { s ->
                if (s.points.isEmpty()) return@forEach
                val path = Path()
                s.points.forEachIndexed { i, p ->
                    val px = x(p.day.toDouble())
                    val py = y(p.value)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(
                    path,
                    color = s.color.copy(alpha = s.lineAlpha),
                    style = Stroke(
                        width = s.strokeWidth.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null,
                    ),
                )
                val visibleCount = s.points.count { it.day >= viewStart && it.day <= viewEnd }
                if (s.showDots && visibleCount <= 120) {
                    val r = if (visibleCount > 60) 2.dp.toPx() else 3.dp.toPx()
                    s.points.forEach { p ->
                        val c = Offset(x(p.day.toDouble()), y(p.value))
                        drawCircle(colors.surface, radius = r + 1.5f, center = c)
                        drawCircle(s.color.copy(alpha = max(s.lineAlpha, 0.6f)), radius = r, center = c)
                    }
                }
            }
        }

        // ---- 选中某天：竖线 + 高亮点 + 浮层 ----
        selectedDay?.let { day ->
            val sx = x(day.toDouble())
            if (sx < leftPad || sx > size.width - rightPad) return@let
            drawLine(colors.outline, Offset(sx, topPad), Offset(sx, topPad + plotH), 1.dp.toPx())
            val rows = series.filter { it.inTooltip }.mapNotNull { s ->
                s.points.firstOrNull { it.day == day }?.let { s to it }
            }
            rows.forEach { (s, p) ->
                val c = Offset(sx, y(p.value))
                drawCircle(colors.surface, radius = 6.dp.toPx(), center = c)
                drawCircle(s.color, radius = 4.5.dp.toPx(), center = c)
            }
            val date = LocalDate.ofEpochDay(day)
            val header = "${date.year}/${date.monthValue}/${date.dayOfMonth} ${weekdayName(date)}"
            val lines = rows.map { (s, p) ->
                val v = if (signedValues) formatDelta(p.value) else formatNumber(p.value)
                s.color to "${s.name}  $v $unitLabel"
            }
            drawTooltip(textMeasurer, tooltipStyle, colors.inverseSurface, header, lines, sx, topPad)
        }
    }
}

private fun DrawScope.drawLabel(measurer: androidx.compose.ui.text.TextMeasurer, text: String, style: TextStyle, at: Offset) {
    drawText(measurer.measure(text, style), topLeft = at)
}

private fun DrawScope.drawTooltip(
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    background: Color,
    header: String,
    lines: List<Pair<Color, String>>,
    anchorX: Float,
    top: Float,
) {
    val pad = 8.dp.toPx()
    val bullet = 8.dp.toPx()
    val gap = 6.dp.toPx()
    val headerLayout = measurer.measure(header, style)
    val lineLayouts = lines.map { it.first to measurer.measure(it.second, style) }
    val contentW = max(
        headerLayout.size.width.toFloat(),
        lineLayouts.maxOfOrNull { it.second.size.width + bullet + gap } ?: 0f,
    )
    val lineH = headerLayout.size.height.toFloat()
    val boxW = contentW + pad * 2
    val boxH = pad * 2 + lineH * (1 + lineLayouts.size)
    var bx = anchorX + 12.dp.toPx()
    if (bx + boxW > size.width) bx = anchorX - 12.dp.toPx() - boxW
    bx = bx.coerceAtLeast(0f)
    val by = top + 4.dp.toPx()
    drawRoundRect(background.copy(alpha = 0.92f), Offset(bx, by), Size(boxW, boxH), CornerRadius(8.dp.toPx()))
    drawText(headerLayout, topLeft = Offset(bx + pad, by + pad))
    lineLayouts.forEachIndexed { i, (color, layout) ->
        val ly = by + pad + lineH * (i + 1)
        drawCircle(color, radius = bullet / 2, center = Offset(bx + pad + bullet / 2, ly + lineH / 2))
        drawText(layout, topLeft = Offset(bx + pad + bullet + gap, ly))
    }
}

/** 选择“好看”的刻度间隔：1、2、2.5、5 × 10^n */
private fun niceStep(range: Double, targetTicks: Int = 5): Double {
    val raw = range / targetTicks
    val mag = 10.0.pow(floor(log10(raw)))
    val n = raw / mag
    val nice = when {
        n <= 1 -> 1.0
        n <= 2 -> 2.0
        n <= 2.5 -> 2.5
        n <= 5 -> 5.0
        else -> 10.0
    }
    return nice * mag
}

/** 横轴刻度：短范围按天，长范围按月 */
private fun xTicks(viewStart: Float, viewEnd: Float): List<Pair<Long, String>> {
    val span = viewEnd - viewStart
    val first = ceil(viewStart).toLong()
    val last = floor(viewEnd).toLong()
    if (span <= 75) {
        val step = listOf(1L, 2L, 3L, 7L, 14L).firstOrNull { span / it <= 6 } ?: 14L
        val result = mutableListOf<Pair<Long, String>>()
        // 让刻度从最右边（通常是今天）往左对齐
        var d = last
        while (d >= first) {
            result += d to formatShortDate(d)
            d -= step
        }
        return result
    }
    val monthStep = listOf(1L, 2L, 3L, 6L, 12L).firstOrNull { span / 30.4f / it <= 6 } ?: 12L
    val result = mutableListOf<Pair<Long, String>>()
    var date = LocalDate.ofEpochDay(first).withDayOfMonth(1)
    if (date.toEpochDay() < first) date = date.plusMonths(1)
    while (date.toEpochDay() <= last) {
        if ((date.monthValue - 1) % monthStep == 0L) {
            val label = if (date.monthValue == 1) "${date.year}年" else "${date.monthValue}月"
            result += date.toEpochDay() to label
        }
        date = date.plusMonths(1)
    }
    return result
}
