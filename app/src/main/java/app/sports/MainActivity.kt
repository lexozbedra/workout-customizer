package app.sports

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.core.content.ContextCompat
import app.sports.ui.Controller
import app.sports.ui.Root
import app.sports.ui.SportsTheme
import app.sports.workout.WorkoutService

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private lateinit var controller: Controller

    // Папка с данными: системное окно выбора, доступ запоминается насовсем.
    private val pickTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: Exception) {
            }
            Store.get(this).setFolder(uri)
        }
    }

    private val pickPlan = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                if (text == null) controller.importFailed("Не удалось прочитать файл.") else controller.importText(text)
            } catch (e: Exception) {
                controller.importFailed("Не удалось прочитать файл: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Без разрешения на уведомления служба всё равно работает, но не видно кнопок паузы на шторке.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        controller = Controller(applicationContext, Store.get(this))
        try { WeightReminder.schedule(this) } catch (_: Exception) { }
        controller.pickFolder = { pickTree.launch(null) }
        controller.pickPlanFile = { pickPlan.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }
        setContent { SportsTheme { Root(controller) } }
    }

    // Пока экран приложения закрыт, служба показывает небольшую плашку поверх других приложений.
    override fun onStart() {
        super.onStart()
        WorkoutService.appVisibility(true)
    }

    override fun onStop() {
        super.onStop()
        WorkoutService.appVisibility(false)
    }
}
