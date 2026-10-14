package app.sports.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sports.core.Body
import app.sports.core.Schedule
import app.sports.workout.WorkoutService
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay

@Composable
fun Root(ctl: Controller) {
    val ui by ctl.store.ui.collectAsState()
    val data = ui.data
    val player by WorkoutService.state.collectAsState()
    val message by ctl.message.collectAsState()
    val schedule = remember(data?.plan, data?.planStart, data?.vacations, data?.moves, data?.tuning) {
        data?.let { Schedule(it.plan, it.planStart, it.vacations, it.moves, it.tuning) }
    }

    // Текущее время обновляется, чтобы дедлайн переноса (21:00), смена суток и автопропуск учитывались без перезапуска.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = LocalDateTime.now()
        }
    }
    LaunchedEffect(now.toLocalDate(), ui.loaded, ui.plans.size) { ctl.store.tick(now.toLocalDate()) }

    var tab by remember { mutableStateOf(0) }
    var detailId by remember { mutableStateOf<String?>(null) }
    // Тренировка открыта из плана: только просмотр, без кнопки «Начать»
    var preview by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(LocalDate.now()) }

    val p = player
    if (p != null) {
        PlayerScreen(p, ctl, onClose = { ctl.closeFinished() })
        return
    }

    BackHandler(enabled = detailId != null) { detailId = null }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val id = detailId
        when {
            !ui.loaded -> Text("Загрузка…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            !ui.hasFolder -> Onboarding(ui.errors, ctl)
            id != null && data != null && schedule != null ->
                DetailScreen(data, schedule, now, id, preview, ctl, onBack = { detailId = null })
            else -> {
                val titles = listOf("Сегодня", "Прогресс", "План", "Планы", "Тело", "Профиль")
                ScrollableTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background, edgePadding = 0.dp) {
                    titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                }
                Column(Modifier.padding(top = 16.dp)) {
                    if (tab == 0 && ui.db.settings.weightReminder && Body.reminderDue(ui.db.weights, now.toLocalDate())) {
                        WeightBanner(onGo = { tab = 4 })
                    }
                    if (tab <= 2 && (data == null || schedule == null)) {
                        NoPlan(onPlans = { tab = 3 })
                    } else when (tab) {
                        0 -> HomeScreen(data!!, schedule!!, now, date, ctl, onDate = { date = it }, onOpen = { detailId = it; preview = false })
                        1 -> ProgressScreen(data!!, schedule!!, now.toLocalDate(), ctl)
                        2 -> PlanScreen(
                            data!!, schedule!!, now, ctl,
                            onWeek = { date = it; tab = 0 },
                            onOpen = { detailId = it; preview = true },
                        )
                        3 -> PlansScreen(ui, ctl, now.toLocalDate())
                        4 -> BodyScreen(ui, ctl, now.toLocalDate())
                        else -> ProfileScreen(ui, ctl, now.toLocalDate())
                    }
                }
            }
        }
    }

    message?.let { m ->
        AlertDialog(
            onDismissRequest = { ctl.dismissMessage() },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { ctl.dismissMessage() }) { Text("Понятно") } },
        )
    }
}

@Composable
private fun NoPlan(onPlans: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Нет активного плана", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)
        Text(
            "Планы теперь лежат в файлах. Импортируй файл плана и нажми «Начать» на вкладке «Планы».",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onPlans) { Text("Открыть «Планы»") }
    }
}

/** Первый запуск: выбираем папку, где будут лежать профиль, прогресс и планы. */
@Composable
private fun Onboarding(errors: List<String>, ctl: Controller) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 24.dp)) {
        Text("Где хранить данные?", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)
        Text(
            "Профиль, прогресс и планы лежат обычными файлами в папке, которую выберешь ты. Они не пропадут при переустановке приложения, и их можно скопировать на другой телефон.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "В системном окне создай или выбери папку (например, «Тренировки») и нажми «Использовать эту папку».",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { ctl.pickFolder() }, modifier = Modifier.fillMaxWidth()) { Text("Выбрать папку") }
        OutlinedButton(onClick = { ctl.store.useInternal() }, modifier = Modifier.fillMaxWidth()) { Text("Пока во внутренней памяти") }
    }
}

/** Месяц без записи веса: мягкая подсказка на главном экране. */
@Composable
private fun WeightBanner(onGo: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 16.dp).border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Пора обновить вес", fontWeight = FontWeight.SemiBold)
        Text("С последней записи прошёл месяц (или записей ещё нет).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onGo) { Text("Записать вес") }
    }
}
