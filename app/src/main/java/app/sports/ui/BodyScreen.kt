package app.sports.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sports.Ui
import app.sports.core.Body
import app.sports.core.ForecastStatus
import app.sports.core.Goal
import app.sports.core.WeightEntry
import java.time.LocalDate
import java.util.Locale

private val BodyShape = RoundedCornerShape(14.dp)

private fun num(v: Double, digits: Int = 1): String = String.format(Locale.US, "%.${digits}f", v).replace('.', ',')
private fun dmy(d: LocalDate): String = "${d.dayOfMonth}.${d.monthValue}.${d.year}"
private fun parseD(s: String): Double = s.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
private fun fmtField(v: Double): String = if (v <= 0.0) "" else if (v % 1.0 == 0.0) v.toInt().toString() else num(v)

/** Вкладка «Тело»: вес, ИМТ, оценка процента жира, цели, прогноз и напоминание. Всё оценочное, не медицинская рекомендация. */
@Composable
fun BodyScreen(ui: Ui, ctl: Controller, today: LocalDate) {
    val db = ui.db
    val p = db.profile
    val last = Body.latest(db.weights)
    val kg = last?.kg ?: p.weightKg
    val bmi = Body.bmi(kg, p.heightCm)
    val age = p.age(today)
    val fat = Body.fatPct(kg, p.heightCm, age, p.sex)
    val forecast = Body.forecast(db.weights, p, today)

    var weightText by remember { mutableStateOf("") }
    var weightDate by remember { mutableStateOf(today) }
    var pickDate by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Тело", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)

        // сейчас
        Column(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, BodyShape).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(if (kg > 0) "${num(kg)} кг" else "Вес не указан", fontSize = 32.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)
            if (last != null) Text("запись от ${dmy(last.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (bmi != null) "ИМТ ${num(bmi)} (${Body.bmiLabel(bmi)})" else "ИМТ: укажи рост и вес (вкладка «Профиль»)",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (fat != null) "Процент жира примерно ${num(fat, 0)}%" else "Процент жира: нужны рост, пол и дата рождения в профиле (формула работает со взрослых лет).",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "ИМТ и процент жира – грубые оценки по формулам (процент жира по формуле Дюрена, ошибка может быть несколько пунктов). Это не медицинская рекомендация.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // запись веса
        Text("Записать вес", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                weightText, { weightText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                label = { Text("Вес, кг") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { pickDate = true }) { Text(if (weightDate == today) "Сегодня" else dmy(weightDate)) }
        }
        val w = parseD(weightText)
        Button(
            onClick = { ctl.addWeight(w, weightDate); weightText = ""; weightDate = today },
            enabled = w in 30.0..300.0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Записать") }

        // график
        if (db.weights.size >= 2) {
            val target = Body.targetWeight(p.goal, p, today)
            Column(
                Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, BodyShape).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Динамика веса", fontWeight = FontWeight.SemiBold)
                WeightChart(db.weights, target)
                val sorted = db.weights.sortedBy { it.date }
                Text(
                    "${dmy(sorted.first().date)} – ${dmy(sorted.last().date)}, от ${num(sorted.minOf { it.kg })} до ${num(sorted.maxOf { it.kg })} кг" +
                        if (target != null) ". Пунктир – цель ${num(target)} кг." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // записи
        if (db.weights.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, BodyShape).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Последние записи", fontWeight = FontWeight.SemiBold)
                db.weights.sortedByDescending { it.date }.take(8).forEach { e: WeightEntry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${dmy(e.date)}  ·  ${num(e.kg)} кг", Modifier.weight(1f))
                        TextButton(onClick = { ctl.deleteWeight(e) }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }

        GoalCard(p.goal, ctl, p.heightCm)

        // прогноз
        Column(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, BodyShape).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Прогноз", fontWeight = FontWeight.SemiBold)
            val f = forecast
            val rate = if (f.kgPerWeek < 0) "−${num(-f.kgPerWeek)}" else "+${num(f.kgPerWeek)}"
            Text(
                when (f.status) {
                    ForecastStatus.NO_GOAL -> "Задай цель выше, и здесь появится прогноз."
                    ForecastStatus.NO_DATA -> "Для прогноза нужны минимум две записи веса с разницей от недели."
                    ForecastStatus.REACHED -> "Цель ${num(f.targetKg)} кг достигнута."
                    ForecastStatus.FLAT -> "Вес почти не меняется ($rate кг в неделю), поэтому дату назвать нельзя."
                    ForecastStatus.WRONG_WAY -> "Сейчас вес идёт в сторону от цели ($rate кг в неделю)."
                    ForecastStatus.ON_TRACK -> "При текущем темпе ($rate кг в неделю) цель ${num(f.targetKg)} кг будет примерно к ${dmy(f.date!!)}."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Темп считается по записям за последние 90 дней. Это простая линия тренда, а не обещание: вес меняется неравномерно.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // напоминание
        Row(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, BodyShape).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Напоминать про вес раз в месяц", fontWeight = FontWeight.Medium)
                Text(
                    "Уведомление придёт около 10:00, если с последней записи прошло 30 дней. Нужно разрешение на уведомления.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = db.settings.weightReminder, onCheckedChange = { ctl.setWeightReminder(it) }, modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (pickDate) DateDialog(weightDate, onPick = { weightDate = if (it.isAfter(today)) today else it }, onDismiss = { pickDate = false })
}

@Composable
private fun GoalCard(goal: Goal, ctl: Controller, heightCm: Int) {
    var gw by remember(goal) { mutableStateOf(fmtField(goal.weightKg)) }
    var gb by remember(goal) { mutableStateOf(fmtField(goal.bmi)) }
    var gf by remember(goal) { mutableStateOf(fmtField(goal.fatPct)) }
    val w = parseD(gw)
    val b = parseD(gb)
    val f = parseD(gf)
    val ok = (gw.isBlank() || w in 30.0..300.0) && (gb.isBlank() || b in 14.0..40.0) && (gf.isBlank() || f in 5.0..60.0)
    val changed = Goal(if (gw.isBlank()) 0.0 else w, if (gb.isBlank()) 0.0 else b, if (gf.isBlank()) 0.0 else f) != goal

    Column(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, BodyShape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Цель", fontWeight = FontWeight.SemiBold)
        Text(
            "Достаточно одной. Если заданы несколько, для прогноза берётся первая по порядку: вес, ИМТ, процент жира.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                gw, { gw = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) }, label = { Text("Вес, кг") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                gb, { gb = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) }, label = { Text("ИМТ") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                gf, { gf = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) }, label = { Text("Жир, %") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
        }
        if (b > 0 && heightCm >= 80) {
            Text("ИМТ ${num(b)} при твоём росте – это ${num(Body.weightForBmi(b, heightCm))} кг.", style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { ctl.saveGoal(Goal(if (gw.isBlank()) 0.0 else w, if (gb.isBlank()) 0.0 else b, if (gf.isBlank()) 0.0 else f)) },
                enabled = changed && ok,
            ) { Text("Сохранить цель") }
            if (goal.weightKg > 0 || goal.bmi > 0 || goal.fatPct > 0) {
                TextButton(onClick = { ctl.saveGoal(Goal()) }) { Text("Убрать цель") }
            }
        }
    }
}

@Composable
private fun WeightChart(entries: List<WeightEntry>, goal: Double?) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val goalColor = Pal.Done
    val pts = entries.sortedBy { it.date }
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val x0 = pts.first().date.toEpochDay()
        val x1 = maxOf(pts.last().date.toEpochDay(), x0 + 1)
        var lo = pts.minOf { it.kg }
        var hi = pts.maxOf { it.kg }
        if (goal != null) { lo = minOf(lo, goal); hi = maxOf(hi, goal) }
        if (hi - lo < 1.0) { hi += 0.5; lo -= 0.5 }
        val pad = 12.dp.toPx()
        fun px(day: Long): Float = pad + (day - x0).toFloat() / (x1 - x0).toFloat() * (size.width - 2 * pad)
        fun py(v: Double): Float = pad + ((hi - v) / (hi - lo)).toFloat() * (size.height - 2 * pad)
        drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        if (goal != null) {
            drawLine(
                goalColor, Offset(pad, py(goal)), Offset(size.width - pad, py(goal)), 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
            )
        }
        val path = Path()
        pts.forEachIndexed { i, e ->
            val x = px(e.date.toEpochDay())
            val y = py(e.kg)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
        pts.forEach { e -> drawCircle(lineColor, 4.dp.toPx(), Offset(px(e.date.toEpochDay()), py(e.kg))) }
    }
}
