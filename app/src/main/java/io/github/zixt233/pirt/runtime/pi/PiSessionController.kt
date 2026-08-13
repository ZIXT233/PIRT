package io.github.zixt233.pirt.runtime.pi

import io.github.zixt233.pirt.model.PiSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/** UI projection for one Pi session. It never decides which process owns it. */
internal class PiSessionController(
    private var session: PiSession,
    private val request: (String, PiRequest<Any?>, (Result<Any?>) -> Unit) -> Unit,
    private val catalog: PiSessionCatalog,
    private val onIdentityChanged: (PiSessionController, String, PiSession) -> Unit,
    private val onStateChanged: (PiSessionController) -> Unit,
    private val onActivityChanged: () -> Unit,
) {
    private var hostKey = session.runtimeKey
    private val _state = MutableStateFlow(
        PiSessionState(process = ProcessState.STARTING, historyLoaded = session.path == null)
    )
    val state: StateFlow<PiSessionState> = _state.asStateFlow()
    @Volatile private var opened = false
    private var pendingAssistantError: String? = null

    fun adopt(value: PiSession) { session = value }

    fun open() {
        if (opened) return
        opened = true
        update { it.copy(process = ProcessState.STARTING) }
        request(
            PiRequest.OpenSession(session.path),
        ) { result ->
            result.onSuccess { agent ->
                update { it.copy(process = ProcessState.RUNNING, agent = agent, agentLoaded = true) }
                refresh()
            }.onFailure {
                opened = false
                requestFailed(it)
            }
        }
    }
    fun event(value: PiStreamEvent) = onEvent(value)
    fun fail(value: PiFailure) = onFailure(value)
    fun hostFailed(value: PiFailure) {
        opened = false
        update { it.copy(process = ProcessState.CRASHED) }
        onFailure(value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> request(value: PiRequest<T>, callback: (Result<T>) -> Unit = {}) {
        request(hostKey, value as PiRequest<Any?>) { result -> callback(result as Result<T>) }
    }

    fun prompt(message: String, images: List<PiImage>) {
        check(opened && state.value.process == ProcessState.RUNNING) { "Pi 会话尚未准备好" }
        update {
            it.copy(
                turn = if (it.agent.streaming) TurnState.QUEUED else TurnState.GENERATING,
                messages = it.messages + PiMessage(UUID.randomUUID().toString(), PiMessageRole.USER, message, images),
                execution = emptyList(),
                failure = null,
            )
        }
        request(PiRequest.Prompt(message, images)) { result -> result.onFailure(::requestFailed) }
    }

    fun steer(message: String, images: List<PiImage>) {
        check(opened && state.value.agent.streaming) { "Pi 当前没有正在执行的回复" }
        request(PiRequest.Steer(message, images)) { result -> result.onFailure(::requestFailed) }
    }

    fun executePiCommand(text: String) {
        check(opened && state.value.process == ProcessState.RUNNING) { "Pi 会话尚未准备好" }
        check(!busy()) { "AI 正在运行，请等待当前操作完成" }
        update { it.copy(execution = emptyList(), failure = null) }
        request(PiRequest.ExecutePiCommand(text)) { result ->
            result.onSuccess {
                refresh()
                catalog.refresh()
            }.onFailure(::requestFailed)
        }
    }

    fun abort() {
        update { it.copy(turn = TurnState.STOPPING) }
        request(PiRequest.Abort) { result ->
            result.onSuccess {
                update { it.copy(turn = TurnState.COMPLETED, agent = it.agent.copy(streaming = false, steeringMessages = emptyList())) }
                refresh()
            }.onFailure(::requestFailed)
        }
    }

    fun requestModels() = request(PiRequest.GetModels) { result ->
        result.onSuccess { values -> update { it.copy(models = values, modelsRevision = it.modelsRevision + 1, failure = null) } }
            .onFailure(::requestFailed)
    }
    fun requestCommands() = request(PiRequest.GetCommands) { result ->
        result.onSuccess { values -> update { it.copy(commands = values, commandsRevision = it.commandsRevision + 1, failure = null) } }
            .onFailure(::requestFailed)
    }
    fun reloadRuntime() {
        check(!busy()) { "AI 正在运行，暂时不能重新加载运行资源" }
        request(PiRequest.ReloadRuntime) { result ->
            result.onSuccess {
                requestCommands()
                refresh()
            }.onFailure(::requestFailed)
        }
    }
    fun requestThinkingLevels() = request(PiRequest.GetThinkingLevels) { result ->
        result.onSuccess { values -> update { it.copy(thinkingLevels = values, thinkingLevelsRevision = it.thinkingLevelsRevision + 1, failure = null) } }
            .onFailure(::requestFailed)
    }
    fun requestStats() = request(PiRequest.GetSessionStats) { result ->
        result.onSuccess { stats -> update { it.copy(stats = stats) } }
    }
    fun exportHtml(callback: (Result<String>) -> Unit) = request(PiRequest.ExportHtml, callback)
    fun respondExtensionUi(
        requestId: String,
        value: String? = null,
        confirmed: Boolean? = null,
        cancelled: Boolean = false,
    ) {
        update { it.copy(extensionUiRequests = it.extensionUiRequests.filterNot { request -> request.id == requestId }) }
        request(PiRequest.ExtensionUiResponse(requestId, value, confirmed, cancelled)) { result ->
            result.onFailure(::requestFailed)
        }
    }
    fun dismissExtensionUi(requestId: String) = update {
        it.copy(extensionUiRequests = it.extensionUiRequests.filterNot { request -> request.id == requestId })
    }
    fun setModel(provider: String, modelId: String) = request(PiRequest.SetModel(provider, modelId)) { result ->
        result.onSuccess { selection ->
            val model = selection.model
            update { current ->
                current.copy(
                    agent = current.agent.copy(
                        provider = model.provider,
                        modelId = model.id,
                        modelName = model.name,
                        thinkingLevel = selection.thinkingLevel,
                    ),
                    thinkingLevels = selection.thinkingLevels,
                    thinkingLevelsRevision = current.thinkingLevelsRevision + 1,
                    failure = null,
                )
            }
        }.onFailure(::requestFailed)
    }
    fun setThinkingLevel(level: String) = request(PiRequest.SetThinkingLevel(level)) { result ->
        result.onSuccess { update { current -> current.copy(agent = current.agent.copy(thinkingLevel = level), failure = null) } }
            .onFailure(::requestFailed)
    }
    fun compact() {
        update { it.copy(turn = TurnState.COMPACTING, agent = it.agent.copy(compacting = true), failure = null) }
        request(PiRequest.Compact) { result ->
            result.onSuccess {
                // compaction_end normally arrives first; this covers a dropped event.
                update { current ->
                    if (current.turn != TurnState.COMPACTING) current else current.copy(
                        turn = TurnState.COMPLETED,
                        agent = current.agent.copy(compacting = false),
                    )
                }
                refresh()
                catalog.refresh()
            }.onFailure(::requestFailed)
        }
    }
    fun setAutoCompaction(enabled: Boolean) = request(PiRequest.SetAutoCompaction(enabled)) { result -> result.onFailure(::requestFailed) }
    fun setAutoRetry(enabled: Boolean) = request(PiRequest.SetAutoRetry(enabled)) { result -> result.onFailure(::requestFailed) }
    fun rename(name: String) = request(PiRequest.SetSessionName(name)) { result ->
        result.onSuccess { catalog.refresh() }.onFailure(::requestFailed)
    }

    fun fork(entryId: String, callback: (Result<PiBranchResult?>) -> Unit) {
        check(!busy()) { "Pi 正在执行，暂时不能 Fork" }
        request(PiRequest.Fork(entryId)) { result -> result.fold(
            onSuccess = { replacement -> replace(replacement, callback) },
            onFailure = {
                requestFailed(it)
                callback(Result.failure(it))
            },
        ) }
    }

    fun cloneSession(callback: (Result<PiBranchResult?>) -> Unit) {
        check(!busy()) { "Pi 正在执行，暂时不能克隆" }
        request(PiRequest.Clone) { result -> result.fold(
            onSuccess = { replacement -> replace(replacement, callback) },
            onFailure = {
                requestFailed(it)
                callback(Result.failure(it))
            },
        ) }
    }

    fun busy(): Boolean = state.value.turn in setOf(TurnState.QUEUED, TurnState.GENERATING, TurnState.RUNNING_TOOL, TurnState.COMPACTING, TurnState.STOPPING)
    fun summary() = PiSessionSummary(state.value.process, state.value.turn)
    fun piSessionId(): String? = state.value.agent.sessionId
    fun sessionDisplayName(): String = session.displayName
    fun runtimeKey(): String = session.runtimeKey
    fun matches(key: String): Boolean = session.runtimeKey == key || session.id == key || piSessionId() == key

    fun refresh() {
        request(PiRequest.GetState) { result ->
            result.onSuccess { agent ->
                update {
                    it.copy(
                        agent = agent,
                        agentLoaded = true,
                        turn = when {
                            agent.compacting -> TurnState.COMPACTING
                            agent.streaming -> TurnState.GENERATING
                            agent.pendingMessageCount > 0 -> TurnState.QUEUED
                            it.turn == TurnState.COMPACTING -> TurnState.COMPLETED
                            else -> it.turn
                        },
                    )
                }
            }.onFailure { error ->
                if (!state.value.agentLoaded) requestFailed(error)
            }
        }
        request(PiRequest.GetMessages) { result ->
            result.onSuccess { messages -> update { it.copy(messages = messages, historyLoaded = true) } }
                .onFailure { error ->
                    if (!state.value.historyLoaded) requestFailed(error)
                }
        }
        requestStats()
    }

    private fun onEvent(event: PiStreamEvent) {
        when (event) {
            is PiStreamEvent.CompactionStarted -> update {
                it.copy(turn = TurnState.COMPACTING, agent = it.agent.copy(compacting = true), failure = null)
            }
            is PiStreamEvent.CompactionEnded -> {
                if (event.errorMessage != null && !event.willRetry) {
                    onFailure(PiFailure.Command("compact", event.errorMessage))
                    update { it.copy(agent = it.agent.copy(compacting = false)) }
                } else {
                    update {
                        it.copy(
                            turn = if (event.willRetry) TurnState.COMPACTING else TurnState.COMPLETED,
                            agent = it.agent.copy(compacting = event.willRetry),
                        )
                    }
                }
                if (!event.willRetry) {
                    refresh()
                    catalog.refresh()
                }
            }
            is PiStreamEvent.ExtensionUiRequested -> update { current ->
                val request = event.request
                when (request.method) {
                    "setStatus" -> current.copy(
                        extensionStatuses = current.extensionStatuses.toMutableMap().apply {
                            if (request.value == null) remove(request.key) else put(request.key, request.value)
                        }
                    )
                    "setWidget" -> current.copy(
                        extensionWidgets = current.extensionWidgets.toMutableMap().apply {
                            if (request.lines.isEmpty()) remove(request.key) else put(request.key, request.lines)
                        }
                    )
                    "setTitle" -> current
                    else -> if (current.extensionUiRequests.any { it.id == request.id }) current else current.copy(
                        extensionUiRequests = current.extensionUiRequests + request,
                    )
                }
            }
            is PiStreamEvent.ExtensionUiCancelled -> update { current ->
                current.copy(extensionUiRequests = current.extensionUiRequests.filterNot { it.id == event.id })
            }
            PiStreamEvent.AgentStarted -> update {
                pendingAssistantError = null
                it.copy(turn = TurnState.GENERATING, agent = it.agent.copy(streaming = true), failure = null)
            }
            PiStreamEvent.AgentSettled -> {
                val finalError = pendingAssistantError
                pendingAssistantError = null
                if (finalError != null) {
                    onFailure(PiFailure.Command("prompt", finalError))
                    update {
                        it.copy(
                            execution = it.execution.finishPendingExecution(),
                            agent = it.agent.copy(streaming = false, steeringMessages = emptyList()),
                        )
                    }
                } else {
                    update {
                        it.copy(
                            turn = if (it.turn == TurnState.FAILED) TurnState.FAILED else TurnState.COMPLETED,
                            execution = it.execution.finishPendingExecution(),
                            agent = it.agent.copy(streaming = false, steeringMessages = emptyList()),
                        )
                    }
                }
                refresh()
                catalog.refresh()
            }
            is PiStreamEvent.AssistantError -> {
                // Pi may immediately auto-retry this provider error. Defer UI
                // failure until auto_retry_end or agent_settled confirms it is final.
                pendingAssistantError = event.message
            }
            is PiStreamEvent.AutoRetryStarted -> {
                pendingAssistantError = null
                update { it.copy(turn = TurnState.QUEUED, failure = null) }
            }
            is PiStreamEvent.AutoRetryEnded -> {
                pendingAssistantError = null
                if (!event.success) {
                    onFailure(PiFailure.Command("prompt", event.finalError ?: "重试 ${event.attempt} 次后仍然失败"))
                }
            }
            PiStreamEvent.Ignored -> Unit
            is PiStreamEvent.Phase -> update { it.copy(turn = event.turn) }
            is PiStreamEvent.Failed -> {
                pendingAssistantError = null
                onFailure(PiFailure.Command("prompt", event.message))
            }
            is PiStreamEvent.QueueUpdated -> update { current ->
                current.copy(agent = current.agent.copy(steeringMessages = event.steering))
            }
            is PiStreamEvent.UserMessageStarted -> update { current ->
                val last = current.messages.lastOrNull()
                if (last?.role == PiMessageRole.USER && last.text == event.text && last.images == event.images) {
                    current
                } else {
                    current.copy(
                        messages = current.messages + PiMessage(
                            UUID.randomUUID().toString(),
                            PiMessageRole.USER,
                            event.text,
                            event.images,
                        ),
                        failure = null,
                    )
                }
            }
            is PiStreamEvent.CustomMessageStarted -> update { current ->
                current.copy(
                    messages = current.messages + PiMessage(
                        UUID.randomUUID().toString(),
                        PiMessageRole.ASSISTANT,
                        event.text,
                        event.images,
                    ),
                    failure = null,
                )
            }
            PiStreamEvent.ThinkingStarted -> update { it.copy(execution = it.execution + PiThinkingState(UUID.randomUUID().toString())) }
            is PiStreamEvent.ThinkingDelta -> update { current ->
                val index = current.execution.indexOfLast { it is PiThinkingState && !it.finished }
                val execution = if (index >= 0) current.execution.mapIndexed { itemIndex, item ->
                    if (itemIndex == index) (item as PiThinkingState).copy(text = item.text + event.text) else item
                } else current.execution + PiThinkingState(UUID.randomUUID().toString(), event.text)
                current.copy(execution = execution)
            }
            PiStreamEvent.ThinkingEnded -> update { current ->
                val index = current.execution.indexOfLast { it is PiThinkingState && !it.finished }
                if (index < 0) current else current.copy(execution = current.execution.mapIndexed { itemIndex, item ->
                    if (itemIndex == index) (item as PiThinkingState).copy(finished = true) else item
                })
            }
            is PiStreamEvent.TextDelta -> update { current ->
                val last = current.messages.lastOrNull()
                val messages = if (last?.role == PiMessageRole.ASSISTANT) {
                    current.messages.dropLast(1) + last.copy(text = last.text + event.text)
                } else current.messages + PiMessage(UUID.randomUUID().toString(), PiMessageRole.ASSISTANT, event.text)
                current.copy(messages = messages, turn = TurnState.GENERATING)
            }
            is PiStreamEvent.ToolStarted -> update {
                it.copy(turn = TurnState.RUNNING_TOOL, execution = it.execution + PiToolState(event.id, event.name, event.summary, event.input))
            }
            is PiStreamEvent.ToolUpdated -> update { current ->
                current.copy(execution = current.execution.map { item ->
                    if (item is PiToolState && item.id == event.id) {
                        item.copy(output = event.output, images = event.images.ifEmpty { item.images })
                    } else item
                })
            }
            is PiStreamEvent.ToolEnded -> update { current ->
                val found = current.execution.any { it is PiToolState && it.id == event.id }
                val completed = current.execution.map { item ->
                    if (item is PiToolState && item.id == event.id) {
                        item.copy(
                            output = event.output.ifBlank { item.output },
                            images = event.images.ifEmpty { item.images },
                            finished = true,
                            failed = event.failed,
                        )
                    } else item
                }
                current.copy(
                    turn = if (event.failed) TurnState.FAILED else TurnState.GENERATING,
                    execution = if (found) completed else completed + PiToolState(
                        event.id,
                        event.name,
                        event.name,
                        output = event.output,
                        images = event.images,
                        finished = true,
                        failed = event.failed,
                    ),
                    messages = if (event.name != SEND_IMAGE_TOOL_NAME || event.images.isEmpty()) current.messages else {
                        val imageMessageId = "tool-image:${event.id}"
                        current.messages.filterNot { it.id == imageMessageId } + PiMessage(
                            id = imageMessageId,
                            role = PiMessageRole.ASSISTANT,
                            text = "",
                            images = event.images,
                        )
                    },
                )
            }
        }
    }

    private fun replace(replacement: PiSessionReplacement, callback: (Result<PiBranchResult?>) -> Unit) {
        if (replacement.cancelled) {
            callback(Result.success(null))
            return
        }
        val newKey = checkNotNull(replacement.sessionKey) { "Pi 没有返回新会话标识" }
        val agent = checkNotNull(replacement.agent) { "Pi 没有返回新会话状态" }
        val oldKey = hostKey
        val firstMessage = replacement.messages.firstOrNull { it.role == PiMessageRole.USER }?.text
        val newSession = PiSession(
            runtimeKey = newKey,
            id = agent.sessionId ?: newKey,
            name = "",
            path = agent.sessionFile,
            firstMessage = firstMessage,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            messageCount = replacement.messages.size,
        )
        hostKey = newKey
        session = newSession
        update {
            it.copy(
                process = ProcessState.RUNNING,
                turn = TurnState.IDLE,
                historyLoaded = true,
                agentLoaded = true,
                messages = replacement.messages,
                execution = emptyList(),
                agent = agent,
                failure = null,
            )
        }
        onIdentityChanged(this, oldKey, newSession)
        catalog.refresh()
        callback(Result.success(PiBranchResult(newSession, replacement.selectedText)))
    }

    private fun requestFailed(error: Throwable) {
        onFailure((error as? PiRequestException)?.failure ?: PiFailure.Protocol(error.message ?: "Pi 请求失败"))
    }

    private fun onFailure(failure: PiFailure) {
        update { current ->
            if (current.failure == failure) return@update current
            val actionable = current.turn in setOf(TurnState.QUEUED, TurnState.GENERATING, TurnState.RUNNING_TOOL, TurnState.COMPACTING, TurnState.STOPPING) || failure is PiFailure.Process
            current.copy(
                turn = if (actionable) TurnState.FAILED else current.turn,
                historyLoaded = true,
                failure = failure,
                messages = if (actionable) current.messages + PiMessage(UUID.randomUUID().toString(), PiMessageRole.SYSTEM, failure.message) else current.messages,
            )
        }
    }

    private fun update(transform: (PiSessionState) -> PiSessionState) {
        val before = summary()
        _state.update(transform)
        if (summary() != before) {
            onStateChanged(this)
            onActivityChanged()
        }
    }
}

private fun List<PiExecutionItem>.finishPendingExecution(): List<PiExecutionItem> = map { item ->
    when (item) {
        is PiThinkingState -> if (item.finished) item else item.copy(finished = true)
        is PiToolState -> if (item.finished) item else item.copy(finished = true)
    }
}

data class PiBranchResult(
    val session: PiSession,
    val selectedText: String?,
)
