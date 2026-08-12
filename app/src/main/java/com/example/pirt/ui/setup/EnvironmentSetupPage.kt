package com.example.pirt.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pirt.runtime.InstallState

@Composable
fun EnvironmentSetupPage(state: InstallState, onRetry: () -> Unit) {
    val message = when (state) {
        InstallState.Idle -> "正在开始……"
        is InstallState.Copying -> {
            val copied = state.copiedBytes / (1024 * 1024)
            val total = state.totalBytes / (1024 * 1024)
            "正在准备离线文件：$copied / $total MiB"
        }
        is InstallState.Downloading -> "正在准备文件……"
        InstallState.Verifying -> "正在检查文件完整性……"
        InstallState.Extracting -> "正在安装开发工具……"
        InstallState.Complete -> "准备完成"
        is InstallState.Failed -> "准备失败：${state.message}"
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("正在准备本地开发环境", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        if (state !is InstallState.Failed) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
        }
        Text(message)
        Spacer(Modifier.height(8.dp))
        Text("所有开发工具都已包含在应用中，此过程不需要网络。", style = MaterialTheme.typography.bodySmall)
        if (state is InstallState.Failed) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onRetry) { Text("重试") }
        }
    }
}
