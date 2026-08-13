package io.github.zixt233.pirt.runtime

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RuntimeDiagnostics {
    private const val TAG = "PIRT-Runtime"
    private val lock = Any()

    fun info(context: Context, component: String, message: String) {
        Log.i(TAG, "[$component] $message")
        append(context, "INFO", component, message)
    }

    fun error(context: Context, component: String, message: String, error: Throwable? = null) {
        Log.e(TAG, "[$component] $message", error)
        append(context, "ERROR", component, buildString {
            append(message)
            error?.stackTraceToString()?.let { append('\n').append(it) }
        })
    }

    private fun append(context: Context, level: String, component: String, message: String) = runCatching {
        synchronized(lock) {
            val directory = File(context.filesDir, "pirt/logs").apply { mkdirs() }
            val log = File(directory, "runtime.log")
            if (log.length() > 2L * 1024 * 1024) {
                File(directory, "runtime.previous.log").also { previous ->
                    if (previous.exists()) previous.delete()
                    log.renameTo(previous)
                }
            }
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            log.appendText("$time $level [$component] $message\n")
        }
    }
}
