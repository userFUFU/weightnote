package com.weightnote.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.weightnote.WeightNoteApp
import com.weightnote.data.db.MetricKeys
import com.weightnote.data.formatNumber
import com.weightnote.domain.logicalDay
import com.weightnote.reminder.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** 小组件上一个分组的今日状态 */
private data class WidgetGroup(
    val id: Long,
    val name: String,
    val color: Int,
    val value: String?,
    val time: String?,
)

private data class WidgetData(
    val profileId: Long?,
    val profileName: String,
    val groups: List<WidgetGroup>,
)

/**
 * 桌面小组件：显示当前身份今天各分组是否已记录体重，点分组直接打开对应的记录面板。
 * 数据变化时由 App 主动刷新；跨午夜后依靠系统每 30 分钟的定时刷新更新“今天”。
 */
class WeightWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        provideContent {
            GlanceTheme { WidgetContent(context, data) }
        }
    }

    private suspend fun loadData(context: Context): WidgetData = withContext(Dispatchers.IO) {
        val container = (context.applicationContext as WeightNoteApp).container
        val db = container.database
        val profiles = db.profileDao().getAll()
        val currentId = container.settings.currentProfileId.first()
        val profile = profiles.firstOrNull { it.id == currentId } ?: profiles.firstOrNull()
            ?: return@withContext WidgetData(null, "体重记", emptyList())
        val unit = profile.weightUnitEnum
        val weight = db.metricDao().getByKey(profile.id, MetricKeys.WEIGHT)
        val now = System.currentTimeMillis()
        val groups = db.groupDao().getByProfile(profile.id).map { g ->
            val rules = db.groupDao().getRules(g.id)
            val today = logicalDay(now, rules)
            val latest = weight?.let {
                db.recordDao().findSameDay(profile.id, g.id, it.id, today, excludeId = -1).maxByOrNull { r -> r.recordedAt }
            }
            WidgetGroup(
                id = g.id,
                name = g.name,
                color = g.color,
                value = latest?.let { "${formatNumber(unit.fromBase(it.value))} ${unit.symbol}" },
                time = latest?.let { formatClock(it.recordedAt) },
            )
        }
        WidgetData(profile.id, profile.name, groups)
    }

    private fun formatClock(millis: Long): String {
        val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
        return String.format("%02d:%02d", t.hour, t.minute)
    }

    companion object {
        /** 数据变化后刷新所有已添加到桌面的小组件 */
        suspend fun refresh(context: Context) {
            runCatching { WeightWidget().updateAll(context) }
        }
    }
}

@Composable
private fun WidgetContent(context: Context, data: WidgetData) {
    val muted = GlanceTheme.colors.onSurfaceVariant
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "今日体重",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(data.profileName, style = TextStyle(color = muted, fontSize = 12.sp))
        }
        Spacer(GlanceModifier.height(6.dp))

        if (data.profileId == null) {
            Text("打开 App 创建身份", style = TextStyle(color = muted, fontSize = 13.sp))
            return@Column
        }

        data.groups.take(4).forEach { g ->
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(actionStartActivity(Notifications.entryIntent(context, data.profileId, g.id))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    GlanceModifier
                        .size(10.dp)
                        .cornerRadius(5.dp)
                        .background(Color(g.color)),
                ) {}
                Spacer(GlanceModifier.width(8.dp))
                Text(g.name, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
                Spacer(GlanceModifier.defaultWeight())
                if (g.value != null) {
                    Text(
                        g.value,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    )
                    Text("  ${g.time}", style = TextStyle(color = muted, fontSize = 11.sp))
                } else {
                    Text("未记录 ›", style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp))
                }
            }
        }

        Spacer(GlanceModifier.defaultWeight())
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(36.dp)
                .cornerRadius(18.dp)
                .background(GlanceTheme.colors.primary)
                .clickable(actionStartActivity(Notifications.entryIntent(context, data.profileId, null))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "+ 记体重",
                style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

class WeightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeightWidget()
}

