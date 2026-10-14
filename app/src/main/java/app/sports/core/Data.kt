package app.sports.core

import java.io.File
import java.time.LocalDate

/* ---------- модели ---------- */

enum class PlanStatus { NOT_STARTED, ACTIVE, PAUSED, FINISHED }

/** Всё, что накоплено по одному плану. Хранится отдельно для каждого плана, поэтому переключение ничего не сбрасывает. */
data class PlanState(
    val planStart: LocalDate? = null,
    val status: PlanStatus = PlanStatus.NOT_STARTED,
    val results: Map<String, Outcome> = emptyMap(),
    val moves: Map<String, Int> = emptyMap(),
    val vacations: List<Vacation> = emptyList(),
    val tuning: Tuning = Tuning.NONE,
    val finishedAt: LocalDate? = null,
)

data class Profile(
    val name: String = "",
    val heightCm: Int = 0,
    val weightKg: Double = 0.0,
    val sex: String = "", // "f" | "m" | ""
    val birthDate: LocalDate? = null,
    val goal: Goal = Goal(),
) {
    fun age(today: LocalDate): Int? = birthDate?.let { b ->
        if (b.isAfter(today)) null else java.time.Period.between(b, today).years
    }
}

/** Цель: любое из трёх значений (0 = не задано). Приоритет при расчёте: вес, ИМТ, процент жира. */
data class Goal(val weightKg: Double = 0.0, val bmi: Double = 0.0, val fatPct: Double = 0.0)

data class WeightEntry(val date: LocalDate, val kg: Double)

data class Settings(val duck: Boolean = true, val overlay: Boolean = true, val weightReminder: Boolean = true)

data class AppDb(
    val profile: Profile = Profile(),
    val settings: Settings = Settings(),
    val states: Map<String, PlanState> = emptyMap(),
    val activeId: String? = null,
    val weights: List<WeightEntry> = emptyList(),
) {
    fun state(id: String): PlanState = states[id] ?: PlanState()
}

/* ---------- папка с данными ---------- */

/** Папка пользователя с файлами. Пути относительные: `profile.json`, `plans/my.json`. */
interface DataFolder {
    fun read(path: String): String?
    fun write(path: String, text: String)
    fun delete(path: String)
    /** Имена файлов в подпапке (без пути). */
    fun list(dir: String): List<String>
}

class MemoryFolder : DataFolder {
    val files = LinkedHashMap<String, String>()
    override fun read(path: String) = files[path]
    override fun write(path: String, text: String) { files[path] = text }
    override fun delete(path: String) { files.remove(path) }
    override fun list(dir: String) = files.keys.filter { it.startsWith("$dir/") }.map { it.substringAfter("$dir/") }
}

/** Обычная папка на диске (внутреннее хранилище приложения и тесты). Запись через временный файл. */
class FileFolder(private val root: File) : DataFolder {
    private fun f(path: String) = File(root, path)
    override fun read(path: String): String? = f(path).takeIf { it.isFile }?.readText(Charsets.UTF_8)
    override fun write(path: String, text: String) {
        val target = f(path)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(target)) { target.writeText(text, Charsets.UTF_8); tmp.delete() }
    }
    override fun delete(path: String) { f(path).delete() }
    override fun list(dir: String): List<String> = f(dir).listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.map { it.name }?.sorted().orEmpty()
}

/* ---------- чтение и запись ---------- */

class Loaded(val db: AppDb, val plans: Map<String, PlanDef>, val errors: List<String>)

class Repo(private val folder: DataFolder) {
    companion object {
        const val PROFILE = "profile.json"
        const val PROGRESS = "progress.json"
        const val PLANS = "plans"
    }

    fun load(): Loaded {
        val errors = ArrayList<String>()
        val plans = LinkedHashMap<String, PlanDef>()
        for (name in folder.list(PLANS).filter { it.endsWith(".json") }.sorted()) {
            try {
                val p = PlanFormat.parse(folder.read("$PLANS/$name").orEmpty())
                if (plans.containsKey(p.id)) errors += "$name: план с id ${p.id} уже есть, файл пропущен" else plans[p.id] = p
            } catch (e: PlanFormatException) {
                errors += "$name: ${e.message}"
            }
        }
        var profile = Profile()
        var settings = Settings()
        var activeId: String? = null
        var weights: List<WeightEntry> = emptyList()
        folder.read(PROFILE)?.let { t ->
            try {
                val o = Json.parse(t).asObj().orEmpty()
                profile = profileFrom(o["profile"].asObj().orEmpty())
                settings = o["settings"].asObj()?.let { Settings(it.bool("duck", true), it.bool("overlay", true), it.bool("weightReminder", true)) } ?: Settings()
                weights = o["weights"].asList().orEmpty().mapNotNull { x ->
                    val r = x.asObj() ?: return@mapNotNull null
                    val d = date(r.str("date")) ?: return@mapNotNull null
                    WeightEntry(d, r.dbl("kg")).takeIf { it.kg > 0 }
                }.sortedBy { it.date }
                activeId = (o["activeId"] as? String)?.takeIf { it.isNotEmpty() }
            } catch (e: Json.ParseException) { errors += "$PROFILE: ${e.message}" }
        }
        val states = LinkedHashMap<String, PlanState>()
        folder.read(PROGRESS)?.let { t ->
            try {
                val o = Json.parse(t).asObj().orEmpty()
                for ((id, v) in o["plans"].asObj().orEmpty()) stateFrom(v.asObj().orEmpty())?.let { states[id] = it }
            } catch (e: Json.ParseException) { errors += "$PROGRESS: ${e.message}" }
        }
        // Не теряем прогресс, если файл плана пропал: состояние остаётся, план можно импортировать снова.
        var active = activeId?.takeIf { states[it]?.status == PlanStatus.ACTIVE }
        if (active == null) active = states.entries.firstOrNull { it.value.status == PlanStatus.ACTIVE }?.key
        val fixed = states.mapValues { (id, s) -> if (s.status == PlanStatus.ACTIVE && id != active) s.copy(status = PlanStatus.PAUSED) else s }
        return Loaded(AppDb(profile, settings, fixed, active, weights), plans, errors)
    }

    fun saveProfile(db: AppDb) {
        val m = linkedMapOf<String, Any?>(
            "profile" to profileMap(db.profile),
            "settings" to linkedMapOf("duck" to db.settings.duck, "overlay" to db.settings.overlay, "weightReminder" to db.settings.weightReminder),
            "activeId" to (db.activeId ?: ""),
            "weights" to db.weights.sortedBy { it.date }.map { linkedMapOf("date" to it.date.toString(), "kg" to it.kg) },
        )
        folder.write(PROFILE, Json.stringify(m))
    }

    fun saveProgress(db: AppDb) {
        val m = linkedMapOf<String, Any?>("plans" to db.states.mapValues { stateMap(it.value) })
        folder.write(PROGRESS, Json.stringify(m))
    }

    fun savePlan(p: PlanDef) = folder.write("$PLANS/${p.id}.json", PlanFormat.toJson(p))

    fun deletePlan(id: String) = folder.delete("$PLANS/$id.json")

    /* --- (де)сериализация --- */

    private fun profileMap(p: Profile) = linkedMapOf<String, Any?>(
        "name" to p.name, "heightCm" to p.heightCm, "weightKg" to p.weightKg, "sex" to p.sex, "birthDate" to (p.birthDate?.toString() ?: ""),
        "goal" to linkedMapOf("weightKg" to p.goal.weightKg, "bmi" to p.goal.bmi, "fatPct" to p.goal.fatPct),
    )

    private fun profileFrom(o: Map<String, Any?>) = Profile(
        o.str("name"), o.int("heightCm"), o.dbl("weightKg"), o.str("sex"), date(o.str("birthDate")),
        o["goal"].asObj()?.let { Goal(it.dbl("weightKg"), it.dbl("bmi"), it.dbl("fatPct")) } ?: Goal(),
    )

    private fun date(s: String): LocalDate? = try { if (s.isBlank()) null else LocalDate.parse(s) } catch (e: Exception) { null }

    private fun stateMap(s: PlanState): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["status"] = s.status.name
        m["planStart"] = s.planStart?.toString() ?: ""
        m["finishedAt"] = s.finishedAt?.toString() ?: ""
        m["results"] = s.results.mapValues { (_, o) ->
            val om = linkedMapOf<String, Any?>("status" to o.status.name, "at" to o.at)
            if (o.percent != 100) om["percent"] = o.percent
            if (o.reason.isNotEmpty()) om["reason"] = o.reason
            if (o.note.isNotEmpty()) om["note"] = o.note
            if (o.activeSec != 0) om["activeSec"] = o.activeSec
            om
        }
        m["moves"] = s.moves
        m["vacations"] = s.vacations.map {
            linkedMapOf("start" to it.start.toString(), "days" to it.days, "alignWeeks" to it.alignWeeks, "reason" to it.reason, "note" to it.note)
        }
        m["tuning"] = linkedMapOf("sec" to s.tuning.sec, "reps" to s.tuning.reps, "extra" to s.tuning.extra)
        return m
    }

    private fun stateFrom(o: Map<String, Any?>): PlanState? {
        val status = try { PlanStatus.valueOf(o.str("status", "NOT_STARTED")) } catch (e: Exception) { PlanStatus.NOT_STARTED }
        val results = LinkedHashMap<String, Outcome>()
        for ((id, v) in o["results"].asObj().orEmpty()) {
            val r = v.asObj() ?: continue
            val st = try { Status.valueOf(r.str("status")) } catch (e: Exception) { continue }
            results[id] = Outcome(st, (r["at"] as? Number)?.toLong() ?: 0L, r.int("percent", 100), r.str("reason"), r.str("note"), r.int("activeSec"))
        }
        val moves = o["moves"].asObj().orEmpty().mapNotNull { (k, v) -> (v as? Number)?.let { k to it.toInt() } }.toMap()
        val vac = o["vacations"].asList().orEmpty().mapNotNull { x ->
            val r = x.asObj() ?: return@mapNotNull null
            val st = date(r.str("start")) ?: return@mapNotNull null
            Vacation(st, r.int("days", 1), r.bool("alignWeeks"), r.str("reason"), r.str("note"))
        }
        val t = o["tuning"].asObj().orEmpty()
        fun ints(k: String) = t[k].asObj().orEmpty().mapNotNull { (a, b) -> (b as? Number)?.let { a to it.toInt() } }.toMap()
        return PlanState(date(o.str("planStart")), status, results, moves, vac, Tuning(ints("sec"), ints("reps"), ints("extra")), date(o.str("finishedAt")))
    }
}

/* ---------- переходы между статусами ---------- */

object Plans {
    /** Делает план активным. Прежний активный уходит на паузу, прогресс обоих сохраняется. Для нового плана нужна дата старта. */
    fun activate(db: AppDb, id: String, start: LocalDate? = null): AppDb {
        val states = db.states.toMutableMap()
        db.activeId?.let { cur -> if (cur != id) states[cur]?.let { states[cur] = it.copy(status = PlanStatus.PAUSED) } }
        val s = db.state(id)
        val begin = if (s.status == PlanStatus.NOT_STARTED || s.planStart == null) (start ?: LocalDate.now()) else s.planStart
        states[id] = s.copy(status = PlanStatus.ACTIVE, planStart = begin, finishedAt = null)
        return db.copy(states = states, activeId = id)
    }

    fun finish(db: AppDb, id: String, today: LocalDate): AppDb {
        val states = db.states.toMutableMap()
        states[id] = db.state(id).copy(status = PlanStatus.FINISHED, finishedAt = today)
        return db.copy(states = states, activeId = if (db.activeId == id) null else db.activeId)
    }

    fun delete(db: AppDb, id: String): AppDb =
        db.copy(states = db.states - id, activeId = if (db.activeId == id) null else db.activeId)

    fun schedule(plan: PlanDef, s: PlanState): Schedule? =
        s.planStart?.let { Schedule(plan, it, s.vacations, s.moves, s.tuning) }

    /** Активный план, у которого прошла последняя тренировка, считается завершённым. */
    fun autoFinish(db: AppDb, defs: Map<String, PlanDef>, today: LocalDate): AppDb {
        val id = db.activeId ?: return db
        val plan = defs[id] ?: return db
        val last = schedule(plan, db.state(id))?.lastDate ?: return db
        return if (last.isBefore(today)) finish(db, id, last) else db
    }

    fun statusOf(db: AppDb, id: String): PlanStatus = db.state(id).status
}
