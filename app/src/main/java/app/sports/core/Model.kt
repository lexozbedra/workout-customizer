package app.sports.core

/** Модели тренировки: шаги, сессии, настройки пользователя. Чистый Kotlin, без Android, чтобы логику можно было тестировать на JVM. */

enum class Kind { WORK, REST }
enum class SessionType { STRENGTH, TREADMILL }

data class Step(
    val index: Int,
    val kind: Kind,
    val title: String,
    val sub: String,
    val durSec: Int,
    val group: String,
    val startSec: Int,
    /** Стабильный код шага: по нему настраивается время упражнения для всех тренировок сразу. */
    val key: String = "",
    /** Число повторений, если шаг считается по повторениям (иначе 0). */
    val reps: Int = 0,
) {
    val endSec: Int get() = startSec + durSec
}

data class OutlineBlock(val head: String, val lines: List<String>)

/** Строка редактора: что можно подправить в тренировке. */
data class Editable(val key: String, val label: String, val durSec: Int, val reps: Int)

/** Упражнение, про которое можно посмотреть видео. */
data class ExerciseRef(val title: String, val query: String)

/**
 * Настройки пользователя поверх базового плана. Ключ вида `w3-2-strength/squat` действует на одну тренировку,
 * просто `squat` – на все. Сам план в коде не меняется.
 */
data class Tuning(
    val sec: Map<String, Int> = emptyMap(),
    val reps: Map<String, Int> = emptyMap(),
    /** Добавка к числу кругов (силовая) или интервалов (дорожка): ключ – id тренировки, `*strength` или `*treadmill`. */
    val extra: Map<String, Int> = emptyMap(),
) {
    fun secFor(sid: String, key: String): Int? = sec["$sid/$key"] ?: sec[key]
    fun repsFor(sid: String, key: String): Int? = reps["$sid/$key"] ?: reps[key]
    fun extraFor(sid: String, type: SessionType): Int =
        extra[sid] ?: extra[if (type == SessionType.STRENGTH) "*strength" else "*treadmill"] ?: 0

    companion object {
        val NONE = Tuning()
    }
}

data class Session(
    val id: String,
    val week: Int,
    val dow: Int, // ISO: 2 = вторник, 4 = четверг, 6 = суббота (день по базовому плану)
    val type: SessionType,
    val title: String,
    val subtitle: String,
    val place: String,
    val steps: List<Step>,
    val outline: List<OutlineBlock>,
    val editable: List<Editable> = emptyList(),
    val exercises: List<ExerciseRef> = emptyList(),
    /** Можно ли менять число кругов или интервалов. */
    val hasRounds: Boolean = false,
) {
    val totalSec: Int get() = steps.last().endSec
    val minutes: Int get() = Math.round(totalSec / 60.0).toInt()
}

data class Phase(val n: Int, val name: String, val weeks: String, val hint: String)
data class WeekInfo(val week: Int, val block: Int, val deload: Boolean, val phase: Phase)
