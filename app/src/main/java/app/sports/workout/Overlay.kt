package app.sports.workout

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Небольшая плашка внизу экрана поверх других приложений («Работа 0:45», пауза, закрыть).
 * Рисуется из службы обычными View, поэтому живёт, пока идёт тренировка, даже когда экран приложения закрыт.
 * Нужно разрешение «Поверх других приложений».
 */
class Overlay(
    private val context: Context,
    private val onOpen: () -> Unit,
    private val onToggle: () -> Unit,
    private val onClose: () -> Unit,
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var label: TextView? = null
    private var toggle: ImageView? = null
    private var lastText = ""
    private var lastWork: Boolean? = null
    private var lastPaused: Boolean? = null

    val isShown: Boolean get() = root != null

    private fun dp(v: Int): Int = Math.round(v * context.resources.displayMetrics.density)

    fun show() {
        if (root != null) return
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(6), dp(8), dp(6))
            elevation = dp(6).toFloat()
            setOnClickListener { onOpen() }
        }
        val text = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, dp(12), 0)
        }
        val pause = ImageView(context).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { onToggle() }
        }
        val close = TextView(context).apply {
            this.text = "×"
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { onClose() }
        }
        bar.addView(text)
        bar.addView(pause, LinearLayout.LayoutParams(dp(44), dp(44)))
        bar.addView(close)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(96)
        }
        try {
            wm.addView(bar, params)
            root = bar
            label = text
            toggle = pause
            lastText = ""
            lastWork = null
            lastPaused = null
        } catch (_: Exception) {
            // нет разрешения «поверх других приложений» – молча обходимся без плашки
            root = null
        }
    }

    fun update(work: Boolean, sec: Int, paused: Boolean) {
        val r = root ?: return
        val t = (if (work) "Работа " else "Отдых ") + (sec / 60) + ":" + (sec % 60).toString().padStart(2, '0')
        if (t != lastText) {
            label?.text = t
            lastText = t
        }
        if (work != lastWork) {
            r.background = GradientDrawable().apply {
                cornerRadius = dp(30).toFloat()
                setColor(if (work) Color.parseColor("#C9402A") else Color.parseColor("#1D6A80"))
            }
            lastWork = work
        }
        if (paused != lastPaused) {
            toggle?.setImageResource(if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
            toggle?.contentDescription = if (paused) "Продолжить" else "Пауза"
            lastPaused = paused
        }
    }

    fun hide() {
        val r = root ?: return
        try {
            wm.removeView(r)
        } catch (_: Exception) {
        }
        root = null
        label = null
        toggle = null
    }
}
