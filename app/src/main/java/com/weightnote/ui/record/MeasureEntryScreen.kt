package com.weightnote.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.weightnote.data.RecordDraft
import com.weightnote.data.UnitType
import com.weightnote.data.db.RecordEntity
import com.weightnote.data.formatNumber
import com.weightnote.domain.suggestGroup
import com.weightnote.ui.MainViewModel
import com.weightnote.ui.Session
import com.weightnote.ui.components.DateTimePickerDialog
import com.weightnote.ui.components.GroupSingleChips
import com.weightnote.ui.components.formatDateTime
import com.weightnote.ui.profile.filterNumber
import kotlinx.coroutines.launch

/** 围度录入：一次可以填多项，空着的不记录 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureEntryScreen(session: Session, vm: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val metrics = session.lengthMetrics
    val unit = session.profile.lengthUnitEnum

    var recordedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var groupId by remember {
        mutableLongStateOf(
            (if (session.profile.autoGroupByTime) suggestGroup(session.groups, session.rules, recordedAt)?.id else null)
                ?: session.groups.firstOrNull()?.id ?: 0L,
        )
    }
    var groupManual by remember { mutableStateOf(false) }
    val suggested = if (session.profile.autoGroupByTime) suggestGroup(session.groups, session.rules, recordedAt) else null
    LaunchedEffect(suggested?.id) {
        if (!groupManual && suggested != null) groupId = suggested.id
    }
    val values = remember { mutableStateMapOf<Long, String>() }
    val hints = remember { mutableStateMapOf<Long, String>() }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pickTime by remember { mutableStateOf(false) }
    var conflicts by remember { mutableStateOf<List<RecordEntity>>(emptyList()) }
    var pendingDrafts by remember { mutableStateOf<List<RecordDraft>>(emptyList()) }

    LaunchedEffect(groupId) {
        metrics.forEach { m ->
            val last = vm.lastRecord(groupId, m.id)
            if (last != null) hints[m.id] = "上次 ${formatNumber(unit.fromBase(last.value))}" else hints.remove(m.id)
        }
    }

    fun save() {
        error = null
        val drafts = mutableListOf<RecordDraft>()
        for (m in metrics) {
            val text = values[m.id].orEmpty()
            if (text.isEmpty()) continue
            val v = text.toDoubleOrNull()
            if (v == null || v <= 0) {
                error = "${m.name}格式不正确"
                return
            }
            validateValue(UnitType.LENGTH, unit.toBase(v))?.let {
                error = "${m.name}：$it"
                return
            }
            drafts += RecordDraft(
                groupId = groupId,
                metricId = m.id,
                inputValue = v,
                inputUnit = unit,
                recordedAt = recordedAt,
                note = note,
            )
        }
        if (drafts.isEmpty()) {
            error = "至少填写一项"
            return
        }
        scope.launch {
            val found = vm.findConflicts(drafts)
            if (found.isEmpty()) {
                vm.saveDrafts(drafts, overwrite = false, message = "已记录 ${drafts.size} 项围度")
                onBack()
            } else {
                pendingDrafts = drafts
                conflicts = found
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记录围度") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = { TextButton(onClick = ::save) { Text("保存") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                GroupSingleChips(session.groups, groupId, onSelect = {
                    groupId = it.id
                    groupManual = true
                })
                AutoGroupHint(
                    session = session,
                    recordedAt = recordedAt,
                    suggested = suggested,
                    selectedGroupId = groupId,
                    onEnable = {
                        groupManual = false
                        vm.setAutoGroup(true)
                    },
                )
            }
            ListItem(
                modifier = Modifier.clickable { pickTime = true },
                headlineContent = { Text(formatDateTime(recordedAt)) },
                overlineContent = { Text("时间") },
                trailingContent = { Icon(Icons.Outlined.Schedule, null) },
            )
            if (metrics.isEmpty()) {
                Text(
                    "没有启用的围度项目，请到 设置 → 指标管理 中开启。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            metrics.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = values[m.id].orEmpty(),
                        onValueChange = { values[m.id] = it.filterNumber() },
                        label = { Text(m.name) },
                        placeholder = { Text(hints[m.id] ?: "") },
                        suffix = { Text(unit.symbol) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(100) },
                label = { Text("备注（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
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
                onBack()
            },
            onKeepBoth = {
                vm.saveDrafts(pendingDrafts, overwrite = false, message = "已保存，图表将使用最新一条")
                conflicts = emptyList()
                onBack()
            },
            onDismiss = { conflicts = emptyList() },
        )
    }
}
