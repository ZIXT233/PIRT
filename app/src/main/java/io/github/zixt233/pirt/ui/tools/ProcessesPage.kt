package io.github.zixt233.pirt.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.zixt233.pirt.runtime.HostProcess
import io.github.zixt233.pirt.runtime.HostProcessKind
import io.github.zixt233.pirt.runtime.HostProcessTreeEntry
import io.github.zixt233.pirt.runtime.HostProcessTreeNode
import io.github.zixt233.pirt.runtime.RuntimeConnection
import io.github.zixt233.pirt.runtime.collectHostProcessForestPids
import io.github.zixt233.pirt.runtime.flattenVisibleHostProcessForest
import io.github.zixt233.pirt.i18n.LocalAppLanguage
import io.github.zixt233.pirt.i18n.AppLanguage
import io.github.zixt233.pirt.i18n.text
import kotlinx.coroutines.delay

private val ProcessTreeIndent = 10.dp
private val ProcessTreeGutter = 18.dp

@Composable
fun ProcessesPage(connection: RuntimeConnection) {
    val language = LocalAppLanguage.current
    val manager by connection.processes.collectAsState()
    val forest by connection.processForest.collectAsState()
    var collapsed by remember { mutableStateOf(setOf<Int>()) }
    var selected by remember { mutableStateOf<HostProcess?>(null) }
    var confirmStop by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(forest) {
        val live = collectHostProcessForestPids(forest)
        collapsed = collapsed.intersect(live)
        selected?.takeUnless { it.pid in live }?.let { selected = null }
    }

    LaunchedEffect(manager) {
        manager?.refresh()
        while (true) {
            delay(2_000)
            manager?.refresh()
        }
    }

    val visible = remember(forest, collapsed) { flattenVisibleHostProcessForest(forest, collapsed) }
    val nodeByPid = remember(forest) { indexProcessForest(forest) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            language.text("按 PPID 组成进程树。点三角展开/收起，点进程查看详情。", "Processes are grouped by PPID. Tap the triangle to expand or collapse, and tap a process for details."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (visible.isEmpty()) {
            Text(language.text("当前没有可读进程。", "No readable processes."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 1.dp,
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(visible, key = { it.process.pid }) { entry ->
                        val node = nodeByPid[entry.process.pid]
                        ProcessTreeRow(
                            entry = entry,
                            expanded = node?.hasChildren == true && entry.process.pid !in collapsed,
                            hasChildren = node?.hasChildren == true,
                            onToggle = {
                                collapsed = if (entry.process.pid in collapsed) {
                                    collapsed - entry.process.pid
                                } else {
                                    collapsed + entry.process.pid
                                }
                            },
                            onOpen = { selected = entry.process },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    }
                }
            }
        }
    }

    selected?.let { process ->
        val childCount = nodeByPid[process.pid]?.children?.size ?: 0
        if (confirmStop) {
            ProcessConfirmStopDialog(
                process = process,
                onDismiss = { confirmStop = false },
                onConfirm = {
                    confirmStop = false
                    selected = null
                    error = manager?.kill(process.pid)?.exceptionOrNull()?.message
                },
            )
        } else {
            ProcessDetailDialog(
                process = process,
                childCount = childCount,
                onDismiss = { selected = null },
                onStop = { confirmStop = true },
            )
        }
    }
}

@Composable
private fun ProcessTreeRow(
    entry: HostProcessTreeEntry,
    expanded: Boolean,
    hasChildren: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val process = entry.process
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = ProcessTreeIndent * entry.depth + 6.dp,
                end = 8.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasChildren) {
            Text(
                text = if (expanded) "▾" else "▸",
                modifier = Modifier
                    .size(ProcessTreeGutter)
                    .clickable(onClick = onToggle)
                    .padding(2.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Spacer(Modifier.width(ProcessTreeGutter))
        }
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                buildString {
                    append(process.label)
                    append(" · pid ")
                    append(process.pid)
                    if (process.independent) append(language.text(" · 独立", " · independent"))
                },
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                process.command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProcessDetailDialog(
    process: HostProcess,
    childCount: Int,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
) {
    val language = LocalAppLanguage.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)) {
                Text(process.label, style = MaterialTheme.typography.headlineSmall)
                Column(
                    Modifier
                        .padding(top = 16.dp, bottom = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailLine("PID", process.pid.toString())
                    DetailLine("PPID", process.ppid.toString())
                    DetailLine(language.text("类型", "Type"), processKindLabel(process.kind, language))
                    DetailLine(language.text("状态", "Status"), if (process.independent) language.text("独立进程", "Independent") else language.text("子进程", "Child process"))
                    if (childCount > 0) DetailLine(language.text("子进程", "Children"), language.text("$childCount 个", "$childCount"))
                    DetailLine(language.text("命令", "Command"))
                    Text(
                        process.command,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (process.independent) {
                        Text(
                            language.text("独立进程会在后台继续运行，不受当前 PIRT 会话影响。", "Independent processes continue in the background and are not tied to the current PIRT conversation."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!process.stoppable) {
                        Text(
                            language.text("此进程受保护，不能从这里停止。", "This process is protected and cannot be stopped here."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DialogActionRow(
                    trailingLabel = language.text("关闭", "Close"),
                    onTrailing = onDismiss,
                    leadingLabel = if (process.stoppable) language.text("停止进程", "Stop process") else null,
                    onLeading = if (process.stoppable) onStop else null,
                )
            }
        }
    }
}

@Composable
private fun ProcessConfirmStopDialog(
    process: HostProcess,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val language = LocalAppLanguage.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)) {
                Text(language.text("停止 ${process.label}？", "Stop ${process.label}?"), style = MaterialTheme.typography.headlineSmall)
                Text(
                    language.text("PID ${process.pid} 将被终止。", "PID ${process.pid} will be terminated."),
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                DialogActionRow(
                    trailingLabel = language.text("取消", "Cancel"),
                    onTrailing = onDismiss,
                    leadingLabel = language.text("停止", "Stop"),
                    onLeading = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun DialogActionRow(
    trailingLabel: String,
    onTrailing: () -> Unit,
    leadingLabel: String?,
    onLeading: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingLabel != null && onLeading != null) {
            TextButton(onClick = onLeading) { Text(leadingLabel) }
        }
        TextButton(onClick = onTrailing) { Text(trailingLabel) }
    }
}

@Composable
private fun DetailLine(label: String, value: String? = null) {
    if (value == null) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Medium)
        }
    }
}

private fun processKindLabel(kind: HostProcessKind, language: AppLanguage): String = when (kind) {
    HostProcessKind.APP -> language.text("PIRT 主进程", "PIRT app")
    HostProcessKind.PI_RUNTIME -> language.text("PIRT Agent", "PIRT agent")
    HostProcessKind.WORKSPACE -> language.text("Workspace 进程", "Workspace process")
}

private fun indexProcessForest(forest: List<HostProcessTreeNode>): Map<Int, HostProcessTreeNode> {
    val result = mutableMapOf<Int, HostProcessTreeNode>()
    fun walk(node: HostProcessTreeNode) {
        result[node.process.pid] = node
        node.children.forEach(::walk)
    }
    forest.forEach(::walk)
    return result
}
