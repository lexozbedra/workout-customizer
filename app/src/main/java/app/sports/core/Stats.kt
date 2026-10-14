package app.sports.core

import java.time.LocalDate

data class Stats(
    val total: Int,
    val done: Int,
    val partial: Int,
    val skipped: Int,
    val pending: Int,
    /** Сумма долей выполнения: полная тренировка = 1, частичная = percent/100. */
    val units: Double,
    val activeSec: Long,
    /** Дни, когда была хоть какая-то тренировка. */
    val activeDays: Int,
    /** Дни, когда была силовая (зал). */
    val gymDays: Int,
    val kcal: Int,
) {
    val fraction: Double get() = if (total == 0) 0.0 else (units / total).coerceIn(0.0, 1.0)
}

object StatsCalc {
    /** Примерные MET по этапам (справочные значения Compendium of Physical Activities, округлённые). */
    private fun met(type: SessionType, s: Step): Double = when (s.key) {
        "tm.hill" -> 10.0
        "tm.steady" -> 8.5
        "tm.jog" -> 8.3
        "tm.hillwalk" -> 7.0
        "tm.warmup" -> 4.0
        "tm.recover" -> 4.0
        "tm.cooldown" -> 3.0
        "prep" -> 2.0
        else -> if (type == SessionType.STRENGTH) (if (s.kind == Kind.WORK) 5.0 else 2.5) else (if (s.kind == Kind.WORK) 7.0 else 3.5)
    }

    /** Средний MET по тренировке, взвешенный по времени шагов. */
    fun avgMet(s: Session): Double {
        val total = s.steps.sumOf { it.durSec }
        if (total == 0) return 0.0
        return s.steps.sumOf { met(s.type, it) * it.durSec } / total
    }

    fun kcal(s: Session, activeSec: Int, weightKg: Int): Double = avgMet(s) * weightKg * activeSec / 3600.0

    fun activeSecOf(s: Session, o: Outcome): Int = when (o.status) {
        Status.SKIPPED -> 0
        Status.DONE -> if (o.activeSec > 0) o.activeSec else s.totalSec
        Status.PARTIAL -> if (o.activeSec > 0) o.activeSec else s.totalSec * o.percent / 100
    }

    fun compute(planned: List<Planned>, results: Map<String, Outcome>, today: LocalDate, weightKg: Int): Stats {
        var done = 0; var partial = 0; var skipped = 0; var pending = 0
        var units = 0.0; var sec = 0L; var kcal = 0.0
        val days = HashSet<LocalDate>()
        val gym = HashSet<LocalDate>()
        for (p in planned) {
            val o = results[p.id]
            when (Results.effective(p, results, today)) {
                Eff.DONE -> done++
                Eff.PARTIAL -> partial++
                Eff.SKIPPED, Eff.AUTO_SKIPPED -> skipped++
                Eff.PENDING -> pending++
            }
            if (o != null && o.status != Status.SKIPPED) {
                units += o.percent.coerceIn(0, 100) / 100.0
                val a = activeSecOf(p.session, o)
                sec += a
                kcal += kcal(p.session, a, weightKg)
                days += p.date
                if (p.session.type == SessionType.STRENGTH) gym += p.date
            }
        }
        return Stats(planned.size, done, partial, skipped, pending, units, sec, days.size, gym.size, Math.round(kcal).toInt())
    }
}
