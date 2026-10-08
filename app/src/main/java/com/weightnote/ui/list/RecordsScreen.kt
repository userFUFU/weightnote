package com.weightnote.ui.list

import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weightnote.data.dayOf
import com.weightnote.data.db.RecordEntity
import com.weightnote.domain.ignoredRecordIds
import com.weightnote.ui.MainViewModel
import com.weightnote.ui.Session
import com.weightnote.ui.components.ColorDot
import com.weightnote.ui.components.formatDayHeader
import com.weightnote.ui.components.formatTime
import com.weightnote.ui.components.groupColor
import com.weightnote.ui.record.recordValueText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecordsScreen(
    session: Session,
    vm: MainViewModel,
    onEdit: (RecordEntity) -> Unit,
    onOpenTrash: () -> Unit,
) {
    var metricFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var groupFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val ignored = remember(session.records) { ignoredRecordIds(session.records) }
    val usedMetricIds = remember(session.records) { session.records.map { it.metricId }.toSet() }
    val metricOptions = session.metrics.filter { it.enabled || it.id in usedMetricIds }
    val filtered = remember(session.records, metricFilter, groupFilter, query) {
        val q = query.trim()
        session.records.filter {
            (metricFilter == null || it.metricId == metricFilter) &&
                (groupFilter == null || it.groupId == groupFilter) &&
                (q.isEmpty() || it.note?.contains(q, ignoreCase = true) == true)
        }
    }
    val byDay = remember(filtered) { filtered.groupBy { it.day } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全部记录") },
                actions = {
                    TextButton(onClick = onOpenTrash) {
                        Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(18.dp))
                        Text(if (session.trash.isEmpty()) " 回收站" else " 回收站 ${session.trash.size}")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(30) },
                placeholder = { Text("搜索备注，如：聚餐") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "清除") } }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(metricFilter == null, onClick = { metricFilter = null }, label = { Text("全部指标") })
                metricOptions.forEach { m ->
                    FilterChip(
                        metricFilter == m.id,
                        onClick = { metricFilter = if (metricFilter == m.id) null else m.id },
                        label = { Text(m.name) },
                    )
                }
            }
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(groupFilter == null, onClick = { groupFilter = null }, label = { Text("全部分组") })
                session.groups.forEach { g ->
                    FilterChip(
                        groupFilter == g.id,
                        onClick = { groupFilter = if (groupFilter == g.id) null else g.id },
                        label = { Text(g.name) },
                        leadingIcon = { ColorDot(Color(g.color)) },
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (session.records.isEmpty()) "还没有记录" else "没有符合条件的记录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    byDay.forEach { (day, list) ->
                        stickyHeader(key = "h$day") {
                            Text(
                                formatDayHeader(day),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        items(list, key = { it.id }) { record ->
                            RecordRow(
                                session = session,
                                record = record,
                                ignored = record.id in ignored,
                                onClick = { onEdit(record) },
                                onDelete = { vm.deleteRecord(record) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordRow(
    session: Session,
    record: RecordEntity,
    ignored: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val group = session.groupById[record.groupId]
    val metric = session.metricById[record.metricId]
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) onDelete()
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        ListItem(
            modifier = Modifier
                .clickable(onClick = onClick)
                .alpha(if (ignored) 0.45f else 1f),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            leadingContent = { ColorDot(groupColor(group), size = 12.dp) },
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${group?.name ?: "?"} · ${metric?.name ?: "?"}")
                }
            },
            supportingContent = {
                val parts = buildList {
                    // 跨午夜分组中午夜后的记录归属前一天，时间前标注“次日”
                    val nextDay = dayOf(record.recordedAt) != record.day
                    add((if (nextDay) "次日 " else "") + formatTime(record.recordedAt))
                    if (ignored) add("同日同组有更新的记录，图表未采用")
                    record.note?.let { add(it) }
                }
                Text(parts.joinToString(" · "), maxLines = 2)
            },
            trailingContent = {
                Text(
                    recordValueText(record, session),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
        )
    }
}
