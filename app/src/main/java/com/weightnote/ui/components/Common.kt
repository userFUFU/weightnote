package com.weightnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.weightnote.data.db.GroupEntity
import com.weightnote.data.localDateTimeOf
import com.weightnote.data.todayDay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

fun groupColor(group: GroupEntity?): Color = group?.let { Color(it.color) } ?: Color.Gray

@Composable
fun ColorDot(color: Color, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** 单选分组标签 */
@Composable
fun GroupSingleChips(
    groups: List<GroupEntity>,
    selectedId: Long?,
    onSelect: (GroupEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { g ->
            FilterChip(
                selected = g.id == selectedId,
                onClick = { onSelect(g) },
                label = { Text(g.name) },
                leadingIcon = { ColorDot(Color(g.color)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(g.color).copy(alpha = 0.22f),
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label(option), maxLines = 1) },
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(
                    confirmText,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    )
}

// ---------------- 日期格式化 ----------------

private val zh = Locale.SIMPLIFIED_CHINESE

fun weekdayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, zh)

/** 今天 / 昨天 / 10月2日（跨年加年份） */
fun formatDayLabel(day: Long): String {
    val today = todayDay()
    val date = LocalDate.ofEpochDay(day)
    return when (day) {
        today -> "今天"
        today - 1 -> "昨天"
        else -> if (date.year == LocalDate.now().year) {
            "${date.monthValue}月${date.dayOfMonth}日"
        } else {
            "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
        }
    }
}

/** 列表分组标题：10月2日 周五 */
fun formatDayHeader(day: Long): String {
    val date = LocalDate.ofEpochDay(day)
    return "${formatDayLabel(day)} ${weekdayName(date)}"
}

fun formatTime(epochMillis: Long): String {
    val t = localDateTimeOf(epochMillis)
    return String.format(Locale.US, "%02d:%02d", t.hour, t.minute)
}

fun formatDateTime(epochMillis: Long): String {
    val t = localDateTimeOf(epochMillis)
    return "${formatDayLabel(t.toLocalDate().toEpochDay())} ${formatTime(epochMillis)}"
}

fun formatShortDate(day: Long): String {
    val d = LocalDate.ofEpochDay(day)
    return "${d.monthValue}/${d.dayOfMonth}"
}

fun formatFullDate(day: Long): String {
    val d = LocalDate.ofEpochDay(day)
    return "${d.year}/${d.monthValue}/${d.dayOfMonth}"
}
