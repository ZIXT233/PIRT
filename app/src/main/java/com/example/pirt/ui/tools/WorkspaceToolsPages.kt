package com.example.pirt.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.pirt.model.WorkspaceConfig
import android.widget.Toast
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.pirt.runtime.GraphicsPasswordStore
import com.example.pirt.runtime.GraphicsState
import com.example.pirt.runtime.RuntimeConnection

@Composable
fun TerminalPage(workspace: WorkspaceConfig, connection: RuntimeConnection) {
    val manager by connection.terminal.collectAsState()
    val state by connection.terminalState.collectAsState()
    var command by rememberSaveable(workspace.rootPath) { mutableStateOf("") }
    var localError by remember(workspace.rootPath) { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()

    LaunchedEffect(manager, workspace.rootPath) { manager?.open(workspace) }
    LaunchedEffect(state.transcript.length) { scroll.scrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ubuntu · workspace", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { manager?.clear() }) { Text("清屏") }
            TextButton(onClick = { manager?.reset() }) { Text(if (state.running) "停止" else "重置") }
        }
        (localError ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = Color(0xFF111318),
            shape = RoundedCornerShape(10.dp),
        ) {
            SelectionContainer {
                Text(
                    state.transcript.ifBlank { "终端已启动。输入命令后点击运行。\n" },
                    modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(scroll),
                    color = Color(0xFFE2E2E2),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入 shell 命令") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = manager != null && state.started && command.isNotBlank() && !state.running,
                onClick = {
                    val value = command
                    command = ""
                    localError = null
                    runCatching { manager?.execute(value) }.onFailure { localError = it.message }
                },
            ) { Text(if (state.running) "运行中" else "运行") }
        }
        Text("当前为持久 Shell 控制台；vim 等全屏交互程序需要后续 PTY 渲染层。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun GraphicsPage(workspace: WorkspaceConfig, connection: RuntimeConnection) {
    val context = LocalContext.current
    val manager by connection.graphics.collectAsState()
    val state by connection.graphicsState.collectAsState()
    var vncPassword by rememberSaveable { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(manager, workspace.rootPath) { manager?.open(workspace) }
    LaunchedEffect(Unit) {
        if (vncPassword.isBlank()) {
            vncPassword = GraphicsPasswordStore.loadOrCreateDefault(context)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            when (val current = state) {
                GraphicsState.Stopped -> Text("Ubuntu 图形桌面未启动", modifier = Modifier.weight(1f))
                GraphicsState.Starting -> {
                    CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在启动 TigerVNC、XFCE 和 noVNC……", modifier = Modifier.weight(1f))
                }
                is GraphicsState.Ready -> Text("Ubuntu 桌面 · DISPLAY=:${current.display}", modifier = Modifier.weight(1f))
                is GraphicsState.Error -> Text("启动失败：${current.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            }
            if (state is GraphicsState.Ready || state is GraphicsState.Starting) {
                OutlinedButton(onClick = { manager?.stop() }) { Text("停止") }
            } else {
                Button(
                    enabled = manager != null,
                    onClick = {
                        localError = GraphicsPasswordStore.validate(vncPassword)
                        if (localError != null) return@Button
                        GraphicsPasswordStore.save(context, vncPassword)
                        manager?.start(vncPassword.trim())
                    },
                ) { Text("启动桌面") }
            }
        }
        if (state !is GraphicsState.Ready) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vncPassword,
                onValueChange = { if (it.length <= 8) vncPassword = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("VNC 密码") },
                placeholder = { Text("最多 8 个字符") },
                singleLine = true,
                enabled = state !is GraphicsState.Starting,
                visualTransformation = PasswordVisualTransformation(),
            )
            Text(
                "首次使用会生成默认密码；修改后会保存，下次启动沿用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        localError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        val ready = state as? GraphicsState.Ready
        if (ready == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("桌面完全在本机运行；首次启动可能需要几秒。")
            }
        } else {
            val vncAddress = "127.0.0.1:${ready.vncPort}"
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("连接桌面", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    SelectionContainer {
                        Text(
                            vncAddress,
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                    Text(
                        "推荐用浏览器打开，无需安装 VNC 客户端。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ready.url)))
                        },
                    ) { Text("浏览器打开") }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("PIRT VNC 地址", vncAddress))
                            },
                        ) { Text("复制地址") }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                GraphicsPasswordStore.openAvnc(context, ready.vncPort, ready.password)
                                    .onFailure { error ->
                                        Toast.makeText(context, error.message ?: "无法打开 aVNC", Toast.LENGTH_SHORT).show()
                                    }
                            },
                        ) { Text("用 aVNC 打开") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("已安装 aVNC 时可用客户端直连；否则用浏览器即可。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
