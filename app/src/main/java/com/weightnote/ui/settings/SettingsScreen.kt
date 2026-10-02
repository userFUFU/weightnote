package com.weightnote.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.weightnote.data.ThemeMode
import com.weightnote.ui.Session
import com.weightnote.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    session: Session,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onEditProfile: (Long) -> Unit,
    onProfiles: () -> Unit,
    onGroups: () -> Unit,
    onMetrics: () -> Unit,
    onBackup: () -> Unit,
) {
    var themeDialog by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("设置") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("身份")
            NavItem(
                Icons.Outlined.Person,
                "当前身份：${session.profile.name}",
                "昵称、身高、单位、目标体重",
            ) { onEditProfile(session.profile.id) }
            NavItem(Icons.Outlined.ManageAccounts, "身份管理", "共 ${session.profiles.size} 个身份，数据互相独立", onProfiles)

            SectionTitle("记录")
            NavItem(
                Icons.Outlined.Category,
                "分组管理",
                "${session.groups.size} 个分组 · 按时间自动归组${if (session.profile.autoGroupByTime) "已开启" else "已关闭"} · 提醒",
                onGroups,
            )
            val enabledCount = session.metrics.count { it.enabled }
            NavItem(Icons.Outlined.Straighten, "指标管理", "已启用 $enabledCount 项（体重、体脂率、围度）", onMetrics)

            SectionTitle("通用")
            NavItem(Icons.Outlined.Palette, "外观", themeMode.label) { themeDialog = true }
            NavItem(Icons.Outlined.Backup, "数据备份", "导出、导入、定期自动备份", onBackup)

            SectionTitle("关于")
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Info, null) },
                headlineContent = { Text("体重记 1.0.0") },
                supportingContent = { Text("所有数据仅保存在本机，不联网、不需要账号") },
            )
        }
    }

    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text("外观") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = mode == themeMode,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onThemeChange(mode)
                                        themeDialog = false
                                    },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = mode == themeMode, onClick = null)
                            Text(mode.label, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themeDialog = false }) { Text("关闭") } },
        )
    }
}

@Composable
fun NavItem(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
    )
}
