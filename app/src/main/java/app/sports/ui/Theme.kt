package app.sports.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.time.LocalDate

/** Цвета сигналов: красный – работа, сине-зелёный – отдых, зелёный – готово. */
object Pal {
    val Work = Color(0xFFC9402A)
    val Rest = Color(0xFF1D6A80)
    val Done = Color(0xFF2A7A4F)
}

@Composable
fun SportsTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFF86A2FF),
            onPrimary = Color(0xFF0B1220),
            background = Color(0xFF0E1318),
            onBackground = Color(0xFFE8EDF2),
            surface = Color(0xFF161D24),
            onSurface = Color(0xFFE8EDF2),
            surfaceVariant = Color(0xFF27323C),
            onSurfaceVariant = Color(0xFF92A0AE),
            outline = Color(0xFF3A4753),
            outlineVariant = Color(0xFF27323C),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF2447D6),
            onPrimary = Color.White,
            background = Color(0xFFEDF0F3),
            onBackground = Color(0xFF131A22),
            surface = Color(0xFFF9FAFB),
            onSurface = Color(0xFF131A22),
            surfaceVariant = Color(0xFFD3DAE1),
            onSurfaceVariant = Color(0xFF566270),
            outline = Color(0xFFB9C2CC),
            outlineVariant = Color(0xFFD3DAE1),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private val MONTHS = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)
private val MONTHS_SHORT = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
private val DAYS_FULL = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
val DAYS_SHORT = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

fun longDate(d: LocalDate): String = "${DAYS_FULL[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"

fun shortDate(d: LocalDate): String = "${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]}"

fun clock(sec: Int): String = (sec / 60).toString() + ":" + (sec % 60).toString().padStart(2, '0')
