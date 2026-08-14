package io.github.zixt233.pirt.runtime

import android.content.Context
import io.github.zixt233.pirt.model.WorkspaceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

data class TerminalState(
    val transcript: String = "",
    val running: Boolean = false,
    val started: Boolean = false,
    val error: String? = null,
)

/** Service-owned persistent shell for the shared workspace. */
class TerminalManager(
    context: Context,
    private val onActivityChanged: () -> Unit,
) {
    private companion object { const val DONE_PREFIX = "__PIRT_COMMAND_DONE__:" }

    private val runtime = PRootRuntime(context.applicationContext)
    private val _state = MutableStateFlow(TerminalState())
    val state: StateFlow<TerminalState> = _state.asStateFlow()
    private var workspace: WorkspaceConfig? = null
    private var process: Process? = null
    private var writer: BufferedWriter? = null

    @Synchronized
    fun open(workspace: WorkspaceConfig) {
        if (process != null) return
        this.workspace = workspace
        startProcess()
    }

    @Synchronized
    fun execute(command: String) {
        require(command.isNotBlank()) { "命令不能为空" }
        check(!_state.value.running) { "上一条命令仍在运行" }
        val output = checkNotNull(writer) { "终端进程不可用" }
        append("\n$ $command\n")
        _state.update { it.copy(running = true, error = null) }
        onActivityChanged()
        output.write(command)
        output.write("\nprintf '${DONE_PREFIX}%s\\n' \"${'$'}?\"\n")
        output.flush()
    }

    @Synchronized
    fun clear() = _state.update { it.copy(transcript = "") }

    @Synchronized
    fun reset() {
        process?.destroy()
        process = null
        writer = null
        _state.update { it.copy(running = false, started = false, error = null) }
        append("\n[终端已重置]\n")
        startProcess()
    }

    @Synchronized fun activeCount(): Int = if (process != null) 1 else 0
    fun busyCount(): Int = if (_state.value.running) 1 else 0

    @Synchronized
    fun close() {
        process?.destroy()
        process = null
        writer = null
        workspace = null
        _state.value = TerminalState()
        onActivityChanged()
    }

    private fun startProcess() {
        val currentWorkspace = checkNotNull(workspace) { "终端尚未指定工作区" }
        runCatching {
            val spec = runtime.terminalProcess(currentWorkspace)
            val child = ProcessBuilder(spec.command).apply {
                environment().putAll(spec.environment)
                redirectErrorStream(true)
            }.start()
            process = child
            writer = BufferedWriter(OutputStreamWriter(child.outputStream, StandardCharsets.UTF_8))
            _state.update { it.copy(started = true, error = null) }
            append("PIRT Debian · workspace\n")
            onActivityChanged()
            Thread({ readOutput(child) }, "pirt-terminal").apply { isDaemon = true }.start()
        }.onFailure { error ->
            _state.update { it.copy(started = false, running = false, error = error.message ?: "终端启动失败") }
        }
    }

    private fun readOutput(child: Process) {
        try {
            BufferedReader(InputStreamReader(child.inputStream, StandardCharsets.UTF_8)).useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith(DONE_PREFIX)) {
                        _state.update { it.copy(running = false) }
                        onActivityChanged()
                    } else append(stripAnsi(line) + "\n")
                }
            }
            synchronized(this) {
                if (process === child) {
                    process = null
                    writer = null
                    _state.update { it.copy(started = false, running = false, error = "终端进程已退出") }
                    onActivityChanged()
                }
            }
        } catch (error: Exception) {
            synchronized(this) {
                if (process === child) {
                    _state.update { it.copy(started = false, running = false, error = error.message ?: "终端通信失败") }
                    onActivityChanged()
                }
            }
        }
    }

    private fun append(text: String) = _state.update { current ->
        val next = current.transcript + text
        current.copy(transcript = if (next.length > 120_000) next.takeLast(100_000) else next)
    }

    private fun stripAnsi(value: String): String = value.replace(Regex("\\u001B\\[[;?0-9]*[ -/]*[@-~]"), "")
}
