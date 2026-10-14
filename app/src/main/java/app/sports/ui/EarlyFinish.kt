package app.sports.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.sports.core.Reasons
import app.sports.workout.PlayerState

/** Досрочное завершение: сколько процентов сделано и почему остановилась. Прогресс при этом не теряется. */
@Composable
fun EarlyFinishDialog(p: PlayerState, ctl: Controller, onBack: () -> Unit) {
    val guess = remember {
        val raw = if (p.totalSec > 0) p.elapsedSec * 100f / p.totalSec else 0f
        (Math.round(raw / 5f) * 5).coerceIn(0, 100)
    }
    var percent by remember { mutableStateOf(guess.toFloat()) }
    var reason by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val value = Math.round(percent / 5f) * 5
    val valid = value >= 100 || reason.isNotEmpty()

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Завершить досрочно") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Сколько тренировки сделано: $value%", fontWeight = FontWeight.SemiBold)
                Slider(value = percent, onValueChange = { percent = it }, valueRange = 0f..100f, steps = 19, modifier = Modifier.fillMaxWidth())
                Text(
                    "Подставлено по времени, ты можешь поправить. Это засчитается в прогресс пропорционально.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (value < 100) {
                    Text("Почему останавливаешься?", fontWeight = FontWeight.SemiBold)
                    ReasonPicker(Reasons.EARLY, reason, { reason = it }, note, { note = it }, noteRequired = false)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { ctl.finishEarly(p.sessionId, value, reason, note.trim(), p.elapsedSec) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("Назад") } },
    )
}
