package com.weightnote.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import com.weightnote.data.MeasureUnit
import com.weightnote.data.TRASH_RETENTION_DAYS
import com.weightnote.data.db.TrashRecordEntity
import com.weightnote.data.dayOf
import com.weightnote.data.formatNumber
import com.weightnote.ui.MainViewModel
import com.weightnote.ui.Session
import com.weightnote.ui.components.ColorDot
import com.weightnote.ui.components.ConfirmDialog
import com.weightnote.ui.components.formatDateTime
import com.weightnote.ui.components.formatDayHeader
import com.weightnote.ui.components.formatTime

/** 回收站：删除的记录保留 7 天，可以恢复或彻底删除 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(session: Session, vm: MainViewModel, onBack: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TrashRecordEntity?>(null) }
    val entries = session.trash
    val byDay = remember(entries) { entries.groupBy { dayOf(it.deletedAt) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("回收站") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) { Text("清空") }
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
            Text(
                "删除的记录会在这里保留 $TRASH_RETENTION_DAYS 天，超过后自动清除。" +
                    "被新记录覆盖的旧记录也会放进来。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("回收站是空的", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    byDay.forEach { (day, list) ->
                        stickyHeader(key = "t$day") {
                            Text(
                                "${formatDayHeader(day)} 删除",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        items(list, key = { it.id }) { entry ->
                            TrashRow(
                                entry = entry,
                                unit = unitSymbolOf(entry),
                                onRestore = { vm.restoreTrash(entry) },
                                onDelete = { deleteTarget = entry },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清空回收站？",
            text = "这 ${entries.size} 条记录会被永久删除，无法恢复。",
            confirmText = "清空",
            destructive = true,
            onConfirm = { vm.clearTrash() },
            onDismiss = { confirmClear = false },
        )
    }
    deleteTarget?.let { entry ->
        ConfirmDialog(
            title = "彻底删除这条记录？",
            text = "删除后无法恢复。",
            confirmText = "永久删除",
            destructive = true,
            onConfirm = { vm.deleteTrashEntry(entry) },
            onDismiss = { deleteTarget = null },
        )
    }
}

private fun unitSymbolOf(entry: TrashRecordEntity): String =
    MeasureUnit.of(entry.inputUnit, MeasureUnit.KG).symbol

@Composable
private fun TrashRow(
    entry: TrashRecordEntity,
    unit: String,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onRestore),
        leadingContent = { ColorDot(Color(entry.groupColor), size = 12.dp) },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${entry.groupName} · ${entry.metricName}")
                Spacer(Modifier.weight(1f))
                Text(
                    "${formatNumber(entry.inputValue)} $unit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("记录时间 ${formatDateTime(entry.recordedAt)}")
                Text(
                    buildString {
                        append("删除于 ${formatTime(entry.deletedAt)} · ${entry.reason}")
                        entry.note?.let { append(" · $it") }
                    },
                    maxLines = 2,
                )
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Outlined.Restore, "恢复", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteForever, "彻底删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}
