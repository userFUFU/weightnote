package com.weightnote.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weightnote.AppContainer
import com.weightnote.data.BackupFrequency
import com.weightnote.data.BackupSettings
import com.weightnote.data.MeasureUnit
import com.weightnote.data.RecordDraft
import com.weightnote.data.ThemeMode
import com.weightnote.data.TimeRange
import com.weightnote.data.UnitType
import com.weightnote.data.backup.BackupScheduler
import com.weightnote.data.db.GroupEntity
import com.weightnote.data.db.GroupTimeRuleEntity
import com.weightnote.data.db.MetricEntity
import com.weightnote.data.db.MetricKeys
import com.weightnote.data.db.ProfileEntity
import com.weightnote.data.db.RecordEntity
import com.weightnote.data.db.ReminderEntity
import com.weightnote.data.db.TrashRecordEntity
import com.weightnote.data.unitTypeOf
import com.weightnote.widget.WeightWidget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 当前身份下的全部数据快照 */
data class Session(
    val profile: ProfileEntity,
    val profiles: List<ProfileEntity>,
    val groups: List<GroupEntity>,
    val rules: List<GroupTimeRuleEntity>,
    val reminders: List<ReminderEntity>,
    val metrics: List<MetricEntity>,
    /** 按时间倒序 */
    val records: List<RecordEntity>,
    /** 回收站，按删除时间倒序 */
    val trash: List<TrashRecordEntity> = emptyList(),
) {
    val groupById: Map<Long, GroupEntity> = groups.associateBy { it.id }
    val metricById: Map<Long, MetricEntity> = metrics.associateBy { it.id }
    val weightMetric: MetricEntity? = metrics.firstOrNull { it.key == MetricKeys.WEIGHT }
    val bodyFatMetric: MetricEntity? = metrics.firstOrNull { it.key == MetricKeys.BODY_FAT }
    val lengthMetrics: List<MetricEntity> =
        metrics.filter { it.enabled && unitTypeOf(it.unitType) == UnitType.LENGTH }

    /** 图表、统计中使用的显示单位 */
    fun displayUnit(metric: MetricEntity): MeasureUnit = when (unitTypeOf(metric.unitType)) {
        UnitType.MASS -> profile.weightUnitEnum
        UnitType.LENGTH -> profile.lengthUnitEnum
        UnitType.PERCENT -> MeasureUnit.PERCENT
    }

    /** 录入时使用的单位：体重优先使用分组的单位设置 */
    fun inputUnit(metric: MetricEntity, group: GroupEntity?): MeasureUnit =
        if (unitTypeOf(metric.unitType) == UnitType.MASS) {
            group?.weightUnit?.let { MeasureUnit.of(it, profile.weightUnitEnum) } ?: profile.weightUnitEnum
        } else {
            displayUnit(metric)
        }
}

sealed interface MainUiState {
    data object Loading : MainUiState
    data object Onboarding : MainUiState
    data class Ready(val session: Session) : MainUiState
}

data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

/** 从通知 / 小组件点进来时，请求打开记录面板；groupId 为空表示按时间自动选分组 */
data class EntryRequest(val groupId: Long?, val nonce: Long = System.nanoTime())

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(
    private val app: Application,
    private val c: AppContainer,
) : ViewModel() {

    private val repo = c.repository
    private val settings = c.settings

    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val backupSettings: StateFlow<BackupSettings> =
        settings.backup.stateIn(viewModelScope, SharingStarted.Eagerly, BackupSettings())

    val uiState: StateFlow<MainUiState> =
        combine(settings.currentProfileId, repo.observeProfiles()) { id, profiles -> id to profiles }
            .flatMapLatest { (id, profiles) ->
                if (profiles.isEmpty()) {
                    flowOf(MainUiState.Onboarding)
                } else {
                    val profile = profiles.firstOrNull { it.id == id } ?: profiles.first()
                    combine(
                        repo.observeGroups(profile.id),
                        repo.observeRules(profile.id),
                        repo.observeReminders(profile.id),
                        repo.observeMetrics(profile.id),
                        repo.observeRecords(profile.id),
                        repo.observeTrash(profile.id),
                    ) { values ->
                        Session(
                            profile = profile,
                            profiles = profiles,
                            groups = values[0] as List<GroupEntity>,
                            rules = values[1] as List<GroupTimeRuleEntity>,
                            reminders = values[2] as List<ReminderEntity>,
                            metrics = values[3] as List<MetricEntity>,
                            records = values[4] as List<RecordEntity>,
                            trash = values[5] as List<TrashRecordEntity>,
                        )
                    }.map { MainUiState.Ready(it) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages

    val entryRequest = MutableStateFlow<EntryRequest?>(null)

    private val session: Session? get() = (uiState.value as? MainUiState.Ready)?.session

    init {
        // 启动时清理超过保留期的回收站记录
        viewModelScope.launch { repo.purgeExpiredTrash() }
        // 记录、分组、身份变化后刷新桌面小组件
        viewModelScope.launch {
            uiState
                .mapNotNull { (it as? MainUiState.Ready)?.session }
                .map { listOf(it.profile, it.groups, it.rules, it.records) }
                .distinctUntilChanged()
                .debounce(800)
                .collect { WeightWidget.refresh(app) }
        }
    }

    fun toast(text: String) {
        _messages.tryEmit(UiMessage(text))
    }

    // ---------------- 身份 ----------------

    fun switchProfile(id: Long) = viewModelScope.launch { settings.setCurrentProfile(id) }

    fun createProfile(profile: ProfileEntity, onCreated: () -> Unit = {}) = viewModelScope.launch {
        val id = repo.createProfile(profile)
        settings.setCurrentProfile(id)
        onCreated()
    }

    fun updateProfile(profile: ProfileEntity) = viewModelScope.launch {
        repo.updateProfile(profile)
    }

    fun deleteProfile(profile: ProfileEntity) = viewModelScope.launch {
        repo.deleteProfile(profile)
        toast("已删除身份「${profile.name}」")
    }

    fun setAutoGroup(enabled: Boolean) = viewModelScope.launch {
        session?.profile?.let { repo.updateProfile(it.copy(autoGroupByTime = enabled)) }
    }

    // ---------------- 记录 ----------------

    suspend fun lastRecord(groupId: Long, metricId: Long): RecordEntity? =
        repo.lastRecord(groupId, metricId) ?: repo.lastRecordOfMetric(metricId)

    suspend fun findConflicts(drafts: List<RecordDraft>): List<RecordEntity> {
        val profileId = session?.profile?.id ?: return emptyList()
        return repo.findConflicts(profileId, drafts)
    }

    fun saveDrafts(drafts: List<RecordDraft>, overwrite: Boolean, message: String? = null) = viewModelScope.launch {
        val profileId = session?.profile?.id ?: return@launch
        val toTrash = if (overwrite) repo.findConflicts(profileId, drafts).size else 0
        repo.saveDrafts(profileId, drafts, overwrite)
        when {
            toTrash > 0 -> toast("${message ?: "已保存"}；被覆盖的 $toTrash 条记录已移入回收站")
            else -> message?.let { toast(it) }
        }
    }

    fun deleteRecord(record: RecordEntity) = viewModelScope.launch {
        val trashId = repo.deleteRecord(record)
        _messages.tryEmit(
            UiMessage("已移入回收站，7 天内可恢复", "撤销") {
                viewModelScope.launch {
                    trashId?.let { repo.restoreFromTrash(it) }
                }
            },
        )
    }

    // ---------------- 回收站 ----------------

    suspend fun countTrash(): Int = session?.profile?.id?.let { repo.countTrash(it) } ?: 0

    /** 清理超过保留期的记录，返回清除条数 */
    fun purgeExpiredTrash() = viewModelScope.launch {
        val removed = repo.purgeExpiredTrash()
        if (removed > 0) toast("已清除 $removed 条超过 7 天的回收站记录")
    }

    fun restoreTrash(entry: TrashRecordEntity) = viewModelScope.launch {
        repo.restoreFromTrash(entry.id)
            .onSuccess {
                toast("已恢复")
                purgeExpiredTrash()
            }
            .onFailure { toast("恢复失败：${it.message}") }
    }

    fun deleteTrashEntry(entry: TrashRecordEntity) = viewModelScope.launch {
        repo.deleteTrashEntry(entry)
    }

    fun clearTrash() = viewModelScope.launch {
        session?.profile?.id?.let { repo.clearTrash(it) }
        toast("回收站已清空")
    }

    // ---------------- 分组 ----------------

    fun saveGroup(group: GroupEntity, rules: List<TimeRange>, reminders: List<ReminderEntity>) =
        viewModelScope.launch {
            repo.saveGroup(group, rules, reminders)
        }

    fun deleteGroup(group: GroupEntity) = viewModelScope.launch {
        repo.deleteGroup(group)
        toast("已删除分组「${group.name}」")
    }

    fun moveGroup(groupId: Long, delta: Int) = viewModelScope.launch {
        session?.profile?.id?.let { repo.moveGroup(it, groupId, delta) }
    }

    suspend fun countRecordsOfGroup(groupId: Long): Int = repo.countRecordsOfGroup(groupId)

    // ---------------- 指标 ----------------

    fun setMetricEnabled(metric: MetricEntity, enabled: Boolean) = viewModelScope.launch {
        repo.updateMetric(metric.copy(enabled = enabled))
    }

    fun renameMetric(metric: MetricEntity, name: String) = viewModelScope.launch {
        repo.updateMetric(metric.copy(name = name))
    }

    fun addMetric(name: String) = viewModelScope.launch {
        session?.profile?.id?.let { repo.addCustomMetric(it, name) }
    }

    fun deleteMetric(metric: MetricEntity) = viewModelScope.launch {
        repo.deleteMetric(metric)
        toast("已删除指标「${metric.name}」")
    }

    suspend fun countRecordsOfMetric(metricId: Long): Int = repo.countRecordsOfMetric(metricId)

    // ---------------- 外观 ----------------

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setTheme(mode) }

    // ---------------- 备份 ----------------

    fun exportJson(uri: Uri) = viewModelScope.launch {
        runCatching { c.backupManager.exportJson(uri) }
            .onSuccess { toast("已导出全部身份，共 $it 条记录") }
            .onFailure { toast("导出失败：${it.message}") }
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        val profileId = session?.profile?.id ?: return@launch
        runCatching { c.backupManager.exportCsv(uri, profileId) }
            .onSuccess { toast("已导出 CSV，共 $it 条记录") }
            .onFailure { toast("导出失败：${it.message}") }
    }

    fun importBackup(uri: Uri, replace: Boolean) = viewModelScope.launch {
        runCatching { c.backupManager.import(uri, replace) }
            .onSuccess { summary ->
                if (replace || session == null) summary.firstProfileId?.let { settings.setCurrentProfile(it) }
                toast("导入完成：${summary.profiles} 个身份，${summary.records} 条记录")
            }
            .onFailure { toast("导入失败：${it.message}") }
    }

    fun setBackupFolder(uri: Uri) = viewModelScope.launch {
        settings.setBackupFolder(uri.toString())
        applyBackupSchedule()
    }

    fun setBackupFrequency(frequency: BackupFrequency) = viewModelScope.launch {
        settings.setBackupFrequency(frequency)
        applyBackupSchedule()
    }

    fun setBackupKeep(count: Int) = viewModelScope.launch { settings.setBackupKeep(count) }

    fun backupNow() = viewModelScope.launch {
        c.backupManager.autoBackup()
            .onSuccess { toast("备份成功：$it") }
            .onFailure { toast("备份失败：${it.message}") }
    }

    private suspend fun applyBackupSchedule() {
        BackupScheduler.apply(app, settings.backupNow())
    }
}
