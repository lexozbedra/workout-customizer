package app.sports.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sports.core.Kind
import app.sports.workout.PlayerState
import app.sports.workout.WorkoutService

@Composable
private fun RoundButton(glyph: Glyph, label: String, big: Boolean, description: String, onClick: () -> Unit) {
    val d = if (big) 88.dp else 60.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.width(if (big) 92.dp else 64.dp)) {
        Box(
            Modifier
                .size(d)
                .clip(CircleShape)
                .background(if (big) Color.White else Color.White.copy(alpha = 0.2f))
                .clickable(onClickLabel = description, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, if (big) Color(0xFF111111) else Color.White, if (big) 38.dp else 26.dp)
        }
        Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

/** Экран тренировки: цвет фона сам говорит, работа сейчас или отдых. Всё идёт автоматически, кнопки нужны только для пауз и перемоток. */
@Composable
fun PlayerScreen(p: PlayerState, ctl: Controller, onClose: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    var early by remember { mutableStateOf(false) }
    var pausedByUs by remember { mutableStateOf(false) }
    // Пока выбираешь, что делать, тренировка стоит на паузе.
    val openConfirm: () -> Unit = {
        if (!p.paused) {
            ctl.send(WorkoutService.ACTION_TOGGLE)
            pausedByUs = true
        }
        confirm = true
    }
    val resume: () -> Unit = {
        confirm = false
        early = false
        if (pausedByUs) {
            pausedByUs = false
            ctl.send(WorkoutService.ACTION_TOGGLE)
        }
    }
    BackHandler { if (p.finished) onClose() else openConfirm() }

    val bg = when {
        p.finished -> Pal.Done
        p.step.kind == Kind.WORK -> Pal.Work
        else -> Pal.Rest
    }

    if (p.finished) {
        Column(
            Modifier.fillMaxSize().background(bg).systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("ГОТОВО", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Тренировка закончена", color = Color.White, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)
            Text(p.title + " отмечена выполненной.", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF111111)),
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp).height(52.dp),
            ) { Text("Закрыть", fontSize = 16.sp) }
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(p.step.group, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("Шаг ${p.step.index + 1} из ${p.stepCount}", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (p.step.kind == Kind.WORK) "РАБОТА" else "ОТДЫХ",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
            )
            Text(p.step.title, color = Color.White, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)
            Text(p.step.sub, color = Color.White, fontSize = 18.sp, lineHeight = 24.sp)
            Text(
                clock(p.remainingSec),
                color = Color.White,
                style = TextStyle(fontSize = 112.sp, lineHeight = 116.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum"),
            )
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.28f))) {
                Box(Modifier.fillMaxWidth(p.progress).height(6.dp).background(Color.White))
            }
        }

        val next = p.next
        if (next != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text("ДАЛЬШЕ", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, letterSpacing = 1.5.sp)
                Text(next.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (next.sub.isNotEmpty()) Text(next.sub, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp)
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
            RoundButton(Glyph.PREV, "Назад", false, "Предыдущий шаг") { ctl.send(WorkoutService.ACTION_PREV) }
            RoundButton(Glyph.RESTART, "Сначала", false, "Текущий шаг сначала") { ctl.send(WorkoutService.ACTION_RESTART) }
            RoundButton(
                if (p.paused) Glyph.PLAY else Glyph.PAUSE,
                if (p.paused) "Продолжить" else "Пауза",
                true,
                if (p.paused) "Продолжить" else "Пауза",
            ) { ctl.send(WorkoutService.ACTION_TOGGLE) }
            RoundButton(Glyph.NEXT, "Дальше", false, "Следующий шаг") { ctl.send(WorkoutService.ACTION_NEXT) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Осталось ${clock(p.totalLeftSec)}", color = Color.White.copy(alpha = 0.95f), fontSize = 14.sp)
            if (p.paused) {
                Text(
                    "ПАУЗА",
                    color = Color(0xFF111111),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            TextButton(onClick = openConfirm) { Text("Завершить", color = Color.White) }
        }
    }

    if (confirm && !early) {
        AlertDialog(
            onDismissRequest = resume,
            title = { Text("Завершить тренировку?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Тренировка на паузе.")
                    TextButton(onClick = resume) { Text("Продолжить тренировку") }
                    TextButton(onClick = { early = true }) { Text("Завершить досрочно и сохранить прогресс") }
                    TextButton(onClick = { confirm = false; ctl.send(WorkoutService.ACTION_STOP) }) { Text("Выйти без сохранения") }
                }
            },
            confirmButton = {},
        )
    }
    if (confirm && early) {
        EarlyFinishDialog(p, ctl, onBack = { early = false })
    }
}
