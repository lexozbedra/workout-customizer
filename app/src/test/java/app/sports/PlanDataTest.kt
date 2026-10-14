package app.sports

import app.sports.core.*
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanDataTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val plan = TestPlan.create()

    @Test fun jsonRoundtrip() {
        for (p in listOf(plan, DefaultPlan.sample())) {
            val text = PlanFormat.toJson(p)
            val back = PlanFormat.parse(text)
            assertEquals(p.id, back.id); assertEquals(p.name, back.name); assertEquals(p.description, back.description)
            assertEquals(p.phases, back.phases); assertEquals(p.notes, back.notes); assertEquals(p.weeks, back.weeks)
            assertEquals(text, PlanFormat.toJson(back))
        }
    }

    @Test fun copyOfShortensFile() {
        val one = DefaultPlan.sample()
        val tripled = PlanDef("c", "C", "", one.phases, emptyList(), listOf(one.weeks[0], one.weeks[0], one.weeks[0]))
        val text = PlanFormat.toJson(tripled)
        assertEquals(2, Regex("\"copyOf\"").findAll(text).count())
        val back = PlanFormat.parse(text)
        assertEquals(3, back.weekCount)
        assertEquals(tripled.weeks, back.weeks)
        assertEquals(tripled.allSessions().map { it.id }, back.allSessions().map { it.id })
    }

    private fun fails(text: String, part: String) {
        try { PlanFormat.parse(text); throw AssertionError("ожидалась ошибка: $part") }
        catch (e: PlanFormatException) { assertTrue("'${e.message}' должно содержать '$part'", e.message!!.contains(part)) }
    }

    @Test fun validationMessages() {
        fails("не json", "JSON")
        fails("[]", "объект")
        fails("""{"id":"bad id","name":"x","weeks":[]}""", "id")
        fails("""{"id":"a","name":"","weeks":[]}""", "name")
        fails("""{"id":"a","name":"x","weeks":[]}""", "нет ни одной недели")
        fails("""{"id":"a","name":"x","weeks":[{"days":{"9":[]}}]}""", "день")
        fails("""{"id":"a","name":"x","weeks":[{"copyOf":1}]}""", "copyOf")
        val bad = """{"id":"w","title":"T","blocks":[{"steps":[{"title":"S"}]}]}"""
        fails("""{"id":"a","name":"x","weeks":[{"days":{"1":[$bad]}}]}""", "sec")
        val w = """{"id":"w","title":"T","blocks":[{"steps":[{"title":"S","sec":20}]}]}"""
        fails("""{"id":"a","name":"x","weeks":[{"days":{"1":[$w,$w]}}]}""", "разные id")
        val ok = """{"id":"w","title":"T","type":"cardio","blocks":[{"steps":[{"title":"S","sec":30}]}]}"""
        val p = PlanFormat.parse("""{"id":"a","name":"x","weeks":[{"days":{"mon":[$ok]}}]}""")
        assertEquals(listOf(1), p.trainingDows)
        assertEquals(1, p.sessionsFor(1, 1).size)
    }

    @Test fun templateExampleParses() {
        val t = PlanFormat.template()
        val json = t.substring(t.indexOf("{\n  \"format\""))
        assertEquals("test-home-2w", PlanFormat.parse(json).id)
    }

    @Test fun repoRoundtripAndStatuses() {
        val f = MemoryFolder()
        val repo = Repo(f)
        repo.savePlan(plan); repo.savePlan(DefaultPlan.sample())
        f.write("plans/broken.json", "{")
        var db = AppDb(profile = Profile("Вика", 168, 60.5, "f", d("1995-03-10")))
        assertEquals(31, db.profile.age(d("2026-10-14")))
        db = Plans.activate(db, TestPlan.ID, d("2026-10-20"))
        val res = mapOf("w1-2-strength" to Outcome(Status.DONE, 5))
        db = db.copy(states = db.states + (TestPlan.ID to db.state(TestPlan.ID).copy(
            results = res, moves = mapOf("w1-4-treadmill" to 1),
            vacations = listOf(Vacation(d("2026-11-02"), 3, true, "Поездка", "н")),
            tuning = Tuning(sec = mapOf("plank" to 40)))))
        repo.saveProfile(db); repo.saveProgress(db)
        // второй план: прежний уходит на паузу, прогресс остаётся
        db = Plans.activate(db, "test-home-2w", d("2026-11-01"))
        assertEquals(PlanStatus.PAUSED, db.state(TestPlan.ID).status)
        assertEquals(PlanStatus.ACTIVE, db.state("test-home-2w").status)
        assertEquals(res, db.state(TestPlan.ID).results)
        db = Plans.activate(db, TestPlan.ID)
        assertEquals(d("2026-10-20"), db.state(TestPlan.ID).planStart)
        assertEquals(PlanStatus.PAUSED, db.state("test-home-2w").status)
        repo.saveProfile(db); repo.saveProgress(db)

        val l = repo.load()
        assertEquals(db, l.db)
        assertEquals(setOf(TestPlan.ID, "test-home-2w"), l.plans.keys)
        assertEquals(1, l.errors.size); assertTrue(l.errors[0].startsWith("broken.json"))
        assertEquals(1, l.db.states.values.count { it.status == PlanStatus.ACTIVE })

        // автозавершение
        val sample = l.plans["test-home-2w"]!!
        var db2 = Plans.activate(l.db, "test-home-2w", d("2026-11-02"))
        assertEquals(db2, Plans.autoFinish(db2, l.plans, d("2026-11-05")))
        val fin = Plans.autoFinish(db2, l.plans, d("2026-12-31"))
        assertEquals(PlanStatus.FINISHED, fin.state("test-home-2w").status)
        assertEquals(null, fin.activeId)
        assertTrue(Plans.delete(fin, "test-home-2w").states["test-home-2w"] == null)
    }
}
