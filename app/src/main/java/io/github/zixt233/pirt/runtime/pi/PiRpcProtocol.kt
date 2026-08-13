package io.github.zixt233.pirt.runtime.pi

import org.json.JSONArray
import org.json.JSONObject

enum class ProcessState { STOPPED, STARTING, RUNNING, EXITED, CRASHED }

enum class TurnState { IDLE, QUEUED, GENERATING, RUNNING_TOOL, COMPACTING, STOPPING, COMPLETED, FAILED }

enum class PiMessageRole { USER, ASSISTANT, SYSTEM }

data class PiImage(val data: String, val mimeType: String)

internal const val SEND_IMAGE_TOOL_NAME = "send_image"

data class PiMessage(
    val id: String,
    val role: PiMessageRole,
    val text: String,
    val images: List<PiImage> = emptyList(),
    val entryId: String? = null,
)

data class PiModel(
    val provider: String,
    val id: String,
    val name: String,
    val reasoning: Boolean,
)

data class PiModelSelection(
    val model: PiModel,
    val thinkingLevel: String?,
    val thinkingLevels: List<String>,
)

data class PiCommand(val name: String, val description: String, val source: String)

data class PiExtensionUiRequest(
    val id: String,
    val method: String,
    val title: String = "",
    val message: String = "",
    val options: List<String> = emptyList(),
    val placeholder: String = "",
    val prefill: String = "",
    val notifyType: String = "info",
    val key: String = "",
    val value: String? = null,
    val lines: List<String> = emptyList(),
)

data class PiTokenUsage(
    val input: Long = 0,
    val output: Long = 0,
    val cacheRead: Long = 0,
    val cacheWrite: Long = 0,
    val total: Long = 0,
)

data class PiContextUsage(
    val tokens: Long?,
    val contextWindow: Long,
    val percent: Double?,
)

data class PiSessionStats(
    val tokens: PiTokenUsage = PiTokenUsage(),
    val contextUsage: PiContextUsage? = null,
)

sealed interface PiExecutionItem {
    val id: String
}

data class PiThinkingState(
    override val id: String,
    val text: String = "",
    val finished: Boolean = false,
) : PiExecutionItem

data class PiToolState(
    override val id: String,
    val name: String,
    val summary: String,
    val input: String = "",
    val output: String = "",
    val images: List<PiImage> = emptyList(),
    val finished: Boolean = false,
    val failed: Boolean = false,
) : PiExecutionItem

data class PiAgentState(
    val provider: String = "",
    val modelId: String = "",
    val modelName: String = "",
    val thinkingLevel: String? = null,
    val streaming: Boolean = false,
    val compacting: Boolean = false,
    val sessionFile: String? = null,
    val sessionId: String? = null,
    val pendingMessageCount: Int = 0,
    val autoCompactionEnabled: Boolean = true,
    val steeringMessages: List<String> = emptyList(),
)

data class PiSessionReplacement(
    val cancelled: Boolean,
    val selectedText: String? = null,
    val sessionKey: String? = null,
    val agent: PiAgentState? = null,
    val messages: List<PiMessage> = emptyList(),
)

sealed interface PiFailure {
    val message: String

    data class Command(val command: String, override val message: String) : PiFailure
    data class Protocol(override val message: String) : PiFailure
    data class Process(override val message: String, val exitCode: Int? = null) : PiFailure
    data class Timeout(val command: String, override val message: String) : PiFailure
}

class PiRequestException(val failure: PiFailure) : Exception(failure.message)

data class PiSessionState(
    val process: ProcessState = ProcessState.STOPPED,
    val turn: TurnState = TurnState.IDLE,
    val historyLoaded: Boolean = false,
    val agentLoaded: Boolean = false,
    val messages: List<PiMessage> = emptyList(),
    val execution: List<PiExecutionItem> = emptyList(),
    val agent: PiAgentState = PiAgentState(),
    val models: List<PiModel> = emptyList(),
    val modelsRevision: Long = 0,
    val commands: List<PiCommand> = emptyList(),
    val commandsRevision: Long = 0,
    val extensionUiRequests: List<PiExtensionUiRequest> = emptyList(),
    val extensionStatuses: Map<String, String> = emptyMap(),
    val extensionWidgets: Map<String, List<String>> = emptyMap(),
    val thinkingLevels: List<String> = emptyList(),
    val thinkingLevelsRevision: Long = 0,
    val stats: PiSessionStats? = null,
    val failure: PiFailure? = null,
)

data class PiSessionSummary(
    val process: ProcessState,
    val turn: TurnState,
)

sealed interface PiStreamEvent {
    data object Ignored : PiStreamEvent
    data object AgentStarted : PiStreamEvent
    data object AgentSettled : PiStreamEvent
    data class AssistantError(val message: String) : PiStreamEvent
    data class AutoRetryStarted(val attempt: Int, val maxAttempts: Int, val delayMs: Long) : PiStreamEvent
    data class AutoRetryEnded(val success: Boolean, val attempt: Int, val finalError: String?) : PiStreamEvent
    data class TextDelta(val text: String) : PiStreamEvent
    data object ThinkingStarted : PiStreamEvent
    data class ThinkingDelta(val text: String) : PiStreamEvent
    data object ThinkingEnded : PiStreamEvent
    data class Failed(val message: String) : PiStreamEvent
    data class QueueUpdated(val steering: List<String>) : PiStreamEvent
    data class UserMessageStarted(val text: String, val images: List<PiImage> = emptyList()) : PiStreamEvent
    data class ToolStarted(val id: String, val name: String, val summary: String, val input: String) : PiStreamEvent
    data class ToolUpdated(val id: String, val output: String, val images: List<PiImage> = emptyList()) : PiStreamEvent
    data class ToolEnded(
        val id: String,
        val name: String,
        val output: String,
        val images: List<PiImage> = emptyList(),
        val failed: Boolean,
    ) : PiStreamEvent
    data class Phase(val turn: TurnState) : PiStreamEvent
    data class CompactionStarted(val reason: String) : PiStreamEvent
    data class CompactionEnded(
        val reason: String,
        val aborted: Boolean,
        val willRetry: Boolean,
        val errorMessage: String?,
    ) : PiStreamEvent
    data class ExtensionUiRequested(val request: PiExtensionUiRequest) : PiStreamEvent
    data class ExtensionUiCancelled(val id: String) : PiStreamEvent
}

sealed class PiRequest<T>(
    val command: String,
    val timeoutMillis: Long = 20_000L,
) {
    abstract fun payload(): JSONObject
    abstract fun decode(data: JSONObject?): T

    protected fun base() = JSONObject().put("type", command)

    data class OpenSession(val sessionPath: String?) : PiRequest<PiAgentState>("session_open", 60_000L) {
        override fun payload() = base().apply { sessionPath?.let { put("sessionPath", it) } }
        override fun decode(data: JSONObject?): PiAgentState = GetState.decode(data)
    }

    data object GetState : PiRequest<PiAgentState>("get_state") {
        override fun payload() = base()
        override fun decode(data: JSONObject?): PiAgentState {
            val value = data ?: JSONObject()
            val model = value.optJSONObject("model")
            return PiAgentState(
                provider = model?.optString("provider").orEmpty(),
                modelId = model?.optString("id").orEmpty(),
                modelName = model?.optString("name").orEmpty(),
                thinkingLevel = value.optString("thinkingLevel").takeIf(String::isNotBlank),
                streaming = value.optBoolean("isStreaming"),
                compacting = value.optBoolean("isCompacting"),
                sessionFile = value.optString("sessionFile").takeIf(String::isNotBlank),
                sessionId = value.optString("sessionId").takeIf(String::isNotBlank),
                pendingMessageCount = value.optInt("pendingMessageCount"),
                autoCompactionEnabled = value.optBoolean("autoCompactionEnabled", true),
                steeringMessages = value.optJSONArray("steeringMessages").toStrings(),
            )
        }
    }

    data object GetMessages : PiRequest<List<PiMessage>>("get_messages") {
        override fun payload() = base()
        override fun decode(data: JSONObject?): List<PiMessage> = decodeMessages(data?.optJSONArray("messages"))
    }

    // An extension command may deliberately wait for an Android dialog before
    // Pi accepts the prompt. Keep this request alive while the user responds.
    data class Prompt(val message: String, val images: List<PiImage>) : PiRequest<Unit>("prompt", 3_600_000L) {
        override fun payload() = base().put("message", message).apply {
            if (images.isNotEmpty()) put("images", JSONArray(images.map { image ->
                JSONObject().put("type", "image").put("data", image.data).put("mimeType", image.mimeType)
            }))
        }
        override fun decode(data: JSONObject?) = Unit
    }

    data object Abort : PiRequest<Unit>("abort") {
        override fun payload() = base()
        override fun decode(data: JSONObject?) = Unit
    }

    data class Steer(val message: String, val images: List<PiImage>) : PiRequest<Unit>("steer") {
        override fun payload() = base().put("message", message).apply {
            if (images.isNotEmpty()) put("images", JSONArray(images.map { image ->
                JSONObject().put("type", "image").put("data", image.data).put("mimeType", image.mimeType)
            }))
        }
        override fun decode(data: JSONObject?) = Unit
    }

    data class Fork(val entryId: String) : PiRequest<PiSessionReplacement>("fork", 120_000L) {
        override fun payload() = base().put("entryId", entryId)
        override fun decode(data: JSONObject?): PiSessionReplacement = decodeReplacement(data)
    }

    data object Clone : PiRequest<PiSessionReplacement>("clone", 120_000L) {
        override fun payload() = base()
        override fun decode(data: JSONObject?): PiSessionReplacement = decodeReplacement(data)
    }

    data object GetModels : PiRequest<List<PiModel>>("get_available_models") {
        override fun payload() = base()
        override fun decode(data: JSONObject?): List<PiModel> = data?.optJSONArray("models").toModels()
    }

    data class SetModel(val provider: String, val modelId: String) : PiRequest<PiModelSelection>("set_model") {
        override fun payload() = base().put("provider", provider).put("modelId", modelId)
        override fun decode(data: JSONObject?): PiModelSelection {
            val value = checkNotNull(data)
            return PiModelSelection(
                model = value.getJSONObject("model").toModel(),
                thinkingLevel = value.optString("thinkingLevel").takeIf(String::isNotBlank),
                thinkingLevels = value.optJSONArray("thinkingLevels").toStrings(),
            )
        }
    }

    data object GetThinkingLevels : PiRequest<List<String>>("get_available_thinking_levels") {
        override fun payload() = base()
        override fun decode(data: JSONObject?): List<String> = data?.optJSONArray("levels").toStrings()
    }

    data class SetThinkingLevel(val level: String) : PiRequest<Unit>("set_thinking_level") {
        override fun payload() = base().put("level", level)
        override fun decode(data: JSONObject?) = Unit
    }

    data object GetSessionStats : PiRequest<PiSessionStats>("get_session_stats") {
        override fun payload() = base()
        override fun decode(data: JSONObject?): PiSessionStats {
            val value = data ?: JSONObject()
            val tokens = value.optJSONObject("tokens") ?: JSONObject()
            val context = value.optJSONObject("contextUsage")
            return PiSessionStats(
                tokens = PiTokenUsage(
                    input = tokens.optLong("input"),
                    output = tokens.optLong("output"),
                    cacheRead = tokens.optLong("cacheRead"),
                    cacheWrite = tokens.optLong("cacheWrite"),
                    total = tokens.optLong("total"),
                ),
                contextUsage = context?.let {
                    PiContextUsage(
                        tokens = it.opt("tokens").takeUnless { token -> token == null || token == JSONObject.NULL }?.let { token -> (token as Number).toLong() },
                        contextWindow = it.optLong("contextWindow"),
                        percent = it.opt("percent").takeUnless { percent -> percent == null || percent == JSONObject.NULL }?.let { percent -> (percent as Number).toDouble() },
                    )
                },
            )
        }
    }

    data object ExportHtml : PiRequest<String>("export_html", 120_000L) {
        override fun payload() = base()
        override fun decode(data: JSONObject?): String = checkNotNull(data)
            .getString("path")
    }

    data object GetCommands : PiRequest<List<PiCommand>>("get_commands") {
        override fun payload() = base()
        override fun decode(data: JSONObject?): List<PiCommand> {
            val values = data?.optJSONArray("commands") ?: return emptyList()
            return buildList {
                for (index in 0 until values.length()) {
                    val command = values.optJSONObject(index) ?: continue
                    add(PiCommand(command.optString("name"), command.optString("description"), command.optString("source")))
                }
            }
        }
    }

    data object Compact : PiRequest<Unit>("compact", 120_000L) {
        override fun payload() = base()
        override fun decode(data: JSONObject?) = Unit
    }

    data class SetAutoCompaction(val enabled: Boolean) : PiRequest<Unit>("set_auto_compaction") {
        override fun payload() = base().put("enabled", enabled)
        override fun decode(data: JSONObject?) = Unit
    }

    data class SetAutoRetry(val enabled: Boolean) : PiRequest<Unit>("set_auto_retry") {
        override fun payload() = base().put("enabled", enabled)
        override fun decode(data: JSONObject?) = Unit
    }

    data class SetSessionName(val name: String) : PiRequest<Unit>("set_session_name") {
        override fun payload() = base().put("name", name)
        override fun decode(data: JSONObject?) = Unit
    }

    data class ExtensionUiResponse(
        val requestId: String,
        val value: String? = null,
        val confirmed: Boolean? = null,
        val cancelled: Boolean = false,
    ) : PiRequest<Unit>("extension_ui_response") {
        override fun payload() = base().put("requestId", requestId).apply {
            if (value != null) put("value", value)
            if (confirmed != null) put("confirmed", confirmed)
            if (cancelled) put("cancelled", true)
        }
        override fun decode(data: JSONObject?) = Unit
    }
}

internal fun parseStreamEvent(json: JSONObject): PiStreamEvent? = when (json.optString("type")) {
    "extension_ui_request" -> PiStreamEvent.ExtensionUiRequested(
        PiExtensionUiRequest(
            id = json.optString("id"),
            method = json.optString("method"),
            title = json.optString("title"),
            message = json.optString("message"),
            options = json.optJSONArray("options").toStrings(),
            placeholder = json.optString("placeholder"),
            prefill = json.optString("prefill"),
            notifyType = json.optString("notifyType", "info"),
            key = json.optString("statusKey").ifBlank { json.optString("widgetKey") },
            value = when (json.optString("method")) {
                "setStatus" -> json.optString("statusText").takeIf(String::isNotBlank)
                "setTitle" -> json.optString("title").takeIf(String::isNotBlank)
                "set_editor_text" -> json.optString("text")
                else -> null
            },
            lines = json.optJSONArray("widgetLines").toStrings(),
        )
    )
    "extension_ui_cancel" -> PiStreamEvent.ExtensionUiCancelled(json.optString("id"))
    "agent_start" -> PiStreamEvent.AgentStarted
    "agent_settled" -> PiStreamEvent.AgentSettled
    "turn_start" -> PiStreamEvent.Phase(TurnState.GENERATING)
    "compaction_start" -> PiStreamEvent.CompactionStarted(json.optString("reason"))
    "compaction_end" -> PiStreamEvent.CompactionEnded(
        reason = json.optString("reason"),
        aborted = json.optBoolean("aborted"),
        willRetry = json.optBoolean("willRetry"),
        errorMessage = json.optString("errorMessage").takeIf(String::isNotBlank),
    )
    "auto_retry_start" -> PiStreamEvent.AutoRetryStarted(
        attempt = json.optInt("attempt"),
        maxAttempts = json.optInt("maxAttempts"),
        delayMs = json.optLong("delayMs"),
    )
    "tool_execution_start" -> PiStreamEvent.ToolStarted(
        json.optString("toolCallId"),
        json.optString("toolName"),
        toolSummary(json.optString("toolName"), json.optJSONObject("args")),
        json.optJSONObject("args")?.toString(2).orEmpty(),
    )
    "tool_execution_update" -> resultContent(json.optJSONObject("partialResult")).let { content ->
        PiStreamEvent.ToolUpdated(json.optString("toolCallId"), content.first, content.second)
    }
    "tool_execution_end" -> resultContent(json.optJSONObject("result")).let { content ->
        PiStreamEvent.ToolEnded(
            json.optString("toolCallId"),
            json.optString("toolName"),
            content.first,
            content.second,
            json.optBoolean("isError"),
        )
    }
    "message_update" -> when (val update = json.optJSONObject("assistantMessageEvent")?.optString("type")) {
        "text_delta" -> PiStreamEvent.TextDelta(json.getJSONObject("assistantMessageEvent").optString("delta"))
        "thinking_start" -> PiStreamEvent.ThinkingStarted
        "thinking_delta" -> PiStreamEvent.ThinkingDelta(json.getJSONObject("assistantMessageEvent").optString("delta"))
        "thinking_end" -> PiStreamEvent.ThinkingEnded
        "text_start", "text_end", "toolcall_start", "toolcall_delta", "toolcall_end" -> PiStreamEvent.Ignored
        else -> null
    }
    "message_end" -> json.optJSONObject("message")
        ?.optString("errorMessage")
        ?.takeIf(String::isNotBlank)
        ?.let(PiStreamEvent::AssistantError)
        ?: PiStreamEvent.Ignored
    "session_error" -> PiStreamEvent.Failed(json.optString("error", "Pi 请求失败"))
    "queue_update" -> PiStreamEvent.QueueUpdated(json.optJSONArray("steering").toStrings())
    "message_start" -> json.optJSONObject("message")?.let { message ->
        if (message.optString("role") != "user") return@let PiStreamEvent.Ignored
        val content = decodeMessageContent(message.opt("content"))
        if (content.first.isBlank() && content.second.isEmpty()) return@let PiStreamEvent.Ignored
        PiStreamEvent.UserMessageStarted(content.first, content.second)
    } ?: PiStreamEvent.Ignored
    "auto_retry_end" -> PiStreamEvent.AutoRetryEnded(
        success = json.optBoolean("success"),
        attempt = json.optInt("attempt"),
        finalError = json.optString("finalError").takeIf(String::isNotBlank),
    )
    "agent_end", "turn_end",
    "summarization_retry_scheduled", "summarization_retry_attempt_start", "summarization_retry_finished" -> PiStreamEvent.Ignored
    else -> null
}

private fun decodeReplacement(data: JSONObject?): PiSessionReplacement {
    val value = data ?: JSONObject()
    if (value.optBoolean("cancelled")) return PiSessionReplacement(cancelled = true)
    return PiSessionReplacement(
        cancelled = false,
        selectedText = value.optString("selectedText").takeIf(String::isNotBlank),
        sessionKey = value.optString("sessionKey").takeIf(String::isNotBlank),
        agent = PiRequest.GetState.decode(value.optJSONObject("state")),
        messages = decodeMessages(value.optJSONArray("messages")),
    )
}

private fun decodeMessages(values: JSONArray?): List<PiMessage> = buildList {
    val messages = values ?: return@buildList
    for (index in 0 until messages.length()) {
        val message = messages.optJSONObject(index) ?: continue
        val entryId = message.optString("entryId").takeIf(String::isNotBlank)
        val displayId = entryId ?: "history-$index"
        if (message.optString("role") == "toolResult") {
            val content = decodeMessageContent(message.opt("content"))
            if (message.optString("toolName") == SEND_IMAGE_TOOL_NAME && content.second.isNotEmpty()) {
                // Only explicit send_image results are user-visible. Images returned by read
                // and other tools stay in the execution trace for the agent to inspect.
                add(PiMessage(displayId, PiMessageRole.ASSISTANT, "", content.second, entryId))
            }
            continue
        }
        val error = message.optString("errorMessage").takeIf(String::isNotBlank)
        if (error != null) {
            add(PiMessage(displayId, PiMessageRole.SYSTEM, error, entryId = entryId))
            continue
        }
        val role = when (message.optString("role")) {
            "user" -> PiMessageRole.USER
            "assistant" -> PiMessageRole.ASSISTANT
            else -> continue
        }
        val content = decodeMessageContent(message.opt("content"))
        if (content.first.isNotBlank() || content.second.isNotEmpty()) {
            add(PiMessage(displayId, role, content.first, content.second, entryId))
        }
    }
}

private fun decodeMessageContent(content: Any?): Pair<String, List<PiImage>> = when (content) {
    is String -> content to emptyList()
    is JSONArray -> {
        val text = mutableListOf<String>()
        val images = mutableListOf<PiImage>()
        for (index in 0 until content.length()) {
            val part = content.optJSONObject(index) ?: continue
            when (part.optString("type")) {
                "text" -> text += part.optString("text")
                "image" -> {
                    val data = part.optString("data")
                    val mimeType = part.optString("mimeType")
                    if (data.isNotBlank() && mimeType.startsWith("image/")) images += PiImage(data, mimeType)
                }
            }
        }
        text.joinToString("\n") to images
    }
    else -> "" to emptyList()
}

private fun JSONArray?.toStrings(): List<String> = buildList {
    val values = this@toStrings ?: return@buildList
    for (index in 0 until values.length()) add(values.optString(index))
}

private fun JSONArray?.toModels(): List<PiModel> = buildList {
    val values = this@toModels ?: return@buildList
    for (index in 0 until values.length()) {
        val model = values.optJSONObject(index) ?: continue
        if (model.optString("provider").isNotBlank() && model.optString("id").isNotBlank()) add(model.toModel())
    }
}

private fun JSONObject.toModel() = PiModel(
    provider = optString("provider"),
    id = optString("id"),
    name = optString("name").ifBlank { optString("id") },
    reasoning = optBoolean("reasoning"),
)

private fun resultContent(result: JSONObject?): Pair<String, List<PiImage>> {
    val content = result?.optJSONArray("content") ?: return "" to emptyList()
    val text = buildList {
        for (index in 0 until content.length()) {
            val part = content.optJSONObject(index) ?: continue
            if (part.optString("type") == "text") add(part.optString("text"))
        }
    }.joinToString("\n")
    val images = buildList {
        for (index in 0 until content.length()) {
            val part = content.optJSONObject(index) ?: continue
            if (part.optString("type") != "image") continue
            val data = part.optString("data")
            val mimeType = part.optString("mimeType")
            if (data.isNotBlank() && mimeType.startsWith("image/")) add(PiImage(data, mimeType))
        }
    }
    return text to images
}

private fun toolSummary(name: String, args: JSONObject?): String {
    if (args == null) return name
    return when (name) {
        "bash" -> args.optString("command")
        "read", "write", "edit", "find", "ls" -> args.optString("path")
        "grep" -> args.optString("pattern")
        else -> ""
    }.ifBlank { name }
}
