package io.github.zixt233.pirt.ui.app

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import io.github.zixt233.pirt.model.PiSession
import io.github.zixt233.pirt.runtime.PRootRuntime
import io.github.zixt233.pirt.runtime.RuntimeConnection
import io.github.zixt233.pirt.model.WorkspaceConfig
import java.io.File
import java.util.UUID

internal data class PendingConversation(val session: PiSession, val piId: String? = null)

/** Conversation navigation state must outlive Activity recreation during rotation. */
internal class ConversationUiState(initialDraft: PiSession) {
    val newConversation = mutableStateOf(initialDraft)
    val selectedSessionId = mutableStateOf<String?>(null)
    val newConversationText = mutableStateOf("")
    val newConversationPiId = mutableStateOf<String?>(null)
    val pendingConversations = mutableStateListOf<PendingConversation>()
    val drafts = mutableStateMapOf<String, String>()
}

/** Holds the fixed workspace and connects UI to Pi-owned runtime state. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    val workspace = WorkspaceConfig(File(application.filesDir, "pirt/workspace").apply { mkdirs() }.absolutePath)
    val runtime = PRootRuntime(application)
    val runtimeConnection = RuntimeConnection(application)
    internal val conversationUi = ConversationUiState(newSession())

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
