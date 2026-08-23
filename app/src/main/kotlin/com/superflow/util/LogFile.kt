package com.superflow.util

import android.content.Context
import android.util.Log
import java.io.File

object LogFile {
    private const val NAME = "logs/app.log"
    private const val MAX_BYTES = 512 * 1024

    fun file(context: Context): File = File(context.filesDir, NAME).apply { parentFile?.mkdirs() }

    fun write(context: Context, tag: String, msg: String) = try {
        val f = file(context)
        val line = "${System.currentTimeMillis()} $tag $msg\n"
        if (f.exists() && f.length() + line.length > MAX_BYTES) {
            val txt = f.readText().takeLast(MAX_BYTES / 2)
            f.writeText(txt + line)
        } else f.appendText(line)
    } catch (_: Exception) { }

    fun read(context: Context): String = try { file(context).takeIf { it.exists() }?.readText().orEmpty() } catch (_: Exception) { "" }

    fun clear(context: Context) = try { file(context).delete() } catch (_: Exception) { }

    fun installCrashHandler(context: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                write(context, "CRASH", Log.getStackTraceString(e).take(4000))
            } catch (_: Exception) { }
            prev?.uncaughtException(t, e)
        }
    }
}
