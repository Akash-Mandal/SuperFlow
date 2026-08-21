package com.superflow.data

import android.content.Context
import com.superflow.core.time.SfTime
import com.superflow.domain.Serial
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local JSON backups written to app-private storage.
 *
 * Backups are full [Serial.exportAll] snapshots — the same shape as a manual
 * export — so any backup file restores through the existing import path.
 * Files rotate to a user-configurable cap (Prefs.maxBackups, default 7).
 */
object Backups {

    private const val DIR = "backups"
    private const val PREFIX = "superflow-backup-"
    private const val EXT = ".json"

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles()
            ?.filter { it.name.startsWith(PREFIX) && it.name.endsWith(EXT) }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    fun latest(context: Context): File? = list(context).firstOrNull()

    /** Create a timestamped backup. Returns the file written, or null on failure. */
    fun create(
        context: Context,
        repo: Repository,
        prefs: Prefs
    ): File? = try {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        val f = File(dir(context), "$PREFIX$stamp$EXT")
        f.writeText(Serial.exportAll(repo).toString(2))
        prune(context, prefs.maxBackups)
        f
    } catch (e: Exception) {
        null
    }

    /** A human label for a backup file. */
    fun label(file: File): String =
        file.name.removePrefix(PREFIX).removeSuffix(EXT).replace('_', ' ')

    /** Restore a backup file through the same transactional import as a manual import. */
    fun restore(context: Context, file: File, repo: Repository): Boolean = try {
        Serial.importAll(repo, JSONObject(file.readText()))
        true
    } catch (e: Exception) {
        false
    }

    fun delete(file: File): Boolean = file.delete()

    private fun prune(context: Context, keep: Int) {
        if (keep < 1) return
        val all = list(context)
        if (all.size > keep) all.drop(keep).forEach { it.delete() }
    }
}
