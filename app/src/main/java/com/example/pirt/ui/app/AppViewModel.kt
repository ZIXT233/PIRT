package com.example.pirt.ui.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.pirt.model.PiSession
import com.example.pirt.runtime.PRootRuntime
import com.example.pirt.runtime.RuntimeConnection
import com.example.pirt.model.WorkspaceConfig
import java.io.File
import java.util.UUID

/** Holds the fixed workspace and connects UI to Pi-owned runtime state. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    val workspace = WorkspaceConfig(File(application.filesDir, "pirt/workspace").apply { mkdirs() }.absolutePath)
    val runtime = PRootRuntime(application)
    val runtimeConnection = RuntimeConnection(application)

    /** Not a session id: it only keeps the new Pi process addressable until get_state returns Pi's id. */
    fun newSession() = PiSession(
        runtimeKey = "draft:${UUID.randomUUID()}",
        name = "",
    )

    fun renameSession(session: PiSession, name: String) = runtimeConnection.manager.value?.rename(session, name)

    fun deleteSession(session: PiSession) = runtimeConnection.manager.value?.delete(session)

    override fun onCleared() {
        runtimeConnection.close()
    }
}
