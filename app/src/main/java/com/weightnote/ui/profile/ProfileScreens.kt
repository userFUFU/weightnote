package com.weightnote.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weightnote.data.db.ProfileEntity
import com.weightnote.data.formatNumber
import com.weightnote.ui.MainViewModel
import com.weightnote.ui.Session
import com.weightnote.ui.components.ConfirmDialog

@Composable
fun OnboardingScreen(onCreate: (ProfileEntity) -> Unit) {
    val form = rememberProfileFormState(null)
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("欢迎使用体重记", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "先创建一个身份，选好常用单位。我们会预设「早晨」（04:00–11:00）和「晚上」（18:00–次日 02:00）两个分组，记录时按当前时间自动选择分组，之后可以在 设置 → 分组管理 中修改或关闭。所有数据只保存在本机。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            ProfileForm(form)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { form.build()?.let(onCreate) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("开始使用") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    session: Session,
    profileId: Long,
    vm: MainViewModel,
    onBack: () -> Unit,
) {
    val editing = session.profiles.firstOrNull { it.id == profileId }
    val form = rememberProfileFormState(editing)
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing == null) "新建身份" else "编辑身份") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    TextButton(onClick = {
                        val entity = form.build() ?: return@TextButton
                        if (editing == null) vm.createProfile(entity) { } else vm.updateProfile(entity)
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            ProfileForm(form)
            if (editing != null && session.profiles.size > 1) {
                Spacer(Modifier.height(32.dp))
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("删除该身份") }
            }
        }
    }

    if (confirmDelete && editing != null) {
        ConfirmDialog(
            title = "删除身份「${editing.name}」？",
            text = "该身份下的所有分组、记录和提醒都会被永久删除，无法恢复。建议先导出备份。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                vm.deleteProfile(editing)
                onBack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    session: Session,
    vm: MainViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("身份管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(0) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("新建身份") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(session.profiles, key = { it.id }) { p ->
                ListItem(
                    modifier = Modifier.clickable { vm.switchProfile(p.id) },
                    leadingContent = {
                        RadioButton(selected = p.id == session.profile.id, onClick = { vm.switchProfile(p.id) })
                    },
                    headlineContent = { Text(p.name) },
                    supportingContent = {
                        val parts = buildList {
                            add("体重单位 ${p.weightUnitEnum.symbol}")
                            p.heightCm?.let { add("身高 ${formatNumber(it)} cm") }
                            p.goalWeightKg?.let { add("目标 ${formatNumber(p.weightUnitEnum.fromBase(it))} ${p.weightUnitEnum.symbol}") }
                        }
                        Text(parts.joinToString(" · "))
                    },
                    trailingContent = {
                        IconButton(onClick = { onEdit(p.id) }) { Icon(Icons.Default.Edit, "编辑") }
                    },
                )
            }
        }
    }
}
