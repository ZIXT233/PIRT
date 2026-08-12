package com.example.pirt.runtime.pi

import com.example.pirt.model.PiSession
import com.example.pirt.runtime.PiControlClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OverlayChatSnapshot(
    val title: String,
    val reply: String?,
    val status: String,
    val canSend: Boolean,
)

/** Projects the sessions owned by the single resident Pi SDK host. */
class PiSessionManager(
    private val client: PiControlClient,
    private val catalog: PiSessionCatalog,
    private val onActivityChanged: () -> Unit,
) {
    private val sessions = LinkedHashMap<String, PiSessionController>()
    private var selected: PiSessionController? = null
    private val _summaries = MutableStateFlow<Map<String, PiSessionSummary>>(emptyMap())
    val summaries: StateFlow<Map<String, PiSessionSummary>> = _summaries.asStateFlow()

    @Synchronized
    fun select(session: PiSession): StateFlow<PiSessionState> {
        val controller = find(session.runtimeKey, session.id) ?: PiSessionController(
            session = session,
            request = { key, request, callback -> client.request(key, request, callback) },
            catalog = catalog,
            onIdentityChanged = ::controllerIdentityChanged,
            onStateChanged = ::controllerChanged,
            onActivityChanged = onActivityChanged,
        ).also { sessions[session.runtimeKey] = it }
        controller.adopt(session)
        selected = controller
        controller.open()
        publish()
        return controller.state
    }

    fun open(session: PiSession): StateFlow<PiSessionState> = select(session)

    @Synchronized fun state(sessionId: String): StateFlow<PiSessionState>? = controller(sessionId)?.state

    @Synchronized
    fun prompt(sessionId: String, message: String, images: List<PiImage> = emptyList()) {
        require(message.isNotBlank() || images.isNotEmpty()) { "消息不能为空" }
        val controller = checkNotNull(controller(sessionId)) { "Pi 会话尚未打开" }
        check(controller === selected) { "只能向当前会话发送消息" }
        controller.prompt(message, images)
    }

    @Synchronized
    fun steer(sessionId: String, message: String, images: List<PiImage> = emptyList()) {
        require(message.isNotBlank() || images.isNotEmpty()) { "消息不能为空" }
        val controller = checkNotNull(controller(sessionId)) { "Pi 会话尚未打开" }
        check(controller === selected) { "只能引导当前会话" }
        controller.steer(message, images)
    }

    @Synchronized fun abort(sessionId: String) = controller(sessionId)?.abort()
    @Synchronized fun requestModels(sessionId: String) = controller(sessionId)?.requestModels()
    @Synchronized fun requestCommands(sessionId: String) = controller(sessionId)?.requestCommands()
    @Synchronized fun requestThinkingLevels(sessionId: String) = controller(sessionId)?.requestThinkingLevels()
    @Synchronized fun setModel(sessionId: String, provider: String, modelId: String) = controller(sessionId)?.setModel(provider, modelId)
    @Synchronized fun setThinkingLevel(sessionId: String, level: String) = controller(sessionId)?.setThinkingLevel(level)
    @Synchronized fun compact(sessionId: String) = controller(sessionId)?.compact()
    @Synchronized fun setAutoCompaction(sessionId: String, enabled: Boolean) = controller(sessionId)?.setAutoCompaction(enabled)
    @Synchronized fun setAutoRetry(sessionId: String, enabled: Boolean) = controller(sessionId)?.setAutoRetry(enabled)
    @Synchronized fun fork(sessionId: String, entryId: String, callback: (Result<PiBranchResult?>) -> Unit) =
        checkNotNull(controller(sessionId)) { "Pi 会话尚未打开" }.also {
            check(it === selected) { "只能 Fork 当前会话" }
        }.fork(entryId, callback)
    @Synchronized fun cloneSession(sessionId: String, callback: (Result<PiBranchResult?>) -> Unit) =
        checkNotNull(controller(sessionId)) { "Pi 会话尚未打开" }.also {
            check(it === selected) { "只能克隆当前会话" }
        }.cloneSession(callback)

    @Synchronized
    fun rename(session: PiSession, name: String) {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "会话名称不能为空" }
        controller(session.runtimeKey)?.rename(normalized) ?: catalog.rename(session, normalized)
    }

    @Synchronized
    fun delete(session: PiSession) {
        if (controller(session.runtimeKey) === selected) selected = null
        sessions.entries.removeAll { it.value.matches(session.runtimeKey) }
        catalog.delete(session)
        publish()
    }

    @Synchronized fun activeCount(): Int = if (sessions.isEmpty()) 0 else 1
    @Synchronized fun busyCount(): Int = sessions.values.count(PiSessionController::busy)
    @Synchronized fun hasBusySession(): Boolean = sessions.values.any(PiSessionController::busy)

    @Synchronized
    fun overlaySnapshot(): OverlayChatSnapshot? {
        val controller = selected ?: return null
        val state = controller.state.value
        val reply = state.messages.lastOrNull { it.role == PiMessageRole.ASSISTANT }?.text
        return OverlayChatSnapshot(
            title = controller.sessionDisplayName(),
            reply = reply?.ifBlank { null },
            status = overlayStatus(state),
            canSend = state.process == ProcessState.RUNNING && state.agentLoaded,
        )
    }

    @Synchronized
    fun overlayPrompt(message: String) {
        val normalized = message.trim()
        require(normalized.isNotEmpty()) { "消息不能为空" }
        val controller = checkNotNull(selected) { "请先在应用内打开一个会话" }
        prompt(controller.runtimeKey(), normalized)
    }

    @Synchronized
    fun accept(sessionKey: String, event: PiStreamEvent) {
        controller(sessionKey)?.event(event)
    }

    @Synchronized
    fun failAll(message: String) {
        sessions.values.forEach { it.hostFailed(PiFailure.Process(message)) }
        publish()
    }

    @Synchronized
    fun shutdown() {
        selected = null
        sessions.clear()
        publish()
    }

    private fun controllerChanged(@Suppress("UNUSED_PARAMETER") controller: PiSessionController) {
        synchronized(this) { publish() }
    }

    private fun controllerIdentityChanged(controller: PiSessionController, oldKey: String, session: PiSession) {
        synchronized(this) {
            sessions.entries.removeAll { it.key == oldKey || it.value === controller }
            sessions[session.runtimeKey] = controller
            selected = controller
            publish()
        }
    }

    private fun publish() {
        _summaries.value = buildMap {
            sessions.forEach { (key, controller) ->
                put(key, controller.summary())
                controller.piSessionId()?.let { put(it, controller.summary()) }
            }
        }
    }

    private fun controller(key: String): PiSessionController? = sessions[key] ?: sessions.values.firstOrNull { it.matches(key) }
    private fun find(runtimeKey: String, piId: String?): PiSessionController? =
        controller(runtimeKey) ?: piId?.let(::controller)

    private fun overlayStatus(state: PiSessionState): String = when {
        state.failure != null -> state.failure.message
        state.process != ProcessState.RUNNING -> "Pi 未就绪"
        state.turn == TurnState.GENERATING || state.agent.streaming -> "正在回复…"
        state.turn == TurnState.RUNNING_TOOL -> "正在执行工具…"
        state.turn == TurnState.QUEUED -> "等待发送…"
        state.turn == TurnState.STOPPING -> "正在停止…"
        else -> "就绪"
    }
}
