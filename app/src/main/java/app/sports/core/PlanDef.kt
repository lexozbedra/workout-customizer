package app.sports.core

/**
 * Тренировочный план как данные. План не зашит в код: его описывает файл (см. [PlanFormat]),
 * а здесь он превращается в тренировки с шагами, таймерами и правками пользователя ([Tuning]).
 */

/** Один шаг: упражнение, интервал бега, отдых. Длительность задаётся секундами либо повторениями и темпом. */
data class StepDef(
    val title: String,
    /** Подпись под названием. Если пусто, строится сама: «12 повторений» или «45 секунд». */
    val sub: String = "",
    val kind: Kind = Kind.WORK,
    val sec: Int = 0,
    val reps: Int = 0,
    /** Что показывать вместо числа повторений, например «8–10». */
    val repsText: String = "",
    /** Секунд на повторение: время шага считается как повторения × темп + 6 секунд (не меньше 20). */
    val tempo: Double = 0.0,
    /** Стабильный код упражнения: по нему правки применяются сразу ко всем таким же. По умолчанию название. */
    val key: String = "",
    /** Если задано, шаг выполняется по очереди для каждой стороны («левая нога», «правая нога»). */
    val sides: List<String> = emptyList(),
    /** Поисковый запрос для кнопки «Узнать больше». */
    val video: String = "",
    val videoTitle: String = "",
    /** Название в редакторе, если оно должно отличаться от названия шага. */
    val label: String = "",
)

data class BlockDef(
    val name: String = "",
    val repeat: Int = 1,
    /** Можно ли менять число кругов в приложении. */
    val adjustable: Boolean = false,
    val groupFormat: String = "{name} · круг {r}/{n}",
    val steps: List<StepDef>,
    /** Отдых между кругами блока. */
    val restBetween: StepDef? = null,
    /** Отдых после блока (кроме последнего блока тренировки). */
    val restAfter: StepDef? = null,
)

data class WorkoutDef(
    val id: String,
    val title: String,
    /** Может содержать {n} (кругов), {work} и {rest} (время работы и отдыха): они подставятся с учётом правок. */
    val subtitle: String = "",
    val place: String = "",
    val type: SessionType = SessionType.STRENGTH,
    val prepSec: Int = 10,
    val prepHint: String = "",
    val notes: List<OutlineBlock> = emptyList(),
    val blocks: List<BlockDef>,
)

data class WeekDef(val phase: Int, val deload: Boolean, val days: Map<Int, List<WorkoutDef>>)

class PlanDef(
    val id: String,
    val name: String,
    val description: String,
    val phases: List<Phase>,
    /** Заметки, которые показываются на экране плана. */
    val notes: List<String>,
    val weeks: List<WeekDef>,
) {
    val weekCount: Int get() = weeks.size

    /** Дни недели (ISO: 1 = понедельник), в которые есть тренировки. */
    val trainingDows: List<Int> by lazy { weeks.flatMap { it.days.keys }.distinct().sorted() }

    fun weekInfo(w: Int): WeekInfo {
        val wk = weeks[w - 1]
        val idx = wk.phase.coerceIn(1, phases.size)
        return WeekInfo(w, idx, wk.deload, phases[idx - 1])
    }

    fun sessionsFor(w: Int, dow: Int, t: Tuning = Tuning.NONE): List<Session> =
        weeks.getOrNull(w - 1)?.days?.get(dow)?.map { build(w, dow, it, t) } ?: emptyList()

    /** Восстанавливает тренировку по её id вида w3-4-treadmill. */
    fun byId(id: String, t: Tuning = Tuning.NONE): Session? {
        val m = ID_RE.matchEntire(id) ?: return null
        val w = m.groupValues[1].toInt()
        val dow = m.groupValues[2].toInt()
        val wo = weeks.getOrNull(w - 1)?.days?.get(dow)?.firstOrNull { it.id == m.groupValues[3] } ?: return null
        return build(w, dow, wo, t)
    }

    fun allSessions(t: Tuning = Tuning.NONE): List<Session> =
        (1..weekCount).flatMap { w -> trainingDows.flatMap { sessionsFor(w, it, t) } }

    /* ---------- сборка шагов ---------- */

    private class Raw(
        val kind: Kind, val title: String, val sub: String, val dur: Int, val group: String,
        val key: String, val reps: Int, val video: String, val videoTitle: String, val label: String,
    )

    private fun expand(s: StepDef, group: String, sid: String, t: Tuning): List<Raw> {
        val key = s.key.ifEmpty { s.title.replace("/", "-") }
        val rOv = if (s.reps > 0) t.repsFor(sid, key) else null
        val reps = if (s.reps > 0) (rOv ?: s.reps) else 0
        val repsText = rOv?.toString() ?: s.repsText.ifEmpty { s.reps.toString() }
        val computed = if (s.reps > 0 && s.tempo > 0) maxOf(20, Math.round(reps * s.tempo + 6).toInt()) else s.sec
        val dur = maxOf(1, t.secFor(sid, key) ?: computed)
        val base = when {
            s.sub.isNotEmpty() -> s.sub
            s.reps > 0 -> "$repsText повторений"
            s.kind == Kind.WORK -> "$dur секунд"
            else -> ""
        }
        val vt = s.videoTitle.ifEmpty { s.title }
        val label = s.label.ifEmpty { s.title }
        return if (s.sides.isEmpty()) {
            listOf(Raw(s.kind, s.title, base, dur, group, key, reps, s.video, vt, label))
        } else {
            s.sides.map { side -> Raw(s.kind, s.title, "$side · $base", dur, group, key, reps, s.video, vt, label) }
        }
    }

    private fun circles(n: Int): String = when {
        n == 1 -> "1 круг"
        n in 2..4 -> "$n круга"
        else -> "$n кругов"
    }

    private fun stepLine(s: StepDef, sid: String, t: Tuning): String {
        val e = expand(s, "", sid, t).first()
        val amount = if (e.reps > 0) {
            (t.repsFor(sid, e.key)?.toString() ?: s.repsText.ifEmpty { s.reps.toString() }) + " повт."
        } else {
            Fmt.dur(e.dur)
        }
        val side = if (s.sides.isNotEmpty()) " на сторону" else ""
        val extra = if (s.sub.isNotEmpty() && s.reps == 0) " · " + s.sub else ""
        return s.title + " – " + amount + side + extra
    }

    private fun build(w: Int, dow: Int, wo: WorkoutDef, t: Tuning): Session {
        val sid = "w$w-$dow-${wo.id}"
        val extra = t.extraFor(sid, wo.type)
        val raw = mutableListOf<Raw>()
        val outline = mutableListOf<OutlineBlock>()
        outline += wo.notes
        if (wo.prepSec > 0) {
            val dur = t.secFor(sid, "prep") ?: wo.prepSec
            raw += Raw(Kind.REST, "Приготовься", wo.prepHint, dur, "Старт", "prep", 0, "", "", "Приготовься")
        }
        var adjRound: List<Raw>? = null
        var adjN = 1
        wo.blocks.forEachIndexed { bi, b ->
            val n = if (b.adjustable) (b.repeat + extra).coerceIn(1, 12) else b.repeat
            val lines = b.steps.map { stepLine(it, sid, t) }.toMutableList()
            b.restBetween?.let { if (n > 1) lines += "Отдых между кругами – " + Fmt.dur(expand(it, "", sid, t).first().dur) }
            b.restAfter?.let { if (bi < wo.blocks.size - 1) lines += "Отдых после блока – " + Fmt.dur(expand(it, "", sid, t).first().dur) }
            val head = (b.name.ifEmpty { "Блок ${bi + 1}" }) + if (n > 1) " · " + circles(n) else ""
            outline += OutlineBlock(head, lines)
            for (r in 1..n) {
                val group = if (n > 1) b.groupFormat.replace("{name}", b.name).replace("{r}", r.toString()).replace("{n}", n.toString())
                else b.name.ifEmpty { "Блок ${bi + 1}" }
                val round = b.steps.flatMap { expand(it, group, sid, t) }
                if (b.adjustable && adjRound == null) { adjRound = round; adjN = n }
                raw += round
                val last = r == n
                val rest = if (!last) b.restBetween else if (bi < wo.blocks.size - 1) b.restAfter else null
                if (rest != null) raw += expand(rest, b.name.ifEmpty { "Блок ${bi + 1}" }, sid, t)
            }
        }
        var at = 0
        val steps = raw.mapIndexed { i, r ->
            val s = Step(i, r.kind, r.title, r.sub, r.dur, r.group, at, r.key, r.reps)
            at += r.dur
            s
        }
        val workDur = (adjRound?.firstOrNull { it.kind == Kind.WORK } ?: raw.firstOrNull { it.kind == Kind.WORK && it.key != "prep" })?.dur ?: 0
        val restDur = (adjRound?.firstOrNull { it.kind == Kind.REST } ?: raw.firstOrNull { it.kind == Kind.REST && it.key != "prep" })?.dur ?: 0
        val subtitle = wo.subtitle.replace("{n}", adjN.toString()).replace("{work}", Fmt.dur(workDur)).replace("{rest}", Fmt.dur(restDur))
        val editable = raw.filter { it.key != "prep" && it.key != "custom" }.distinctBy { it.key }.map { r ->
            val step = steps[raw.indexOf(r)]
            Editable(r.key, r.label, step.durSec, step.reps)
        }
        val exercises = raw.filter { it.video.isNotEmpty() }.map { ExerciseRef(it.videoTitle, it.video) }.distinctBy { it.title }
        return Session(
            sid, w, dow, wo.type, wo.title, subtitle, wo.place, steps, outline, editable, exercises,
            hasRounds = wo.blocks.any { it.adjustable },
        )
    }

    companion object {
        private val ID_RE = Regex("w(\\d+)-(\\d)-(.+)")
    }
}

object Fmt {
    /** 300 -> «5 мин», 90 -> «1 мин 30 с», 45 -> «45 с». */
    fun dur(sec: Int): String = when {
        sec < 60 -> "$sec с"
        sec % 60 == 0 -> "${sec / 60} мин"
        else -> "${sec / 60} мин ${sec % 60} с"
    }
}
