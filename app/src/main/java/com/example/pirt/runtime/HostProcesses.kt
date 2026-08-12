package com.example.pirt.runtime

import android.os.Process
import java.io.File

enum class HostProcessKind {
    APP,
    PI_RUNTIME,
    WORKSPACE,
}

data class HostProcess(
    val pid: Int,
    val ppid: Int,
    val name: String,
    val command: String,
    val kind: HostProcessKind,
) {
    val label: String get() = hostProcessLabel(name, command)
    val independent: Boolean get() = ppid <= 1
    val stoppable: Boolean get() = kind == HostProcessKind.WORKSPACE
}

data class HostProcessTreeNode(
    val process: HostProcess,
    val children: List<HostProcessTreeNode> = emptyList(),
) {
    val hasChildren: Boolean get() = children.isNotEmpty()
}

data class HostProcessTreeEntry(
    val process: HostProcess,
    val depth: Int,
)

private val hostProcessSortKey = compareBy<HostProcess>({ it.kind.ordinal }, { it.independent }, { it.label }, { it.pid })

internal fun buildHostProcessForest(processes: List<HostProcess>): List<HostProcessTreeNode> {
    if (processes.isEmpty()) return emptyList()
    val byPid = processes.associateBy { it.pid }
    val childProcesses = mutableMapOf<Int, MutableList<HostProcess>>()
    val roots = mutableListOf<HostProcess>()
    for (process in processes) {
        val parent = byPid[process.ppid]
        if (parent != null && parent.pid != process.pid) {
            childProcesses.getOrPut(parent.pid) { mutableListOf() }.add(process)
        } else {
            roots.add(process)
        }
    }
    roots.sortWith(hostProcessSortKey)
    childProcesses.values.forEach { values -> values.sortWith(hostProcessSortKey) }
    val visited = mutableSetOf<Int>()
    fun toNode(process: HostProcess): HostProcessTreeNode {
        if (!visited.add(process.pid)) return HostProcessTreeNode(process)
        val children = childProcesses[process.pid].orEmpty().map(::toNode)
        return HostProcessTreeNode(process, children)
    }
    val forest = roots.map(::toNode).toMutableList()
    processes.filter { it.pid !in visited }.sortedWith(hostProcessSortKey).forEach { forest.add(toNode(it)) }
    return forest
}

internal fun flattenHostProcessTree(forest: List<HostProcessTreeNode>): List<HostProcessTreeEntry> {
    val result = mutableListOf<HostProcessTreeEntry>()
    fun walk(node: HostProcessTreeNode, depth: Int) {
        result.add(HostProcessTreeEntry(node.process, depth))
        node.children.forEach { walk(it, depth + 1) }
    }
    forest.forEach { walk(it, 0) }
    return result
}

internal fun flattenVisibleHostProcessForest(
    forest: List<HostProcessTreeNode>,
    collapsed: Set<Int>,
): List<HostProcessTreeEntry> {
    val result = mutableListOf<HostProcessTreeEntry>()
    fun walk(node: HostProcessTreeNode, depth: Int) {
        result.add(HostProcessTreeEntry(node.process, depth))
        if (node.process.pid !in collapsed) {
            node.children.forEach { walk(it, depth + 1) }
        }
    }
    forest.forEach { walk(it, 0) }
    return result
}

internal fun collectHostProcessForestPids(forest: List<HostProcessTreeNode>): Set<Int> {
    val ids = mutableSetOf<Int>()
    fun walk(node: HostProcessTreeNode) {
        ids.add(node.process.pid)
        node.children.forEach(::walk)
    }
    forest.forEach(::walk)
    return ids
}

internal fun hostProcessForestWorkspaceCount(forest: List<HostProcessTreeNode>): Int {
    fun walk(node: HostProcessTreeNode): Int {
        val self = if (node.process.kind == HostProcessKind.WORKSPACE) 1 else 0
        return self + node.children.sumOf(::walk)
    }
    return forest.sumOf(::walk)
}

internal fun hostProcessTreePrefix(depth: Int): String = when {
    depth <= 0 -> ""
    else -> "  ".repeat(depth - 1) + "└ "
}

internal fun hostProcessLabel(name: String, command: String): String = when {
    command.contains("pirt-control-bridge") -> "Pi"
    command.contains("PowerNukkitX", ignoreCase = true) -> "PowerNukkitX"
    command.contains("libproot_exec") -> "PRoot"
    command == "com.example.pirt" || name.contains("example.pirt") -> "PIRT"
    command.startsWith("java ") -> "Java"
    command.startsWith("python") -> "Python"
    name == "bash" || command.startsWith("bash ") -> "Shell"
    name == "tail" -> "日志跟踪"
    name == "sleep" -> "Sleep"
    else -> name.ifBlank { command.substringBefore(' ').substringAfterLast('/') }.ifBlank { "进程" }
}

internal fun parseProcRealUid(status: String): Int? =
    status.lineSequence()
        .firstOrNull { it.startsWith("Uid:") }
        ?.substringAfter("Uid:")
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.firstOrNull()
        ?.toIntOrNull()

private fun procStatusValue(status: String, key: String): String? =
    status.lineSequence().firstOrNull { it.startsWith(key) }?.substringAfter(key)?.trim()

private fun classifyProcess(pid: Int, ppid: Int, name: String, command: String): HostProcessKind {
    if (pid == Process.myPid()) return HostProcessKind.APP
    if (command.contains("pirt-control-bridge") || command.contains("libproot_exec")) {
        return HostProcessKind.PI_RUNTIME
    }
    return HostProcessKind.WORKSPACE
}

object HostProcesses {
    fun list(): List<HostProcess> {
        val uid = Process.myUid()
        val proc = File("/proc")
        val entries = proc.listFiles { file -> file.name.all(Char::isDigit) } ?: return emptyList()
        return entries.mapNotNull { directory ->
            val pid = directory.name.toIntOrNull() ?: return@mapNotNull null
            val status = runCatching { File(directory, "status").readText() }.getOrNull() ?: return@mapNotNull null
            val processUid = parseProcRealUid(status) ?: return@mapNotNull null
            if (processUid != uid) return@mapNotNull null
            val command = runCatching {
                File(directory, "cmdline").readBytes().toString(Charsets.UTF_8).replace('\u0000', ' ').trim()
            }.getOrDefault("").ifBlank {
                procStatusValue(status, "Name:").orEmpty()
            }
            if (command.isBlank()) return@mapNotNull null
            val ppid = procStatusValue(status, "PPid:")?.toIntOrNull() ?: 0
            val nameValue = procStatusValue(status, "Name:").orEmpty()
            HostProcess(
                pid = pid,
                ppid = ppid,
                name = nameValue,
                command = command,
                kind = classifyProcess(pid, ppid, nameValue, command),
            )
        }.sortedWith(compareBy({ it.kind.ordinal }, { it.independent }, { it.label }, { it.pid }))
    }

    fun forest(): List<HostProcessTreeNode> = buildHostProcessForest(list())

    fun tree(): List<HostProcessTreeEntry> = flattenHostProcessTree(forest())

    fun kill(pid: Int): Result<Unit> = runCatching {
        val target = list().firstOrNull { it.pid == pid } ?: error("进程已不存在")
        check(target.stoppable) { "不能停止 ${target.label}" }
        Process.sendSignal(pid, Process.SIGNAL_KILL)
    }
}
