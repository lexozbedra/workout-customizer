package app.sports.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.sports.core.Fmt
import app.sports.core.Planned
import app.sports.core.Reasons
import app.sports.core.Schedule
import app.sports.core.Session
import app.sports.core.Tuning
import app.sports.core.Vacation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    ) {
        DatePicker(state = state)
    }
}

/** Перенос: показываем только те дни недели, куда ещё можно успеть. */
@Composable
fun MoveDialog(p: Planned, targets: List<LocalDate>, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Перенести тренировку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.session.title + ". Сейчас: " + longDate(p.date).lowercase() + ".", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Перенос возможен внутри этой недели, до 21:00 того дня, который наступает раньше: исходного или нового.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                targets.forEach { d ->
                    OutlinedButton(onClick = { onPick(d); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(longDate(d)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Выбор причины радио-кнопками; для «Другое» показывается поле комментария. */
@Composable
fun ReasonPicker(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    noteRequired: Boolean,
) {
    Column {
        options.forEach { o ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected == o, onClick = { onSelect(o) }, role = Role.RadioButton),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == o, onClick = null, modifier = Modifier.padding(end = 10.dp, top = 8.dp, bottom = 8.dp))
                Text(o)
            }
        }
        if (selected == Reasons.OTHER) {
            OutlinedTextField(
                value = note,
                onValueChange = onNote,
                label = { Text(if (noteRequired) "Комментарий (обязательно)" else "Комментарий") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/** Отпуск: сначала причина, потом даты и длительность. Внизу видно, куда уедет конец плана. */
@Composable
fun VacationDialog(schedule: Schedule, today: LocalDate, onSave: (Vacation) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(today) }
    var days by remember { mutableStateOf(7) }
    var align by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val vacation = Vacation(start, days, align, reason, note.trim())
    val lastDate = Schedule(schedule.planStart, schedule.vacations + vacation, schedule.moves, schedule.tuning).lastDate
    val valid = reason.isNotEmpty() && (reason != Reasons.OTHER || note.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Отпуск") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Что случилось?", fontWeight = FontWeight.SemiBold)
                ReasonPicker(Reasons.VACATION, reason, { reason = it }, note, { note = it }, noteRequired = true)
                Text("План встанет на паузу и продолжится после отпуска.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Начало: " + longDate(start))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { if (days > 1) days-- }) { Text("−") }
                    Text(days.toString() + " дн.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(72.dp))
                    OutlinedButton(onClick = { if (days < 90) days++ }) { Text("+") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Сдвинуть на целые недели")
                        Text(
                            "Тренировки останутся на своих днях недели (вторник, четверг, суббота).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = align, onCheckedChange = { align = it }, modifier = Modifier.padding(start = 8.dp))
                }
                if (lastDate != null) {
                    Text(
                        "Сдвиг: " + vacation.shiftDays + " дн. Последняя тренировка плана: " + longDate(lastDate).lowercase() + ".",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(vacation); onDismiss() }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
    if (picking) DateDialog(start, onPick = { start = it }, onDismiss = { picking = false })
}

private fun stepFor(sec: Int): Int = if (sec <= 60) 5 else if (sec <= 300) 15 else 30

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onMinus) { Text("−") }
        Text(value, Modifier.width(84.dp), style = MaterialTheme.typography.titleSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedButton(onClick = onPlus) { Text("+") }
    }
}

/**
 * Редактор тренировки: время и повторения каждого упражнения, число кругов, отдых.
 * Правки хранятся на телефоне поверх плана; можно применить к одной тренировке или ко всем таким же.
 */
@Composable
fun EditDialog(session: Session, tuning: Tuning, ctl: Controller, onDismiss: () -> Unit) {
    var global by remember { mutableStateOf(false) }
    val sid = session.id
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настроить тренировку") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Для всех таких упражнений", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (global) "Правка действует во всех тренировках плана." else "Правка действует только в этой тренировке.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = global, onCheckedChange = { global = it }, modifier = Modifier.padding(start = 8.dp))
                }
                if (session.hasRounds) {
                    val extra = tuning.extraFor(sid, session.type)
                    val what = if (session.type == app.sports.core.SessionType.STRENGTH) "Кругов в суперсете" else "Интервалов"
                    Stepper(
                        what,
                        if (extra == 0) "по плану" else (if (extra > 0) "+$extra" else "$extra"),
                        { ctl.setExtra(sid, session.type, extra - 1, global) },
                        { ctl.setExtra(sid, session.type, extra + 1, global) },
                    )
                }
                session.editable.forEach { e ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(e.label, fontWeight = FontWeight.SemiBold)
                        if (e.reps > 0) {
                            Stepper(
                                "Повторений", e.reps.toString(),
                                { ctl.setReps(sid, e.key, e.reps - 1, global) },
                                { ctl.setReps(sid, e.key, e.reps + 1, global) },
                            )
                        }
                        Stepper(
                            "Время", Fmt.dur(e.durSec),
                            { ctl.setSec(sid, e.key, e.durSec - stepFor(e.durSec - 1), global) },
                            { ctl.setSec(sid, e.key, e.durSec + stepFor(e.durSec), global) },
                        )
                    }
                }
                Text(
                    "Для упражнений на повторения время считается из повторений. Если поменять время вручную, оно останется как заданное.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { ctl.resetSession(sid) }) { Text("Вернуть эту тренировку к плану") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}
