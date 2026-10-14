package app.sports.core

/** Ошибка в файле плана, текст понятен человеку и показывается при импорте. */
class PlanFormatException(msg: String) : Exception(msg)

/** Файл плана (JSON) <-> [PlanDef], проверка, шаблон для нейросети. */
object PlanFormat {
    const val FORMAT = "workout-plan"
    const val VERSION = 1

    private val ID_RE = Regex("[A-Za-z0-9_-]{1,40}")
    private val WORKOUT_ID_RE = Regex("[A-Za-z0-9_]{1,20}")

    private fun fail(path: String, msg: String): Nothing = throw PlanFormatException(if (path.isEmpty()) msg else "$path: $msg")

    /* ---------- чтение ---------- */

    fun parse(text: String): PlanDef {
        val root = try {
            Json.parse(text).asObj() ?: fail("", "Файл должен быть JSON-объектом { ... }")
        } catch (e: Json.ParseException) {
            fail("", "Это не JSON: " + e.message)
        }
        val format = root.str("format")
        if (format.isNotEmpty() && format != FORMAT) fail("", "Поле format должно быть \"$FORMAT\", а не \"$format\"")
        if (root.int("version", VERSION) > VERSION) fail("", "Файл сделан для более новой версии приложения")
        val id = root.str("id").trim()
        if (!ID_RE.matches(id)) fail("id", "нужен короткий код из латинских букв, цифр, - и _ (например my-plan), а сейчас \"$id\"")
        val name = root.str("name").trim()
        if (name.isEmpty()) fail("name", "название плана не может быть пустым")

        val phases = root["phases"].asList().orEmpty().mapIndexed { i, p ->
            val o = p.asObj() ?: fail("phases[${i + 1}]", "ожидался объект")
            Phase(i + 1, o.str("name", "Фаза ${i + 1}"), o.str("weeks"), o.str("hint"))
        }.ifEmpty { listOf(Phase(1, name, "", "")) }

        val weeksRaw = root["weeks"].asList() ?: fail("weeks", "нужен список недель")
        if (weeksRaw.isEmpty()) fail("weeks", "в плане нет ни одной недели")
        if (weeksRaw.size > 104) fail("weeks", "слишком много недель (максимум 104)")
        val weeks = ArrayList<WeekDef>()
        weeksRaw.forEachIndexed { wi, wr ->
            val path = "weeks[${wi + 1}]"
            val o = wr.asObj() ?: fail(path, "ожидался объект")
            val phase = o.int("phase", 1)
            if (phase !in 1..phases.size) fail("$path.phase", "фаза $phase не описана (в phases их ${phases.size})")
            val copy = o["copyOf"]
            val days: Map<Int, List<WorkoutDef>>
            if (copy != null) {
                val k = (copy as? Number)?.toInt() ?: fail("$path.copyOf", "нужен номер недели")
                if (k < 1 || k > wi) fail("$path.copyOf", "можно копировать только одну из предыдущих недель (1–$wi)")
                days = weeks[k - 1].days
                weeks += WeekDef(if (o.containsKey("phase")) phase else weeks[k - 1].phase, if (o.containsKey("deload")) o.bool("deload") else weeks[k - 1].deload, days)
            } else {
                val dm = o["days"].asObj() ?: fail("$path.days", "нужен объект с днями недели, например {\"2\": [...], \"4\": [...]}")
                val parsed = LinkedHashMap<Int, List<WorkoutDef>>()
                for ((dk, dv) in dm) {
                    val dow = dayOf(dk) ?: fail("$path.days", "неизвестный день \"$dk\" (нужно число 1–7, где 1 – понедельник)")
                    val list = dv.asList() ?: fail("$path.days.$dk", "ожидался список тренировок")
                    val ws = list.mapIndexed { ti, tr -> workout(tr, "$path.days.$dk[${ti + 1}]") }
                    if (ws.map { it.id }.toSet().size != ws.size) fail("$path.days.$dk", "у тренировок одного дня должны быть разные id")
                    if (ws.isNotEmpty()) parsed[dow] = ws
                }
                weeks += WeekDef(phase, o.bool("deload"), parsed.toSortedMap())
            }
        }
        if (weeks.all { it.days.isEmpty() }) fail("weeks", "ни в одной неделе нет тренировок")
        return PlanDef(id, name, root.str("description"), phases, root["notes"].asList().orEmpty().mapNotNull { it as? String }, weeks)
    }

    private val DAY_NAMES = mapOf("mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6, "sun" to 7)

    private fun dayOf(k: String): Int? = k.trim().toIntOrNull()?.takeIf { it in 1..7 } ?: DAY_NAMES[k.trim().lowercase()]

    private fun typeOf(s: String, path: String): SessionType = when (s.trim().lowercase()) {
        "strength", "gym", "силовая" -> SessionType.STRENGTH
        "cardio", "treadmill", "кардио", "дорожка" -> SessionType.TREADMILL
        else -> fail(path, "тип \"$s\" неизвестен, нужен strength или cardio")
    }

    private fun workout(raw: Any?, path: String): WorkoutDef {
        val o = raw.asObj() ?: fail(path, "ожидался объект")
        val id = o.str("id").trim()
        if (!WORKOUT_ID_RE.matches(id)) fail("$path.id", "нужен короткий код из латинских букв, цифр и _ (например strength), а сейчас \"$id\"")
        val title = o.str("title").trim()
        if (title.isEmpty()) fail("$path.title", "нет названия тренировки")
        val prep = o["prep"].asObj()
        val blocksRaw = o["blocks"].asList() ?: fail("$path.blocks", "нужен список блоков")
        if (blocksRaw.isEmpty()) fail("$path.blocks", "в тренировке нет блоков")
        val notes = o["notes"].asList().orEmpty().mapIndexed { i, n ->
            val no = n.asObj() ?: fail("$path.notes[${i + 1}]", "ожидался объект {\"title\": ..., \"text\": ...}")
            OutlineBlock(no.str("title"), listOf(no.str("text")))
        }
        return WorkoutDef(
            id, title, o.str("subtitle"), o.str("place"), typeOf(o.str("type", "strength"), "$path.type"),
            prep?.int("sec", 10) ?: 10, prep?.str("hint") ?: "", notes,
            blocksRaw.mapIndexed { bi, b -> block(b, "$path.blocks[${bi + 1}]") },
        )
    }

    private fun block(raw: Any?, path: String): BlockDef {
        val o = raw.asObj() ?: fail(path, "ожидался объект")
        val repeat = o.int("repeat", 1)
        if (repeat !in 1..50) fail("$path.repeat", "число кругов должно быть от 1 до 50")
        val stepsRaw = o["steps"].asList() ?: fail("$path.steps", "нужен список шагов")
        if (stepsRaw.isEmpty()) fail("$path.steps", "в блоке нет шагов")
        return BlockDef(
            o.str("name"), repeat, o.bool("adjustable"), o.str("groupFormat", "{name} · круг {r}/{n}"),
            stepsRaw.mapIndexed { i, s -> step(s, "$path.steps[${i + 1}]", Kind.WORK) },
            o["restBetween"]?.let { step(it, "$path.restBetween", Kind.REST) },
            o["restAfter"]?.let { step(it, "$path.restAfter", Kind.REST) },
        )
    }

    private fun step(raw: Any?, path: String, defKind: Kind): StepDef {
        val o = raw.asObj() ?: fail(path, "ожидался объект")
        val title = o.str("title").trim()
        if (title.isEmpty()) fail("$path.title", "нет названия шага")
        val kind = when (o.str("kind", if (defKind == Kind.WORK) "work" else "rest").lowercase()) {
            "work", "работа" -> Kind.WORK
            "rest", "отдых" -> Kind.REST
            else -> fail("$path.kind", "нужно work или rest")
        }
        val sec = o.int("sec")
        val reps = o.int("reps")
        val tempo = o.dbl("tempo")
        if (sec < 0 || sec > 3 * 3600) fail("$path.sec", "секунд должно быть от 1 до 10800")
        if (sec == 0 && !(reps > 0 && tempo > 0)) fail(path, "\"$title\": укажи sec (секунды) либо reps вместе с tempo (секунд на повторение)")
        val key = o.str("key").replace("/", "-")
        val sides = o["sides"].asList().orEmpty().mapNotNull { it as? String }
        if (sides.size > 4) fail("$path.sides", "сторон не больше четырёх")
        return StepDef(title, o.str("sub"), kind, sec, reps, o.str("repsText"), tempo, key, sides, o.str("video"), o.str("videoTitle"), o.str("label"))
    }

    /* ---------- запись ---------- */

    fun toJson(plan: PlanDef): String = Json.stringify(toMap(plan), true)

    fun toMap(p: PlanDef): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["format"] = FORMAT
        m["version"] = VERSION
        m["id"] = p.id
        m["name"] = p.name
        m["description"] = p.description
        if (p.notes.isNotEmpty()) m["notes"] = p.notes
        m["phases"] = p.phases.map { linkedMapOf("name" to it.name, "weeks" to it.weeks, "hint" to it.hint) }
        m["weeks"] = p.weeks.mapIndexed { i, w ->
            val wm = LinkedHashMap<String, Any?>()
            wm["phase"] = w.phase
            if (w.deload) wm["deload"] = true
            val same = (0 until i).firstOrNull { p.weeks[it].days == w.days && w.days.isNotEmpty() }
            if (same != null) wm["copyOf"] = same + 1
            else wm["days"] = w.days.mapKeys { it.key.toString() }.mapValues { (_, l) -> l.map { workoutMap(it) } }
            wm
        }
        return m
    }

    private fun workoutMap(w: WorkoutDef): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["id"] = w.id
        m["title"] = w.title
        if (w.subtitle.isNotEmpty()) m["subtitle"] = w.subtitle
        if (w.place.isNotEmpty()) m["place"] = w.place
        m["type"] = if (w.type == SessionType.STRENGTH) "strength" else "cardio"
        if (w.prepSec != 10 || w.prepHint.isNotEmpty()) m["prep"] = linkedMapOf("sec" to w.prepSec, "hint" to w.prepHint)
        if (w.notes.isNotEmpty()) m["notes"] = w.notes.map { linkedMapOf("title" to it.head, "text" to it.lines.joinToString(" ")) }
        m["blocks"] = w.blocks.map { b ->
            val bm = LinkedHashMap<String, Any?>()
            if (b.name.isNotEmpty()) bm["name"] = b.name
            if (b.repeat != 1) bm["repeat"] = b.repeat
            if (b.adjustable) bm["adjustable"] = true
            if (b.groupFormat != "{name} · круг {r}/{n}") bm["groupFormat"] = b.groupFormat
            bm["steps"] = b.steps.map { stepMap(it, Kind.WORK) }
            b.restBetween?.let { bm["restBetween"] = stepMap(it, Kind.REST) }
            b.restAfter?.let { bm["restAfter"] = stepMap(it, Kind.REST) }
            bm
        }
        return m
    }

    private fun stepMap(s: StepDef, defKind: Kind): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["title"] = s.title
        if (s.kind != defKind) m["kind"] = if (s.kind == Kind.WORK) "work" else "rest"
        if (s.sub.isNotEmpty()) m["sub"] = s.sub
        if (s.sec > 0) m["sec"] = s.sec
        if (s.reps > 0) m["reps"] = s.reps
        if (s.repsText.isNotEmpty()) m["repsText"] = s.repsText
        if (s.tempo > 0) m["tempo"] = s.tempo
        if (s.key.isNotEmpty()) m["key"] = s.key
        if (s.sides.isNotEmpty()) m["sides"] = s.sides
        if (s.video.isNotEmpty()) m["video"] = s.video
        if (s.videoTitle.isNotEmpty()) m["videoTitle"] = s.videoTitle
        if (s.label.isNotEmpty()) m["label"] = s.label
        return m
    }

    /* ---------- шаблон для нейросети ---------- */

    /** Текст, который можно целиком отдать нейросети вместе с описанием желаемого плана. */
    fun template(): String = """
ИНСТРУКЦИЯ ДЛЯ НЕЙРОСЕТИ

Ты помогаешь составить тренировочный план для приложения «Тренировки». Ответь одним JSON-файлом: только JSON, без пояснений и без обрамления в ```. Файл должен строго соответствовать формату ниже, иначе приложение его не импортирует. Сначала уточни у меня цель, сколько недель, в какие дни и где я тренируюсь, какое оборудование есть и какой у меня опыт. Когда всё выяснишь, выдай файл.

ФОРМАТ

Корень: format = "workout-plan", version = 1, id (латиница, цифры, - и _, например my-plan), name, description, notes (необязательный список строк для экрана плана), phases, weeks.

phases: список фаз {name, weeks (текст, например "1–4"), hint (одно предложение)}. Если фаз нет, достаточно одной.

weeks: список недель по порядку. Неделя: phase (номер фазы, с 1), deload (true для разгрузочной недели), days. Ключи days это дни недели числами: "1" понедельник ... "7" воскресенье. Значение дня это список тренировок. Если неделя точно повторяет одну из предыдущих, вместо days напиши "copyOf": номер той недели (это сильно сокращает файл).

Тренировка: id (латиница, цифры, _, уникален внутри дня, например strength), title, subtitle (необязательно; можно использовать {n}, {work}, {rest}: число кругов, время работы и время отдыха первого блока с adjustable), place (например "Зал", "Дорожка", "Дом"), type ("strength" – силовая, "cardio" – бег, ходьба, кардио), prep (необязательно: {"sec": 10, "hint": "..."}, пауза «приготовься» в начале), notes (необязательно: список {"title": ..., "text": ...}), blocks.

Блок: name, repeat (сколько раз повторить, по умолчанию 1), adjustable (true, если пользователь может менять число кругов), steps, restBetween (отдых между повторами блока), restAfter (отдых после блока, кроме последнего блока тренировки).

Шаг: title, kind ("work" – работа, "rest" – отдых; в steps по умолчанию work, в restBetween и restAfter по умолчанию rest), sub (подпись под названием, необязательно), sec (секунды) ИЛИ reps (повторения) вместе с tempo (секунд на одно повторение; время шага = reps × tempo + 6 секунд), repsText (необязательно, например "8–10", показывается вместо числа повторений), key (короткий код упражнения: по нему правки применяются ко всем таким же; для упражнений ставь одинаковый key в разных неделях), sides (необязательно, например ["левая нога", "правая нога"]: шаг будет выполнен по очереди для каждой стороны), video (поисковый запрос для кнопки «Узнать больше», например "приседания с гантелью техника"), videoTitle (название для этой кнопки), label (название шага в редакторе, необязательно).

ПРАВИЛА
1. Суперсет: блок с несколькими steps, repeat = число кругов, restBetween = отдых между кругами.
2. Интервалы: блок repeat = N, adjustable = true, steps = [работа, восстановление].
3. Одно и то же упражнение везде называй и помечай key одинаково.
4. Используй только поля, описанные выше. Все числа без кавычек.

ПРИМЕР (рабочий файл, можно взять за образец)

${toJson(DefaultPlan.sample())}
""".trimIndent()
}
