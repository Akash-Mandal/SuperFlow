package com.superflow.ai

import android.content.Context
import com.superflow.domain.CommandBus
import com.superflow.util.Dates
import org.json.JSONObject
import java.io.File

/**
 * Automatic safety snapshots.
 *
 * Full Control removes confirmation friction, so correctness has to come from
 * somewhere else: a full snapshot before every multi-step or destructive run,
 * plus the per-action undo trail.
 */
object Snapshots {

    private const val DIR = "snapshots"
    private const val KEEP = 12

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun save(context: Context, bus: CommandBus): File? = try {
        val f = File(dir(context), "snap-${System.currentTimeMillis()}.json")
        f.writeText(bus.snapshot().toString())
        prune(context)
        f
    } catch (e: Exception) {
        null
    }

    fun list(context: Context): List<File> =
        dir(context).listFiles()?.filter { it.name.startsWith("snap-") }
            ?.sortedByDescending { it.name } ?: emptyList()

    fun restore(context: Context, file: File, bus: CommandBus): Boolean = try {
        bus.restoreSnapshot(JSONObject(file.readText())).ok
    } catch (e: Exception) {
        false
    }

    fun label(file: File): String {
        val stamp = file.name.removePrefix("snap-").removeSuffix(".json").toLongOrNull()
            ?: return file.name
        return "${Dates.stamp(stamp)} · ${file.length() / 1024}KB"
    }

    fun clear(context: Context) = list(context).forEach { it.delete() }

    private fun prune(context: Context) {
        val files = list(context)
        if (files.size > KEEP) files.drop(KEEP).forEach { it.delete() }
    }
}
