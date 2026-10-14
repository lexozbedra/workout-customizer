package app.sports.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ForecastStatus { REACHED, ON_TRACK, WRONG_WAY, FLAT, NO_DATA, NO_GOAL }

/** [date] есть только при ON_TRACK; [kgPerWeek] – текущий темп (минус = худеешь). */
data class Forecast(val status: ForecastStatus, val date: LocalDate? = null, val kgPerWeek: Double = 0.0, val targetKg: Double = 0.0)

/** Показатели тела: ИМТ, оценка процента жира, цели, прогноз, напоминание о весе. Всё оценочное, не медицина. */
object Body {
    fun bmi(kg: Double, heightCm: Int): Double? =
        if (kg <= 0.0 || heightCm < 80) null else kg / ((heightCm / 100.0) * (heightCm / 100.0))

    fun bmiLabel(b: Double): String = when {
        b < 18.5 -> "ниже нормы"
        b < 25.0 -> "норма"
        b < 30.0 -> "выше нормы"
        else -> "ожирение"
    }

    private fun sexFlag(sex: String): Double? = when (sex) { "m" -> 1.0; "f" -> 0.0; else -> null }

    /** Оценка по формуле Дюрена: 1,2 × ИМТ + 0,23 × возраст − 10,8 × (1 для мужчин) − 5,4. Нужны рост, пол и возраст. Точность невысокая. */
    fun fatPct(kg: Double, heightCm: Int, age: Int?, sex: String): Double? {
        val b = bmi(kg, heightCm) ?: return null
        val s = sexFlag(sex) ?: return null
        if (age == null || age < 18) return null
        return (1.2 * b + 0.23 * age - 10.8 * s - 5.4).coerceAtLeast(3.0)
    }

    fun weightForBmi(b: Double, heightCm: Int): Double = b * (heightCm / 100.0) * (heightCm / 100.0)

    fun weightForFat(fat: Double, heightCm: Int, age: Int?, sex: String): Double? {
        val s = sexFlag(sex) ?: return null
        if (age == null || age < 18 || heightCm < 80) return null
        val b = (fat - 0.23 * age + 10.8 * s + 5.4) / 1.2
        return if (b <= 0.0) null else weightForBmi(b, heightCm)
    }

    /** Целевой вес в кг. Если заданы несколько целей, берётся первая: вес, затем ИМТ, затем процент жира. */
    fun targetWeight(goal: Goal, p: Profile, today: LocalDate): Double? = when {
        goal.weightKg > 0 -> goal.weightKg
        goal.bmi > 0 && p.heightCm >= 80 -> weightForBmi(goal.bmi, p.heightCm)
        goal.fatPct > 0 -> weightForFat(goal.fatPct, p.heightCm, p.age(today), p.sex)
        else -> null
    }

    /** Темп изменения веса, кг в день: линейная регрессия по записям за последние [windowDays] дней. Нужно две записи с разницей от недели. */
    fun slopePerDay(entries: List<WeightEntry>, today: LocalDate, windowDays: Int = 90): Double? {
        val pts = entries.filter { !it.date.isAfter(today) && !it.date.isBefore(today.minusDays(windowDays.toLong())) }.sortedBy { it.date }
        if (pts.size < 2) return null
        if (ChronoUnit.DAYS.between(pts.first().date, pts.last().date) < 7) return null
        val x0 = pts.first().date
        val xs = pts.map { ChronoUnit.DAYS.between(x0, it.date).toDouble() }
        val ys = pts.map { it.kg }
        val mx = xs.average()
        val my = ys.average()
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den == 0.0) return null
        return xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den
    }

    fun forecast(entries: List<WeightEntry>, p: Profile, today: LocalDate): Forecast {
        val target = targetWeight(p.goal, p, today) ?: return Forecast(ForecastStatus.NO_GOAL)
        val last = entries.filter { !it.date.isAfter(today) }.maxByOrNull { it.date } ?: return Forecast(ForecastStatus.NO_DATA, targetKg = target)
        val diff = target - last.kg
        if (Math.abs(diff) < 0.3) return Forecast(ForecastStatus.REACHED, targetKg = target)
        val slope = slopePerDay(entries, today) ?: return Forecast(ForecastStatus.NO_DATA, targetKg = target)
        val perWeek = slope * 7
        if (Math.abs(slope) < 0.005) return Forecast(ForecastStatus.FLAT, kgPerWeek = perWeek, targetKg = target)
        if (slope * diff <= 0) return Forecast(ForecastStatus.WRONG_WAY, kgPerWeek = perWeek, targetKg = target)
        val days = diff / slope
        if (days > 365 * 3) return Forecast(ForecastStatus.FLAT, kgPerWeek = perWeek, targetKg = target)
        return Forecast(ForecastStatus.ON_TRACK, last.date.plusDays(Math.ceil(days).toLong()), perWeek, target)
    }

    /** Пора напомнить про вес: записей нет или последней больше [intervalDays] дней. */
    fun reminderDue(entries: List<WeightEntry>, today: LocalDate, intervalDays: Int = 30): Boolean {
        val last = entries.maxByOrNull { it.date } ?: return true
        return !last.date.plusDays(intervalDays.toLong()).isAfter(today)
    }

    /** Добавляет запись (на ту же дату заменяет). */
    fun withEntry(entries: List<WeightEntry>, e: WeightEntry): List<WeightEntry> =
        (entries.filter { it.date != e.date } + e).sortedBy { it.date }

    fun latest(entries: List<WeightEntry>): WeightEntry? = entries.maxByOrNull { it.date }
}
