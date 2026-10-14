package app.sports.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Отпуск: с даты [start] на [days] дней. Всё, что запланировано в этот день или позже, уезжает вперёд.
 * При [alignWeeks] сдвиг округляется вверх до целых недель, и тренировки остаются на своих днях недели.
 */
data class Vacation(
    val start: LocalDate,
    val days: Int,
    val alignWeeks: Boolean = false,
    val reason: String = "",
    val note: String = "",
) {
    val shiftDays: Int get() = if (alignWeeks) ((days + 6) / 7) * 7 else days
    val lastDay: LocalDate get() = start.plusDays((days - 1).toLong())
    fun contains(d: LocalDate): Boolean = !d.isBefore(start) && !d.isAfter(lastDay)
}

/** Тренировка с датой, на которую она выпала сейчас. */
data class Planned(val session: Session, val baseDate: LocalDate, val shiftedDate: LocalDate, val date: LocalDate) {
    val id: String get() = session.id
}

enum class DayStatus { BEFORE_START, IN_PLAN, AFTER_END }

class Schedule(
    val plan: PlanDef,
    val planStart: LocalDate,
    vacations: List<Vacation> = emptyList(),
    /** id тренировки -> на сколько дней она сдвинута вручную относительно даты после отпусков. */
    val moves: Map<String, Int> = emptyMap(),
    /** Настройки пользователя (время, повторения, число кругов). */
    val tuning: Tuning = Tuning.NONE,
) {
    val vacations: List<Vacation> = vacations.sortedBy { it.start }

    /** Дата по базовому плану: недели считаются от понедельника недели старта. */
    fun baseDate(week: Int, dow: Int): LocalDate =
        planStart.with(DayOfWeek.MONDAY).plusDays(((week - 1) * 7 + (dow - 1)).toLong())

    /** Сдвиг даты вперёд на отпуска, которые начались раньше неё. */
    fun shift(d: LocalDate): LocalDate {
        var s = d
        for (v in vacations) if (!s.isBefore(v.start)) s = s.plusDays(v.shiftDays.toLong())
        return s
    }

    val planned: List<Planned> by lazy {
        val out = mutableListOf<Planned>()
        for (w in 1..plan.weekCount) for (dow in plan.trainingDows) {
            val base = baseDate(w, dow)
            if (base.isBefore(planStart)) continue
            val shifted = shift(base)
            for (s in plan.sessionsFor(w, dow, tuning)) {
                val date = shifted.plusDays((moves[s.id] ?: 0).toLong())
                out += Planned(s, base, shifted, date)
            }
        }
        out.sortedWith(compareBy<Planned> { it.date }.thenBy { it.session.id })
    }

    fun on(date: LocalDate): List<Planned> = planned.filter { it.date == date }

    fun find(id: String): Planned? = planned.firstOrNull { it.id == id }

    fun vacationOn(date: LocalDate): Vacation? = vacations.firstOrNull { it.contains(date) }

    val firstDate: LocalDate? get() = planned.firstOrNull()?.date
    val lastDate: LocalDate? get() = planned.lastOrNull()?.date

    fun status(date: LocalDate): DayStatus {
        val first = firstDate ?: return DayStatus.BEFORE_START
        val last = lastDate ?: return DayStatus.AFTER_END
        return when {
            date.isBefore(first) -> DayStatus.BEFORE_START
            date.isAfter(last) -> DayStatus.AFTER_END
            else -> DayStatus.IN_PLAN
        }
    }

    /** Номер недели плана, к которой относится дата: неделя ближайшей тренировки в этот день или позже. */
    fun weekAt(date: LocalDate): Int? = planned.firstOrNull { !it.date.isBefore(date) }?.session?.week

    fun nextTrainingAfter(date: LocalDate): LocalDate? = planned.firstOrNull { it.date.isAfter(date) }?.date

    /* ---------- перенос ---------- */

    /**
     * Перенос внутри одной календарной недели (понедельник – воскресенье). Нужно успеть до 21:00 того из двух дней,
     * исходного или нового, который наступает раньше.
     */
    fun canMove(from: LocalDate, to: LocalDate, now: LocalDateTime): Boolean {
        if (from == to) return false
        if (from.with(DayOfWeek.MONDAY) != to.with(DayOfWeek.MONDAY)) return false
        val earlier = if (from.isBefore(to)) from else to
        return now.isBefore(earlier.atTime(21, 0))
    }

    fun allowedTargets(p: Planned, now: LocalDateTime): List<LocalDate> {
        val monday = p.date.with(DayOfWeek.MONDAY)
        return (0L..6L).map { monday.plusDays(it) }
            .filter { canMove(p.date, it, now) && vacationOn(it) == null }
    }

    /** Новая карта переносов после перемещения тренировки [id] на [newDate]. */
    fun withMove(id: String, newDate: LocalDate): Map<String, Int> {
        val p = find(id) ?: return moves
        val delta = ChronoUnit.DAYS.between(p.shiftedDate, newDate).toInt()
        val m = moves.toMutableMap()
        if (delta == 0) m.remove(id) else m[id] = delta
        return m
    }
}
