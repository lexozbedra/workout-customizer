package app.sports.workout

import app.sports.core.Step

/**
 * Часы тренировки: позиция в миллисекундах, пауза, перемотка. Время берётся из [now] (на телефоне это SystemClock.elapsedRealtime),
 * поэтому таймер не зависит от экрана и не уходит при блокировке.
 */
class WorkoutEngine(val steps: List<Step>, private val now: () -> Long) {
    val totalMs: Long = steps.last().endSec * 1000L

    private var basePos = 0L
    private var since = 0L
    var running = false
        private set

    fun position(): Long {
        val p = if (running) basePos + (now() - since) else basePos
        return minOf(totalMs, maxOf(0L, p))
    }

    val finished: Boolean get() = position() >= totalMs

    fun start() { since = now(); running = true }
    fun pause() { basePos = position(); running = false }
    fun resume() { if (!running) { since = now(); running = true } }
    fun seek(ms: Long) { basePos = ms.coerceIn(0L, totalMs); since = now() }

    fun stepIndex(pos: Long = position()): Int {
        var lo = 0
        var hi = steps.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (steps[mid].startSec * 1000L <= pos) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun msToStepEnd(pos: Long = position()): Long = steps[stepIndex(pos)].endSec * 1000L - pos

    /** Куда перейти по кнопке «назад»: в начало текущего шага, а если он только начался, то в начало предыдущего. */
    fun prevTarget(pos: Long = position()): Long {
        val i = stepIndex(pos)
        val start = steps[i].startSec * 1000L
        return if (pos - start > 2000L || i == 0) start else steps[i - 1].startSec * 1000L
    }

    /** Куда перейти по кнопке «дальше»; null, если это последний шаг. */
    fun nextTarget(pos: Long = position()): Long? {
        val i = stepIndex(pos)
        return steps.getOrNull(i + 1)?.let { it.startSec * 1000L }
    }
}
