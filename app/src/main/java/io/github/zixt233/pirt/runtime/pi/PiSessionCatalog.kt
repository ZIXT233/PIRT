package io.github.zixt233.pirt.runtime.pi

import android.content.Context
import io.github.zixt233.pirt.model.PiSession
import io.github.zixt233.pirt.runtime.PiControlClient
import io.github.zixt233.pirt.runtime.RuntimeDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean

/** Pi's SessionManager is the only catalog owner; this class only exposes its current projection. */
class PiSessionCatalog(
    context: Context,
    private val control: PiControlClient,
) {
    private val appContext = context.applicationContext
    private val loading = AtomicBoolean(false)
    private val refreshRequested = AtomicBoolean(false)
    private val _sessions = MutableStateFlow<List<PiSession>>(emptyList())
    val sessions: StateFlow<List<PiSession>> = _sessions.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    fun refresh() {
        refreshRequested.set(true)
        if (!loading.compareAndSet(false, true)) return
        Thread({
            do {
                refreshRequested.set(false)
                runCatching { execute("list") }
                    .map(::decode)
                    .onSuccess {
                        _sessions.value = it.sortedByDescending(PiSession::updatedAt)
                        _loaded.value = true
                    }
                    .onFailure { RuntimeDiagnostics.error(appContext, "pi-catalog", "读取 Pi 会话失败", it) }
            } while (refreshRequested.get())
            loading.set(false)
            if (refreshRequested.get()) refresh()
        }, "pirt-session-catalog").apply { isDaemon = true }.start()
    }

    fun rename(session: PiSession, name: String) = mutate("rename", session.path, name.trim())

    fun delete(session: PiSession) = mutate("delete", session.path)

    private fun mutate(command: String, vararg args: String?) {
        val values = args.map { requireNotNull(it) { "Pi session has no JSONL path" } }
        Thread({
            runCatching { execute(command, *values.toTypedArray()) }
                .onSuccess { refresh() }
                .onFailure { RuntimeDiagnostics.error(appContext, "pi-catalog", "$command Pi 会话失败", it) }
        }, "pirt-session-$command").apply { isDaemon = true }.start()
    }

    private fun execute(command: String, vararg args: String): String {
        control.start()
        val data = when (command) {
            "list" -> control.execute("sessions_list")
            "rename" -> control.execute("session_rename") {
                put("path", args[0])
                put("name", args[1])
            }
            "delete" -> control.execute("session_delete") { put("path", args[0]) }
            else -> error("Unknown Pi catalog command: $command")
        }
        return data.optJSONArray("sessions")?.toString() ?: "[]"
    }

    private fun decode(text: String): List<PiSession> {
        val values = JSONArray(text)
        return buildList {
            for (index in 0 until values.length()) {
                val value = values.getJSONObject(index)
                add(PiSession(
                    runtimeKey = value.getString("id"),
                    id = value.getString("id"),
                    name = value.optString("name"),
                    path = value.getString("path"),
                    firstMessage = value.optString("firstMessage").takeIf(String::isNotBlank),
                    createdAt = value.optLong("createdAt"),
                    updatedAt = value.optLong("updatedAt"),
                    messageCount = value.optInt("messageCount"),
                ))
            }
        }
    }
}
