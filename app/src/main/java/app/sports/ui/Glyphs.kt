package app.sports.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Glyph { PLAY, PAUSE, NEXT, PREV, RESTART }

/** Значки кнопок плеера рисуются кодом, чтобы не тянуть библиотеку иконок. */
@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp = 28.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        fun triangle(x0: Float, x1: Float): Path = Path().apply {
            moveTo(x0 * w, 0.14f * h)
            lineTo(x1 * w, 0.5f * h)
            lineTo(x0 * w, 0.86f * h)
            close()
        }
        when (glyph) {
            Glyph.PLAY -> drawPath(triangle(0.25f, 0.9f), color)
            Glyph.PAUSE -> {
                drawRect(color, Offset(0.2f * w, 0.14f * h), Size(0.2f * w, 0.72f * h))
                drawRect(color, Offset(0.6f * w, 0.14f * h), Size(0.2f * w, 0.72f * h))
            }
            Glyph.NEXT -> {
                drawPath(triangle(0.1f, 0.75f), color)
                drawRect(color, Offset(0.78f * w, 0.14f * h), Size(0.13f * w, 0.72f * h))
            }
            Glyph.PREV -> {
                drawPath(
                    Path().apply {
                        moveTo(0.9f * w, 0.14f * h)
                        lineTo(0.25f * w, 0.5f * h)
                        lineTo(0.9f * w, 0.86f * h)
                        close()
                    },
                    color,
                )
                drawRect(color, Offset(0.09f * w, 0.14f * h), Size(0.13f * w, 0.72f * h))
            }
            Glyph.RESTART -> {
                drawArc(
                    color = color,
                    startAngle = -50f,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = Offset(0.16f * w, 0.18f * h),
                    size = Size(0.68f * w, 0.68f * h),
                    style = Stroke(width = 0.11f * w),
                )
                drawPath(
                    Path().apply {
                        moveTo(0.6f * w, 0.02f * h)
                        lineTo(0.6f * w, 0.4f * h)
                        lineTo(0.94f * w, 0.2f * h)
                        close()
                    },
                    color,
                )
            }
        }
    }
}
