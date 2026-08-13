package io.github.zixt233.pirt.runtime

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PiAuthProcessState { STOPPED, STARTING, READY, FAILED }
enum class PiAuthActivity { STARTING, LOADING_PROVIDERS, LOADING_MODELS, LOGGING_IN, LOGGING_OUT, SELECTING_MODEL }

data class PiAuthState(
    val process: PiAuthProcessState = PiAuthProcessState.STOPPED,
    val providers: List<PiProvider> = emptyList(),
    val providersLoaded: Boolean = false,
    val models: List<PiModel> = emptyList(),
    val modelsRevision: Long = 0,
    val selectedProvider: String? = null,
    val selectedModel: String? = null,
    val prompt: PiAuthEvent.Prompt? = null,
    val notice: PiAuthEvent.Notice? = null,
    val selectionRevision: Long = 0,
    val error: String? = null,
    val activity: PiAuthActivity? = null,
    val activeLoginId: String? = null,
)

/** Service-owned authentication state and command boundary. */
class PiAuthManager(
    context: Context,
    workspace: io.github.zixt233.pirt.model.WorkspaceConfig,
    private val onActivityChanged: () -> Unit,
    sessionListener: (String, io.github.zixt233.pirt.runtime.pi.PiStreamEvent) -> Unit = { _, _ -> },
    sessionFailure: (String) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val runtime = PRootRuntime(appContext)
    private val _state = MutableStateFlow(PiAuthState())
    val state: StateFlow<PiAuthState> = _state.asStateFlow()

    val control = PiControlClient(
        processSpec = runtime.piAgentHostProcess(workspace),
        listener = ::accept,
        sessionListener = sessionListener,
        sessionFailure = sessionFailure,
        diagnostic = { message, error ->
            if (error == null) RuntimeDiagnostics.info(appContext, "control", message)
            else RuntimeDiagnostics.error(appContext, "control", message, error)
        },
    )
    @Volatile private var client: PiControlClient? = null
    @Volatile private var modelAfterLogin: String? = null
    @Volatile private var activeLoginId: String? = null

    @Synchronized
    fun start() {
        if (client != null) return
        _state.update { it.copy(process = PiAuthProcessState.STARTING, activity = PiAuthActivity.STARTING, error = null) }
        runCatching {
            client = control
            control.start()
            if (control.isReady()) {
                _state.update { it.copy(process = PiAuthProcessState.READY, activity = PiAuthActivity.LOADING_PROVIDERS) }
                control.loadProviders()
            }
            onActivityChanged()
        }.onFailure { error ->
            client = null
            fail(error.message ?: "认证进程启动失败", error)
            onActivityChanged()
        }
    }

    fun loadProviders() {
        _state.update { it.copy(activity = PiAuthActivity.LOADING_PROVIDERS, error = null) }
        requireClient().loadProviders()
    }
    fun loadModels(providerId: String? = null) {
        _state.update { it.copy(activity = PiAuthActivity.LOADING_MODELS, error = null) }
        requireClient().loadModels(providerId)
    }
    fun login(providerId: String, authType: String, loginMethod: String? = null) {
        // 旧登录由 bridge 在新 login 开头 abortAllLogins 清理；这里不要先 cancel，
        // 否则旧 login 的失败回包会把新登录状态清掉。
        if (!prepareNetworkForLogin()) return
        modelAfterLogin = providerId
        val loginId = requireClient().login(providerId, authType, loginMethod)
        activeLoginId = loginId
        _state.update {
            it.copy(
                prompt = null,
                notice = null,
                activity = PiAuthActivity.LOGGING_IN,
                error = null,
                activeLoginId = loginId,
            )
        }
    }
    fun configureCustomProvider(
        name: String,
        baseUrl: String,
        apiKey: String,
        models: List<String> = emptyList(),
        providerId: String? = null,
    ) {
        if (!prepareNetworkForLogin()) return
        val id = sanitizeProviderId(providerId?.takeIf { it.isNotBlank() } ?: name)
        modelAfterLogin = id
        _state.update { it.copy(prompt = null, notice = null, activity = PiAuthActivity.LOGGING_IN, error = null) }
        requireClient().configureCustomProvider(name, baseUrl, apiKey, models, id)
    }
    fun removeCustomProvider(providerId: String) {
        _state.update { it.copy(activity = PiAuthActivity.LOGGING_OUT, error = null) }
        requireClient().removeCustomProvider(providerId)
    }
    fun logout(providerId: String) {
        _state.update { it.copy(activity = PiAuthActivity.LOGGING_OUT, error = null) }
        requireClient().logout(providerId)
    }
    fun selectModel(providerId: String, modelId: String) {
        _state.update { it.copy(activity = PiAuthActivity.SELECTING_MODEL, error = null) }
        requireClient().selectModel(providerId, modelId)
    }
    fun answerPrompt(promptId: String, value: String) {
        _state.update { it.copy(prompt = null, activity = PiAuthActivity.LOGGING_IN, error = null) }
        requireClient().answerPrompt(promptId, value)
    }
    fun cancelLogin(loginId: String) {
        modelAfterLogin = null
        activeLoginId = null
        _state.update { it.copy(prompt = null, notice = null, activity = null, error = null, activeLoginId = null) }
        runCatching { requireClient().cancelLogin(loginId) }
    }
    fun cancelActiveLogin() {
        val id = activeLoginId ?: _state.value.activeLoginId ?: return
        cancelLogin(id)
    }
    fun dismissNotice() = _state.update { it.copy(notice = null) }

    @Synchronized fun activeCount(): Int = if (client == null) 0 else 1

    @Synchronized
    fun close() {
        control.close()
        client = null
        modelAfterLogin = null
        activeLoginId = null
        _state.value = PiAuthState()
        onActivityChanged()
    }

    private fun requireClient(): PiControlClient = checkNotNull(client) { "Pi 认证进程尚未启动" }

    private fun sanitizeProviderId(value: String): String {
        val id = value.trim().lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-')
            .take(48)
        return id.ifBlank { "custom" }
    }

    private fun clearLoginTracking() {
        modelAfterLogin = null
        activeLoginId = null
    }

    private fun prepareNetworkForLogin(): Boolean {
        val error = runtime.refreshNetworkConfiguration().exceptionOrNull() ?: return true
        clearLoginTracking()
        _state.update {
            it.copy(
                prompt = null,
                notice = null,
                activity = null,
                error = "无法为 Linux 环境配置网络：${error.message ?: "未知 DNS 错误"}",
                activeLoginId = null,
            )
        }
        RuntimeDiagnostics.error(appContext, "auth", "Could not configure guest DNS", error)
        return false
    }

    private fun accept(event: PiAuthEvent) {
        when (event) {
            PiAuthEvent.Ready -> {
                val authRequested = client != null
                _state.update {
                    it.copy(
                        process = PiAuthProcessState.READY,
                        activity = if (authRequested) PiAuthActivity.LOADING_PROVIDERS else null,
                        error = null,
                    )
                }
                if (authRequested) control.loadProviders()
            }
            is PiAuthEvent.Providers -> {
                val pendingModel = modelAfterLogin
                clearLoginTracking()
                _state.update {
                    it.copy(
                        providers = event.values,
                        providersLoaded = true,
                        prompt = null,
                        notice = null,
                        activity = null,
                        error = null,
                        selectedProvider = event.selectedProvider,
                        selectedModel = event.selectedModel,
                        activeLoginId = null,
                    )
                }
                pendingModel?.takeIf { providerId ->
                    event.values.any { it.id == providerId && it.configured }
                }?.let { providerId ->
                    _state.update { it.copy(activity = PiAuthActivity.LOADING_MODELS) }
                    client?.loadModels(providerId)
                }
            }
            is PiAuthEvent.Models -> _state.update {
                it.copy(
                    models = event.values,
                    modelsRevision = it.modelsRevision + 1,
                    prompt = null,
                    notice = null,
                    activity = null,
                    selectedProvider = event.selectedProvider,
                    selectedModel = event.selectedModel,
                    error = null,
                )
            }
            // 保持 LOGGING_IN，避免弹窗期间按钮可再次点登录，叠出第二次 Codex auth。
            is PiAuthEvent.Prompt -> {
                val currentLogin = activeLoginId ?: _state.value.activeLoginId
                if (currentLogin == event.loginId) {
                    _state.update {
                        it.copy(prompt = event, activity = PiAuthActivity.LOGGING_IN, error = null)
                    }
                }
            }
            is PiAuthEvent.Notice -> {
                val currentLogin = activeLoginId ?: _state.value.activeLoginId
                if (currentLogin == event.loginId) {
                    _state.update {
                        it.copy(notice = event, activity = PiAuthActivity.LOGGING_IN, error = null)
                    }
                }
            }
            is PiAuthEvent.Selected -> _state.update {
                it.copy(
                    selectedProvider = event.providerId,
                    selectedModel = event.modelId,
                    selectionRevision = it.selectionRevision + 1,
                    prompt = null,
                    notice = null,
                    activity = null,
                    error = null,
                )
            }
            is PiAuthEvent.Error -> {
                // 被新登录顶替的旧 login 失败回包，不能清掉当前登录态。
                val currentLogin = activeLoginId ?: _state.value.activeLoginId
                if (
                    event.requestId != null &&
                    currentLogin != null &&
                    event.requestId != currentLogin
                ) {
                    return
                }
                clearLoginTracking()
                _state.update {
                    it.copy(prompt = null, notice = null, activity = null, error = event.message, activeLoginId = null)
                }
            }
            is PiAuthEvent.ProcessFailed -> {
                synchronized(this) {
                    client = null
                }
                clearLoginTracking()
                fail(event.message)
                onActivityChanged()
            }
        }
    }

    private fun fail(message: String, error: Throwable? = null) {
        _state.update { it.copy(process = PiAuthProcessState.FAILED, providersLoaded = true, activity = null, error = message) }
        if (error == null) RuntimeDiagnostics.info(appContext, "auth", message)
        else RuntimeDiagnostics.error(appContext, "auth", message, error)
    }
}
