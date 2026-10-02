package com.weightnote.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.weightnote.data.MeasureUnit
import com.weightnote.data.SettingsStore
import com.weightnote.data.dayOf
import com.weightnote.data.db.AppDatabase
import com.weightnote.data.db.GroupEntity
import com.weightnote.data.db.GroupTimeRuleEntity
import com.weightnote.data.db.MetricEntity
import com.weightnote.data.db.ProfileEntity
import com.weightnote.data.db.RecordEntity
import com.weightnote.data.db.ReminderEntity
import com.weightnote.data.formatNumber
import com.weightnote.data.localDateTimeOf
import com.weightnote.data.unitTypeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class ImportSummary(val profiles: Int, val records: Int, val firstProfileId: Long?)

class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    /** 导入完成后回调（重新安排提醒等） */
    private val onDataChanged: suspend () -> Unit,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    // ---------------- 生成 / 解析 ----------------

    suspend fun buildBackup(profileIds: Set<Long>? = null): BackupFile = withContext(Dispatchers.IO) {
        val groupDao = db.groupDao()
        val metricDao = db.metricDao()
        val recordDao = db.recordDao()
        val profiles = db.profileDao().getAll().filter { profileIds == null || it.id in profileIds }
        BackupFile(
            exportedAt = System.currentTimeMillis(),
            profiles = profiles.map { p ->
                ProfileBackup(
                    name = p.name,
                    heightCm = p.heightCm,
                    gender = p.gender,
                    birthYear = p.birthYear,
                    weightUnit = p.weightUnit,
                    lengthUnit = p.lengthUnit,
                    goalWeightKg = p.goalWeightKg,
                    autoGroupByTime = p.autoGroupByTime,
                    createdAt = p.createdAt,
                    groups = groupDao.getByProfile(p.id).map { g ->
                        GroupBackup(
                            id = g.id,
                            name = g.name,
                            color = g.color,
                            weightUnit = g.weightUnit,
                            sortOrder = g.sortOrder,
                            rules = groupDao.getRules(g.id).map { RuleBackup(it.startMinute, it.endMinute) },
                            reminders = groupDao.getReminders(g.id).map { ReminderBackup(it.minuteOfDay, it.enabled) },
                        )
                    },
                    metrics = metricDao.getByProfile(p.id).map { m ->
                        MetricBackup(m.id, m.key, m.name, m.unitType, m.builtIn, m.enabled, m.sortOrder)
                    },
                    records = recordDao.getByProfile(p.id).map { r ->
                        RecordBackup(r.groupId, r.metricId, r.inputValue, r.inputUnit, r.recordedAt, r.note)
                    },
                )
            },
        )
    }

    fun encode(file: BackupFile, gzip: Boolean): ByteArray {
        val bytes = json.encodeToString(BackupFile.serializer(), file).toByteArray(Charsets.UTF_8)
        if (!gzip) return bytes
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): BackupFile {
        val isGzip = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        val raw = if (isGzip) GZIPInputStream(bytes.inputStream()).use { it.readBytes() } else bytes
        return json.decodeFromString(BackupFile.serializer(), raw.toString(Charsets.UTF_8))
    }

    // ---------------- 手动导出 ----------------

    suspend fun exportJson(uri: Uri): Int = withContext(Dispatchers.IO) {
        val backup = buildBackup()
        writeUri(uri, encode(backup, gzip = false))
        backup.profiles.sumOf { it.records.size }
    }

    suspend fun exportCsv(uri: Uri, profileId: Long): Int = withContext(Dispatchers.IO) {
        val groups = db.groupDao().getByProfile(profileId).associateBy { it.id }
        val metrics = db.metricDao().getByProfile(profileId).associateBy { it.id }
        val records = db.recordDao().getByProfile(profileId)
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val sb = StringBuilder("\uFEFF") // BOM，方便 Excel 识别 UTF-8
        sb.append("时间,分组,指标,数值,单位,标准值,标准单位,备注\n")
        records.forEach { r ->
            val metric = metrics[r.metricId]
            val base = MeasureUnit.baseOf(unitTypeOf(metric?.unitType ?: "MASS"))
            val unit = MeasureUnit.of(r.inputUnit, base)
            sb.append(
                listOf(
                    localDateTimeOf(r.recordedAt).format(fmt),
                    groups[r.groupId]?.name.orEmpty(),
                    metric?.name.orEmpty(),
                    formatNumber(r.inputValue),
                    unit.symbol,
                    formatNumber(r.value, 2),
                    base.symbol,
                    r.note.orEmpty(),
                ).joinToString(",") { csvEscape(it) },
            ).append('\n')
        }
        writeUri(uri, sb.toString().toByteArray(Charsets.UTF_8))
        records.size
    }

    private fun csvEscape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun writeUri(uri: Uri, bytes: ByteArray) {
        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("无法写入文件")
        out.use { it.write(bytes) }
    }

    // ---------------- 导入 ----------------

    /**
     * @param replace true：清空现有全部数据后恢复；false：将备份中的身份作为新身份追加
     */
    suspend fun import(uri: Uri, replace: Boolean): ImportSummary = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取文件")
        val backup = runCatching { decode(bytes) }.getOrElse { error("文件格式不正确，不是有效的备份文件") }
        val summary = restore(backup, replace)
        onDataChanged()
        summary
    }

    private suspend fun restore(backup: BackupFile, replace: Boolean): ImportSummary = db.withTransaction {
        val profileDao = db.profileDao()
        val groupDao = db.groupDao()
        val metricDao = db.metricDao()
        val recordDao = db.recordDao()
        if (replace) profileDao.deleteAll()

        var firstId: Long? = null
        var recordCount = 0
        backup.profiles.forEach { pb ->
            val profileId = profileDao.insert(
                ProfileEntity(
                    name = pb.name,
                    heightCm = pb.heightCm,
                    gender = pb.gender,
                    birthYear = pb.birthYear,
                    weightUnit = pb.weightUnit,
                    lengthUnit = pb.lengthUnit,
                    goalWeightKg = pb.goalWeightKg,
                    autoGroupByTime = pb.autoGroupByTime,
                    createdAt = pb.createdAt,
                ),
            )
            if (firstId == null) firstId = profileId

            val groupIds = HashMap<Long, Long>()
            pb.groups.forEach { gb ->
                val gid = groupDao.insert(
                    GroupEntity(
                        profileId = profileId,
                        name = gb.name,
                        color = gb.color,
                        weightUnit = gb.weightUnit,
                        sortOrder = gb.sortOrder,
                    ),
                )
                groupIds[gb.id] = gid
                groupDao.insertRules(gb.rules.map { GroupTimeRuleEntity(groupId = gid, startMinute = it.start, endMinute = it.end) })
                groupDao.insertReminders(gb.reminders.map { ReminderEntity(groupId = gid, minuteOfDay = it.minute, enabled = it.enabled) })
            }

            val metricIds = HashMap<Long, MetricEntity>()
            pb.metrics.forEach { mb ->
                val entity = MetricEntity(
                    profileId = profileId,
                    key = mb.key,
                    name = mb.name,
                    unitType = mb.unitType,
                    builtIn = mb.builtIn,
                    enabled = mb.enabled,
                    sortOrder = mb.sortOrder,
                )
                val mid = metricDao.insert(entity)
                metricIds[mb.id] = entity.copy(id = mid)
            }

            val records = pb.records.mapNotNull { rb ->
                val gid = groupIds[rb.groupId] ?: return@mapNotNull null
                val metric = metricIds[rb.metricId] ?: return@mapNotNull null
                val unit = MeasureUnit.of(rb.unit, MeasureUnit.baseOf(unitTypeOf(metric.unitType)))
                RecordEntity(
                    profileId = profileId,
                    groupId = gid,
                    metricId = metric.id,
                    value = unit.toBase(rb.value),
                    inputValue = rb.value,
                    inputUnit = unit.name,
                    recordedAt = rb.time,
                    day = dayOf(rb.time),
                    note = rb.note,
                )
            }
            recordDao.insertAll(records)
            recordCount += records.size
        }
        ImportSummary(backup.profiles.size, recordCount, firstId)
    }

    // ---------------- 自动备份 ----------------

    /** 写入一份 gzip 压缩的 JSON 备份到用户选择的文件夹，并清理多余的旧备份 */
    suspend fun autoBackup(): Result<String> = withContext(Dispatchers.IO) {
        val config = settings.backupNow()
        val result = runCatching {
            val folder = config.folderUri ?: error("未设置备份文件夹")
            val dir = DocumentFile.fromTreeUri(context, Uri.parse(folder))
            if (dir == null || !dir.canWrite()) error("无法访问备份文件夹，请重新选择")
            val name = "$FILE_PREFIX${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}$FILE_SUFFIX"
            val file = dir.createFile("application/octet-stream", name) ?: error("无法创建备份文件")
            val bytes = encode(buildBackup(), gzip = true)
            writeUri(file.uri, bytes)
            dir.listFiles()
                .filter { it.name?.startsWith(FILE_PREFIX) == true && it.name?.endsWith(FILE_SUFFIX) == true }
                .sortedByDescending { it.name }
                .drop(config.keepCount.coerceAtLeast(1))
                .forEach { it.delete() }
            "$name（${(bytes.size + 1023) / 1024} KB）"
        }
        settings.setBackupResult(
            System.currentTimeMillis(),
            result.fold({ "成功：$it" }, { "失败：${it.message}" }),
        )
        result
    }

    companion object {
        const val FILE_PREFIX = "weightnote-"
        const val FILE_SUFFIX = ".json.gz"
    }
}
