package app.sports

import app.sports.core.*
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun e(s: String, kg: Double) = WeightEntry(d(s), kg)

    @Test fun bmiAndFat() {
        assertEquals(21.26, Body.bmi(60.0, 168)!!, 0.01)
        assertEquals("норма", Body.bmiLabel(21.3))
        assertEquals("ниже нормы", Body.bmiLabel(17.0)); assertEquals("выше нормы", Body.bmiLabel(27.0)); assertEquals("ожирение", Body.bmiLabel(31.0))
        assertEquals(null, Body.bmi(60.0, 0)); assertEquals(null, Body.fatPct(60.0, 168, 30, ""))
        assertEquals(27.2, Body.fatPct(60.0, 168, 31, "f")!!, 0.1)
        assertEquals(null, Body.fatPct(60.0, 168, null, "f"))
        // обратный расчёт: вес для процента жира даёт тот же процент
        val w = Body.weightForFat(25.0, 168, 31, "f")!!
        assertEquals(25.0, Body.fatPct(w, 168, 31, "f")!!, 0.01)
        assertEquals(60.0, Body.weightForBmi(21.2585, 168), 0.01)
    }

    @Test fun targetsFromGoals() {
        val p = Profile("В", 168, 60.0, "f", d("1995-03-10"))
        val t = d("2026-10-14")
        assertEquals(null, Body.targetWeight(Goal(), p, t))
        assertEquals(55.0, Body.targetWeight(Goal(weightKg = 55.0, bmi = 20.0), p, t)!!, 1e-9)
        assertEquals(Body.weightForBmi(20.0, 168), Body.targetWeight(Goal(bmi = 20.0), p, t)!!, 1e-9)
        assertTrue(Body.targetWeight(Goal(fatPct = 24.0), p, t)!! < 60.0)
        assertEquals(null, Body.targetWeight(Goal(fatPct = 24.0), p.copy(sex = ""), t))
    }

    @Test fun slopeAndForecast() {
        val today = d("2026-10-14")
        val p = Profile("В", 168, 62.0, "f", d("1995-03-10"), Goal(weightKg = 58.0))
        val down = listOf(e("2026-08-14", 66.0), e("2026-09-14", 64.0), e("2026-10-14", 62.0)) // -2 кг за месяц
        val sl = Body.slopePerDay(down, today)!!
        assertEquals(-2.0 / 30, sl, 0.01)
        val f = Body.forecast(down, p, today)
        assertEquals(ForecastStatus.ON_TRACK, f.status)
        assertEquals(-0.46, f.kgPerWeek, 0.05)
        assertTrue(f.date!!.isAfter(d("2026-11-20")) && f.date!!.isBefore(d("2026-12-31")))
        assertEquals(ForecastStatus.WRONG_WAY, Body.forecast(down, p.copy(goal = Goal(weightKg = 70.0)), today).status)
        assertEquals(ForecastStatus.REACHED, Body.forecast(down, p.copy(goal = Goal(weightKg = 62.1)), today).status)
        assertEquals(ForecastStatus.NO_GOAL, Body.forecast(down, p.copy(goal = Goal()), today).status)
        assertEquals(ForecastStatus.NO_DATA, Body.forecast(listOf(e("2026-10-14", 62.0)), p, today).status)
        assertEquals(ForecastStatus.NO_DATA, Body.forecast(listOf(e("2026-10-10", 63.0), e("2026-10-14", 62.0)), p, today).status) // меньше недели
        val flat = listOf(e("2026-09-01", 62.0), e("2026-10-14", 62.0))
        assertEquals(ForecastStatus.FLAT, Body.forecast(flat, p, today).status)
    }

    @Test fun reminderAndEntries() {
        val today = d("2026-10-14")
        assertTrue(Body.reminderDue(emptyList(), today))
        assertTrue(!Body.reminderDue(listOf(e("2026-09-20", 60.0)), today))
        assertTrue(Body.reminderDue(listOf(e("2026-09-14", 60.0)), today))
        val l = Body.withEntry(listOf(e("2026-09-14", 60.0)), e("2026-10-14", 61.0))
        assertEquals(2, l.size)
        assertEquals(1, Body.withEntry(l, e("2026-10-14", 59.0)).count { it.date == d("2026-10-14") })
        assertEquals(59.0, Body.latest(Body.withEntry(l, e("2026-10-14", 59.0)))!!.kg, 1e-9)
    }

    @Test fun profileGoalsAndWeightsPersist() {
        val f = MemoryFolder()
        val repo = Repo(f)
        val db = AppDb(
            Profile("В", 168, 60.0, "f", d("1995-03-10"), Goal(55.0, 0.0, 24.0)),
            Settings(true, false, false),
            weights = listOf(e("2026-09-14", 62.0), e("2026-10-14", 60.0)),
        )
        repo.saveProfile(db); repo.saveProgress(db)
        assertEquals(db, repo.load().db)
    }
}
