package app.sports.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.sports.Store
import app.sports.core.Body
import app.sports.core.Goal
import app.sports.core.Outcome
import app.sports.core.WeightEntry
import app.sports.core.PlanFormat
import app.sports.core.PlanState
import app.sports.core.PlanStatus
import app.sports.core.Plans
import app.sports.core.Profile
import app.sports.core.Schedule
import app.sports.core.Settings
import app.sports.core.Status
import app.sports.core.Tuning
import app.sports.core.Vacation
import app.sports.workout.WorkoutService
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Действия пользователя. Всё сохраняется в папке с данными через [Store]. */
class Controller(private val context: Context, val store: Store) {
    /** Системные окна выбора (папка, файл плана): подключаются из MainActivity. */
    var pickFolder: () -> Unit = {}
    var pickPlanFile: () -> Unit = {}

    private val msg = MutableStateFlow<String?>(null)
    /** Сообщение для пользователя (результат импорта и т. п.). */
    val message: StateFlow<String?> = msg
    fun dismissMessage() { msg.value = null }

    private fun planSec(id: String): Int {
        val d = store.current.data ?: return 0
        return d.plan.byId(id, d.tuning)?.totalSec ?: 0
    }

    private fun upd(change: (PlanState) -> PlanState) = store.updateActive(change)

    /** «Отметить» / снять отметку: полностью выполненная тренировка. */
    fun toggleDone(id: String) {
        val total = planSec(id)
        upd { d ->
            if (d.results[id]?.status == Status.DONE) d.copy(results = d.results - id)
            else d.copy(results = d.results + (id to Outcome(Status.DONE, System.currentTimeMillis(), 100, activeSec = total)))
        }
    }

    /** Отметить пропущенной (или вернуть обратно). */
    fun toggleSkipped(id: String) = upd { d ->
        if (d.results[id]?.status == Status.SKIPPED) d.copy(results = d.results - id)
        else d.copy(results = d.results + (id to Outcome(Status.SKIPPED, System.currentTimeMillis(), 0)))
    }

    /** Сбросить любую отметку (в том числе частичную). */
    fun clearResult(id: String) = upd { it.copy(results = it.results - id) }

    /** Досрочное завершение из плеера: сохраняем процент и причину, потом останавливаем службу. */
    fun finishEarly(id: String, percent: Int, reason: String, note: String, elapsedSec: Int) {
        val p = percent.coerceIn(0, 100)
        upd { d ->
            val o = if (p >= 100) Outcome(Status.DONE, System.currentTimeMillis(), 100, activeSec = elapsedSec)
            else Outcome(Status.PARTIAL, System.currentTimeMillis(), p, reason, note, elapsedSec)
            d.copy(results = d.results + (id to o))
        }
        WorkoutService.send(context, WorkoutService.ACTION_STOP)
    }

    fun move(schedule: Schedule, id: String, date: LocalDate) = upd { it.copy(moves = schedule.withMove(id, date)) }

    fun addVacation(v: Vacation) = upd { it.copy(vacations = it.vacations + v) }

    fun removeVacation(v: Vacation) = upd { it.copy(vacations = it.vacations - v) }

    fun setStart(d: LocalDate) = upd { it.copy(planStart = d) }

    fun setDuck(on: Boolean) = store.updateDb { it.copy(settings = it.settings.copy(duck = on)) }

    fun setOverlay(on: Boolean) {
        store.updateDb { it.copy(settings = it.settings.copy(overlay = on)) }
        WorkoutService.overlaySettingChanged()
    }

    fun canOverlay(): Boolean = android.provider.Settings.canDrawOverlays(context)

    /** Открывает системный экран «Поверх других приложений» для этого приложения. */
    fun requestOverlayPermission() {
        val i = Intent(
            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(i)
        } catch (_: Exception) {
        }
    }

    /* ---------- профиль ---------- */

    /** Сохраняет профиль. Если вес изменился, он попадает в историю на сегодня. Цели в профиле не трогаем. */
    fun saveProfile(p: Profile) = store.updateDb { db ->
        val merged = p.copy(goal = db.profile.goal)
        if (p.weightKg >= 30.0 && p.weightKg != db.profile.weightKg) {
            val w = Body.withEntry(db.weights, WeightEntry(LocalDate.now(), p.weightKg))
            db.copy(profile = merged, weights = w)
        } else db.copy(profile = merged)
    }

    /* ---------- вес, цели, напоминание ---------- */

    fun addWeight(kg: Double, date: LocalDate) = store.updateDb { db ->
        val w = Body.withEntry(db.weights, WeightEntry(date, kg.coerceIn(30.0, 300.0)))
        db.copy(weights = w, profile = db.profile.copy(weightKg = Body.latest(w)?.kg ?: db.profile.weightKg))
    }

    fun deleteWeight(e: WeightEntry) = store.updateDb { db ->
        val w = db.weights - e
        db.copy(weights = w, profile = db.profile.copy(weightKg = Body.latest(w)?.kg ?: 0.0))
    }

    fun saveGoal(g: Goal) = store.updateDb { it.copy(profile = it.profile.copy(goal = g)) }

    fun setWeightReminder(on: Boolean) = store.updateDb { it.copy(settings = it.settings.copy(weightReminder = on)) }

    /* ---------- планы ---------- */

    /** Начать план с даты [start] (или продолжить с того места, где остановились). Прежний активный уходит на паузу. */
    fun activate(id: String, start: LocalDate?) = store.updateDb { Plans.activate(it, id, start) }

    fun finishPlan(id: String) = store.updateDb { Plans.finish(it, id, LocalDate.now()) }

    /** Начать завершённый план заново: прогресс обнуляется. */
    fun restart(id: String, start: LocalDate) = store.updateDb { db ->
        Plans.activate(db.copy(states = db.states + (id to PlanState())), id, start)
    }

    fun deletePlan(id: String) = store.deletePlan(id)

    fun importText(text: String) { msg.value = store.importPlan(text) }

    fun importFailed(reason: String) { msg.value = reason }

    /** Шаблон для нейросети: копируем в буфер обмена и кладём файл в папку с данными. */
    fun copyTemplate() {
        val t = PlanFormat.template()
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Шаблон плана", t))
            val saved = store.writeFile("plan-template.txt", t)
            msg.value = "Шаблон скопирован. Вставь его в чат с нейросетью и опиши, какой план нужен." + if (saved) " Копия лежит в папке данных (plan-template.txt)." else ""
        } catch (_: Exception) {
            msg.value = "Не получилось скопировать шаблон."
        }
    }

    fun shareTemplate() {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, PlanFormat.template())
        try { context.startActivity(Intent.createChooser(i, "Шаблон плана").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
    }

    /* ---------- правка тренировок ---------- */

    private fun scoped(sid: String, key: String, global: Boolean) = if (global) key else "$sid/$key"

    /** Время шага в секундах. Для упражнений на повторения ручное время отключает автоматический расчёт. */
    fun setSec(sid: String, key: String, sec: Int, global: Boolean) = upd { d ->
        val t = d.tuning
        val k = scoped(sid, key, global)
        val cleaned = if (global) t.sec - "$sid/$key" else t.sec
        d.copy(tuning = t.copy(sec = cleaned + (k to sec.coerceIn(5, 3600))))
    }

    /** Число повторений; время упражнения снова считается по плану из повторений. */
    fun setReps(sid: String, key: String, reps: Int, global: Boolean) = upd { d ->
        val t = d.tuning
        val k = scoped(sid, key, global)
        val reps0 = if (global) t.reps - "$sid/$key" else t.reps
        d.copy(tuning = t.copy(reps = reps0 + (k to reps.coerceIn(1, 100)), sec = t.sec - "$sid/$key" - (if (global) key else "")))
    }

    /** Добавка к числу кругов / интервалов (от −3 до +3). */
    fun setExtra(sid: String, type: app.sports.core.SessionType, extra: Int, global: Boolean) = upd { d ->
        val k = if (global) (if (type == app.sports.core.SessionType.STRENGTH) "*strength" else "*treadmill") else sid
        val cleaned = if (global) d.tuning.extra - sid else d.tuning.extra
        d.copy(tuning = d.tuning.copy(extra = cleaned + (k to extra.coerceIn(-3, 3))))
    }

    /** Вернуть эту тренировку к плану (общие правки для всех тренировок не трогаем). */
    fun resetSession(sid: String) = upd { d ->
        val t = d.tuning
        val pre = "$sid/"
        d.copy(
            tuning = Tuning(
                t.sec.filterKeys { !it.startsWith(pre) },
                t.reps.filterKeys { !it.startsWith(pre) },
                t.extra - sid,
            ),
        )
    }

    fun resetAllTuning() = upd { it.copy(tuning = Tuning.NONE) }

    /* ---------- прочее ---------- */

    fun startWorkout(id: String) = WorkoutService.start(context, id)

    fun send(action: String) = WorkoutService.send(context, action)

    fun closeFinished() = WorkoutService.clear()

    /** Видео по упражнению: открывается в браузере или в приложении YouTube. Единственное место, где нужен интернет. */
    fun openVideo(query: String) {
        val uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // нет приложения для открытия ссылок – молча ничего не делаем
        }
    }
}
