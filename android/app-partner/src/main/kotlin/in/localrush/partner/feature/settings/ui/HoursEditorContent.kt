package `in`.localrush.partner.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.partner.R
import `in`.localrush.partner.feature.settings.vm.DayHours
import `in`.localrush.partner.feature.settings.vm.HoursEditor
import java.time.LocalTime

/** Weekly hours editor: a row per weekday with open/close pickers and a closed switch. */
@Composable
fun HoursEditorContent(days: List<DayHours>, onChange: (List<DayHours>) -> Unit, modifier: Modifier = Modifier) {
    val names = stringArrayResource(R.array.weekdays)
    var picking by remember { mutableStateOf<Pair<Int, Boolean>?>(null) } // weekday to isOpenTime
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        days.sortedBy { it.weekday }.forEach { day ->
            LRCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(names.getOrElse(day.weekday) { day.weekday.toString() }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(stringResource(if (day.closed) R.string.hours_closed else R.string.hours_open), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = !day.closed, onCheckedChange = { open -> onChange(HoursEditor.update(days, day.weekday) { it.copy(closed = !open) }) })
                }
                if (!day.closed) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LRButton(DateTimeUtils.formatTime(day.open), onClick = { picking = day.weekday to true }, modifier = Modifier.weight(1f), style = LRButtonStyle.Outlined)
                        LRButton(DateTimeUtils.formatTime(day.close), onClick = { picking = day.weekday to false }, modifier = Modifier.weight(1f), style = LRButtonStyle.Outlined)
                    }
                    if (!day.isValid) Text(stringResource(R.string.hours_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                    TextButton(onClick = { onChange(HoursEditor.applyToAll(days, day.weekday)) }) { Text(stringResource(R.string.hours_apply_all)) }
                }
            }
        }
    }
    picking?.let { (weekday, isOpen) ->
        val day = days.first { it.weekday == weekday }
        TimePickerDialog(
            initial = if (isOpen) day.open else day.close,
            title = stringResource(if (isOpen) R.string.hours_pick_open else R.string.hours_pick_close),
            onDismiss = { picking = null },
            onConfirm = { t -> onChange(HoursEditor.update(days, weekday) { if (isOpen) it.copy(open = t) else it.copy(close = t) }); picking = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initial: LocalTime, title: String, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
