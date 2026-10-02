package com.weightnote.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.weightnote.data.epochMillisOf
import com.weightnote.data.localDateTimeOf
import com.weightnote.data.todayDay
import java.time.LocalDate
import java.time.ZoneOffset

/** DatePicker 使用 UTC 零点的毫秒数表示日期 */
private fun dayToUtcMillis(day: Long): Long = LocalDate.ofEpochDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun utcMillisToDay(millis: Long): Long = Math.floorDiv(millis, 86_400_000L)

@OptIn(ExperimentalMaterial3Api::class)
private object PastOrToday : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcMillisToDay(utcTimeMillis) <= todayDay()
    override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialMinute: Int,
    title: String = "选择时间",
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 先选日期再选时间 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    initialMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember(initialMillis) { localDateTimeOf(initialMillis) }
    var pickedDay by remember { mutableStateOf<Long?>(null) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = dayToUtcMillis(initial.toLocalDate().toEpochDay()),
        selectableDates = PastOrToday,
    )
    val day = pickedDay
    if (day == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    pickedDay = dateState.selectedDateMillis?.let { utcMillisToDay(it) }
                        ?: initial.toLocalDate().toEpochDay()
                }) { Text("下一步") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        ) {
            DatePicker(state = dateState, showModeToggle = false)
        }
    } else {
        TimePickerDialog(
            initialMinute = initial.hour * 60 + initial.minute,
            onConfirm = { minute ->
                val dt = LocalDate.ofEpochDay(day).atTime(minute / 60, minute % 60)
                onConfirm(epochMillisOf(dt))
            },
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    initialStartDay: Long?,
    initialEndDay: Long?,
    onConfirm: (Long, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartDay?.let { dayToUtcMillis(it) },
        initialSelectedEndDateMillis = initialEndDay?.let { dayToUtcMillis(it) },
        selectableDates = PastOrToday,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = utcMillisToDay(state.selectedStartDateMillis!!)
                    val end = state.selectedEndDateMillis?.let { utcMillisToDay(it) } ?: start
                    onConfirm(start, end)
                },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DateRangePicker(
            state = state,
            showModeToggle = false,
            modifier = Modifier.height(480.dp),
        )
    }
}
