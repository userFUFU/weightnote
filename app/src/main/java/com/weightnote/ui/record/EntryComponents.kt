package com.weightnote.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weightnote.data.MeasureUnit
import com.weightnote.data.UnitType
import com.weightnote.data.db.GroupEntity
import com.weightnote.data.db.RecordEntity
import com.weightnote.data.formatNumber
import com.weightnote.ui.Session
import com.weightnote.ui.components.ColorDot
import com.weightnote.ui.components.formatDateTime
import com.weightnote.ui.components.formatTime
import com.weightnote.ui.components.groupColor

/** 自定义数字键盘的按键 */
sealed interface KeypadKey {
    data class Digit(val ch: Char) : KeypadKey
    data object Dot : KeypadKey
    data object Backspace : KeypadKey
}

/**
 * 计算按键后的新文本。
 * @param fresh 当前文本是否为“预填值”，预填状态下第一次输入会整体替换
 */
fun applyKey(current: String, fresh: Boolean, key: KeypadKey, maxInt: Int = 3, maxDecimals: Int = 1): String {
    val base = if (fresh) "" else current
    return when (key) {
        is KeypadKey.Digit -> {
            if (base == "0") return key.ch.toString()
            val dot = base.indexOf('.')
            if (dot >= 0) {
                if (base.length - dot - 1 >= maxDecimals) base else base + key.ch
            } else {
                if (base.length >= maxInt) base else base + key.ch
            }
        }
        KeypadKey.Dot -> when {
            base.isEmpty() -> "0."
            base.contains('.') -> base
            else -> "$base."
        }
        KeypadKey.Backspace -> if (fresh) "" else current.dropLast(1)
    }
}

@Composable
fun NumberKeypad(onKey: (KeypadKey) -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf(
        listOf(KeypadKey.Digit('1'), KeypadKey.Digit('2'), KeypadKey.Digit('3')),
        listOf(KeypadKey.Digit('4'), KeypadKey.Digit('5'), KeypadKey.Digit('6')),
        listOf(KeypadKey.Digit('7'), KeypadKey.Digit('8'), KeypadKey.Digit('9')),
        listOf(KeypadKey.Dot, KeypadKey.Digit('0'), KeypadKey.Backspace),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = { onKey(key) },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                    ) {
                        when (key) {
                            is KeypadKey.Digit -> Text(key.ch.toString(), fontSize = 22.sp)
                            KeypadKey.Dot -> Text(".", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            KeypadKey.Backspace -> Icon(Icons.AutoMirrored.Outlined.Backspace, "删除")
                        }
                    }
                }
            }
        }
    }
}

/** 合理范围校验（基础单位），返回错误信息或 null */
fun validateValue(type: UnitType, base: Double): String? = when (type) {
    UnitType.MASS -> if (base !in 2.0..400.0) "体重数值超出合理范围" else null
    UnitType.PERCENT -> if (base !in 1.0..80.0) "体脂率需在 1%~80% 之间" else null
    UnitType.LENGTH -> if (base !in 5.0..300.0) "围度数值超出合理范围" else null
}

fun recordValueText(record: RecordEntity, session: Session): String {
    val metric = session.metricById[record.metricId]
    val unit = if (metric == null) record.inputUnitEnum else {
        MeasureUnit.of(record.inputUnit, session.displayUnit(metric))
    }
    return "${formatNumber(record.inputValue)} ${unit.symbol}"
}

/** “同一天同组已有记录”提示框：默认覆盖，可选保留两条 */
@Composable
fun ConflictDialog(
    session: Session,
    conflicts: List<RecordEntity>,
    onOverwrite: () -> Unit,
    onKeepBoth: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("当天已有记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                conflicts.forEach { r ->
                    val group = session.groupById[r.groupId]
                    val metric = session.metricById[r.metricId]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(groupColor(group))
                        Text(
                            "  「${group?.name}」${metric?.name} ${recordValueText(r, session)}（${formatDateTime(r.recordedAt)}）",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Text(
                    "覆盖：删除旧记录，只保留本次。\n保留两条：两条都保存，图表中使用时间最新的一条。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOverwrite) { Text("覆盖") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("取消") }
                TextButton(onClick = onKeepBoth) { Text("保留两条") }
            }
        },
    )
}

/**
 * 分组下方的提示：
 * - 已开启自动归组：说明按哪个时间选中了哪个分组
 * - 未开启但配置了时间段：提供一键开启
 */
@Composable
fun AutoGroupHint(
    session: Session,
    recordedAt: Long,
    suggested: GroupEntity?,
    selectedGroupId: Long,
    onEnable: () -> Unit,
) {
    val style = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val time = formatTime(recordedAt)
    when {
        session.profile.autoGroupByTime && suggested != null -> Text(
            if (suggested.id == selectedGroupId) {
                "已按时间 $time 自动选择「${suggested.name}」"
            } else {
                "按时间 $time 应为「${suggested.name}」，当前为手动选择"
            },
            style = style,
            color = muted,
            modifier = Modifier.padding(top = 4.dp),
        )
        session.profile.autoGroupByTime && session.rules.isNotEmpty() -> Text(
            "$time 不在任何分组的时间段内，请手动选择分组",
            style = style,
            color = muted,
            modifier = Modifier.padding(top = 4.dp),
        )
        !session.profile.autoGroupByTime && session.rules.isNotEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("按时间自动选择分组未开启", style = style, color = muted, modifier = Modifier.weight(1f))
            TextButton(onClick = onEnable) { Text("开启") }
        }
    }
}

@Composable
fun BigValueDisplay(
    text: String,
    unit: String,
    fresh: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = when {
        text.isEmpty() -> MaterialTheme.colorScheme.outline
        fresh -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text.ifEmpty { "0.0" },
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                " $unit",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
}
