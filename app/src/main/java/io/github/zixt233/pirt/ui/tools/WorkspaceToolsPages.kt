package io.github.zixt233.pirt.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ButtonDefaults
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
import io.github.zixt233.pirt.model.WorkspaceConfig
import android.widget.Toast
import androidx.compose.ui.text.input.PasswordVisualTransformation
import io.github.zixt233.pirt.runtime.GraphicsPasswordStore
import io.github.zixt233.pirt.runtime.GraphicsState
import io.github.zixt233.pirt.runtime.RuntimeConnection
import io.github.zixt233.pirt.i18n.LocalAppLanguage
import io.github.zixt233.pirt.i18n.text

@Composable
fun TerminalPage(workspace: WorkspaceConfig, connection: RuntimeConnection) {
    val language = LocalAppLanguage.current
    val manager by connection.terminal.collectAsState()
    val state by connection.terminalState.collectAsState()
    var command by rememberSaveable(workspace.rootPath) { mutableStateOf("") }
    var localError by remember(workspace.rootPath) { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()

    LaunchedEffect(manager, workspace.rootPath) { manager?.open(workspace) }
    LaunchedEffect(state.transcript.length) { scroll.scrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Debian · workspace", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { manager?.clear() }) { Text(language.text("清屏", "Clear")) }
            TextButton(onClick = { manager?.reset() }) { Text(if (state.running) language.text("停止", "Stop") else language.text("重置", "Reset")) }
        }
        (localError ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = Color(0xFF111318),
            shape = RoundedCornerShape(10.dp),
        ) {
            SelectionContainer {
                Text(
                    state.transcript.ifBlank { language.text("终端已启动。输入命令后点击运行。\n", "Terminal ready. Enter a command and tap Run.\n") },
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
                placeholder = { Text(language.text("输入 shell 命令", "Enter a shell command")) },
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
            ) { Text(if (state.running) language.text("运行中", "Running") else language.text("运行", "Run")) }
        }
        Text(language.text("当前为持久 Shell 控制台；vim 等全屏交互程序需要后续 PTY 渲染层。", "This is a persistent shell. Full-screen tools such as vim require a PTY renderer."), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun GraphicsPage(workspace: WorkspaceConfig, connection: RuntimeConnection) {
    val language = LocalAppLanguage.current
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
                GraphicsState.Stopped -> Text(language.text("本地图形桌面未启动", "Local desktop is stopped"), modifier = Modifier.weight(1f))
                GraphicsState.Starting -> {
                    CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(language.text("正在启动 TigerVNC、XFCE 和 noVNC……", "Starting TigerVNC, XFCE, and noVNC…"), modifier = Modifier.weight(1f))
                }
                is GraphicsState.Ready -> Text(language.text("本地图形桌面 · DISPLAY=:${current.display}", "Local desktop · DISPLAY=:${current.display}"), modifier = Modifier.weight(1f))
                is GraphicsState.Error -> Text(language.text("启动失败：${current.message}", "Startup failed: ${current.message}"), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            }
            if (state is GraphicsState.Ready || state is GraphicsState.Starting) {
                OutlinedButton(onClick = { manager?.stop() }) { Text(language.text("停止", "Stop")) }
            } else {
                Button(
                    enabled = manager != null,
                    onClick = {
                        localError = GraphicsPasswordStore.validate(vncPassword)
                        if (localError != null) return@Button
                        GraphicsPasswordStore.save(context, vncPassword)
                        manager?.start(vncPassword.trim())
                    },
                ) { Text(language.text("启动本地桌面", "Start local desktop")) }
            }
        }
        if (state !is GraphicsState.Ready) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vncPassword,
                onValueChange = { if (it.length <= 8) vncPassword = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(language.text("VNC 密码", "VNC password")) },
                placeholder = { Text(language.text("最多 8 个字符", "Up to 8 characters")) },
                singleLine = true,
                enabled = state !is GraphicsState.Starting,
                visualTransformation = PasswordVisualTransformation(),
            )
            Text(
                language.text("首次使用会生成默认密码；修改后会保存，下次启动沿用。", "A default password is generated on first use. Changes are saved for the next launch."),
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
        if (ready != null) {
            val vncAddress = "127.0.0.1:${ready.vncPort}"
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(language.text("连接桌面", "Connect to desktop"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    SelectionContainer {
                        Text(
                            vncAddress,
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ready.url)))
                        },
                    ) { Text(language.text("浏览器打开", "Open in browser")) }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(language.text("PIRT VNC 地址", "PIRT VNC address"), vncAddress))
                            },
                        ) { Text(language.text("复制地址", "Copy address")) }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                            onClick = {
                                GraphicsPasswordStore.openAvnc(context, ready.vncPort, ready.password)
                                    .onFailure { error ->
                                        Toast.makeText(context, error.message ?: "无法打开 aVNC", Toast.LENGTH_SHORT).show()
                                    }
                            },
                        ) { Text(language.text("用 aVNC 打开", "Open with aVNC")) }
                    }
                }
            }
        }
    }
}
