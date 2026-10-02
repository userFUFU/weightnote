package com.weightnote.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.weightnote.data.GroupPalette
import com.weightnote.data.MeasureUnit
import com.weightnote.data.TimeRange
import com.weightnote.data.UnitType
import com.weightnote.data.db.GroupEntity
import com.weightnote.data.db.ReminderEntity
import com.weightnote.data.formatMinute
import com.weightnote.domain.rangesOverlap
import com.weightnote.reminder.Notifications
import com.weightnote.ui.MainViewModel
import com.weightnote.ui.Session
import com.weightnote.ui.components.ColorDot
import com.weightnote.ui.components.ConfirmDialog
import com.weightnote.ui.components.SectionTitle
import com.weightnote.ui.components.SegmentedChoice
import com.weightnote.ui.components.TimePickerDialog
import kotlinx.coroutines.launch

private fun rangeText(start: Int, end: Int): String =
    "${formatMinute(start)}–${formatMinute(end)}" + if (start > end) "（跨午夜）" else ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    session: Session,
    vm: MainViewModel,
    onBack: () -> Unit,
    onEditGroup: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分组管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEditGroup(0) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("新建分组") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("按时间自动归组", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "开启后，新建记录时按各分组的时间段自动选中分组，仍可手动修改。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = session.profile.autoGroupByTime, onCheckedChange = { vm.setAutoGroup(it) })
                    }
                }
            }
            itemsIndexed(session.groups, key = { _, g -> g.id }) { index, g ->
                val rules = session.rules.filter { it.groupId == g.id }
                val reminders = session.reminders.filter { it.groupId == g.id && it.enabled }
                ListItem(
                    modifier = Modifier.clickable { onEditGroup(g.id) },
                    leadingContent = { ColorDot(Color(g.color), size = 16.dp) },
                    headlineContent = { Text(g.name) },
                    supportingContent = {
                        val parts = buildList {
                            if (rules.isNotEmpty()) add("时间段 " + rules.joinToString("、") { rangeText(it.startMinute, it.endMinute) })
                            if (reminders.isNotEmpty()) add("提醒 " + reminders.joinToString("、") { formatMinute(it.minuteOfDay) })
                            add("单位 " + (g.weightUnit?.let { MeasureUnit.of(it, MeasureUnit.KG).symbol } ?: "跟随身份"))
                        }
                        Text(parts.joinToString(" · "))
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { vm.moveGroup(g.id, -1) }, enabled = index > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, "上移")
                            }
                            IconButton(onClick = { vm.moveGroup(g.id, 1) }, enabled = index < session.groups.size - 1) {
                                Icon(Icons.Default.KeyboardArrowDown, "下移")
                            }
                        }
                    },
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

private data class ReminderDraft(val minute: Int, val enabled: Boolean)

/** 单位选项：null 表示跟随身份 */
private val unitOptions: List<MeasureUnit?> = listOf(null) + MeasureUnit.forType(UnitType.MASS)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditScreen(
    session: Session,
    groupId: Long,
    vm: MainViewModel,
    onBack: () -> Unit,
) {
    val editing = session.groupById[groupId]
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(editing?.name ?: "") }
    var color by remember {
        mutableIntStateOf(
            editing?.color ?: GroupPalette.firstOrNull { c -> session.groups.none { it.color == c } } ?: GroupPalette[0],
        )
    }
    var unit by remember { mutableStateOf(editing?.weightUnit?.let { MeasureUnit.of(it, MeasureUnit.KG) }) }
    val rules = remember {
        mutableStateListOf<TimeRange>().apply {
            addAll(session.rules.filter { it.groupId == groupId }.map { TimeRange(it.startMinute, it.endMinute) })
        }
    }
    val reminders = remember {
        mutableStateListOf<ReminderDraft>().apply {
            addAll(session.reminders.filter { it.groupId == groupId }.map { ReminderDraft(it.minuteOfDay, it.enabled) })
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var pickRuleStart by remember { mutableStateOf(false) }
    var pendingRuleStart by remember { mutableStateOf<Int?>(null) }
    var pickReminder by remember { mutableStateOf(false) }
    var deleteInfo by remember { mutableStateOf<Int?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.toast("未授予通知权限，提醒将无法显示")
    }

    // 与其他分组的时间段重叠检查
    val otherRules = session.rules.filter { it.groupId != groupId }
    val overlaps = rules.flatMap { r ->
        otherRules.filter { rangesOverlap(r, TimeRange(it.startMinute, it.endMinute)) }
            .mapNotNull { session.groupById[it.groupId]?.name }
    }.distinct()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing == null) "新建分组" else "编辑分组") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    TextButton(onClick = {
                        val n = name.trim()
                        if (n.isEmpty()) {
                            error = "请填写分组名称"
                            return@TextButton
                        }
                        if (session.groups.any { it.name == n && it.id != groupId }) {
                            error = "已存在同名分组"
                            return@TextButton
                        }
                        val entity = (editing ?: GroupEntity(profileId = session.profile.id, name = n, color = color))
                            .copy(name = n, color = color, weightUnit = unit?.name)
                        vm.saveGroup(
                            entity,
                            rules.toList(),
                            reminders.map { ReminderEntity(groupId = groupId, minuteOfDay = it.minute, enabled = it.enabled) },
                        )
                        onBack()
                    }) { Text("保存") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(12) },
                label = { Text("分组名称") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )

            SectionTitle("曲线颜色")
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GroupPalette.forEach { c ->
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (c == color) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape,
                            )
                            .clickable { color = c },
                        contentAlignment = Alignment.Center,
                    ) {
                        ColorDot(Color(c), size = if (c == color) 26.dp else 36.dp)
                        if (c == color) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            SectionTitle("体重输入单位")
            SegmentedChoice(
                options = unitOptions,
                selected = unit,
                label = { it?.symbol ?: "跟随身份" },
                onSelect = { unit = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Text(
                "图表和统计统一使用身份的单位（${session.profile.weightUnitEnum.symbol}）显示",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("时间段规则")
            Text(
                if (session.profile.autoGroupByTime) {
                    "记录时间落在以下时间段内时，自动选中本分组"
                } else {
                    "「按时间自动归组」当前已关闭，规则暂不生效（可在分组管理页开启）"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            rules.forEachIndexed { i, r ->
                ListItem(
                    headlineContent = { Text(rangeText(r.start, r.end)) },
                    trailingContent = {
                        IconButton(onClick = { rules.removeAt(i) }) { Icon(Icons.Outlined.DeleteOutline, "删除") }
                    },
                )
            }
            if (overlaps.isNotEmpty()) {
                Text(
                    "与「${overlaps.joinToString("」「")}」的时间段有重叠，重叠时间内优先选择排序靠前的分组",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            TextButton(onClick = { pickRuleStart = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Default.Add, null)
                Text(" 添加时间段")
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("提醒")
            Text(
                "每天到点提醒；如果当天本分组已记录体重，则不提醒",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            reminders.forEachIndexed { i, r ->
                ListItem(
                    headlineContent = { Text(formatMinute(r.minute), style = MaterialTheme.typography.titleMedium) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = r.enabled, onCheckedChange = { reminders[i] = r.copy(enabled = it) })
                            IconButton(onClick = { reminders.removeAt(i) }) { Icon(Icons.Outlined.DeleteOutline, "删除") }
                        }
                    },
                )
            }
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                pickReminder = true
            }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Default.Add, null)
                Text(" 添加提醒")
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            if (editing != null && session.groups.size > 1) {
                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { scope.launch { deleteInfo = vm.countRecordsOfGroup(groupId) } },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) { Text("删除分组") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (pickRuleStart) {
        TimePickerDialog(
            initialMinute = 6 * 60,
            title = "开始时间",
            onConfirm = {
                pendingRuleStart = it
                pickRuleStart = false
            },
            onDismiss = { pickRuleStart = false },
        )
    }
    pendingRuleStart?.let { start ->
        TimePickerDialog(
            initialMinute = (start + 4 * 60) % (24 * 60),
            title = "结束时间（早于开始时间表示跨午夜）",
            onConfirm = { end ->
                if (end == start) vm.toast("开始和结束时间不能相同") else rules.add(TimeRange(start, end))
                pendingRuleStart = null
            },
            onDismiss = { pendingRuleStart = null },
        )
    }
    if (pickReminder) {
        TimePickerDialog(
            initialMinute = 7 * 60 + 30,
            title = "提醒时间",
            onConfirm = { minute ->
                if (reminders.none { it.minute == minute }) {
                    reminders.add(ReminderDraft(minute, true))
                    reminders.sortBy { it.minute }
                }
                pickReminder = false
            },
            onDismiss = { pickReminder = false },
        )
    }
    deleteInfo?.let { count ->
        ConfirmDialog(
            title = "删除分组「${editing?.name}」？",
            text = if (count > 0) "该分组下的 $count 条记录也会被一起删除，无法恢复。" else "该分组下没有记录。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                editing?.let { vm.deleteGroup(it) }
                onBack()
            },
            onDismiss = { deleteInfo = null },
        )
    }
}
