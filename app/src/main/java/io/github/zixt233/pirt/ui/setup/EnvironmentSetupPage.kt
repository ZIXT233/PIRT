package io.github.zixt233.pirt.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zixt233.pirt.runtime.InstallState
import io.github.zixt233.pirt.i18n.LocalAppLanguage
import io.github.zixt233.pirt.i18n.text

@Composable
fun EnvironmentSetupPage(
    state: InstallState,
    logs: List<String> = emptyList(),
    onRetry: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val message = when (state) {
        InstallState.Idle -> language.text("正在开始……", "Starting…")
        is InstallState.Copying -> {
            val copied = state.copiedBytes / (1024 * 1024)
            val total = state.totalBytes / (1024 * 1024)
            language.text("正在准备离线文件：$copied / $total MiB", "Preparing offline files: $copied / $total MiB")
        }
        is InstallState.Downloading -> language.text("正在准备文件……", "Preparing files…")
        is InstallState.Verifying -> {
            val read = state.readBytes / (1024 * 1024)
            val total = (state.totalBytes / (1024 * 1024)).coerceAtLeast(1)
            language.text("正在检查文件完整性：$read / $total MiB", "Verifying files: $read / $total MiB")
        }
        is InstallState.Extracting -> {
            val read = state.readBytes / (1024 * 1024)
            val total = (state.totalBytes / (1024 * 1024)).coerceAtLeast(1)
            language.text("正在安装开发工具：$read / $total MiB", "Installing development tools: $read / $total MiB")
        }
        InstallState.Complete -> language.text("准备完成", "Ready")
        is InstallState.Failed -> language.text("准备失败：${state.message}", "Setup failed: ${state.message}")
    }
    val progress = when (state) {
        is InstallState.Copying -> fraction(state.copiedBytes, state.totalBytes)
        is InstallState.Downloading -> state.totalBytes?.let { fraction(state.downloadedBytes, it) }
        is InstallState.Verifying -> fraction(state.readBytes, state.totalBytes)
        is InstallState.Extracting -> fraction(state.readBytes, state.totalBytes)
        InstallState.Complete -> 1f
        else -> null
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(language.text("正在准备本地开发环境", "Preparing the local development environment"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        if (state !is InstallState.Failed) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
        }
        Text(message)
        Spacer(Modifier.height(16.dp))
        if (state !is InstallState.Failed) {
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(0.85f),
                    drawStopIndicator = {},
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.85f))
            }
        }
        if (state is InstallState.Extracting && state.recentEntries.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth(0.85f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                state.recentEntries.forEach { path ->
                    Text(
                        text = path,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state is InstallState.Failed) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onRetry) { Text(language.text("重试", "Retry")) }
        }
    }
}

private fun fraction(read: Long, total: Long): Float? {
    if (total <= 0L) return null
    return (read.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}
