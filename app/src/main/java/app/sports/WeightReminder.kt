package app.sports

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Напоминание обновить вес раз в месяц. Раз в сутки (около 10:00) будильник будит приёмник, он смотрит
 * на копию данных в настройках приложения и, если с последнего взвешивания прошло 30 дней, показывает уведомление
 * (не чаще раза в неделю, пока вес не записан).
 */
object WeightReminder {
    const val PREFS = "sports-location"
    private const val CHANNEL = "weight_reminder"
    const val INTERVAL_DAYS = 30

    /** Планируем суточную проверку. Вызывается при каждом запуске приложения и после перезагрузки телефона. */
    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, 1, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        var first = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0)
        if (first.isBefore(LocalDateTime.now())) first = first.plusDays(1)
        val at = first.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setInexactRepeating(AlarmManager.RTC, at, AlarmManager.INTERVAL_DAY, pi)
    }

    fun check(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("mirrored", false) || !prefs.getBoolean("weightReminder", true)) return
        val today = LocalDate.now().toEpochDay()
        val last = prefs.getLong("lastWeightDay", Long.MIN_VALUE)
        val due = last == Long.MIN_VALUE || last + INTERVAL_DAYS <= today
        val reminded = prefs.getLong("remindedDay", Long.MIN_VALUE)
        if (!due || (reminded != Long.MIN_VALUE && reminded + 7 > today)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Напоминание про вес", NotificationManager.IMPORTANCE_DEFAULT))
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = open?.let { PendingIntent.getActivity(context, 2, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Пора обновить вес")
            .setContentText("Прошёл месяц с последней записи. Открой вкладку «Тело».")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        nm.notify(4711, n)
        prefs.edit().putLong("remindedDay", today).apply()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        WeightReminder.check(context)
    }
}

/** После перезагрузки телефона будильник пропадает, поэтому ставим его заново. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) WeightReminder.schedule(context)
    }
}
