package app.sports.workout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.sports.MainActivity
import app.sports.R
import app.sports.Store
import app.sports.core.Outcome
import app.sports.core.Status
import app.sports.core.Session
import app.sports.core.Step
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Всё, что нужно экрану плеера. */
data class PlayerState(
    val sessionId: String,
    val title: String,
    val stepCount: Int,
    val step: Step,
    val next: Step?,
    val remainingSec: Int,
    val totalLeftSec: Int,
    val progress: Float,
    val paused: Boolean,
    val finished: Boolean,
    /** Сколько секунд тренировки уже прошло и сколько она длится по плану (для досрочного завершения). */
    val elapsedSec: Int = 0,
    val totalSec: Int = 0,
)

/**
 * Тренировка идёт в службе переднего плана, а не в экране: таймер и сигналы работают при выключенном экране
 * и пока в другом приложении играет музыка или видео. Экран только показывает состояние [state] и отправляет команды.
 */
class WorkoutService : Service() {
    companion object {
        const val ACTION_START = "app.sports.START"
        const val ACTION_TOGGLE = "app.sports.TOGGLE"
        const val ACTION_RESTART = "app.sports.RESTART"
        const val ACTION_NEXT = "app.sports.NEXT"
        const val ACTION_PREV = "app.sports.PREV"
        const val ACTION_STOP = "app.sports.STOP"
        private const val EXTRA_ID = "id"
        private const val CHANNEL = "workout"
        private const val CHANNEL_DONE = "workout_done"
        private const val NOTIF_ID = 17
        private const val NOTIF_DONE_ID = 18

        private val mutableState = MutableStateFlow<PlayerState?>(null)
        val state: StateFlow<PlayerState?> = mutableState

        fun start(context: Context, sessionId: String) {
            val i = Intent(context, WorkoutService::class.java).setAction(ACTION_START).putExtra(EXTRA_ID, sessionId)
            ContextCompat.startForegroundService(context, i)
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, WorkoutService::class.java).setAction(action))
        }

        /** Закрыть экран «Тренировка закончена». */
        fun clear() {
            mutableState.value = null
        }

        @Volatile
        private var instance: WorkoutService? = null

        /** Виден ли экран приложения. Пока он виден, плашка не нужна; когда уходишь в другое приложение – появляется. */
        @Volatile
        private var appVisible = true

        fun appVisibility(visible: Boolean) {
            appVisible = visible
            instance?.let {
                if (visible) it.overlayDismissed = false
                it.refreshOverlay()
            }
        }

        /** После включения/выключения плашки в настройках. */
        fun overlaySettingChanged() {
            instance?.refreshOverlay()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var engine: WorkoutEngine? = null
    private var session: Session? = null
    private var planId: String? = null
    private var signals: Signals? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastIdx = -1
    private var lastNotifSec = -1
    private var lastNotifPaused = false
    private var overlay: Overlay? = null
    private var overlayDismissed = false
    private var lastWork = true
    private var lastRemaining = 0
    private var lastPausedFlag = false

    private val tick = object : Runnable {
        override fun run() {
            val e = engine ?: return
            val pos = e.position()
            if (e.finished) {
                finishWorkout()
                return
            }
            val idx = e.stepIndex(pos)
            if (idx != lastIdx) {
                lastIdx = idx
                if (e.running && idx > 0) signals?.play(e.steps[idx].kind)
            }
            publish(e, pos)
            if (e.running) {
                val untilEnd = e.msToStepEnd(pos)
                handler.postDelayed(this, minOf(250L, maxOf(5L, untilEnd + 3L)))
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Тренировка", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Тренировка закончена", NotificationManager.IMPORTANCE_DEFAULT))
        signals = Signals(this)
        instance = this
        overlay = Overlay(
            this,
            onOpen = { try { openAppIntent().send() } catch (_: Exception) { } },
            onToggle = { startService(Intent(this, WorkoutService::class.java).setAction(ACTION_TOGGLE)) },
            onClose = { overlayDismissed = true; overlay?.hide() },
        )
    }

    /** Плашка показывается, когда идёт тренировка, экран приложения закрыт, она включена в настройках, есть разрешение и она не закрыта крестиком. */
    private fun refreshOverlay() {
        val o = overlay ?: return
        val want = engine != null && !appVisible && !overlayDismissed &&
            Store.get(this).current.db.settings.overlay && Settings.canDrawOverlays(this)
        if (want) {
            o.show()
            o.update(lastWork, lastRemaining, lastPausedFlag)
        } else {
            o.hide()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val e = engine
        when (intent?.action) {
            ACTION_START -> {
                // Сразу показываем уведомление, иначе система остановит службу.
                startForegroundCompat(buildNotification("Тренировка", "Запуск", false))
                begin(intent.getStringExtra(EXTRA_ID))
            }
            ACTION_TOGGLE -> if (e != null) toggle(e) else stopSelf()
            ACTION_RESTART -> if (e != null) seek(e, e.steps[e.stepIndex()].startSec * 1000L) else stopSelf()
            ACTION_NEXT -> if (e != null) {
                val target = e.nextTarget()
                if (target != null) seek(e, target) else finishWorkout()
            } else stopSelf()
            ACTION_PREV -> if (e != null) seek(e, e.prevTarget()) else stopSelf()
            ACTION_STOP -> stopAll()
            else -> if (e == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin(id: String?) {
        cancelTicks()
        releaseWakeLock()
        val data = Store.get(this).current.data
        val s = id?.let { data?.plan?.byId(it, data.tuning) }
        planId = data?.planId
        if (s == null) {
            stopAll()
            return
        }
        session = s
        val e = WorkoutEngine(s.steps) { SystemClock.elapsedRealtime() }
        engine = e
        lastIdx = -1
        lastNotifSec = -1
        acquireWakeLock()
        e.start()
        tick.run()
    }

    private fun toggle(e: WorkoutEngine) {
        if (e.running) {
            e.pause()
            cancelTicks()
            releaseWakeLock()
        } else {
            e.resume()
            acquireWakeLock()
        }
        tick.run()
    }

    private fun seek(e: WorkoutEngine, ms: Long) {
        cancelTicks()
        e.seek(ms)
        lastIdx = -1 // тот же шаг, перемотанный в начало, снова даст сигнал
        tick.run()
    }

    private fun publish(e: WorkoutEngine, pos: Long) {
        val s = session ?: return
        val idx = e.stepIndex(pos)
        val step = e.steps[idx]
        val remainingSec = maxOf(0, Math.ceil((step.endSec * 1000L - pos) / 1000.0).toInt())
        val totalLeft = maxOf(0, Math.ceil((e.totalMs - pos) / 1000.0).toInt())
        val progress = ((pos - step.startSec * 1000L).toFloat() / (step.durSec * 1000L)).coerceIn(0f, 1f)
        mutableState.value = PlayerState(
            sessionId = s.id,
            title = s.title,
            stepCount = e.steps.size,
            step = step,
            next = e.steps.getOrNull(idx + 1),
            remainingSec = remainingSec,
            totalLeftSec = totalLeft,
            progress = Math.round(progress * 500f) / 500f,
            paused = !e.running,
            finished = false,
            elapsedSec = (pos / 1000L).toInt(),
            totalSec = s.totalSec,
        )
        lastWork = step.kind == app.sports.core.Kind.WORK
        lastRemaining = remainingSec
        lastPausedFlag = !e.running
        if (overlay?.isShown == true) overlay?.update(lastWork, remainingSec, !e.running)
        else if (!appVisible && !overlayDismissed) refreshOverlay()
        if (remainingSec != lastNotifSec || lastNotifPaused != !e.running) {
            lastNotifSec = remainingSec
            lastNotifPaused = !e.running
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification(step.title, formatTime(remainingSec) + " · осталось " + formatTime(totalLeft), !e.running))
        }
    }

    private fun finishWorkout() {
        val s = session
        val e = engine
        cancelTicks()
        releaseWakeLock()
        overlay?.hide()
        if (s != null && e != null) {
            val outcome = Outcome(Status.DONE, System.currentTimeMillis(), 100, activeSec = s.totalSec)
            planId?.let { Store.get(this).recordResult(it, s.id, outcome) }
            val last = e.steps.last()
            mutableState.value = PlayerState(
                sessionId = s.id,
                title = s.title,
                stepCount = e.steps.size,
                step = last,
                next = null,
                remainingSec = 0,
                totalLeftSec = 0,
                progress = 1f,
                paused = false,
                finished = true,
                elapsedSec = s.totalSec,
                totalSec = s.totalSec,
            )
            signals?.playFinish()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(
                NOTIF_DONE_ID,
                NotificationCompat.Builder(this, CHANNEL_DONE)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle("Тренировка закончена")
                    .setContentText(s.title + " отмечена выполненной")
                    .setContentIntent(openAppIntent())
                    .setAutoCancel(true)
                    .build()
            )
        }
        engine = null
        session = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopAll() {
        cancelTicks()
        releaseWakeLock()
        overlay?.hide()
        engine = null
        session = null
        mutableState.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cancelTicks() = handler.removeCallbacks(tick)

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sports:workout").apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, WorkoutService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(title: String, text: String, paused: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(0, if (paused) "Продолжить" else "Пауза", action(ACTION_TOGGLE, 1))
            .addAction(0, "Дальше", action(ACTION_NEXT, 2))
            .addAction(0, "Стоп", action(ACTION_STOP, 3))
            .build()

    private fun formatTime(sec: Int): String = (sec / 60).toString() + ":" + (sec % 60).toString().padStart(2, '0')

    override fun onDestroy() {
        cancelTicks()
        releaseWakeLock()
        overlay?.hide()
        if (instance === this) instance = null
        super.onDestroy()
    }
}
