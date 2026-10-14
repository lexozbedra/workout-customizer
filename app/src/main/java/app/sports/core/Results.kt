package app.sports.core

import java.time.LocalDate

enum class Status { DONE, PARTIAL, SKIPPED }

/**
 * Итог по тренировке. percent – доля выполненного (для DONE всегда 100), activeSec – сколько секунд реально
 * прошло (0 = неизвестно, тогда берём плановую длительность), reason/note – причина досрочного завершения или пропуска.
 */
data class Outcome(
    val status: Status,
    val at: Long,
    val percent: Int = 100,
    val reason: String = "",
    val note: String = "",
    val activeSec: Int = 0,
)

/** Состояние тренировки с точки зрения экрана. AUTO_SKIPPED – день прошёл, а отметки и переноса нет. */
enum class Eff { DONE, PARTIAL, SKIPPED, AUTO_SKIPPED, PENDING }

object Results {
    fun effective(p: Planned, results: Map<String, Outcome>, today: LocalDate): Eff {
        val o = results[p.id]
        return when {
            o != null -> when (o.status) {
                Status.DONE -> Eff.DONE
                Status.PARTIAL -> Eff.PARTIAL
                Status.SKIPPED -> Eff.SKIPPED
            }
            p.date.isBefore(today) -> Eff.AUTO_SKIPPED
            else -> Eff.PENDING
        }
    }

    fun isSkipped(e: Eff): Boolean = e == Eff.SKIPPED || e == Eff.AUTO_SKIPPED
}

/** Причины, из которых выбирает пользователь. «Другое» требует комментария (для отпуска). */
object Reasons {
    val VACATION = listOf("Поездка", "Болезнь", "Другое")
    val EARLY = listOf("Не успела", "Устала", "Другое")
    const val OTHER = "Другое"
}
