package io.github.zixt233.pirt.ui.app

import io.github.zixt233.pirt.model.PiSession
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationUiStateTest {
    @Test
    fun retainsPendingSelectionWhilePiSessionIsNotYetPersisted() {
        val draft = PiSession(runtimeKey = "draft:one", name = "")
        val state = ConversationUiState(draft)
        val pending = PendingConversation(
            session = draft.copy(firstMessage = "hello"),
            piId = "pi-session-id",
        )

        state.selectedSessionId.value = draft.runtimeKey
        state.pendingConversations += pending

        assertEquals("draft:one", state.selectedSessionId.value)
        assertEquals(pending, state.pendingConversations.single())
    }
}
