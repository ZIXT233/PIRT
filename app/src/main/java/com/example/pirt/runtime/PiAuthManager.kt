package com.example.pirt.runtime

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
)

/** Service-owned authentication state and command boundary. */
class PiAuthManager(
    context: Context,
    workspace: com.example.pirt.model.WorkspaceConfig,
    private val onActivityChanged: () -> Unit,
    sessionListener: (String, com.example.pirt.runtime.pi.PiStreamEvent) -> Unit = { _, _ -> },
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
    fun login(providerId: String, authType: String) {
        modelAfterLogin = providerId
        _state.update { it.copy(prompt = null, notice = null, activity = PiAuthActivity.LOGGING_IN, error = null) }
        requireClient().login(providerId, authType)
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
        _state.update { it.copy(prompt = null, activity = PiAuthActivity.LOGGING_IN) }
        requireClient().answerPrompt(promptId, value)
    }
    fun cancelLogin(loginId: String) {
        _state.update { it.copy(prompt = null, notice = null, activity = null) }
        requireClient().cancelLogin(loginId)
    }
    fun dismissNotice() = _state.update { it.copy(notice = null) }

    @Synchronized fun activeCount(): Int = if (client == null) 0 else 1

    @Synchronized
    fun close() {
        control.close()
        client = null
        modelAfterLogin = null
        _state.value = PiAuthState()
        onActivityChanged()
    }

    private fun requireClient(): PiControlClient = checkNotNull(client) { "Pi 认证进程尚未启动" }

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
                _state.update {
                    it.copy(
                        providers = event.values,
                        providersLoaded = true,
                        activity = null,
                        error = null,
                        selectedProvider = event.selectedProvider,
                        selectedModel = event.selectedModel,
                    )
                }
                modelAfterLogin?.takeIf { providerId ->
                    event.values.any { it.id == providerId && it.configured }
                }?.let { providerId ->
                    modelAfterLogin = null
                    _state.update { it.copy(activity = PiAuthActivity.LOADING_MODELS) }
                    client?.loadModels(providerId)
                }
            }
            is PiAuthEvent.Models -> _state.update {
                it.copy(
                    models = event.values,
                    modelsRevision = it.modelsRevision + 1,
                    activity = null,
                    selectedProvider = event.selectedProvider,
                    selectedModel = event.selectedModel,
                    error = null,
                )
            }
            is PiAuthEvent.Prompt -> _state.update { it.copy(prompt = event, activity = null, error = null) }
            is PiAuthEvent.Notice -> _state.update { it.copy(notice = event, activity = PiAuthActivity.LOGGING_IN, error = null) }
            is PiAuthEvent.Selected -> _state.update {
                it.copy(
                    selectedProvider = event.providerId,
                    selectedModel = event.modelId,
                    selectionRevision = it.selectionRevision + 1,
                    activity = null,
                    error = null,
                )
            }
            is PiAuthEvent.Error -> _state.update { it.copy(activity = null, error = event.message) }
            is PiAuthEvent.ProcessFailed -> {
                synchronized(this) {
                    client = null
                }
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
