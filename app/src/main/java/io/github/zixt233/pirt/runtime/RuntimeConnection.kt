package io.github.zixt233.pirt.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import io.github.zixt233.pirt.runtime.pi.PiSessionManager
import io.github.zixt233.pirt.runtime.pi.PiSessionSummary
import io.github.zixt233.pirt.model.PiSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Activity-independent gateway to the process owners held by RuntimeService. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RuntimeConnection(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val binder = MutableStateFlow<RuntimeService.RuntimeBinder?>(null)
    private var bound = false

    val manager: StateFlow<PiSessionManager?> = binder
        .map { it?.sessions }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val auth: StateFlow<PiAuthManager?> = binder
        .map { it?.auth }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val authState: StateFlow<PiAuthState> = binder
        .flatMapLatest { it?.auth?.state ?: flowOf(PiAuthState()) }
        .stateIn(scope, SharingStarted.Eagerly, PiAuthState())

    val terminal: StateFlow<TerminalManager?> = binder
        .map { it?.terminal }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val terminalState: StateFlow<TerminalState> = binder
        .flatMapLatest { it?.terminal?.state ?: flowOf(TerminalState()) }
        .stateIn(scope, SharingStarted.Eagerly, TerminalState())

    val graphics: StateFlow<GraphicsManager?> = binder
        .map { it?.graphics }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val graphicsState: StateFlow<GraphicsState> = binder
        .flatMapLatest { it?.graphics?.state ?: flowOf(GraphicsState.Stopped) }
        .stateIn(scope, SharingStarted.Eagerly, GraphicsState.Stopped)

    val summaries: StateFlow<Map<String, PiSessionSummary>> = binder
        .flatMapLatest { it?.sessions?.summaries ?: flowOf(emptyMap()) }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val sessions: StateFlow<List<PiSession>> = binder
        .flatMapLatest { it?.catalog?.sessions ?: flowOf(emptyList()) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val sessionsLoaded: StateFlow<Boolean> = binder
        .flatMapLatest { it?.catalog?.loaded ?: flowOf(false) }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val processes: StateFlow<ProcessManager?> = binder
        .map { it?.processes }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val processForest: StateFlow<List<HostProcessTreeNode>> = binder
        .flatMapLatest { it?.processes?.forest ?: flowOf(emptyList()) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun refreshSessions() = binder.value?.catalog?.refresh()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder.value = service as? RuntimeService.RuntimeBinder
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder.value = null
        }
    }

    @Synchronized
    fun connect() {
        if (bound) return
        RuntimeService.start(appContext)
        bound = appContext.bindService(Intent(appContext, RuntimeService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    @Synchronized
    fun disconnect() {
        if (bound) appContext.unbindService(connection)
        bound = false
        binder.value = null
    }

    @Synchronized
    override fun close() {
        disconnect()
        scope.cancel()
    }
}
