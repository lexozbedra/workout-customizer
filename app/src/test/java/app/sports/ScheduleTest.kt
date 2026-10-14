package app.sports

import app.sports.core.DayStatus
import app.sports.core.Eff
import app.sports.core.Outcome
import app.sports.core.Results
import app.sports.core.Status
import app.sports.core.StatsCalc
import app.sports.core.Tuning
import app.sports.core.TestPlan
import app.sports.core.Schedule
import app.sports.core.SessionType
import app.sports.core.Vacation
import app.sports.workout.WorkoutEngine
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun dt(s: String) = LocalDateTime.parse(s)
    private val plan = TestPlan.create()
    private val base = Schedule(plan, d("2026-10-20")) // старт во вторник 20 октября 2026

    @Test
    fun stepsAreContiguousAndIdsResolve() {
        plan.allSessions().forEach { s ->
            assertTrue(s.id, s.steps.zipWithNext().all { (a, b) -> a.endSec == b.startSec })
            assertEquals(s.id, plan.byId(s.id)?.id)
        }
        assertEquals(80, plan.allSessions().map { it.id }.toSet().size)
    }

    @Test
    fun baseDates() {
        assertEquals(d("2026-10-20"), base.baseDate(1, 2))
        assertEquals(d("2026-10-22"), base.baseDate(1, 4))
        assertEquals(d("2026-10-24"), base.baseDate(1, 6))
        assertEquals(d("2026-10-27"), base.baseDate(2, 2))
        assertEquals(80, base.planned.size)
        assertEquals(2, base.on(d("2026-10-20")).size)
        assertEquals(d("2027-02-06"), base.lastDate)
        assertEquals(DayStatus.BEFORE_START, base.status(d("2026-10-14")))
        assertEquals(DayStatus.AFTER_END, base.status(d("2027-02-10")))
    }

    @Test
    fun vacationShiftsPlanByItsLength() {
        val v7 = Schedule(plan, d("2026-10-20"), listOf(Vacation(d("2026-10-27"), 7)))
        assertEquals(2, v7.on(d("2026-10-22")).size)
        assertEquals(d("2026-11-03"), v7.find("w2-2-strength")!!.date)
        assertTrue((0L..6L).all { v7.on(d("2026-10-27").plusDays(it)).isEmpty() })
        assertEquals(d("2027-02-13"), v7.lastDate)

        val v10 = Schedule(plan, d("2026-10-20"), listOf(Vacation(d("2026-10-28"), 10)))
        assertEquals(d("2026-10-27"), v10.find("w2-2-strength")!!.date)
        assertEquals(d("2026-11-08"), v10.find("w2-4-strength")!!.date)
        assertEquals(d("2027-02-16"), v10.lastDate)

        val aligned = Schedule(plan, d("2026-10-20"), listOf(Vacation(d("2026-10-28"), 10, alignWeeks = true)))
        assertEquals(d("2026-11-12"), aligned.find("w2-4-strength")!!.date)
        assertTrue(aligned.planned.all { it.date.dayOfWeek == it.baseDate.dayOfWeek })

        val two = Schedule(plan, d("2026-10-20"), listOf(Vacation(d("2026-12-01"), 5), Vacation(d("2026-11-03"), 3)))
        assertEquals(d("2027-02-14"), two.lastDate)
    }

    @Test
    fun rescheduleRulesWithinCalendarWeek() {
        val tue = base.find("w1-2-strength")!!
        fun at(now: String) = base.allowedTargets(tue, dt(now)).map { it.toString() }

        assertTrue("2026-10-19" in at("2026-10-19T20:00:00")) // на понедельник до 21:00 понедельника
        assertTrue("2026-10-19" !in at("2026-10-19T21:01:00"))
        assertTrue("2026-10-21" in at("2026-10-19T21:01:00")) // на среду можно до 21:00 вторника
        assertTrue(listOf("2026-10-21", "2026-10-22", "2026-10-23", "2026-10-25").all { it in at("2026-10-20T20:59:00") })
        assertTrue(at("2026-10-20T21:01:00").isEmpty())
        assertTrue("2026-10-18" !in at("2026-10-15T08:00:00")) // прошлое воскресенье
        assertTrue("2026-10-26" !in at("2026-10-15T08:00:00")) // следующий понедельник
        assertTrue("2026-10-20" !in at("2026-10-15T08:00:00"))
        assertEquals(6, at("2026-10-15T08:00:00").size)

        val withVacation = Schedule(plan, d("2026-10-20"), listOf(Vacation(d("2026-10-23"), 2)))
        assertTrue(withVacation.allowedTargets(tue, dt("2026-10-15T08:00:00")).none { it == d("2026-10-23") || it == d("2026-10-24") })
    }

    @Test
    fun movesArePersistedAsOffsets() {
        val moved = Schedule(plan, d("2026-10-20"), emptyList(), base.withMove("w1-2-strength", d("2026-10-21")))
        assertEquals(1, moved.moves["w1-2-strength"])
        assertEquals(d("2026-10-21"), moved.find("w1-2-strength")!!.date)
        assertEquals(d("2026-10-20"), moved.find("w1-2-treadmill")!!.date)
        assertTrue(moved.withMove("w1-2-strength", d("2026-10-20")).isEmpty())
        val both = Schedule(plan, d("2026-10-20"), listOf(Vacation(d("2026-10-27"), 7)), mapOf("w1-2-strength" to 1))
        assertEquals(d("2026-10-21"), both.find("w1-2-strength")!!.date)
        assertEquals(d("2026-11-03"), both.find("w2-2-strength")!!.date)
    }

    @Test
    fun engineClockPausesAndSeeks() {
        var t = 0L
        val e = WorkoutEngine(plan.sessionsFor(1, 2)[0].steps) { t }
        e.start(); t += 12_000
        assertEquals(1, e.stepIndex())
        e.pause(); t += 60_000
        assertEquals(12_000L, e.position())
        e.resume(); t += 1_000
        assertEquals(13_000L, e.position())
        e.seek(e.steps[1].startSec * 1000L)
        assertEquals(0L, e.prevTarget())
        t += 5_000
        assertEquals(10_000L, e.prevTarget())
        assertEquals(e.steps[2].startSec * 1000L, e.nextTarget())
        e.seek(e.totalMs - 1)
        assertTrue(!e.finished)
        t += 5
        assertTrue(e.finished)
    }

    @Test
    fun tuningChangesOnlyWhatWasAsked() {
        val sid = "w1-2-strength"
        val plain = plan.byId(sid)!!
        val one = plan.byId(sid, Tuning(reps = mapOf("$sid/squat" to 15)))!!
        assertTrue(one.steps.any { it.key == "squat" && it.reps == 15 && it.sub.startsWith("15") })
        assertTrue(one.totalSec > plain.totalSec)
        // для другой тренировки та же правка не действует
        assertEquals(plain.totalSec, plan.byId("w2-2-strength")!!.totalSec - (plan.byId("w2-2-strength")!!.totalSec - plain.totalSec))
        val global = plan.byId("w2-2-strength", Tuning(reps = mapOf("squat" to 15)))!!
        assertTrue(global.steps.filter { it.key == "squat" }.all { it.reps == 15 })
        val plank = plan.byId(sid, Tuning(sec = mapOf("plank" to 20)))!!
        assertTrue(plank.steps.filter { it.key == "plank" }.all { it.durSec == 20 && it.sub == "20 секунд" })
        val rounds = plan.byId(sid, Tuning(extra = mapOf("*strength" to 1)))!!
        assertTrue(rounds.totalSec > plain.totalSec)
        val tm = plan.byId("w1-2-treadmill", Tuning(extra = mapOf("w1-2-treadmill" to -1), sec = mapOf("tm.hill" to 90)))!!
        assertEquals(3, tm.steps.count { it.key == "tm.hill" })
        assertTrue(tm.steps.filter { it.key == "tm.hill" }.all { it.durSec == 90 })
        // шаги идут подряд без дыр
        var t = 0
        for (st in tm.steps) { assertEquals(t, st.startSec); t += st.durSec }
        assertTrue(plain.editable.any { it.key == "rest.round" } && plain.exercises.size == 2)
    }

    @Test
    fun skippedAndPartialAndStats() {
        val sch = Schedule(plan, d("2026-10-20"))
        val first = sch.planned.first()          // вт 20 окт, силовая
        val second = sch.planned[1]              // вт 20 окт, дорожка
        val third = sch.planned[2]               // чт 22 окт
        val today = d("2026-10-21")
        assertEquals(Eff.AUTO_SKIPPED, Results.effective(first, emptyMap(), today))
        assertEquals(Eff.PENDING, Results.effective(third, emptyMap(), today))
        val res = mapOf(
            first.id to Outcome(Status.PARTIAL, 0, percent = 50, reason = "Устала", activeSec = first.session.totalSec / 2),
            second.id to Outcome(Status.DONE, 0),
            third.id to Outcome(Status.SKIPPED, 0, reason = "Другое"),
        )
        assertEquals(Eff.PARTIAL, Results.effective(first, res, today))
        val st = StatsCalc.compute(sch.planned, res, today, 65)
        assertEquals(80, st.total)
        assertEquals(1, st.done); assertEquals(1, st.partial); assertEquals(1, st.skipped); assertEquals(77, st.pending)
        assertEquals(1.5, st.units, 1e-9)
        assertEquals(1, st.activeDays); assertEquals(1, st.gymDays)
        assertEquals((first.session.totalSec / 2 + second.session.totalSec).toLong(), st.activeSec)
        assertTrue(st.kcal in 100..800)
        // идущая вперёд дата: прошедший, но не отмеченный день считается пропущенным только до отметки
        val late = res + (third.id to Outcome(Status.DONE, 0))
        assertEquals(Eff.DONE, Results.effective(third, late, d("2026-12-01")))
    }
}
