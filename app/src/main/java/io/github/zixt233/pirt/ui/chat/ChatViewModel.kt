package io.github.zixt233.pirt.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.zixt233.pirt.model.ChatImage
import io.github.zixt233.pirt.model.ChatMessage
import io.github.zixt233.pirt.model.MessageRole
import io.github.zixt233.pirt.model.PiSession
import io.github.zixt233.pirt.runtime.RuntimeConnection
import io.github.zixt233.pirt.runtime.pi.PiCommand
import io.github.zixt233.pirt.runtime.pi.PiBranchResult
import io.github.zixt233.pirt.runtime.pi.PiExecutionItem
import io.github.zixt233.pirt.runtime.pi.PiExtensionUiRequest
import io.github.zixt233.pirt.runtime.pi.PiImage
import io.github.zixt233.pirt.runtime.pi.PiMessageRole
import io.github.zixt233.pirt.runtime.pi.PiModel
import io.github.zixt233.pirt.runtime.pi.PiSessionState
import io.github.zixt233.pirt.runtime.pi.PiSessionStats
import io.github.zixt233.pirt.runtime.pi.PiSessionManager
import io.github.zixt233.pirt.runtime.pi.ProcessState
import io.github.zixt233.pirt.runtime.pi.TurnState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ChatUiState(
    val process: ProcessState = ProcessState.STARTING,
    val turn: TurnState = TurnState.IDLE,
    val historyLoaded: Boolean = false,
    val agentLoaded: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val execution: List<PiExecutionItem> = emptyList(),
    val provider: String = "",
    val modelId: String = "",
    val modelName: String = "",
    val thinkingLevel: String? = null,
    val sessionId: String? = null,
    val streaming: Boolean = false,
    val steeringMessages: List<String> = emptyList(),
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
    val error: String? = null,
) {
    val busy: Boolean get() = turn in setOf(TurnState.QUEUED, TurnState.GENERATING, TurnState.RUNNING_TOOL, TurnState.COMPACTING, TurnState.STOPPING)
    val ready: Boolean get() = process == ProcessState.RUNNING && historyLoaded && agentLoaded
}

class ChatViewModel(
    private val session: PiSession,
    private val runtime: RuntimeConnection,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState(historyLoaded = session.path == null))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private var sessionJob: Job? = null

    init {
        viewModelScope.launch {
            runtime.manager.collectLatest { manager ->
                sessionJob?.cancel()
                if (manager == null) return@collectLatest
                bind(manager)
            }
        }
    }

    /** Rebinds a restored screen to a live controller, recreating a process stopped while off-screen. */
    fun activate() {
        runtime.manager.value?.let(::bind)
    }

    fun prompt(message: String, images: List<ChatImage> = emptyList()) {
        runtime.manager.value?.prompt(session.runtimeKey, message, images.map { PiImage(it.data, it.mimeType) })
    }

    fun steer(message: String, images: List<ChatImage> = emptyList()) {
        runtime.manager.value?.steer(session.runtimeKey, message, images.map { PiImage(it.data, it.mimeType) })
    }

    fun abort() = runtime.manager.value?.abort(session.runtimeKey)
    fun requestModels() = runtime.manager.value?.requestModels(session.runtimeKey)
    fun requestCommands() = runtime.manager.value?.requestCommands(session.runtimeKey)
    fun requestThinkingLevels() = runtime.manager.value?.requestThinkingLevels(session.runtimeKey)
    fun requestStats() = runtime.manager.value?.requestStats(session.runtimeKey)
    fun respondExtensionUi(
        requestId: String,
        value: String? = null,
        confirmed: Boolean? = null,
        cancelled: Boolean = false,
    ) = runtime.manager.value?.respondExtensionUi(session.runtimeKey, requestId, value, confirmed, cancelled)
    fun dismissExtensionUi(requestId: String) = runtime.manager.value?.dismissExtensionUi(session.runtimeKey, requestId)
    fun exportHtml(callback: (Result<String>) -> Unit) {
        val manager = runtime.manager.value
            ?: return callback(Result.failure(IllegalStateException("PIRT 尚未连接")))
        runCatching {
            manager.exportHtml(session.runtimeKey) { result -> viewModelScope.launch { callback(result) } }
        }.onFailure { callback(Result.failure(it)) }
    }
    fun setModel(provider: String, modelId: String) = runtime.manager.value?.setModel(session.runtimeKey, provider, modelId)
    fun setThinkingLevel(level: String) = runtime.manager.value?.setThinkingLevel(session.runtimeKey, level)
    fun compact() = runtime.manager.value?.compact(session.runtimeKey)
    fun setAutoCompaction(enabled: Boolean) = runtime.manager.value?.setAutoCompaction(session.runtimeKey, enabled)
    fun setAutoRetry(enabled: Boolean) = runtime.manager.value?.setAutoRetry(session.runtimeKey, enabled)
    fun fork(entryId: String, callback: (Result<PiBranchResult?>) -> Unit) {
        val manager = runtime.manager.value ?: return callback(Result.failure(IllegalStateException("PIRT 尚未连接")))
        runCatching { manager.fork(session.runtimeKey, entryId) { result ->
            viewModelScope.launch { callback(result) }
        } }.onFailure { callback(Result.failure(it)) }
    }

    fun cloneSession(callback: (Result<PiBranchResult?>) -> Unit) {
        val manager = runtime.manager.value ?: return callback(Result.failure(IllegalStateException("PIRT 尚未连接")))
        runCatching { manager.cloneSession(session.runtimeKey) { result ->
            viewModelScope.launch { callback(result) }
        } }.onFailure { callback(Result.failure(it)) }
    }

    private fun bind(manager: PiSessionManager) {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            manager.open(session).collect(::accept)
        }
    }

    private fun accept(value: PiSessionState) {
        _state.value = ChatUiState(
            process = value.process,
            turn = value.turn,
            historyLoaded = value.historyLoaded,
            agentLoaded = value.agentLoaded,
            messages = value.messages.map { message ->
                ChatMessage(
                    id = message.id,
                    role = when (message.role) {
                        PiMessageRole.USER -> MessageRole.USER
                        PiMessageRole.ASSISTANT -> MessageRole.ASSISTANT
                        PiMessageRole.SYSTEM -> MessageRole.SYSTEM
                    },
                    text = message.text,
                    images = message.images.map { ChatImage(it.data, it.mimeType) },
                    entryId = message.entryId,
                )
            },
            execution = value.execution,
            provider = value.agent.provider,
            modelId = value.agent.modelId,
            modelName = value.agent.modelName,
            thinkingLevel = value.agent.thinkingLevel,
            sessionId = value.agent.sessionId,
            streaming = value.agent.streaming,
            steeringMessages = value.agent.steeringMessages,
            models = value.models,
            modelsRevision = value.modelsRevision,
            commands = value.commands,
            commandsRevision = value.commandsRevision,
            extensionUiRequests = value.extensionUiRequests,
            extensionStatuses = value.extensionStatuses,
            extensionWidgets = value.extensionWidgets,
            thinkingLevels = value.thinkingLevels,
            thinkingLevelsRevision = value.thinkingLevelsRevision,
            stats = value.stats,
            error = value.failure?.message,
        )
    }

    companion object {
        fun factory(session: PiSession, runtime: RuntimeConnection) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChatViewModel(session, runtime) as T
            }
    }
}
