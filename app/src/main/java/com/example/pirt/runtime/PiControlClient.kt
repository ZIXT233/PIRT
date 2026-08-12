package com.example.pirt.runtime

import com.example.pirt.runtime.pi.PiFailure
import com.example.pirt.runtime.pi.PiRequest
import com.example.pirt.runtime.pi.PiRequestException
import com.example.pirt.runtime.pi.PiStreamEvent
import com.example.pirt.runtime.pi.parseStreamEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class PiProvider(
    val id: String,
    val name: String,
    val authTypes: List<String>,
    val configured: Boolean,
)

data class PiModel(
    val provider: String,
    val id: String,
    val name: String,
    val reasoning: Boolean,
    val contextWindow: Long,
)

data class PiAuthOption(val id: String, val label: String, val description: String?)

sealed interface PiAuthEvent {
    data object Ready : PiAuthEvent
    data class Providers(
        val values: List<PiProvider>,
        val selectedProvider: String? = null,
        val selectedModel: String? = null,
    ) : PiAuthEvent
    data class Models(
        val values: List<PiModel>,
        val selectedProvider: String?,
        val selectedModel: String?,
    ) : PiAuthEvent
    data class Prompt(
        val loginId: String,
        val promptId: String,
        val kind: String,
        val message: String,
        val placeholder: String?,
        val options: List<PiAuthOption>,
    ) : PiAuthEvent
    data class Notice(
        val loginId: String,
        val kind: String,
        val message: String?,
        val url: String?,
        val userCode: String?,
        val verificationUri: String?,
    ) : PiAuthEvent
    data class Selected(val providerId: String, val modelId: String) : PiAuthEvent
    data class Error(val command: String?, val message: String) : PiAuthEvent
    data class ProcessFailed(val message: String) : PiAuthEvent
}

/** One resident JSONL control plane for Pi-owned global data and operations. */
class PiControlClient(
    private val processSpec: RuntimeProcessSpec,
    private val listener: (PiAuthEvent) -> Unit,
    private val sessionListener: (String, PiStreamEvent) -> Unit = { _, _ -> },
    private val sessionFailure: (String) -> Unit = {},
    private val diagnostic: (String, Throwable?) -> Unit,
) : Closeable {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private val closed = AtomicBoolean(false)
    @Volatile private var ready = false
    private val pending = ConcurrentHashMap<String, CompletableFuture<JSONObject>>()
    private val requests = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "pirt-pi-request").apply { isDaemon = true }
    }

    @Synchronized
    fun start() {
        check(!closed.get()) { "Pi SDK host is closed" }
        if (process != null) return
        val child = ProcessBuilder(processSpec.command).apply { environment().putAll(processSpec.environment) }.start()
        process = child
        writer = BufferedWriter(OutputStreamWriter(child.outputStream, StandardCharsets.UTF_8))
        Thread({
            child.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines -> lines.forEach(::readStdout) }
            processStopped("Pi SDK host exited (${child.waitFor()})")
        }, "pirt-pi-host-output").apply { isDaemon = true }.start()
        Thread({
            child.errorStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.forEach { if (it.isNotBlank()) diagnostic("Pi SDK host stderr: $it", null) }
            }
        }, "pirt-pi-host-error").apply { isDaemon = true }.start()
    }

    fun isReady(): Boolean = ready

    fun loadProviders() = command("providers")

    fun loadModels(providerId: String? = null) = command("models") {
        if (providerId != null) put("providerId", providerId)
    }

    fun login(providerId: String, authType: String): String = command("login") {
        put("providerId", providerId)
        put("authType", authType)
    }

    fun logout(providerId: String) = command("logout") { put("providerId", providerId) }

    fun selectModel(providerId: String, modelId: String) = command("select_model") {
        put("providerId", providerId)
        put("modelId", modelId)
    }

    fun answerPrompt(promptId: String, value: String) = command("auth_prompt_response") {
        put("promptId", promptId)
        put("value", value)
    }

    fun cancelLogin(loginId: String) = command("cancel_login") { put("loginId", loginId) }

    fun execute(type: String, timeoutMillis: Long = 60_000L, configure: JSONObject.() -> Unit = {}): JSONObject {
        val id = UUID.randomUUID().toString()
        val future = CompletableFuture<JSONObject>()
        pending[id] = future
        runCatching { send(JSONObject().put("id", id).put("type", type).apply(configure)) }
            .onFailure { pending.remove(id)?.completeExceptionally(it) }
        return try {
            try {
                future.get(timeoutMillis, TimeUnit.MILLISECONDS)
            } catch (error: ExecutionException) {
                throw (error.cause ?: error)
            }
        } finally {
            pending.remove(id, future)
        }
    }

    fun <T> request(sessionKey: String, request: PiRequest<T>, callback: (Result<T>) -> Unit = {}) {
        runCatching(::start).onFailure {
            callback(Result.failure(PiRequestException(PiFailure.Process(it.message ?: "Pi SDK host failed to start"))))
            return
        }
        requests.execute {
            runCatching {
                val data = execute(request.command, request.timeoutMillis) {
                    put("sessionKey", sessionKey)
                    val payload = request.payload()
                    payload.keys().forEach { key -> if (key != "type") put(key, payload.get(key)) }
                }
                request.decode(data)
            }.onSuccess { callback(Result.success(it)) }
                .onFailure {
                    callback(Result.failure(PiRequestException(PiFailure.Command(request.command, it.message ?: "Pi operation failed"))))
                }
        }
    }

    @Synchronized
    private fun command(type: String, configure: JSONObject.() -> Unit = {}): String {
        val id = UUID.randomUUID().toString()
        send(JSONObject().put("id", id).put("type", type).apply(configure))
        return id
    }

    private fun send(value: JSONObject) {
        checkNotNull(process) { "Pi SDK host 尚未启动" }
        checkNotNull(writer).apply {
            write(value.toString())
            newLine()
            flush()
        }
    }

    private fun readStdout(line: String) {
        if (line.isBlank()) return
        runCatching { JSONObject(line) }
            .onSuccess { json ->
                val sessionKey = json.optString("sessionKey")
                if (sessionKey.isNotBlank()) {
                    parseStreamEvent(json)?.let { sessionListener(sessionKey, it) }
                    return@onSuccess
                }
                val future = json.optString("id").takeIf(String::isNotBlank)?.let(pending::remove)
                if (future != null && json.optString("type") == "response") {
                    if (json.optBoolean("success")) future.complete(json.optJSONObject("data") ?: JSONObject())
                    else future.completeExceptionally(IllegalStateException(json.optString("error", "Pi control operation failed")))
                } else {
                    if (json.optString("type") == "ready") ready = true
                    listener(parse(json))
                }
            }
            .onFailure { error ->
                diagnostic("Invalid Pi control record: $line", error)
                if (!closed.get()) listener(PiAuthEvent.ProcessFailed("Pi control protocol failed"))
            }
    }

    private fun parse(json: JSONObject): PiAuthEvent = when (json.optString("type")) {
        "ready" -> PiAuthEvent.Ready
        "auth_prompt" -> {
            val prompt = json.getJSONObject("prompt")
            PiAuthEvent.Prompt(
                loginId = json.getString("loginId"),
                promptId = json.getString("promptId"),
                kind = prompt.getString("type"),
                message = prompt.optString("message"),
                placeholder = prompt.optString("placeholder").takeIf(String::isNotBlank),
                options = prompt.optJSONArray("options").toAuthOptions(),
            )
        }
        "auth_event" -> {
            val event = json.getJSONObject("event")
            PiAuthEvent.Notice(
                loginId = json.getString("loginId"),
                kind = event.getString("type"),
                message = event.optString("message").takeIf(String::isNotBlank)
                    ?: event.optString("instructions").takeIf(String::isNotBlank),
                url = event.optString("url").takeIf(String::isNotBlank),
                userCode = event.optString("userCode").takeIf(String::isNotBlank),
                verificationUri = event.optString("verificationUri").takeIf(String::isNotBlank),
            )
        }
        "response" -> parseResponse(json)
        else -> PiAuthEvent.Error(null, json.optString("error", "Pi 返回了未知消息"))
    }

    @Synchronized
    private fun processStopped(message: String) {
        process = null
        runCatching { writer?.close() }
        writer = null
        ready = false
        pending.values.forEach { it.completeExceptionally(IllegalStateException(message)) }
        pending.clear()
        sessionFailure(message)
        if (!closed.get()) listener(PiAuthEvent.ProcessFailed(message))
    }

    private fun parseResponse(json: JSONObject): PiAuthEvent {
        val command = json.optString("command").takeIf(String::isNotBlank)
        if (!json.optBoolean("success")) {
            return PiAuthEvent.Error(command, json.optString("error", "Pi 操作失败"))
        }
        val data = json.optJSONObject("data") ?: JSONObject()
        data.optJSONArray("providers")?.let {
            return PiAuthEvent.Providers(
                values = it.toProviders(),
                selectedProvider = data.optString("selectedProvider").takeIf(String::isNotBlank),
                selectedModel = data.optString("selectedModel").takeIf(String::isNotBlank),
            )
        }
        data.optJSONArray("models")?.let {
            return PiAuthEvent.Models(
                values = it.toModels(),
                selectedProvider = data.optString("selectedProvider").takeIf(String::isNotBlank),
                selectedModel = data.optString("selectedModel").takeIf(String::isNotBlank),
            )
        }
        if (command == "select_model") {
            return PiAuthEvent.Selected(data.getString("providerId"), data.getString("modelId"))
        }
        return PiAuthEvent.Ready
    }

    private fun JSONArray?.toAuthOptions(): List<PiAuthOption> = buildList {
        val values = this@toAuthOptions ?: return@buildList
        for (index in 0 until values.length()) {
            val value = values.getJSONObject(index)
            add(PiAuthOption(value.getString("id"), value.getString("label"), value.optString("description").takeIf(String::isNotBlank)))
        }
    }

    private fun JSONArray.toProviders(): List<PiProvider> = buildList {
        for (index in 0 until length()) {
            val value = getJSONObject(index)
            val types = value.getJSONArray("authTypes")
            add(
                PiProvider(
                    id = value.getString("id"),
                    name = value.getString("name"),
                    authTypes = List(types.length()) { types.getString(it) },
                    configured = !value.isNull("configuredAuth") || value.optJSONObject("status")?.optBoolean("configured") == true,
                )
            )
        }
    }

    private fun JSONArray.toModels(): List<PiModel> = buildList {
        for (index in 0 until length()) {
            val value = getJSONObject(index)
            add(
                PiModel(
                    provider = value.getString("provider"),
                    id = value.getString("id"),
                    name = value.getString("name"),
                    reasoning = value.optBoolean("reasoning"),
                    contextWindow = value.optLong("contextWindow"),
                )
            )
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        pending.values.forEach { it.completeExceptionally(IllegalStateException("Pi control process stopped")) }
        pending.clear()
        requests.shutdownNow()
        ready = false
        runCatching { writer?.close() }
        writer = null
        process?.destroy()
        process = null
    }
}
