package com.weightnote.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weightnote.data.BackupFrequency
import com.weightnote.ui.MainViewModel
import com.weightnote.ui.Session
import com.weightnote.ui.components.SectionTitle
import com.weightnote.ui.components.SegmentedChoice
import com.weightnote.ui.components.formatDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(session: Session, vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val backup by vm.backupSettings.collectAsStateWithLifecycle()
    var importUri by remember { mutableStateOf<Uri?>(null) }
    val stamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportJson(it) }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.exportCsv(it) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        importUri = uri
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            vm.setBackupFolder(uri)
        }
    }
    val folderName = remember(backup.folderUri) {
        backup.folderUri?.let { runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }.getOrNull() ?: it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据备份") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("手动导出 / 导入")
            ListItem(
                modifier = Modifier.clickable { exportJson.launch("weightnote-backup-$stamp.json") },
                leadingContent = { Icon(Icons.Outlined.FileDownload, null) },
                headlineContent = { Text("导出完整备份（JSON）") },
                supportingContent = { Text("包含全部身份、分组、指标和记录，可用于恢复") },
            )
            ListItem(
                modifier = Modifier.clickable { exportCsv.launch("weightnote-${session.profile.name}-$stamp.csv") },
                leadingContent = { Icon(Icons.Outlined.TableChart, null) },
                headlineContent = { Text("导出表格（CSV）") },
                supportingContent = { Text("当前身份「${session.profile.name}」的记录，可用 Excel 打开") },
            )
            ListItem(
                modifier = Modifier.clickable { openBackup.launch(arrayOf("*/*")) },
                leadingContent = { Icon(Icons.Outlined.FileUpload, null) },
                headlineContent = { Text("从备份恢复") },
                supportingContent = { Text("支持手动导出的 .json 和自动备份的 .json.gz 文件") },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("定期自动备份")
            ListItem(
                modifier = Modifier.clickable { pickFolder.launch(null) },
                leadingContent = { Icon(Icons.Outlined.Folder, null) },
                headlineContent = { Text(if (folderName == null) "选择备份文件夹" else "备份到：$folderName") },
                supportingContent = { Text("建议选择不会被清理的文件夹，如「Documents」下新建一个目录") },
            )
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("备份频率", style = MaterialTheme.typography.labelLarge)
                SegmentedChoice(
                    options = BackupFrequency.entries,
                    selected = backup.frequency,
                    label = { it.label },
                    onSelect = { vm.setBackupFrequency(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("保留份数", style = MaterialTheme.typography.labelLarge)
                SegmentedChoice(
                    options = listOf(3, 5, 10, 30),
                    selected = backup.keepCount,
                    label = { "$it 份" },
                    onSelect = { vm.setBackupKeep(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    when {
                        backup.folderUri == null -> "尚未选择文件夹，自动备份未启用"
                        backup.frequency == BackupFrequency.OFF -> "自动备份已关闭"
                        else -> "已启用：${backup.frequency.label}自动备份一次（系统会在设备空闲时执行），超出份数的旧备份会被自动删除"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                backup.lastBackupAt?.let { at ->
                    Text(
                        "上次备份：${formatDateTime(at)}\n${backup.lastBackupResult.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = { vm.backupNow() },
                    enabled = backup.folderUri != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("立即备份") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    importUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text("恢复方式") },
            text = {
                Text(
                    "追加导入：备份中的身份作为新身份加入，现有数据不变。\n\n" +
                        "覆盖恢复：先清空本机全部数据，再恢复备份内容。此操作不可撤销。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.importBackup(uri, replace = true)
                    importUri = null
                }) { Text("覆盖恢复", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.foundation.layout.Row {
                    TextButton(onClick = { importUri = null }) { Text("取消") }
                    TextButton(onClick = {
                        vm.importBackup(uri, replace = false)
                        importUri = null
                    }) { Text("追加导入") }
                }
            },
        )
    }
}
