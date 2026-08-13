package io.github.zixt233.pirt.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lists and stops workspace processes. Not tied to Pi session lifecycle. */
class ProcessManager(
    private val onActivityChanged: () -> Unit,
) {
    private val _forest = MutableStateFlow<List<HostProcessTreeNode>>(emptyList())
    val forest: StateFlow<List<HostProcessTreeNode>> = _forest.asStateFlow()

    fun refresh() {
        _forest.value = HostProcesses.forest()
    }

    fun kill(pid: Int): Result<Unit> {
        val result = HostProcesses.kill(pid)
        refresh()
        onActivityChanged()
        return result
    }

    fun workspaceCount(): Int = hostProcessForestWorkspaceCount(_forest.value)
}
