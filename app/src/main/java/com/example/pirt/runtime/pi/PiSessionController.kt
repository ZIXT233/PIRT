package com.example.pirt.runtime.pi

import com.example.pirt.model.PiSession
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
    fun requestThinkingLevels() = request(PiRequest.GetThinkingLevels) { result ->
        result.onSuccess { values -> update { it.copy(thinkingLevels = values, thinkingLevelsRevision = it.thinkingLevelsRevision + 1, failure = null) } }
            .onFailure(::requestFailed)
    }
    fun setModel(provider: String, modelId: String) = request(PiRequest.SetModel(provider, modelId)) { result ->
        result.onSuccess { model ->
            update { current -> current.copy(agent = current.agent.copy(provider = model.provider, modelId = model.id, modelName = model.name), failure = null) }
        }.onFailure(::requestFailed)
    }
    fun setThinkingLevel(level: String) = request(PiRequest.SetThinkingLevel(level)) { result ->
        result.onSuccess { update { current -> current.copy(agent = current.agent.copy(thinkingLevel = level), failure = null) } }
            .onFailure(::requestFailed)
    }
    fun compact() = request(PiRequest.Compact) { result -> result.onFailure(::requestFailed) }
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

    fun busy(): Boolean = state.value.turn in setOf(TurnState.QUEUED, TurnState.GENERATING, TurnState.RUNNING_TOOL, TurnState.STOPPING)
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
                            agent.compacting || agent.streaming -> TurnState.GENERATING
                            agent.pendingMessageCount > 0 -> TurnState.QUEUED
                            else -> it.turn
                        },
                    )
                }
            }.onFailure(::requestFailed)
        }
        request(PiRequest.GetMessages) { result ->
            result.onSuccess { messages -> update { it.copy(messages = messages, historyLoaded = true) } }
                .onFailure(::requestFailed)
        }
    }

    private fun onEvent(event: PiStreamEvent) {
        when (event) {
            PiStreamEvent.AgentStarted -> update {
                it.copy(turn = TurnState.GENERATING, agent = it.agent.copy(streaming = true), failure = null)
            }
            PiStreamEvent.AgentSettled -> {
                update {
                    it.copy(
                        turn = if (it.turn == TurnState.FAILED) TurnState.FAILED else TurnState.COMPLETED,
                        agent = it.agent.copy(streaming = false, steeringMessages = emptyList()),
                    )
                }
                refresh()
                catalog.refresh()
            }
            PiStreamEvent.Ignored -> Unit
            is PiStreamEvent.Phase -> update { it.copy(turn = event.turn) }
            is PiStreamEvent.Failed -> onFailure(PiFailure.Command("prompt", event.message))
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
                    if (item is PiToolState && item.id == event.id) item.copy(output = event.output) else item
                })
            }
            is PiStreamEvent.ToolEnded -> update { current ->
                val found = current.execution.any { it is PiToolState && it.id == event.id }
                val completed = current.execution.map { item ->
                    if (item is PiToolState && item.id == event.id) item.copy(output = event.output.ifBlank { item.output }, finished = true, failed = event.failed) else item
                }
                current.copy(
                    turn = if (event.failed) TurnState.FAILED else TurnState.GENERATING,
                    execution = if (found) completed else completed + PiToolState(event.id, event.name, event.name, output = event.output, finished = true, failed = event.failed),
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
            val actionable = current.turn in setOf(TurnState.QUEUED, TurnState.GENERATING, TurnState.RUNNING_TOOL, TurnState.STOPPING) || failure is PiFailure.Process
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

data class PiBranchResult(
    val session: PiSession,
    val selectedText: String?,
)
