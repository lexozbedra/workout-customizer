package app.sports

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.sports.core.DataFolder

/**
 * Папка, которую пользователь один раз выбрал в системном окне. Доступ сохраняется между запусками,
 * файлы лежат у пользователя и переживают переустановку приложения.
 */
class SafFolder(private val context: Context, val uri: Uri) : DataFolder {
    private val root: DocumentFile? get() = DocumentFile.fromTreeUri(context, uri)

    val displayName: String get() = root?.name ?: uri.lastPathSegment?.substringAfterLast(':') ?: "папка"

    fun isReadable(): Boolean = root?.let { it.exists() && it.canRead() && it.canWrite() } == true

    private fun dir(path: String, create: Boolean): DocumentFile? {
        var cur = root ?: return null
        val parts = path.split('/').dropLast(1)
        for (p in parts) {
            cur = cur.findFile(p)?.takeIf { it.isDirectory } ?: if (create) (cur.createDirectory(p) ?: return null) else return null
        }
        return cur
    }

    private fun file(path: String): DocumentFile? = dir(path, false)?.findFile(path.substringAfterLast('/'))?.takeIf { it.isFile }

    override fun read(path: String): String? = try {
        file(path)?.let { f -> context.contentResolver.openInputStream(f.uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }
    } catch (_: Exception) { null }

    override fun write(path: String, text: String) {
        val name = path.substringAfterLast('/')
        val d = dir(path, true) ?: throw java.io.IOException("нет доступа к папке")
        val f = d.findFile(name)?.takeIf { it.isFile } ?: d.createFile("application/octet-stream", name)
            ?: throw java.io.IOException("не удалось создать $name")
        context.contentResolver.openOutputStream(f.uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw java.io.IOException("не удалось записать $name")
    }

    override fun delete(path: String) {
        try { file(path)?.delete() } catch (_: Exception) { }
    }

    override fun list(dir: String): List<String> = try {
        val d = dir("$dir/x", false)
        d?.listFiles()?.filter { it.isFile }?.mapNotNull { it.name }?.sorted().orEmpty()
    } catch (_: Exception) { emptyList() }
}
