package app.sports.ui

import androidx.compose.ui.graphics.Color
import app.sports.core.Eff
import app.sports.core.Outcome

val PartialColor = Color(0xFFB7791F)
val SkipColor = Color(0xFF9A4A4A)

/** Цвет отметки тренировки: выполнена, частично, пропущена, ещё впереди. */
fun effColor(e: Eff, pending: Color): Color = when (e) {
    Eff.DONE -> Pal.Done
    Eff.PARTIAL -> PartialColor
    Eff.SKIPPED, Eff.AUTO_SKIPPED -> SkipColor
    Eff.PENDING -> pending
}

/** Подпись состояния; null, если тренировка ещё не отмечена. */
fun effLabel(e: Eff, o: Outcome?): String? = when (e) {
    Eff.DONE -> "Выполнена ✓"
    Eff.PARTIAL -> "Выполнена частично: ${o?.percent ?: 0}%" + reasonSuffix(o)
    Eff.SKIPPED -> "Пропущена" + reasonSuffix(o)
    Eff.AUTO_SKIPPED -> "Пропущена: день прошёл, отметки и переноса не было"
    Eff.PENDING -> null
}

private fun reasonSuffix(o: Outcome?): String {
    if (o == null || o.reason.isBlank()) return ""
    return " · " + o.reason.lowercase() + if (o.note.isNotBlank()) " (${o.note})" else ""
}

fun hoursMinutes(sec: Long): String {
    val m = sec / 60
    return if (m >= 60) "${m / 60} ч ${m % 60} мин" else "$m мин"
}
