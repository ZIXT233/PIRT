package io.github.zixt233.pirt.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceModelsTest {
    @Test
    fun emptySessionTitleRemainsPresentationNeutral() {
        val session = PiSession(runtimeKey = "draft", name = "")

        assertEquals("", session.displayName)
    }

    @Test
    fun firstMessageIsUsedWhenSessionHasNoTitle() {
        val session = PiSession(runtimeKey = "session", name = "", firstMessage = "Build an app")

        assertEquals("Build an app", session.displayName)
    }
}
