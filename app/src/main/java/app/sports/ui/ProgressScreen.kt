package app.sports.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sports.AppData
import app.sports.core.Eff
import app.sports.core.Results
import app.sports.core.Schedule
import app.sports.core.StatsCalc
import java.time.LocalDate

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, minLines = 2)
    }
}

/** Вкладка «Прогресс»: сколько тренировок плана сделано и сводка по времени, дням и калориям. */
@Composable
fun ProgressScreen(data: AppData, schedule: Schedule, today: LocalDate, ctl: Controller) {
    val st = StatsCalc.compute(schedule.planned, data.results, today, data.weightKg)
    val percent = Math.round(st.fraction * 100).toInt()
    val units = Math.round(st.units * 10) / 10.0
    val unitsText = if (units % 1.0 == 0.0) units.toInt().toString() else units.toString().replace('.', ',')

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Прогресс", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)

        Column(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$percent%", fontSize = 44.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)
                Spacer(Modifier.width(10.dp))
                Text(
                    "$unitsText из ${st.total} тренировок плана",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            LinearProgressIndicator(
                progress = { st.fraction.toFloat() },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = Pal.Done,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                "Полностью: ${st.done} · частично: ${st.partial} · пропущено: ${st.skipped} · впереди: ${st.pending}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Частично выполненная тренировка засчитывается пропорционально проценту.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // все карточки одинаковой ширины и высоты
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            StatTile(hoursMinutes(st.activeSec), "время тренировок", Modifier.weight(1f).fillMaxHeight())
            StatTile("≈ ${st.kcal}", "ккал сожжено (оценка)", Modifier.weight(1f).fillMaxHeight())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            StatTile(st.gymDays.toString(), "дней в зале (силовые)", Modifier.weight(1f).fillMaxHeight())
            StatTile(st.activeDays.toString(), "активных дней", Modifier.weight(1f).fillMaxHeight())
        }

        Column(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Вес для расчёта калорий: ${data.weightKg} кг", fontWeight = FontWeight.SemiBold)
            Text(
                "Вес берётся из вкладки «Профиль» (если он там не указан, считаем 65 кг).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Калории считаются грубо: средняя нагрузка по этапам тренировки (бег в горку, шаг, силовые и отдых между подходами) × вес × время. Точность порядка ±30%.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // журнал: что выполнено не до конца или пропущено, с причинами
        val log = schedule.planned
            .map { it to Results.effective(it, data.results, today) }
            .filter { it.second == Eff.PARTIAL || it.second == Eff.SKIPPED || it.second == Eff.AUTO_SKIPPED }
            .sortedByDescending { it.first.date }
        if (log.isNotEmpty()) {
            Text("Журнал пропусков и неполных тренировок", fontWeight = FontWeight.SemiBold)
            log.forEach { (p, e) ->
                Column(
                    Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).padding(12.dp),
                ) {
                    Text("${shortDate(p.date)} · ${p.session.title}")
                    Text(effLabel(e, data.results[p.id]) ?: "", style = MaterialTheme.typography.bodySmall, color = effColor(e, MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
