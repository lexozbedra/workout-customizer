package app.sports

import android.content.Context
import android.net.Uri
import app.sports.core.AppDb
import app.sports.core.DataFolder
import app.sports.core.FileFolder
import app.sports.core.Outcome
import app.sports.core.PlanDef
import app.sports.core.PlanFormat
import app.sports.core.PlanFormatException
import app.sports.core.PlanState
import app.sports.core.PlanStatus
import app.sports.core.Plans
import app.sports.core.Profile
import app.sports.core.Repo
import app.sports.core.Settings
import app.sports.core.Tuning
import app.sports.core.Vacation
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Срез данных активного плана, с которым работают экраны. */
data class AppData(
    val plan: PlanDef,
    val planId: String,
    val planStart: LocalDate,
    /** Итоги тренировок: выполнена, выполнена частично, пропущена. Нет записи – ещё не отмечена. */
    val results: Map<String, Outcome>,
    val vacations: List<Vacation>,
    val moves: Map<String, Int>,
    /** Свои правки времени, повторений и числа кругов. */
    val tuning: Tuning,
    val duck: Boolean,
    /** Плашка с таймером поверх других приложений. */
    val overlay: Boolean,
    /** Вес для грубой оценки калорий (из профиля, 65 если не указан). */
    val weightKg: Int,
)

/** Всё, что видит интерфейс. [data] есть, только когда есть активный план. */
data class Ui(
    val loaded: Boolean = false,
    /** Папка выбрана (внешняя или внутренняя). Пока нет, показывается экран первого запуска. */
    val hasFolder: Boolean = false,
    val internal: Boolean = false,
    val folderName: String = "",
    val db: AppDb = AppDb(),
    val plans: Map<String, PlanDef> = emptyMap(),
    val errors: List<String> = emptyList(),
) {
    val data: AppData?
        get() {
            val id = db.activeId ?: return null
            val plan = plans[id] ?: return null
            val s = db.state(id)
            val start = s.planStart ?: return null
            val w = db.profile.weightKg
            return AppData(plan, id, start, s.results, s.vacations, s.moves, s.tuning, db.settings.duck, db.settings.overlay, if (w >= 30) Math.round(w).toInt() else 65)
        }
}

/**
 * Данные лежат в папке пользователя: profile.json, progress.json и plans/*.json. В самом приложении хранится только
 * ссылка на выбранную папку. Один объект на процесс: его читают и интерфейс, и фоновая служба.
 */
class Store private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("sports-location", Context.MODE_PRIVATE)
    private val io = Executors.newSingleThreadExecutor()
    private val flow = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = flow

    private var folder: DataFolder? = null
    private var repo: Repo? = null

    init {
        io.execute { open() }
    }

    val current: Ui get() = flow.value

    /* ---------- открытие папки ---------- */

    private fun internalFolder(): DataFolder = FileFolder(File(context.filesDir, "data"))

    private fun open() {
        val uriStr = prefs.getString("folderUri", null)
        val f: DataFolder? = when {
            uriStr != null -> SafFolder(context, Uri.parse(uriStr)).takeIf { it.isReadable() }
            prefs.getBoolean("internal", false) -> internalFolder()
            else -> null
        }
        if (f == null) {
            val lost = uriStr != null
            flow.value = Ui(loaded = true, hasFolder = false, errors = if (lost) listOf("Папка с данными недоступна. Выбери её заново: данные в ней не пропали.") else emptyList())
            return
        }
        attach(f)
    }

    private fun attach(f: DataFolder) {
        folder = f
        val r = Repo(f)
        repo = r
        val l = r.load()
        val db = Plans.autoFinish(l.db, l.plans, LocalDate.now())
        flow.value = Ui(true, true, f !is SafFolder, if (f is SafFolder) f.displayName else "память приложения", db, l.plans, l.errors)
        mirror(db)
        if (db != l.db) persist(db)
    }

    /** Пользователь выбрал папку. Если в ней уже есть данные, берём их, иначе переносим туда текущие. */
    fun setFolder(uri: Uri) {
        io.execute {
            val f = SafFolder(context, uri)
            if (!f.isReadable()) {
                flow.value = flow.value.copy(errors = listOf("Не удалось получить доступ к выбранной папке."))
                return@execute
            }
            val old = flow.value
            val probe = Repo(f).load()
            val hasData = probe.plans.isNotEmpty() || probe.db.states.isNotEmpty() || f.read(Repo.PROFILE) != null
            prefs.edit().putString("folderUri", uri.toString()).putBoolean("internal", false).apply()
            if (hasData || !old.hasFolder) attach(f)
            else {
                try {
                    val r = Repo(f)
                    old.plans.values.forEach { r.savePlan(it) }
                    r.saveProfile(old.db); r.saveProgress(old.db)
                } catch (e: Exception) {
                    flow.value = old.copy(errors = listOf("Не удалось записать в папку: ${e.message}"))
                    return@execute
                }
                attach(f)
            }
        }
    }

    fun useInternal() {
        io.execute {
            prefs.edit().remove("folderUri").putBoolean("internal", true).apply()
            attach(internalFolder())
        }
    }

    fun reload() { io.execute { if (folder != null) attach(folder!!) } }

    fun clearErrors() { flow.value = flow.value.copy(errors = emptyList()) }

    /* ---------- изменения ---------- */

    /** Копия двух значений в настройках приложения, чтобы приёмник напоминания не лез в папку с данными. */
    private fun mirror(db: AppDb) {
        val last = db.weights.maxByOrNull { it.date }
        prefs.edit()
            .putBoolean("mirrored", true)
            .putBoolean("weightReminder", db.settings.weightReminder)
            .putLong("lastWeightDay", last?.date?.toEpochDay() ?: Long.MIN_VALUE)
            .apply()
    }

    private fun persist(db: AppDb, plan: PlanDef? = null, deleted: String? = null) {
        mirror(db)
        val r = repo ?: return
        io.execute {
            try {
                plan?.let { r.savePlan(it) }
                deleted?.let { r.deletePlan(it) }
                r.saveProfile(db); r.saveProgress(db)
            } catch (e: Exception) {
                flow.value = flow.value.copy(errors = flow.value.errors + "Не удалось сохранить: ${e.message}")
            }
        }
    }

    @Synchronized
    fun updateDb(change: (AppDb) -> AppDb) {
        val cur = flow.value
        val next = change(cur.db)
        if (next == cur.db) return
        flow.value = cur.copy(db = next)
        persist(next)
    }

    /** Изменение состояния активного плана. */
    fun updateActive(change: (PlanState) -> PlanState) = updateDb { db ->
        val id = db.activeId ?: return@updateDb db
        db.copy(states = db.states + (id to change(db.state(id))))
    }

    /** Отметка из службы тренировки: пишется в тот план, который был активен в её начале. */
    fun recordResult(planId: String, sessionId: String, outcome: Outcome) = updateDb { db ->
        val s = db.states[planId] ?: return@updateDb db
        db.copy(states = db.states + (planId to s.copy(results = s.results + (sessionId to outcome))))
    }

    fun activePlan(): PlanDef? = flow.value.data?.plan

    /** Импорт плана из текста файла. Возвращает сообщение для пользователя. */
    @Synchronized
    fun importPlan(text: String): String {
        val plan = try { PlanFormat.parse(text) } catch (e: PlanFormatException) { return "Не получилось импортировать: ${e.message}" }
        val cur = flow.value
        val existed = cur.plans.containsKey(plan.id)
        flow.value = cur.copy(plans = cur.plans + (plan.id to plan))
        persist(flow.value.db, plan = plan)
        return if (existed) "План «${plan.name}» обновлён, прогресс сохранён." else "План «${plan.name}» добавлен."
    }

    @Synchronized
    fun deletePlan(id: String) {
        val cur = flow.value
        val db = Plans.delete(cur.db, id)
        flow.value = cur.copy(db = db, plans = cur.plans - id)
        persist(db, deleted = id)
    }

    fun tick(today: LocalDate) {
        val cur = flow.value
        if (!cur.hasFolder) return
        val next = Plans.autoFinish(cur.db, cur.plans, today)
        if (next != cur.db) updateDb { Plans.autoFinish(it, cur.plans, today) }
    }

    /** Записать произвольный файл в папку данных (например, шаблон плана). */
    fun writeFile(path: String, text: String): Boolean {
        val f = folder ?: return false
        io.execute { try { f.write(path, text) } catch (_: Exception) { } }
        return true
    }

    companion object {
        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context.applicationContext).also { instance = it }
            }
    }
}
