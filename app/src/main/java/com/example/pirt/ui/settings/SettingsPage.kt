package com.example.pirt.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pirt.runtime.OverlayPermission
import com.example.pirt.runtime.PRootRuntime
import com.example.pirt.runtime.PiAuthActivity
import com.example.pirt.runtime.PiAuthEvent
import com.example.pirt.runtime.PiAuthOption
import com.example.pirt.runtime.PiModel
import com.example.pirt.runtime.PiProvider
import com.example.pirt.runtime.RuntimeConnection
import com.example.pirt.runtime.RuntimeService
import com.example.pirt.ui.sortProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsPage(
    runtime: PRootRuntime,
    connection: RuntimeConnection,
    onboarding: Boolean = false,
    focusOverlaySection: Boolean = false,
    onOverlaySectionFocused: () -> Unit = {},
    onModelSelected: () -> Unit = {},
    onSkip: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val auth by connection.auth.collectAsState()
    val authState by connection.authState.collectAsState()
    var probeStatus by remember { mutableStateOf<String?>(null) }
    var overlayGranted by remember { mutableStateOf(OverlayPermission.canDraw(context)) }
    var overlayEnabled by remember { mutableStateOf(OverlayPermission.isUserEnabled(context)) }
    val overlayBringIntoView = remember { BringIntoViewRequester() }
    val providers = remember(authState.providers) { sortProviders(authState.providers) }
    val connectedProviders = remember(providers) { providers.filter(PiProvider::configured) }
    val loginProviders = remember(providers) { providers.filterNot(PiProvider::configured) }
    var activeProviderId by remember { mutableStateOf<String?>(null) }
    var showModels by remember { mutableStateOf(false) }
    var modelsRequested by remember { mutableStateOf(false) }
    var modelSelectionRequested by remember { mutableStateOf(false) }
    var loginProviderMenuExpanded by remember { mutableStateOf(false) }
    var selectedLoginProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    val authBusy = authState.activity != null

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = OverlayPermission.canDraw(context)
                overlayEnabled = OverlayPermission.isUserEnabled(context)
                RuntimeService.refreshNotification()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(focusOverlaySection) {
        if (focusOverlaySection) {
            overlayBringIntoView.bringIntoView()
            onOverlaySectionFocused()
        }
    }

    LaunchedEffect(auth) { auth?.start() }
    LaunchedEffect(loginProviders) {
        if (loginProviders.none { it.id == selectedLoginProviderId }) {
            selectedLoginProviderId = loginProviders.firstOrNull()?.id
        }
    }
    LaunchedEffect(authState.modelsRevision) {
        if (modelsRequested && authState.modelsRevision > 0) {
            modelsRequested = false
            showModels = true
        }
    }
    LaunchedEffect(authState.selectionRevision) {
        if (modelSelectionRequested && authState.selectionRevision > 0) {
            modelSelectionRequested = false
            showModels = false
            onModelSelected()
        }
    }
    LaunchedEffect(authState.notice) {
        val notice = authState.notice ?: return@LaunchedEffect
        val link = notice.url ?: notice.verificationUri
        if (link != null && notice.kind == "auth_url") {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            auth?.dismissNotice()
        }
    }

    LaunchedEffect(Unit) {
        probeStatus = "正在检查……"
        val result = withContext(Dispatchers.IO) { runtime.probe() }
        probeStatus = result.fold(
            onSuccess = { "本地开发环境运行正常（${it.lineSequence().last()}）" },
            onFailure = { "检查失败：${it.message}" },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        authState.activity?.let { activity ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(authActivityLabel(activity))
                }
            }
        }
        if (authState.providersLoaded && providers.isEmpty() && authState.error == null) {
            item { Text("Pi 没有返回可用的 AI 服务。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item {
            Text("悬浮窗保活", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        }
        item {
            Card(Modifier.fillMaxWidth().bringIntoViewRequester(overlayBringIntoView)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "应用切到后台时，Pi 会话和 workspace 进程可能被系统冻结。开启悬浮窗可在后台保持这些进程活跃。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("启用悬浮窗", fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    !overlayGranted -> "需要先授予系统悬浮窗权限"
                                    overlayEnabled -> "浮标已启用，后台会显示保活入口"
                                    else -> "已授权但未启用，不会显示浮标"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = overlayEnabled && overlayGranted,
                            enabled = overlayGranted,
                            onCheckedChange = { enabled ->
                                overlayEnabled = enabled
                                OverlayPermission.setUserEnabled(context, enabled)
                                RuntimeService.refreshNotification()
                            },
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = { OverlayPermission.openSettings(context) }) {
                            Text(if (overlayGranted) "管理悬浮窗权限" else "授予悬浮窗权限")
                        }
                        Text(
                            if (overlayGranted) "已授权" else "未授权",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (overlayGranted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }
        }
        item {
            Text("新会话默认模型", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val defaultProvider = providers.firstOrNull { it.id == authState.selectedProvider }
                    Text(
                        authState.selectedModel?.let { model ->
                            "${defaultProvider?.name ?: authState.selectedProvider.orEmpty()} · $model"
                        } ?: "尚未设置",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "仅用于新建会话；历史会话继续使用各自保存的模型。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (connectedProviders.isNotEmpty()) {
                        Button(
                            enabled = !authBusy,
                            onClick = {
                                activeProviderId = null
                                modelsRequested = true
                                auth?.loadModels()
                            },
                        ) { Text("选择默认模型") }
                    } else {
                        Text("登录一个 AI 服务后即可设置。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Text("登录 AI 账号", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        }
        if (loginProviders.isEmpty()) {
            item { Text("所有可用服务均已连接。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            loginProviders.firstOrNull { it.id == selectedLoginProviderId }?.let { provider ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box {
                                OutlinedButton(onClick = { loginProviderMenuExpanded = true }) {
                                    Text("${provider.name} ▾")
                                }
                                DropdownMenu(
                                    expanded = loginProviderMenuExpanded,
                                    onDismissRequest = { loginProviderMenuExpanded = false },
                                ) {
                                    loginProviders.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.name) },
                                            onClick = {
                                                selectedLoginProviderId = option.id
                                                loginProviderMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if ("oauth" in provider.authTypes) {
                                    Button(enabled = !authBusy, onClick = {
                                        activeProviderId = provider.id
                                        modelsRequested = true
                                        auth?.login(provider.id, "oauth")
                                    }) { Text("账号登录") }
                                }
                                if ("api_key" in provider.authTypes) {
                                    OutlinedButton(enabled = !authBusy, onClick = {
                                        activeProviderId = provider.id
                                        modelsRequested = true
                                        auth?.login(provider.id, "api_key")
                                    }) { Text("使用密钥") }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("已登录账号", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        }
        if (connectedProviders.isEmpty()) {
            item { Text("尚未登录任何 AI 服务。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(connectedProviders, key = { it.id }) { provider ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(provider.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (provider.id == authState.selectedProvider) "新会话默认模型来源" else "已连接",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(enabled = !authBusy, onClick = { auth?.logout(provider.id) }) { Text("退出") }
                    }
                }
            }
        }
        authState.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (onboarding) {
            item { TextButton(onClick = onSkip) { Text("稍后设置，先进入应用") } }
        } else {
            item {
                Text("本地开发环境", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
            }
            item { Text("开发工具已经随应用离线安装，无需任何手动配置。") }
            probeStatus?.let { status ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (status == "正在检查……") {
                            CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            status,
                            color = if (status.startsWith("检查失败")) MaterialTheme.colorScheme.error else Color.Unspecified,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    authState.prompt?.let { prompt ->
        AuthPromptDialog(
            prompt = prompt,
            onAnswer = { value -> auth?.answerPrompt(prompt.promptId, value) },
            onCancel = { auth?.cancelLogin(prompt.loginId) },
        )
    }
    authState.notice?.takeIf { it.kind == "device_code" }?.let { notice ->
        AuthNoticeDialog(
            notice = notice,
            onOpen = { link -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) },
            onDismiss = { auth?.dismissNotice() },
        )
    }
    if (showModels) {
        ModelPickerDialog(
            provider = providers.firstOrNull { it.id == activeProviderId },
            models = authState.models,
            selectedProvider = authState.selectedProvider,
            selectedModel = authState.selectedModel,
            onSelect = { model ->
                modelSelectionRequested = true
                auth?.selectModel(model.provider, model.id)
            },
            onDismiss = { showModels = false },
        )
    }
}

private fun authActivityLabel(activity: PiAuthActivity): String = when (activity) {
    PiAuthActivity.STARTING -> "正在启动 Pi 认证服务……"
    PiAuthActivity.LOADING_PROVIDERS -> "正在读取 AI 服务与登录状态……"
    PiAuthActivity.LOADING_MODELS -> "正在读取模型列表……"
    PiAuthActivity.LOGGING_IN -> "正在等待登录结果……"
    PiAuthActivity.LOGGING_OUT -> "正在退出账号……"
    PiAuthActivity.SELECTING_MODEL -> "正在保存新会话默认模型……"
}

@Composable
private fun AuthNoticeDialog(
    notice: PiAuthEvent.Notice,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val link = notice.url ?: notice.verificationUri
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (notice.kind == "device_code") "设备码登录" else "登录提示") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                notice.message?.let { Text(it) }
                notice.userCode?.let {
                    Text("设备码", style = MaterialTheme.typography.labelLarge)
                    Text(it, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("在登录页面输入上面的设备码。")
                }
            }
        },
        confirmButton = {
            if (link != null) TextButton(onClick = { onOpen(link) }) { Text("打开登录页面") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun AuthPromptDialog(
    prompt: PiAuthEvent.Prompt,
    onAnswer: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var value by remember(prompt.promptId) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("连接 AI 服务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(localizedAuthPrompt(prompt))
                if (prompt.kind == "select") {
                    prompt.options.forEach { option ->
                        Card(Modifier.fillMaxWidth().clickable { onAnswer(option.id) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(localizedAuthOption(option), fontWeight = FontWeight.SemiBold)
                                option.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        placeholder = prompt.placeholder?.let { { Text(it) } },
                        singleLine = true,
                        visualTransformation = if (prompt.kind == "secret") PasswordVisualTransformation()
                        else androidx.compose.ui.text.input.VisualTransformation.None,
                    )
                }
            }
        },
        confirmButton = {
            if (prompt.kind != "select") {
                TextButton(onClick = { onAnswer(value) }, enabled = value.isNotBlank()) { Text("继续") }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}

private fun localizedAuthPrompt(prompt: PiAuthEvent.Prompt): String = when {
    "Select OpenAI Codex login method" in prompt.message -> "选择 Codex 登录方式"
    prompt.kind == "manual_code" -> "请在浏览器完成登录；如果没有自动返回，请粘贴授权码或完整回调链接。"
    else -> prompt.message
}

private fun localizedAuthOption(option: PiAuthOption): String = when (option.id) {
    "browser" -> "系统浏览器登录（推荐）"
    "device_code" -> "设备码登录（可跨设备）"
    else -> option.label
}

@Composable
private fun ModelPickerDialog(
    provider: PiProvider?,
    models: List<PiModel>,
    selectedProvider: String?,
    selectedModel: String?,
    onSelect: (PiModel) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(provider?.let { "选择 ${it.name} 的新会话默认模型" } ?: "设置新会话默认模型") },
        text = {
            if (models.isEmpty()) {
                Text("Pi 没有找到当前账户可用的模型。可以稍后重试，或检查服务账户权限。")
            } else {
                LazyColumn(Modifier.height(400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(models, key = { "${it.provider}/${it.id}" }) { model ->
                        Card(Modifier.fillMaxWidth().clickable { onSelect(model) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(model.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    buildString {
                                        append(model.id)
                                        if (model.reasoning) append(" · 支持推理")
                                        if (model.provider == selectedProvider && model.id == selectedModel) append(" · 新会话默认")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
