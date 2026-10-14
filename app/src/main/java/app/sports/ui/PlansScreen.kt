package app.sports.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sports.Ui
import app.sports.core.Eff
import app.sports.core.PlanDef
import app.sports.core.PlanState
import app.sports.core.PlanStatus
import app.sports.core.Plans
import app.sports.core.Results
import java.time.DayOfWeek
import java.time.LocalDate

private val PlanCardShape = RoundedCornerShape(14.dp)

private fun statusText(s: PlanStatus) = when (s) {
    PlanStatus.NOT_STARTED -> "не начат"
    PlanStatus.ACTIVE -> "активен"
    PlanStatus.PAUSED -> "на паузе"
    PlanStatus.FINISHED -> "завершён"
}

private sealed class PlanAsk {
    class Start(val id: String, val restart: Boolean) : PlanAsk()
    class Finish(val id: String) : PlanAsk()
    class Delete(val id: String) : PlanAsk()
}

/** Вкладка «Планы»: коллекция планов из папки с данными. Активен может быть только один, прогресс остальных сохраняется. */
@Composable
fun PlansScreen(ui: Ui, ctl: Controller, today: LocalDate) {
    var ask by remember { mutableStateOf<PlanAsk?>(null) }
    val db = ui.db
    val plans = ui.plans.values.sortedWith(
        compareBy<PlanDef> {
            when (db.state(it.id).status) {
                PlanStatus.ACTIVE -> 0
                PlanStatus.PAUSED -> 1
                PlanStatus.NOT_STARTED -> 2
                PlanStatus.FINISHED -> 3
            }
        }.thenBy { it.name },
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Планы", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)
        Text(
            "Активен один план. Если начать другой, текущий встанет на паузу, а его прогресс сохранится.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { ctl.pickPlanFile() }, modifier = Modifier.weight(1f)) { Text("Импорт из файла") }
            OutlinedButton(onClick = { ctl.copyTemplate() }, modifier = Modifier.weight(1f)) { Text("Шаблон для нейросети") }
        }
        TextButton(onClick = { ctl.shareTemplate() }) { Text("Отправить шаблон в другое приложение") }

        if (plans.isEmpty()) {
            Text(
                "Пока планов нет. Нажми «Импорт из файла» и выбери файл плана (.json), например из папки plans репозитория.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        plans.forEach { plan ->
            val st = db.state(plan.id)
            PlanCard(plan, st, today,
                onStart = { ask = PlanAsk.Start(plan.id, false) },
                onResume = { ctl.activate(plan.id, null) },
                onRestart = { ask = PlanAsk.Start(plan.id, true) },
                onFinish = { ask = PlanAsk.Finish(plan.id) },
                onDelete = { ask = PlanAsk.Delete(plan.id) },
            )
        }

        if (ui.errors.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, PlanCardShape).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Есть проблемы с файлами", fontWeight = FontWeight.SemiBold)
                ui.errors.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { ctl.store.clearErrors() }) { Text("Скрыть") }
            }
        }
    }

    when (val a = ask) {
        is PlanAsk.Start -> DateDialog(
            nextMonday(today),
            onPick = { d -> if (a.restart) ctl.restart(a.id, d) else ctl.activate(a.id, d) },
            onDismiss = { ask = null },
        )
        is PlanAsk.Finish -> ConfirmDialog(
            "Завершить план?", "План станет «завершён», прогресс останется в статистике.", "Завершить",
            onOk = { ctl.finishPlan(a.id) }, onDismiss = { ask = null },
        )
        is PlanAsk.Delete -> ConfirmDialog(
            "Удалить план?", "Файл плана и весь его прогресс будут удалены из папки данных. Вернуть их не получится.", "Удалить",
            onOk = { ctl.deletePlan(a.id) }, onDismiss = { ask = null },
        )
        null -> {}
    }
}

private fun nextMonday(today: LocalDate): LocalDate = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))

@Composable
private fun ConfirmDialog(title: String, text: String, ok: String, onOk: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onOk(); onDismiss() }) { Text(ok) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun PlanCard(
    plan: PlanDef,
    st: PlanState,
    today: LocalDate,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onFinish: () -> Unit,
    onDelete: () -> Unit,
) {
    val progress = remember(plan, st) {
        val sch = Plans.schedule(plan, st)
        if (sch == null) null else {
            val list = sch.planned
            val done = list.count { Results.effective(it, st.results, today) == Eff.DONE }
            Triple(done, list.size, sch.firstDate to sch.lastDate)
        }
    }
    val active = st.status == PlanStatus.ACTIVE
    Column(
        Modifier.fillMaxWidth().border(if (active) 2.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, PlanCardShape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(plan.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(statusText(st.status), color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        }
        if (plan.description.isNotBlank()) Text(plan.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val meta = buildString {
            append("${plan.weekCount} нед.")
            if (progress != null) {
                append(" · выполнено ${progress.first} из ${progress.second}")
                val (f, l) = progress.third
                if (f != null && l != null) append(" · ${shortDate(f)} – ${shortDate(l)}")
            }
            if (st.status == PlanStatus.FINISHED && st.finishedAt != null) append(" · завершён ${shortDate(st.finishedAt)}")
        }
        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when (st.status) {
                PlanStatus.NOT_STARTED -> TextButton(onClick = onStart) { Text("Начать") }
                PlanStatus.ACTIVE -> TextButton(onClick = onFinish) { Text("Завершить") }
                PlanStatus.PAUSED -> {
                    TextButton(onClick = onResume) { Text("Продолжить") }
                    TextButton(onClick = onFinish) { Text("Завершить") }
                }
                PlanStatus.FINISHED -> TextButton(onClick = onRestart) { Text("Начать заново") }
            }
            TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
        }
    }
}
