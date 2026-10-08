package com.weightnote.ui.chart

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.weightnote.data.MeasureUnit
import com.weightnote.data.db.MetricKeys
import com.weightnote.data.db.RecordEntity
import com.weightnote.data.formatDelta
import com.weightnote.data.formatNumber
import com.weightnote.data.todayDay
import com.weightnote.domain.ChartPoint
import com.weightnote.domain.bmiOf
import com.weightnote.domain.difference
import com.weightnote.domain.latestPerDay
import com.weightnote.domain.movingAverage
import com.weightnote.domain.overnightDifference
import com.weightnote.domain.statsOf
import com.weightnote.ui.Session
import com.weightnote.ui.components.ColorDot
import com.weightnote.ui.components.DateRangePickerDialog
import com.weightnote.ui.components.formatFullDate
import com.weightnote.ui.components.formatShortDate

enum class ChartRange(val label: String, val days: Long?) {
    D7("7天", 7),
    D30("30天", 30),
    D90("90天", 90),
    D180("半年", 182),
    Y1("1年", 365),
    ALL("全部", null),
    CUSTOM("自定义", null),
}

/** 图表可选的指标：普通指标用 id，BMI 是虚拟指标 */
data class ChartMetric(val key: String, val name: String, val metricId: Long?, val unit: MeasureUnit?) {
    val isBmi: Boolean get() = key == MetricKeys.BMI
}

fun chartMetrics(session: Session): List<ChartMetric> {
    val used = session.records.map { it.metricId }.toSet()
    val list = mutableListOf<ChartMetric>()
    session.metrics.filter { it.enabled || it.id in used }.forEach { m ->
        list += ChartMetric("m${m.id}", m.name, m.id, session.displayUnit(m))
        if (m.key == MetricKeys.WEIGHT && session.profile.heightCm != null) {
            list += ChartMetric(MetricKeys.BMI, "BMI", null, null)
        }
    }
    return list
}

/** 取某分组某指标的点（已按“同日同组取最新”去重），值已换算成显示单位 */
fun groupPoints(
    session: Session,
    latest: List<RecordEntity>,
    metric: ChartMetric,
    groupId: Long,
    startDay: Long?,
    endDay: Long?,
): List<ChartPoint> {
    val metricId = if (metric.isBmi) session.weightMetric?.id else metric.metricId
    return latest.asSequence()
        .filter { it.groupId == groupId && it.metricId == metricId }
        .filter { startDay == null || it.day >= startDay }
        .filter { endDay == null || it.day <= endDay }
        .mapNotNull { r ->
            val v = if (metric.isBmi) bmiOf(r.value, session.profile.heightCm) else metric.unit?.fromBase(r.value)
            v?.let { ChartPoint(r.day, it) }
        }
        .sortedBy { it.day }
        .toList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(session: Session) {
    val metrics = chartMetrics(session)
    var metricKey by rememberSaveable(session.profile.id) { mutableStateOf(metrics.firstOrNull()?.key) }
    val metric = metrics.firstOrNull { it.key == metricKey } ?: metrics.firstOrNull()
    var range by rememberSaveable { mutableStateOf(ChartRange.D30) }
    var customStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var customEnd by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickRange by remember { mutableStateOf(false) }
    var hiddenGroups by remember(session.profile.id) { mutableStateOf(emptySet<Long>()) }
    var showMa by rememberSaveable { mutableStateOf(false) }
    var showGoal by rememberSaveable { mutableStateOf(true) }
    var showDiff by rememberSaveable { mutableStateOf(false) }
    var diffMode by rememberSaveable { mutableStateOf(DiffMode.OVERNIGHT) }
    // 差值曲线默认「前一天 = 时间最晚的分组（晚上）」「第二天 = 最早的分组（早晨）」
    val defaultDiff = remember(session.groups, session.rules) { defaultDiffGroups(session) }
    var diffA by remember(session.profile.id) { mutableStateOf(defaultDiff.first) }
    var diffB by remember(session.profile.id) { mutableStateOf(defaultDiff.second) }
    val latest = remember(session.records) { latestPerDay(session.records) }
    val today = todayDay()

    Scaffold(topBar = { TopAppBar(title = { Text("趋势") }) }) { padding ->
        if (metric == null) {
            Text("暂无可显示的指标", modifier = Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        val metricId = if (metric.isBmi) session.weightMetric?.id else metric.metricId
        val firstDay = remember(latest, metricId) {
            latest.filter { it.metricId == metricId }.minOfOrNull { it.day }
        }
        val (startDay, endDay) = when (range) {
            ChartRange.ALL -> (firstDay ?: (today - 29)).coerceAtMost(today - 6) to today
            ChartRange.CUSTOM -> (customStart ?: (today - 29)) to (customEnd ?: today)
            else -> (today - range.days!! + 1) to today
        }
        val unitLabel = metric.unit?.symbol ?: ""
        val groupPointMap = remember(latest, metric, startDay, endDay, session.groups) {
            session.groups.associate { g -> g.id to groupPoints(session, latest, metric, g.id, startDay, endDay) }
        }
        // 隔夜差值要用到前一天的数据，所以往前多取一天
        val diffStartDay = if (diffMode == DiffMode.OVERNIGHT) startDay - 1 else startDay
        val diffPointMap = remember(latest, metric, diffStartDay, endDay, session.groups) {
            session.groups.associate { g -> g.id to groupPoints(session, latest, metric, g.id, diffStartDay, endDay) }
        }
        val goal = if (metric.metricId == session.weightMetric?.id && showGoal) {
            session.profile.goalWeightKg?.let { metric.unit?.fromBase(it) }
        } else {
            null
        }

        val series = session.groups.filter { it.id !in hiddenGroups }.flatMap { g ->
            val pts = groupPointMap[g.id].orEmpty()
            if (pts.isEmpty()) return@flatMap emptyList()
            val color = Color(g.color)
            if (showMa) {
                listOf(
                    ChartSeries("raw${g.id}", g.name, color, pts, strokeWidth = 1.dp, lineAlpha = 0.35f),
                    ChartSeries("ma${g.id}", "${g.name}·均线", color, movingAverage(pts), strokeWidth = 3.dp, showDots = false),
                )
            } else {
                listOf(ChartSeries("g${g.id}", g.name, color, pts))
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ChipRow {
                metrics.forEach { m ->
                    FilterChip(m.key == metric.key, onClick = { metricKey = m.key }, label = { Text(m.name) })
                }
            }
            ChipRow {
                ChartRange.entries.forEach { r ->
                    FilterChip(
                        selected = r == range,
                        onClick = {
                            if (r == ChartRange.CUSTOM) pickRange = true else range = r
                        },
                        label = {
                            Text(
                                if (r == ChartRange.CUSTOM && range == ChartRange.CUSTOM && customStart != null) {
                                    "${formatFullDate(customStart!!)}–${formatFullDate(customEnd ?: today)}"
                                } else {
                                    r.label
                                },
                            )
                        },
                    )
                }
            }
            ChipRow {
                session.groups.forEach { g ->
                    val selected = g.id !in hiddenGroups
                    FilterChip(
                        selected = selected,
                        onClick = {
                            hiddenGroups = if (selected) hiddenGroups + g.id else hiddenGroups - g.id
                        },
                        label = { Text(g.name) },
                        leadingIcon = { ColorDot(Color(g.color)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(g.color).copy(alpha = 0.22f),
                        ),
                    )
                }
            }

            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                LineChart(
                    series = series,
                    startDay = startDay,
                    endDay = endDay,
                    unitLabel = unitLabel,
                    goal = goal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(8.dp),
                )
                Text(
                    "单指左右拖动 · 双指缩放 · 双击还原 · 点按查看数值",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            ChipRow {
                FilterChip(showMa, onClick = { showMa = !showMa }, label = { Text("7日均线") })
                if (metric.metricId == session.weightMetric?.id && session.profile.goalWeightKg != null) {
                    FilterChip(showGoal, onClick = { showGoal = !showGoal }, label = { Text("目标线") })
                }
                if (session.groups.size >= 2) {
                    FilterChip(showDiff, onClick = { showDiff = !showDiff }, label = { Text("差值曲线") })
                }
            }

            StatsCard(session, groupPointMap, hiddenGroups, unitLabel)

            if (showDiff && session.groups.size >= 2) {
                DiffCard(
                    session = session,
                    groupPointMap = diffPointMap,
                    a = diffA,
                    b = diffB,
                    onA = { diffA = it },
                    onB = { diffB = it },
                    mode = diffMode,
                    onModeChange = { diffMode = it },
                    startDay = startDay,
                    endDay = endDay,
                    unitLabel = unitLabel,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickRange) {
        DateRangePickerDialog(
            initialStartDay = customStart,
            initialEndDay = customEnd,
            onConfirm = { s, e ->
                customStart = s
                customEnd = maxOf(e, s)
                range = ChartRange.CUSTOM
                pickRange = false
            },
            onDismiss = { pickRange = false },
        )
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun StatsCard(
    session: Session,
    groupPointMap: Map<Long, List<ChartPoint>>,
    hidden: Set<Long>,
    unitLabel: String,
) {
    val rows = session.groups.filter { it.id !in hidden }.mapNotNull { g ->
        statsOf(groupPointMap[g.id].orEmpty())?.let { g to it }
    }
    if (rows.isEmpty()) return
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("区间统计（$unitLabel）".replace("（）", ""), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            StatsRow(listOf("分组", "最新", "最高", "最低", "平均", "变化"), header = true)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            rows.forEach { (g, s) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(Color(g.color), size = 8.dp)
                    StatsRow(
                        listOf(
                            " ${g.name}",
                            formatNumber(s.last.value),
                            formatNumber(s.max.value),
                            formatNumber(s.min.value),
                            formatNumber(s.average),
                            formatDelta(s.change),
                        ),
                    )
                }
            }
            Text(
                "按每天每组最新一条记录统计",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun StatsRow(cells: List<String>, header: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        cells.forEachIndexed { i, text ->
            Text(
                text,
                modifier = Modifier.weight(if (i == 0) 1.4f else 1f),
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (header) FontWeight.Normal else if (i == 0) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

/** 差值曲线的计算方式 */
enum class DiffMode(val label: String) {
    OVERNIGHT("隔夜变化"),
    SAME_DAY("同一天变化"),
}

/** 某分组一天中的“代表时间”，取第一条时间段的开始时间；没有规则返回 -1 */
private fun groupStartMinute(session: Session, groupId: Long): Int =
    session.rules.filter { it.groupId == groupId }.minOfOrNull { it.startMinute } ?: -1

/** 默认把开始时间最晚的分组当作“前一天”（晚上），最早当作“第二天”（早晨） */
fun defaultDiffGroups(session: Session): Pair<Long?, Long?> {
    val withStart = session.groups.map { it.id to groupStartMinute(session, it.id) }
    val timed = withStart.filter { it.second >= 0 }
    if (timed.size >= 2) {
        return timed.maxBy { it.second }.first to timed.minBy { it.second }.first
    }
    return session.groups.getOrNull(0)?.id to session.groups.getOrNull(1)?.id
}

@Composable
private fun DiffCard(
    session: Session,
    groupPointMap: Map<Long, List<ChartPoint>>,
    a: Long?,
    b: Long?,
    onA: (Long) -> Unit,
    onB: (Long) -> Unit,
    mode: DiffMode,
    onModeChange: (DiffMode) -> Unit,
    startDay: Long,
    endDay: Long,
    unitLabel: String,
) {
    val groupA = session.groupById[a]
    val groupB = session.groupById[b]
    val overnight = mode == DiffMode.OVERNIGHT
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text("差值曲线", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(6.dp))
            ChipRow {
                DiffMode.entries.forEach { m ->
                    FilterChip(m == mode, onClick = { onModeChange(m) }, label = { Text(m.label) })
                }
            }
            Text(
                if (overnight) {
                    "每天的变化 = 第二天「${groupB?.name ?: "?"}」− 前一天「${groupA?.name ?: "?"}」，点落在第二天"
                } else {
                    "每天的变化 = 同一天「${groupB?.name ?: "?"}」− 同一天「${groupA?.name ?: "?"}」"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            Text(
                if (overnight) "前一天（晚上）" else "被减数 A",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            ChipRow {
                session.groups.forEach { g ->
                    FilterChip(g.id == a, onClick = { onA(g.id) }, label = { Text(g.name) }, leadingIcon = { ColorDot(Color(g.color)) })
                }
            }
            Text(
                if (overnight) "第二天（早晨）" else "被减数 B",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            ChipRow {
                session.groups.forEach { g ->
                    FilterChip(g.id == b, onClick = { onB(g.id) }, label = { Text(g.name) }, leadingIcon = { ColorDot(Color(g.color)) })
                }
            }

            if (groupA == null || groupB == null || a == b) {
                Text(
                    "请选择两个不同的分组",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
                return@Column
            }

            val pointsA = groupPointMap[a].orEmpty()
            val pointsB = groupPointMap[b].orEmpty()
            val diff = if (overnight) overnightDifference(pointsA, pointsB) else difference(pointsA, pointsB)
            val valueOfA = { day: Long -> pointsA.firstOrNull { it.day == day }?.value }
            val valueOfB = { day: Long -> pointsB.firstOrNull { it.day == day }?.value }

            LineChart(
                series = listOf(
                    ChartSeries(
                        key = "diff",
                        name = if (overnight) "隔夜变化" else "同一天变化",
                        color = MaterialTheme.colorScheme.tertiary,
                        points = diff,
                    ),
                ),
                startDay = startDay,
                endDay = endDay,
                unitLabel = unitLabel,
                showZeroLine = true,
                signedValues = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(8.dp),
            )

            // 明确指出最近一次是怎么算出来的，避免只看公式看不懂
            diff.lastOrNull()?.let { latestDiff ->
                val dayB = latestDiff.day
                val dayA = if (overnight) dayB - 1 else dayB
                val valueA = valueOfA(dayA)
                val valueB = valueOfB(dayB)
                if (valueA != null && valueB != null) {
                    Text(
                        "最近一次：" +
                            "${formatShortDate(dayB)}「${groupB.name}」${formatNumber(valueB)} " +
                            "− ${formatShortDate(dayA)}「${groupA.name}」${formatNumber(valueA)} " +
                            "= ${formatDelta(latestDiff.value)} $unitLabel",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            statsOf(diff)?.let { s ->
                Text(
                    "平均 ${formatDelta(s.average)}  ·  最大 ${formatDelta(s.max.value)}  ·  最小 ${formatDelta(s.min.value)}  ·  共 ${s.count} 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}
