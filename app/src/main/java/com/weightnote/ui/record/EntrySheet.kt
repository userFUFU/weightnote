package com.weightnote.ui.record

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weightnote.data.MeasureUnit
import com.weightnote.data.RecordDraft
import com.weightnote.data.UnitType
import com.weightnote.data.db.RecordEntity
import com.weightnote.data.formatNumber
import com.weightnote.data.unitTypeOf
import com.weightnote.domain.suggestGroup
import com.weightnote.ui.MainViewModel
import com.weightnote.ui.Session
import com.weightnote.ui.components.DateTimePickerDialog
import com.weightnote.ui.components.GroupSingleChips
import com.weightnote.ui.components.formatDateTime
import kotlinx.coroutines.launch

sealed interface EntryMode {
    /** 新建体重记录；presetGroupId 用于从首页“今日”卡片或通知直接指定分组 */
    data class NewWeight(val presetGroupId: Long? = null) : EntryMode

    /** 编辑任意指标的一条记录 */
    data class Edit(val record: RecordEntity) : EntryMode
}

/** 记录 / 编辑面板：数字键盘输入，默认带出该分组上次的值 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(
    session: Session,
    mode: EntryMode,
    vm: MainViewModel,
    onDismiss: () -> Unit,
) {
    val editRecord = (mode as? EntryMode.Edit)?.record
    val metric = editRecord?.let { session.metricById[it.metricId] } ?: session.weightMetric ?: return
    val metricType = unitTypeOf(metric.unitType)
    val bodyFatMetric = session.bodyFatMetric?.takeIf { it.enabled && editRecord == null }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var groupId by remember { mutableLongStateOf(initialGroupId(session, mode)) }
    val group = session.groupById[groupId]
    val unit: MeasureUnit = editRecord?.let { MeasureUnit.of(it.inputUnit, session.displayUnit(metric)) }
        ?: session.inputUnit(metric, group)

    var recordedAt by remember { mutableLongStateOf(editRecord?.recordedAt ?: System.currentTimeMillis()) }
    var valueText by remember { mutableStateOf(editRecord?.let { formatNumber(it.inputValue) } ?: "") }
    var valueFresh by remember { mutableStateOf(true) }
    var fatText by remember { mutableStateOf("") }
    var fatFresh by remember { mutableStateOf(false) }
    var activeField by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf(editRecord?.note.orEmpty()) }
    var lastHint by remember { mutableStateOf<String?>(null) }
    var fatHint by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickTime by remember { mutableStateOf(false) }
    var conflicts by remember { mutableStateOf<List<RecordEntity>>(emptyList()) }
    var pendingDrafts by remember { mutableStateOf<List<RecordDraft>>(emptyList()) }

    // 新建时：按分组带出上次的值（用户还没动过键盘时才覆盖）
    LaunchedEffect(groupId, unit) {
        if (editRecord != null) return@LaunchedEffect
        val last = vm.lastRecord(groupId, metric.id)
        if (last != null) {
            lastHint = "上次（${session.groupById[last.groupId]?.name ?: ""}）：" +
                "${formatNumber(unit.fromBase(last.value))} ${unit.symbol} · ${formatDateTime(last.recordedAt)}"
            if (valueFresh) valueText = formatNumber(unit.fromBase(last.value))
        } else {
            lastHint = null
            if (valueFresh) valueText = ""
        }
        bodyFatMetric?.let { fm ->
            fatHint = vm.lastRecord(groupId, fm.id)?.let { "上次 ${formatNumber(it.value)}%" }
        }
    }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    fun buildDrafts(): List<RecordDraft>? {
        error = null
        val v = valueText.toDoubleOrNull()
        if (v == null || v <= 0) {
            error = "请输入${metric.name}"
            return null
        }
        validateValue(metricType, unit.toBase(v))?.let { error = it; return null }
        if (group == null) {
            error = "请先选择分组"
            return null
        }
        val drafts = mutableListOf(
            RecordDraft(
                editingId = editRecord?.id ?: 0,
                groupId = groupId,
                metricId = metric.id,
                inputValue = v,
                inputUnit = unit,
                recordedAt = recordedAt,
                note = note,
            ),
        )
        if (bodyFatMetric != null && fatText.isNotEmpty()) {
            val f = fatText.toDoubleOrNull()
            if (f == null) {
                error = "体脂率格式不正确"
                return null
            }
            validateValue(UnitType.PERCENT, f)?.let { error = it; return null }
            drafts += RecordDraft(
                groupId = groupId,
                metricId = bodyFatMetric.id,
                inputValue = f,
                inputUnit = MeasureUnit.PERCENT,
                recordedAt = recordedAt,
            )
        }
        return drafts
    }

    fun save() {
        val drafts = buildDrafts() ?: return
        scope.launch {
            val found = vm.findConflicts(drafts)
            if (found.isEmpty()) {
                vm.saveDrafts(drafts, overwrite = false, message = if (editRecord == null) "已记录" else "已保存")
                close()
            } else {
                pendingDrafts = drafts
                conflicts = found
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (editRecord == null) "记录${metric.name}" else "编辑${metric.name}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (editRecord != null) {
                    IconButton(onClick = {
                        vm.deleteRecord(editRecord)
                        close()
                    }) { Icon(Icons.Outlined.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error) }
                }
            }
            Spacer(Modifier.height(8.dp))
            GroupSingleChips(session.groups, groupId, onSelect = { groupId = it.id })

            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = if (activeField == 0) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeField = 0 },
            ) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BigValueDisplay(valueText, unit.symbol, fresh = valueFresh && editRecord == null)
                    lastHint?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (bodyFatMetric != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            if (activeField == 1) 2.dp else 1.dp,
                            if (activeField == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeField = 1 },
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("体脂率（可选）", style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (fatText.isEmpty()) (fatHint ?: "未填写") else "$fatText %",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (fatText.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { pickTime = true },
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("时间", style = MaterialTheme.typography.labelSmall)
                            Text(formatDateTime(recordedAt), style = MaterialTheme.typography.titleMedium)
                        }
                        Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(100) },
                placeholder = { Text("备注（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            }

            Spacer(Modifier.height(12.dp))
            NumberKeypad(onKey = { key ->
                focus.clearFocus()
                error = null
                if (activeField == 0) {
                    valueText = applyKey(valueText, valueFresh && editRecord == null, key)
                    valueFresh = false
                } else {
                    fatText = applyKey(fatText, fatFresh, key, maxInt = 2)
                    fatFresh = false
                }
            })
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = ::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("保存") }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickTime) {
        DateTimePickerDialog(
            initialMillis = recordedAt,
            onConfirm = {
                recordedAt = it
                pickTime = false
            },
            onDismiss = { pickTime = false },
        )
    }

    if (conflicts.isNotEmpty()) {
        ConflictDialog(
            session = session,
            conflicts = conflicts,
            onOverwrite = {
                vm.saveDrafts(pendingDrafts, overwrite = true, message = "已覆盖当天的旧记录")
                conflicts = emptyList()
                close()
            },
            onKeepBoth = {
                vm.saveDrafts(pendingDrafts, overwrite = false, message = "已保存，图表将使用最新一条")
                conflicts = emptyList()
                close()
            },
            onDismiss = { conflicts = emptyList() },
        )
    }
}

/** 初始分组：编辑时用原分组；新建时依次取指定分组、时间规则推荐的分组、第一个分组 */
private fun initialGroupId(session: Session, mode: EntryMode): Long {
    if (mode is EntryMode.Edit) return mode.record.groupId
    val preset = (mode as EntryMode.NewWeight).presetGroupId?.takeIf { session.groupById.containsKey(it) }
    if (preset != null) return preset
    if (session.profile.autoGroupByTime) {
        suggestGroup(session.groups, session.rules, System.currentTimeMillis())?.let { return it.id }
    }
    return session.groups.firstOrNull()?.id ?: 0L
}
