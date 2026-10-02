package com.weightnote.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.weightnote.data.UnitType
import com.weightnote.data.db.MetricEntity
import com.weightnote.data.db.MetricKeys
import com.weightnote.data.unitTypeOf
import com.weightnote.ui.MainViewModel
import com.weightnote.ui.Session
import com.weightnote.ui.components.ConfirmDialog
import com.weightnote.ui.components.SectionTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsScreen(session: Session, vm: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nameDialog by remember { mutableStateOf<MetricEntity?>(null) }
    var addDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Pair<MetricEntity, Int>?>(null) }

    val body = session.metrics.filter { unitTypeOf(it.unitType) != UnitType.LENGTH }
    val lengths = session.metrics.filter { unitTypeOf(it.unitType) == UnitType.LENGTH }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("指标管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addDialog = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("添加围度项目") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item { SectionTitle("身体指标") }
            items(body, key = { it.id }) { m ->
                ListItem(
                    headlineContent = { Text(m.name) },
                    supportingContent = {
                        Text(
                            when (m.key) {
                                MetricKeys.WEIGHT -> "必选 · BMI 由体重和身高自动计算"
                                MetricKeys.BODY_FAT -> "记录体重时可选填"
                                else -> ""
                            },
                        )
                    },
                    trailingContent = {
                        if (m.key != MetricKeys.WEIGHT) {
                            Switch(checked = m.enabled, onCheckedChange = { vm.setMetricEnabled(m, it) })
                        }
                    },
                )
            }
            item { SectionTitle("围度（单位 ${session.profile.lengthUnitEnum.symbol}）") }
            items(lengths, key = { it.id }) { m ->
                ListItem(
                    headlineContent = { Text(m.name) },
                    supportingContent = if (m.builtIn) null else { { Text("自定义") } },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!m.builtIn) {
                                IconButton(onClick = { nameDialog = m }) { Icon(Icons.Outlined.Edit, "重命名") }
                                IconButton(onClick = {
                                    scope.launch { deleteTarget = m to vm.countRecordsOfMetric(m.id) }
                                }) { Icon(Icons.Outlined.DeleteOutline, "删除") }
                            }
                            Switch(checked = m.enabled, onCheckedChange = { vm.setMetricEnabled(m, it) })
                        }
                    },
                )
            }
            item {
                Text(
                    "关闭的指标不会出现在录入页面，已有记录仍会保留，可在图表和记录列表中查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (addDialog) {
        NameDialog(
            title = "添加围度项目",
            initial = "",
            onConfirm = {
                vm.addMetric(it)
                addDialog = false
            },
            onDismiss = { addDialog = false },
        )
    }
    nameDialog?.let { m ->
        NameDialog(
            title = "重命名",
            initial = m.name,
            onConfirm = {
                vm.renameMetric(m, it)
                nameDialog = null
            },
            onDismiss = { nameDialog = null },
        )
    }
    deleteTarget?.let { (m, count) ->
        ConfirmDialog(
            title = "删除「${m.name}」？",
            text = if (count > 0) "该项目的 $count 条记录也会被一起删除，无法恢复。" else "该项目还没有记录。",
            confirmText = "删除",
            destructive = true,
            onConfirm = { vm.deleteMetric(m) },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(10) },
                placeholder = { Text("例如：颈围、小腿围") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
