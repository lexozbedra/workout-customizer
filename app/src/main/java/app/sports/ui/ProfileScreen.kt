package app.sports.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sports.Ui
import app.sports.core.Profile
import java.time.LocalDate

private val ProfileShape = RoundedCornerShape(14.dp)

private fun fmtKg(v: Double): String = if (v <= 0.0) "" else if (v % 1.0 == 0.0) v.toInt().toString() else v.toString().replace('.', ',')

/** Вкладка «Профиль»: имя, рост, вес, пол, дата рождения и папка, где лежат все данные. */
@Composable
fun ProfileScreen(ui: Ui, ctl: Controller, today: LocalDate) {
    val p = ui.db.profile
    var name by remember(p) { mutableStateOf(p.name) }
    var height by remember(p) { mutableStateOf(if (p.heightCm > 0) p.heightCm.toString() else "") }
    var weight by remember(p) { mutableStateOf(fmtKg(p.weightKg)) }
    var sex by remember(p) { mutableStateOf(p.sex) }
    var birth by remember(p) { mutableStateOf(p.birthDate) }
    var pickBirth by remember { mutableStateOf(false) }

    val h = height.trim().toIntOrNull() ?: 0
    val w = weight.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
    val heightOk = height.isBlank() || h in 80..250
    val weightOk = weight.isBlank() || w in 30.0..300.0
    val changed = name.trim() != p.name || (if (height.isBlank()) 0 else h) != p.heightCm ||
        (if (weight.isBlank()) 0.0 else w) != p.weightKg || sex != p.sex || birth != p.birthDate

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Профиль", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)

        OutlinedTextField(name, { name = it }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                height, { height = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Рост, см") }, singleLine = true,
                isError = !heightOk, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                weight, { weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) }, label = { Text("Вес, кг") }, singleLine = true,
                isError = !weightOk, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
        }

        Text("Пол", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sex == "f", onClick = { sex = if (sex == "f") "" else "f" }, label = { Text("Женский") })
            FilterChip(selected = sex == "m", onClick = { sex = if (sex == "m") "" else "m" }, label = { Text("Мужской") })
        }

        OutlinedButton(onClick = { pickBirth = true }, modifier = Modifier.fillMaxWidth()) {
            val age = birth?.let { b -> Profile(birthDate = b).age(today) }
            Text(if (birth == null) "Дата рождения: не указана" else "Дата рождения: ${birth!!.dayOfMonth}.${birth!!.monthValue}.${birth!!.year}" + (age?.let { " ($it)" } ?: ""))
        }

        Button(
            onClick = {
                ctl.saveProfile(Profile(name.trim(), if (height.isBlank()) 0 else h, if (weight.isBlank()) 0.0 else w, sex, birth))
            },
            enabled = changed && heightOk && weightOk,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Сохранить") }
        Text(
            "Изменённый вес попадает в историю на вкладке «Тело» (запись на сегодня) и используется для оценки калорий. Рост, пол и дата рождения нужны для ИМТ и процента жира.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // папка данных
        Column(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, ProfileShape).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Папка с данными", fontWeight = FontWeight.SemiBold)
            Text(
                if (ui.internal) "Сейчас данные лежат во внутренней памяти приложения и пропадут при удалении приложения. Выбери свою папку, чтобы они были у тебя."
                else "Сейчас: ${ui.folderName}. Там лежат profile.json, progress.json и папка plans. Их можно копировать и переносить.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { ctl.pickFolder() }) { Text(if (ui.internal) "Выбрать папку" else "Сменить папку") }
            Text(
                "Если в новой папке уже есть данные, приложение возьмёт их. Если она пустая, туда переедут текущие.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (pickBirth) DateDialog(birth ?: LocalDate.of(1995, 1, 1), onPick = { birth = it }, onDismiss = { pickBirth = false })
}
