package io.github.zixt233.pirt.runtime

import android.content.Context
import android.net.Uri
import io.github.zixt233.pirt.model.WorkspaceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.Socket

sealed interface GraphicsState {
    data object Stopped : GraphicsState
    data object Starting : GraphicsState
    data class Ready(val url: String, val display: Int, val vncPort: Int, val password: String) : GraphicsState
    data class Error(val message: String) : GraphicsState
}

/** Service-owned localhost-only VNC/noVNC desktop. */
class GraphicsManager(
    context: Context,
    private val onActivityChanged: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val runtime = PRootRuntime(appContext)
    private val _state = MutableStateFlow<GraphicsState>(GraphicsState.Stopped)
    val state: StateFlow<GraphicsState> = _state.asStateFlow()
    private var workspace: WorkspaceConfig? = null
    @Volatile private var process: Process? = null
    private var generation: Long = 0

    @Synchronized fun open(workspace: WorkspaceConfig) { this.workspace = workspace }

    @Synchronized
    fun start(password: String) {
        if (process?.isRunning() == true || _state.value is GraphicsState.Starting) return
        val currentWorkspace = checkNotNull(workspace) { "图形会话尚未准备好" }
        val requestGeneration = ++generation
        publish(GraphicsState.Starting)
        Thread({ launch(currentWorkspace, password, requestGeneration) }, "pirt-graphics").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        generation++
        process?.destroy()
        process = null
        publish(GraphicsState.Stopped)
    }

    @Synchronized fun activeCount(): Int = if (process?.isRunning() == true || _state.value is GraphicsState.Starting) 1 else 0

    @Synchronized
    fun close() {
        generation++
        process?.destroy()
        process = null
        workspace = null
        publish(GraphicsState.Stopped)
    }

    private fun launch(workspace: WorkspaceConfig, password: String, requestGeneration: Long) {
        runCatching {
            val graphics = runtime.graphicsProcess(workspace, password)
            val child = ProcessBuilder(graphics.process.command).apply {
                environment().putAll(graphics.process.environment)
                redirectErrorStream(true)
            }.start()
            synchronized(this) {
                if (requestGeneration != generation) {
                    child.destroy()
                    return@runCatching
                }
                process = child
            }
            val output = StringBuilder()
            Thread({
                runCatching {
                    child.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach {
                            synchronized(output) {
                                output.appendLine(it)
                                if (output.length > 20_000) output.delete(0, output.length - 16_000)
                            }
                        }
                    }
                }
            }, "pirt-graphics-log").apply { isDaemon = true }.start()

            var ready = false
            for (attempt in 0 until 100) {
                if (!child.isRunning()) break
                ready = canConnect(graphics.webPort)
                if (ready) break
                Thread.sleep(100)
            }
            if (!ready) {
                val details = synchronized(output) { output.toString().trim() }
                child.destroy()
                error(details.ifBlank { "图形服务启动超时" })
            }
            synchronized(this) {
                if (requestGeneration != generation || process !== child) {
                    child.destroy()
                    return@runCatching
                }
            }
            val query = "autoconnect=1&reconnect=1&resize=scale&shared=1&password=${Uri.encode(password)}"
            publish(
                GraphicsState.Ready(
                    url = "http://127.0.0.1:${graphics.webPort}/vnc.html?$query",
                    display = graphics.display,
                    vncPort = graphics.vncPort,
                    password = password,
                )
            )
            RuntimeDiagnostics.info(appContext, "graphics", "Ready on DISPLAY=:${graphics.display}, web port ${graphics.webPort}")
            val exit = child.waitFor()
            synchronized(this) {
                if (requestGeneration == generation && process === child) {
                    process = null
                    if (_state.value !is GraphicsState.Stopped) {
                        val details = synchronized(output) { output.toString().trim() }
                        publish(GraphicsState.Error(details.ifBlank { "图形服务已退出（$exit）" }))
                    }
                }
            }
        }.onFailure { error ->
            synchronized(this) {
                if (requestGeneration == generation) {
                    process?.destroy()
                    process = null
                    RuntimeDiagnostics.error(appContext, "graphics", error.message ?: "图形环境启动失败", error)
                    publish(GraphicsState.Error(error.message ?: "图形环境启动失败"))
                }
            }
        }
    }

    private fun publish(value: GraphicsState) {
        _state.value = value
        onActivityChanged()
    }

    private fun canConnect(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 150) }
        true
    }.getOrDefault(false)

    private fun Process.isRunning(): Boolean = try {
        exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }
}
