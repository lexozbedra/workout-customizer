package app.sports.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sports.AppData
import app.sports.core.DayStatus
import app.sports.core.Eff
import app.sports.core.Outcome
import app.sports.core.Planned
import app.sports.core.Results
import app.sports.core.Schedule
import app.sports.core.SessionType
import app.sports.core.Vacation
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

private val CardShape = RoundedCornerShape(14.dp)

@Composable
private fun Chip(text: String, dot: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun SessionCard(
    p: Planned,
    eff: Eff,
    outcome: Outcome?,
    canMove: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onClear: () -> Unit,
    onMove: () -> Unit,
) {
    val s = p.session
    val label = effLabel(eff, outcome)
    Card(
        modifier = Modifier.fillMaxWidth().clip(CardShape).clickable(onClick = onOpen),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, effColor(eff, MaterialTheme.colorScheme.outlineVariant)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Chip(s.place, if (s.type == SessionType.STRENGTH) Pal.Work else Pal.Rest)
                Text("≈ ${s.minutes} мин", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Column {
                Text(s.title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)
                Text(s.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.date != p.shiftedDate) {
                    Text(
                        "Перенесена с " + longDate(p.shiftedDate).lowercase(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (label != null) {
                    Text(label, color = effColor(eff, MaterialTheme.colorScheme.onSurface), fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text("Начать") }
                OutlinedButton(onClick = onToggle, modifier = Modifier.weight(1f)) {
                    Text(if (eff == Eff.DONE) "Выполнена ✓" else "Отметить")
                }
            }
            if (canMove && eff == Eff.PENDING) {
                TextButton(onClick = onMove) { Text("Перенести на другой день") }
            }
            if (eff == Eff.PENDING || eff == Eff.AUTO_SKIPPED) {
                TextButton(onClick = onSkip) { Text("Пропустить") }
            }
            if (eff == Eff.SKIPPED) TextButton(onClick = onSkip) { Text("Убрать отметку «пропущена»") }
            if (eff == Eff.PARTIAL) TextButton(onClick = onClear) { Text("Сбросить отметку") }
        }
    }
}

@Composable
fun HomeScreen(
    data: AppData,
    schedule: Schedule,
    now: LocalDateTime,
    date: LocalDate,
    ctl: Controller,
    onDate: (LocalDate) -> Unit,
    onOpen: (String) -> Unit,
) {
    val today = now.toLocalDate()
    val sessions = schedule.on(date)
    val vacation = schedule.vacationOn(date)
    var moving by remember { mutableStateOf<Planned?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // заголовок
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (date == today) "СЕГОДНЯ" else "ВЫБРАННЫЙ ДЕНЬ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(longDate(date), fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)
            val first = sessions.firstOrNull()
            val line = when {
                first != null -> {
                    val info = data.plan.weekInfo(first.session.week)
                    "Неделя ${info.week} из ${data.plan.weekCount} · ${info.phase.name}" + if (info.deload) " · разгрузка" else ""
                }
                schedule.status(date) == DayStatus.BEFORE_START ->
                    schedule.firstDate?.let { "План стартует: " + longDate(it).lowercase() }
                schedule.status(date) == DayStatus.AFTER_END -> "План на ${data.plan.weekCount} недель завершён"
                else -> null
            }
            if (line != null) Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // неделя: стрелки или свайп по полосе меняют неделю
        val monday = date.with(DayOfWeek.MONDAY)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onDate(date.minusWeeks(1)) }) { Text("‹ Назад") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    shortDate(monday) + " – " + shortDate(monday.plusDays(6)),
                    fontWeight = FontWeight.Medium,
                )
                if (today.with(DayOfWeek.MONDAY) != monday) {
                    TextButton(onClick = { onDate(today) }) { Text("К сегодняшнему дню") }
                }
            }
            TextButton(onClick = { onDate(date.plusWeeks(1)) }) { Text("Вперёд ›") }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().pointerInput(date) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (drag > 80f) onDate(date.minusWeeks(1)) else if (drag < -80f) onDate(date.plusWeeks(1))
                    },
                    onHorizontalDrag = { _, d -> drag += d },
                )
            },
        ) {
            for (i in 0..6) {
                val d = monday.plusDays(i.toLong())
                val list = schedule.on(d)
                val selected = d == date
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { onDate(d) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        DAYS_SHORT[i].uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(d.dayOfMonth.toString(), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Row(Modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (schedule.vacationOn(d) != null) {
                            Text("отп", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            list.forEach {
                                val e = Results.effective(it, data.results, today)
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(effColor(e, MaterialTheme.colorScheme.outline)),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (vacation != null) {
            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Отпуск: ${shortDate(vacation.start)} – ${shortDate(vacation.lastDay)}", fontWeight = FontWeight.SemiBold)
                    if (vacation.reason.isNotBlank()) {
                        Text(
                            "Причина: " + vacation.reason.lowercase() + if (vacation.note.isNotBlank()) " (${vacation.note})" else "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "План на паузе, тренировки сдвинуты на ${vacation.shiftDays} дн.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (sessions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sessions.forEach { p ->
                    val eff = Results.effective(p, data.results, today)
                    SessionCard(
                        p = p,
                        eff = eff,
                        outcome = data.results[p.id],
                        canMove = schedule.allowedTargets(p, now).isNotEmpty(),
                        onOpen = { onOpen(p.id) },
                        onStart = { ctl.startWorkout(p.id) },
                        onToggle = { ctl.toggleDone(p.id) },
                        onSkip = { ctl.toggleSkipped(p.id) },
                        onClear = { ctl.clearResult(p.id) },
                        onMove = { moving = p },
                    )
                }
                if (sessions.size > 1) {
                    Text(
                        "В этот день две тренировки. Силовая в зале, потом отдельно дорожка: время на лестницу и воду между ними.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else if (vacation == null) {
            val next = schedule.nextTrainingAfter(date)
            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (schedule.status(date) == DayStatus.BEFORE_START) "Тренировок пока нет." else "В этот день отдых.",
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (next != null) {
                        Text(
                            "Ближайшая: " + longDate(next).lowercase() + " – " +
                                schedule.on(next).joinToString(" и ") { it.session.title },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { onDate(next) }) { Text("Открыть этот день") }
                    }
                }
            }
        }
    }

    moving?.let { p ->
        MoveDialog(
            p = p,
            targets = schedule.allowedTargets(p, now),
            onPick = { ctl.move(schedule, p.id, it) },
            onDismiss = { moving = null },
        )
    }
}

/** Содержимое тренировки. В режиме [preview] (открыто из плана) можно только смотреть и настраивать, без запуска. */
@Composable
fun DetailScreen(
    data: AppData,
    schedule: Schedule,
    now: LocalDateTime,
    id: String,
    preview: Boolean,
    ctl: Controller,
    onBack: () -> Unit,
) {
    val p = schedule.find(id)
    var moving by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    if (p == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("‹ Назад") }
            Text("Эта тренировка больше не в плане.")
        }
        return
    }
    val s = p.session
    val eff = Results.effective(p, data.results, now.toLocalDate())
    val label = effLabel(eff, data.results[id])
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("‹ Назад") }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${s.place.uppercase()} · ≈ ${s.minutes} МИН · НЕДЕЛЯ ${s.week}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(s.title, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)
            Text(s.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(longDate(p.date), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (label != null) Text(label, color = effColor(eff, MaterialTheme.colorScheme.onSurface), fontWeight = FontWeight.Medium)
        }
        if (!preview) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { ctl.startWorkout(id) }, modifier = Modifier.weight(1f)) { Text("Начать") }
                OutlinedButton(onClick = { ctl.toggleDone(id) }, modifier = Modifier.weight(1f)) {
                    Text(if (eff == Eff.DONE) "Выполнена ✓" else "Отметить")
                }
            }
            if (eff == Eff.PENDING && schedule.allowedTargets(p, now).isNotEmpty()) {
                TextButton(onClick = { moving = true }) { Text("Перенести на другой день") }
            }
            if (eff == Eff.PENDING || eff == Eff.AUTO_SKIPPED) TextButton(onClick = { ctl.toggleSkipped(id) }) { Text("Пропустить") }
            if (eff == Eff.SKIPPED) TextButton(onClick = { ctl.toggleSkipped(id) }) { Text("Убрать отметку «пропущена»") }
            if (eff == Eff.PARTIAL) TextButton(onClick = { ctl.clearResult(id) }) { Text("Сбросить отметку") }
        } else {
            Text(
                "Просмотр плана. Запустить тренировку можно на вкладке «Сегодня».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Настроить тренировку") }
        s.outline.forEach { block ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(block.head.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                block.lines.forEach { Text(it) }
            }
        }
        if (s.exercises.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("КАК ВЫПОЛНЯТЬ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Откроется поиск видео в браузере или YouTube. Нужен интернет, остальное приложение работает без него.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.exercises.forEach { ex ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(ex.title, Modifier.weight(1f))
                        TextButton(onClick = { ctl.openVideo(ex.query) }) { Text("Узнать больше") }
                    }
                }
            }
        }
    }
    if (moving) {
        MoveDialog(
            p = p,
            targets = schedule.allowedTargets(p, now),
            onPick = { ctl.move(schedule, id, it) },
            onDismiss = { moving = false },
        )
    }
    if (editing) EditDialog(s, data.tuning, ctl, onDismiss = { editing = false })
}

@Composable
fun PlanScreen(
    data: AppData,
    schedule: Schedule,
    now: LocalDateTime,
    ctl: Controller,
    onWeek: (LocalDate) -> Unit,
    onOpen: (String) -> Unit,
) {
    var pickStart by remember { mutableStateOf(false) }
    var addVacation by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<Int>()) }
    val today = now.toLocalDate()
    val t = data.tuning
    val tuned = t.sec.isNotEmpty() || t.reps.isNotEmpty() || t.extra.isNotEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(data.plan.name, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)

        // старт
        OutlinedButton(onClick = { pickStart = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Старт плана: " + longDate(data.planStart).lowercase())
        }

        // отпуск
        Column(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Отпуск", fontWeight = FontWeight.SemiBold)
            if (data.vacations.isEmpty()) {
                Text(
                    "Отпусков нет. Если добавить, план встанет на паузу и сдвинется вперёд на его длительность.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            data.vacations.sortedBy { it.start }.forEach { v: Vacation ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${shortDate(v.start)} – ${shortDate(v.lastDay)} · ${v.days} дн.")
                        if (v.reason.isNotBlank()) {
                            Text(
                                v.reason + if (v.note.isNotBlank()) ": ${v.note}" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            "сдвиг плана на ${v.shiftDays} дн." + if (v.alignWeeks) " (целые недели)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { ctl.removeVacation(v) }) { Text("Удалить") }
                }
            }
            Button(onClick = { addVacation = true }) { Text("Добавить отпуск") }
        }

        // плашка поверх других приложений
        Column(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Плашка с таймером поверх других приложений", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Когда ты уходишь из приложения во время тренировки, внизу экрана появляется «Работа 0:45» или «Отдых 1:10» с кнопкой паузы. Нажми на плашку, чтобы открыть полный экран, × убирает плашку, тренировка продолжается.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = data.overlay, onCheckedChange = { ctl.setOverlay(it) }, modifier = Modifier.padding(start = 8.dp))
            }
            if (data.overlay && !ctl.canOverlay()) {
                Text(
                    "Чтобы плашка работала, нужно разрешение «Поверх других приложений».",
                    style = MaterialTheme.typography.bodySmall,
                    color = SkipColor,
                )
                Button(onClick = { ctl.requestOverlayPermission() }) { Text("Разрешить") }
            }
        }

        // звук
        Row(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Приглушать музыку на время сигнала", fontWeight = FontWeight.SemiBold)
                Text(
                    "Музыка и видео не останавливаются, просто становятся тише на пару секунд.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = data.duck, onCheckedChange = { ctl.setDuck(it) }, modifier = Modifier.padding(start = 8.dp))
        }

        if (tuned) {
            Row(
                Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("В плане есть твои правки времени и повторений.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { ctl.resetAllTuning() }) { Text("Сбросить все") }
            }
        }

        // фазы
        data.plan.phases.forEach { ph ->
            Column(
                Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape).padding(14.dp),
            ) {
                Text(ph.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("Недели ${ph.weeks}. ${ph.hint}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (data.plan.notes.isNotEmpty()) Column(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            data.plan.notes.forEach { n -> Text(n, style = MaterialTheme.typography.bodyMedium) }
        }

        Text("Нажми на неделю, чтобы увидеть её тренировки.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // недели
        for (w in 1..data.plan.weekCount) {
            val info = data.plan.weekInfo(w)
            val list = schedule.planned.filter { it.session.week == w }
            if (list.isEmpty()) continue
            val from = list.minOf { it.date }
            val to = list.maxOf { it.date }
            val effs = list.map { Results.effective(it, data.results, today) }
            val doneCount = effs.count { it == Eff.DONE }
            val skipCount = effs.count { Results.isSkipped(it) }
            val current = !today.isBefore(from) && !today.isAfter(to)
            val open = w in expanded
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            if (current) 2.dp else 1.dp,
                            if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { expanded = if (open) expanded - w else expanded + w }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(w.toString(), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info.phase.name + if (info.deload) " · разгрузка" else "", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${shortDate(from)} – ${shortDate(to)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "$doneCount/${list.size}",
                            color = if (doneCount == list.size) Pal.Done else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (doneCount == list.size) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (skipCount > 0) Text("пропущено $skipCount", style = MaterialTheme.typography.labelSmall, color = SkipColor)
                    }
                }
                if (open) {
                    list.forEachIndexed { i, pl ->
                        val e = effs[i]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpen(pl.id) }
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(effColor(e, MaterialTheme.colorScheme.outline)))
                            Column(Modifier.weight(1f)) {
                                Text(pl.session.title, fontWeight = FontWeight.Medium)
                                Text(
                                    shortDate(pl.date) + " · " + pl.session.subtitle + " · ≈ ${pl.session.minutes} мин",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = { onWeek(from) }) { Text("Показать эту неделю на экране «Сегодня»") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (pickStart) DateDialog(data.planStart, onPick = { ctl.setStart(it) }, onDismiss = { pickStart = false })
    if (addVacation) VacationDialog(schedule, today, onSave = { ctl.addVacation(it) }, onDismiss = { addVacation = false })
}
