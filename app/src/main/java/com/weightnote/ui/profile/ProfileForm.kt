package com.weightnote.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.weightnote.data.MeasureUnit
import com.weightnote.data.UnitType
import com.weightnote.data.db.ProfileEntity
import com.weightnote.data.formatNumber
import com.weightnote.ui.components.SegmentedChoice
import java.time.LocalDate

@Stable
class ProfileFormState(private val initial: ProfileEntity?) {
    var name by mutableStateOf(initial?.name ?: "")
    var gender by mutableStateOf(initial?.gender)
    var birthYear by mutableStateOf(initial?.birthYear?.toString() ?: "")
    var height by mutableStateOf(initial?.heightCm?.let { formatNumber(it) } ?: "")
    var weightUnit by mutableStateOf(initial?.weightUnitEnum ?: MeasureUnit.KG)
        private set
    var lengthUnit by mutableStateOf(initial?.lengthUnitEnum ?: MeasureUnit.CM)
    var goal by mutableStateOf(
        initial?.goalWeightKg?.let { formatNumber((initial.weightUnitEnum).fromBase(it)) } ?: "",
    )
    var error by mutableStateOf<String?>(null)

    /** 切换体重单位时，同步换算已填的目标体重 */
    fun changeWeightUnit(unit: MeasureUnit) {
        goal.toDoubleOrNull()?.let { goal = formatNumber(unit.fromBase(weightUnit.toBase(it))) }
        weightUnit = unit
    }

    /** 校验并生成实体，失败时设置 error 并返回 null */
    fun build(): ProfileEntity? {
        error = null
        val n = name.trim()
        if (n.isEmpty()) return fail("请填写昵称")
        val h = height.trim().takeIf { it.isNotEmpty() }?.let {
            it.toDoubleOrNull()?.takeIf { v -> v in 50.0..250.0 } ?: return fail("身高需在 50~250 cm 之间")
        }
        val year = birthYear.trim().takeIf { it.isNotEmpty() }?.let {
            it.toIntOrNull()?.takeIf { v -> v in 1900..LocalDate.now().year } ?: return fail("出生年份不正确")
        }
        val g = goal.trim().takeIf { it.isNotEmpty() }?.let {
            it.toDoubleOrNull()?.takeIf { v -> v > 0 }?.let { v -> weightUnit.toBase(v) }
                ?: return fail("目标体重不正确")
        }
        val base = initial ?: ProfileEntity(name = n)
        return base.copy(
            name = n,
            heightCm = h,
            gender = gender,
            birthYear = year,
            weightUnit = weightUnit.name,
            lengthUnit = lengthUnit.name,
            goalWeightKg = g,
        )
    }

    private fun fail(msg: String): ProfileEntity? {
        error = msg
        return null
    }
}

@Composable
fun rememberProfileFormState(initial: ProfileEntity?) = remember(initial?.id) { ProfileFormState(initial) }

private enum class GenderOption(val value: String?, val label: String) {
    NONE(null, "不设置"), MALE("MALE", "男"), FEMALE("FEMALE", "女")
}

@Composable
fun ProfileForm(state: ProfileFormState, modifier: Modifier = Modifier) {
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { state.name = it.take(20) },
            label = { Text("昵称 *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("体重单位", style = MaterialTheme.typography.labelLarge)
        SegmentedChoice(
            options = MeasureUnit.forType(UnitType.MASS),
            selected = state.weightUnit,
            label = { if (it == MeasureUnit.JIN) "斤" else "${it.label} ${it.symbol}" },
            onSelect = state::changeWeightUnit,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("围度单位", style = MaterialTheme.typography.labelLarge)
        SegmentedChoice(
            options = MeasureUnit.forType(UnitType.LENGTH),
            selected = state.lengthUnit,
            label = { "${it.label} ${it.symbol}" },
            onSelect = { state.lengthUnit = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.height,
                onValueChange = { state.height = it.filterNumber() },
                label = { Text("身高 cm") },
                supportingText = { Text("填写后自动计算 BMI") },
                singleLine = true,
                keyboardOptions = decimal,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.goal,
                onValueChange = { state.goal = it.filterNumber() },
                label = { Text("目标体重 ${state.weightUnit.symbol}") },
                supportingText = { Text("可选") },
                singleLine = true,
                keyboardOptions = decimal,
                modifier = Modifier.weight(1f),
            )
        }

        Text("性别（可选）", style = MaterialTheme.typography.labelLarge)
        SegmentedChoice(
            options = GenderOption.entries,
            selected = GenderOption.entries.first { it.value == state.gender },
            label = { it.label },
            onSelect = { state.gender = it.value },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.birthYear,
            onValueChange = { v -> state.birthYear = v.filter { it.isDigit() }.take(4) },
            label = { Text("出生年份（可选）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** 只保留数字和一个小数点，最多 1 位小数 */
fun String.filterNumber(maxDecimals: Int = 1): String {
    val sb = StringBuilder()
    var dot = false
    var decimals = 0
    for (ch in this) {
        when {
            ch.isDigit() -> {
                if (dot) {
                    if (decimals >= maxDecimals) continue
                    decimals++
                }
                sb.append(ch)
            }
            (ch == '.' || ch == '。') && !dot -> {
                dot = true
                sb.append('.')
            }
        }
    }
    return sb.toString().take(7)
}
