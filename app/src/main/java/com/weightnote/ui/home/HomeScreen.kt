package com.weightnote.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weightnote.data.formatDelta
import com.weightnote.data.formatNumber
import com.weightnote.data.todayDay
import com.weightnote.domain.bmiCategory
import com.weightnote.domain.bmiOf
import com.weightnote.domain.latestPerDay
import com.weightnote.domain.logicalDay
import com.weightnote.ui.MainViewModel
import com.weightnote.ui.Session
import com.weightnote.ui.chart.ChartMetric
import com.weightnote.ui.chart.ChartSeries
import com.weightnote.ui.chart.LineChart
import com.weightnote.ui.chart.groupPoints
import com.weightnote.ui.chart.defaultDiffGroups
import com.weightnote.ui.components.formatFullDate
import com.weightnote.data.MeasureUnit
import com.weightnote.domain.ChartPoint
import com.weightnote.domain.GoalForecast
import com.weightnote.domain.averageIn
import com.weightnote.domain.difference
import com.weightnote.domain.forecastGoal
import com.weightnote.domain.overnightDifference
import com.weightnote.domain.statsOf
import com.weightnote.ui.components.ColorDot
import com.weightnote.ui.components.formatDateTime
import com.weightnote.ui.components.formatTime
import com.weightnote.ui.components.groupColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    session: Session,
    vm: MainViewModel,
    onRecordWeight: (groupId: Long?) -> Unit,
    onRecordMeasure: () -> Unit,
    onOpenChart: () -> Unit,
    onManageProfiles: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(
                            Modifier.clickable { menu = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(session.profile.name, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, "切换身份")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            session.profiles.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.name) },
                                    leadingIcon = {
                                        if (p.id == session.profile.id) Icon(Icons.Default.Check, null) else Spacer(Modifier.width(24.dp))
                                    },
                                    onClick = {
                                        menu = false
                                        vm.switchProfile(p.id)
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("管理身份") },
                                leadingIcon = { Icon(Icons.Outlined.ManageAccounts, null) },
                                onClick = {
                                    menu = false
                                    onManageProfiles()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onRecordWeight(null) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("记体重") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCard(session)
            InsightsCard(session)
            TodayCard(session, onRecordWeight)
            MiniChartCard(session, onOpenChart)
            OutlinedButton(onClick = onRecordMeasure, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Straighten, null)
                Spacer(Modifier.width(8.dp))
                Text("记录围度")
            }
            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun SummaryCard(session: Session) {
    val weight = session.weightMetric ?: return
    val unit = session.profile.weightUnitEnum
    val weightRecords = session.records.filter { it.metricId == weight.id }
    val latest = weightRecords.firstOrNull()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (latest == null) {
                Text("还没有体重记录", style = MaterialTheme.typography.titleMedium)
                Text(
                    "点右下角「记体重」开始第一条记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                return@Column
            }
            val group = session.groupById[latest.groupId]
            Text("当前体重", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatNumber(unit.fromBase(latest.value)), fontSize = 44.sp, fontWeight = FontWeight.Bold)
                Text(" ${unit.symbol}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(groupColor(group))
                Text(
                    "  ${group?.name ?: ""} · ${formatDateTime(latest.recordedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // 与同分组上一天的记录比较
            val sameGroup = latestPerDay(weightRecords.filter { it.groupId == latest.groupId })
                .sortedByDescending { it.recordedAt }
            val previous = sameGroup.firstOrNull { it.day < latest.day }
            val items = buildList {
                previous?.let {
                    add("较上次${group?.name ?: ""}" to "${formatDelta(unit.fromBase(latest.value - it.value))} ${unit.symbol}")
                }
                bmiOf(latest.value, session.profile.heightCm)?.let {
                    add("BMI" to "${formatNumber(it)} ${bmiCategory(it)}")
                }
                session.bodyFatMetric?.let { fm ->
                    session.records.firstOrNull { it.metricId == fm.id }?.let { add("体脂率" to "${formatNumber(it.value)}%") }
                }
            }
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items.forEach { (label, value) ->
                        Column {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            session.profile.goalWeightKg?.let { goal ->
                val start = weightRecords.lastOrNull()?.value ?: latest.value
                val remain = latest.value - goal
                Spacer(Modifier.height(14.dp))
                Text(
                    when {
                        kotlin.math.abs(remain) < 0.05 -> "已达到目标 ${formatNumber(unit.fromBase(goal))} ${unit.symbol}"
                        else -> "目标 ${formatNumber(unit.fromBase(goal))} ${unit.symbol} · 还差 ${formatNumber(unit.fromBase(kotlin.math.abs(remain)))} ${unit.symbol}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (start != goal) {
                    val progress = ((start - latest.value) / (start - goal)).toFloat().coerceIn(0f, 1f)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                // 按「早晨」最近 3 周的趋势预测
                val forecast = remember(session.records, goal) { goalForecast(session, goal) }
                forecast?.let { f ->
                    Spacer(Modifier.height(6.dp))
                    val trend = "近 3 周每周 ${formatDelta(unit.fromBase(f.perWeek))} ${unit.symbol}"
                    Text(
                        when {
                            f.reached -> "按趋势已到达目标附近（$trend）"
                            f.day != null -> "按趋势预计 ${formatFullDate(f.day)} 达到目标（$trend）"
                            else -> "${f.reason}（$trend）"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

/** 体重点（基础单位 kg），按“同日同组取最新”去重 */
private fun weightPointsKg(session: Session, groupId: Long?, fromDay: Long, toDay: Long): List<ChartPoint> {
    val weight = session.weightMetric ?: return emptyList()
    groupId ?: return emptyList()
    val metric = ChartMetric("m${weight.id}", weight.name, weight.id, MeasureUnit.KG)
    return groupPoints(session, latestPerDay(session.records), metric, groupId, fromDay, toDay)
}

private fun goalForecast(session: Session, goalKg: Double): GoalForecast? {
    val today = todayDay()
    val morning = defaultDiffGroups(session).second
    return forecastGoal(weightPointsKg(session, morning, today - 20, today), goalKg, today)
}

/** 根据早晚数据自动得出的几条结论 */
@Composable
private fun InsightsCard(session: Session) {
    val unit = session.profile.weightUnitEnum
    val insights = remember(session.records, session.groups, session.rules, unit) {
        val today = todayDay()
        val (eveningId, morningId) = defaultDiffGroups(session)
        val evening = session.groupById[eveningId]
        val morning = session.groupById[morningId]
        val m30 = weightPointsKg(session, morningId, today - 30, today)
        val e30 = weightPointsKg(session, eveningId, today - 30, today)
        buildList {
            if (morning != null && evening != null && morningId != eveningId) {
                statsOf(overnightDifference(e30, m30))?.takeIf { it.count >= 3 }?.let {
                    add(
                        "隔夜变化" to
                            "近 30 天平均每晚 ${formatDelta(unit.fromBase(it.average))} ${unit.symbol}" +
                            "（${evening.name} → 次日${morning.name}，${it.count} 天）",
                    )
                }
                statsOf(difference(m30, e30))?.takeIf { it.count >= 3 }?.let {
                    add(
                        "日内变化" to
                            "近 30 天平均每天 ${formatDelta(unit.fromBase(it.average))} ${unit.symbol}" +
                            "（${morning.name} → ${evening.name}，${it.count} 天）",
                    )
                }
            }
            if (morning != null) {
                val pts = weightPointsKg(session, morningId, today - 13, today)
                val thisWeek = averageIn(pts, today - 6, today)
                val lastWeek = averageIn(pts, today - 13, today - 7)
                if (thisWeek != null && lastWeek != null) {
                    add(
                        "周对比" to
                            "${morning.name}近 7 天均值 ${formatNumber(unit.fromBase(thisWeek))} ${unit.symbol}，" +
                            "较前 7 天 ${formatDelta(unit.fromBase(thisWeek - lastWeek))} ${unit.symbol}",
                    )
                }
            }
        }
    }
    if (insights.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("数据洞察", style = MaterialTheme.typography.titleSmall)
            insights.forEach { (label, text) ->
                Row {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(64.dp),
                    )
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TodayCard(session: Session, onRecordWeight: (Long?) -> Unit) {
    val weight = session.weightMetric ?: return
    val unit = session.profile.weightUnitEnum
    val now = System.currentTimeMillis()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text("今日", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
            session.groups.forEach { g ->
                // 跨午夜分组在午夜后仍算“前一天”，如 00:12 时「晚上」显示的是昨晚的记录
                val today = logicalDay(now, session.rules.filter { it.groupId == g.id })
                val rec = session.records.firstOrNull { it.groupId == g.id && it.metricId == weight.id && it.day == today }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onRecordWeight(g.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ColorDot(Color(g.color), size = 12.dp)
                    Text("  ${g.name}", modifier = Modifier.weight(1f))
                    if (rec != null) {
                        Text(
                            "${formatNumber(unit.fromBase(rec.value))} ${unit.symbol}",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "  ${formatTime(rec.recordedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        TextButton(onClick = { onRecordWeight(g.id) }) { Text("未记录 · 去记录") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniChartCard(session: Session, onOpenChart: () -> Unit) {
    val weight = session.weightMetric ?: return
    val today = todayDay()
    val start = today - 29
    val metric = ChartMetric("m${weight.id}", weight.name, weight.id, session.profile.weightUnitEnum)
    val latest = remember(session.records) { latestPerDay(session.records) }
    val series = remember(latest, session.groups, session.profile) {
        session.groups.mapNotNull { g ->
            val pts = groupPoints(session, latest, metric, g.id, start, today)
            if (pts.isEmpty()) null else ChartSeries("g${g.id}", g.name, Color(g.color), pts)
        }
    }
    if (series.isEmpty()) return
    Card(
        onClick = onOpenChart,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("近 30 天", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("查看趋势 ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            LineChart(
                series = series,
                startDay = start,
                endDay = today,
                unitLabel = session.profile.weightUnitEnum.symbol,
                interactive = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}
