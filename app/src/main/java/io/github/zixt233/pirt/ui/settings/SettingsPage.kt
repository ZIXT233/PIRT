package io.github.zixt233.pirt.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.zixt233.pirt.runtime.OverlayPermission
import io.github.zixt233.pirt.runtime.PRootRuntime
import io.github.zixt233.pirt.runtime.PiAuthActivity
import io.github.zixt233.pirt.runtime.PiAuthEvent
import io.github.zixt233.pirt.runtime.PiAuthOption
import io.github.zixt233.pirt.runtime.PiModel
import io.github.zixt233.pirt.runtime.PiProvider
import io.github.zixt233.pirt.runtime.RuntimeConnection
import io.github.zixt233.pirt.runtime.RuntimeArtifacts
import io.github.zixt233.pirt.runtime.RuntimeService
import io.github.zixt233.pirt.i18n.AppLanguage
import io.github.zixt233.pirt.i18n.AppLanguageStore
import io.github.zixt233.pirt.i18n.LocalAppLanguage
import io.github.zixt233.pirt.i18n.text
import io.github.zixt233.pirt.ui.sortProviders
import kotlinx.coroutines.delay

@Composable
fun SettingsPage(
    runtime: PRootRuntime,
    connection: RuntimeConnection,
    onboarding: Boolean = false,
    focusOverlayToken: Int = 0,
    onModelSelected: () -> Unit = {},
    onReplaceInitialEnvironment: () -> Unit = {},
    onSkip: () -> Unit = {},
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val auth by connection.auth.collectAsState()
    val authState by connection.authState.collectAsState()
    val rootfsBuild = remember(runtime.paths.rootfs) {
        runCatching {
            runtime.paths.rootfs.resolve(".pirt-rootfs-version").readText().trim()
        }.getOrNull().takeUnless { it.isNullOrBlank() } ?: RuntimeArtifacts.debianArm64.version
    }
    val appVersion = remember(context) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).let { info ->
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            "${info.versionName} ($code)"
        }
    }
    var overlayGranted by remember { mutableStateOf(OverlayPermission.canDraw(context)) }
    var overlayEnabled by remember { mutableStateOf(OverlayPermission.isUserEnabled(context)) }
    val listState = rememberLazyListState()
    val providers = remember(authState.providers) { sortProviders(authState.providers) }
    val connectedProviders = remember(providers) { providers.filter(PiProvider::configured) }
    val loginProviders = remember(providers) { providers.filterNot(PiProvider::configured) }
    var activeProviderId by remember { mutableStateOf<String?>(null) }
    var showModels by remember { mutableStateOf(false) }
    var modelsRequested by remember { mutableStateOf(false) }
    var modelSelectionRequested by remember { mutableStateOf(false) }
    var loginProviderMenuExpanded by remember { mutableStateOf(false) }
    var selectedLoginProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingBrowserLoginProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    var showOverlayRequiredDialog by rememberSaveable { mutableStateOf(false) }
    var showReplaceEnvironmentDialog by rememberSaveable { mutableStateOf(false) }
    var customName by rememberSaveable(language) { mutableStateOf(language.text("兼容 API", "Compatible API")) }
    var customBaseUrl by rememberSaveable { mutableStateOf("") }
    var customApiKey by rememberSaveable { mutableStateOf("") }
    val authBusy = authState.activity != null
    val selectedLoginProvider = loginProviders.firstOrNull { it.id == selectedLoginProviderId }
    val overlayItemIndex = remember(
        authState.activity,
        authState.providersLoaded,
        providers.size,
        authState.error,
        loginProviders.size,
        selectedLoginProvider,
        connectedProviders.size,
        onboarding,
    ) {
        var index = 0
        if (authState.activity != null) index += 1
        if (authState.error != null) index += 1
        if (authState.providersLoaded && providers.isEmpty() && authState.error == null) index += 1
        index += 1 // Language
        index += 1 // 默认模型
        index += 1 // 登录 AI
        index += 1 // 已登录账号
        index += 1 // 添加兼容 API
        index // 悬浮窗
    }

    DisposableEffect(lifecycleOwner, auth) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val previouslyGranted = overlayGranted
                OverlayPermission.activateAfterGrant(context, previouslyGranted)
                overlayGranted = OverlayPermission.canDraw(context)
                overlayEnabled = OverlayPermission.isUserEnabled(context)
                RuntimeService.refreshNotification()
                val pendingProviderId = pendingBrowserLoginProviderId
                if (pendingProviderId != null && overlayGranted && overlayEnabled) {
                    pendingBrowserLoginProviderId = null
                    showOverlayRequiredDialog = false
                    activeProviderId = pendingProviderId
                    modelsRequested = true
                    auth?.login(pendingProviderId, "oauth", "browser")
                } else if (pendingProviderId != null) {
                    showOverlayRequiredDialog = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(focusOverlayToken, overlayItemIndex) {
        if (focusOverlayToken <= 0) return@LaunchedEffect
        delay(80)
        listState.animateScrollToItem(overlayItemIndex)
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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(language.text("语言", "Language"), style = MaterialTheme.typography.titleLarge)
                    Text(
                        language.text("选择应用界面语言", "Choose the app interface language"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (language == AppLanguage.SIMPLIFIED_CHINESE) {
                            Button(onClick = {}) { Text("简体中文") }
                        } else {
                            OutlinedButton(onClick = {
                                AppLanguageStore.set(context, AppLanguage.SIMPLIFIED_CHINESE)
                                RuntimeService.refreshNotification()
                            }) { Text("简体中文") }
                        }
                        if (language == AppLanguage.ENGLISH) {
                            Button(onClick = {}) { Text("English") }
                        } else {
                            OutlinedButton(onClick = {
                                AppLanguageStore.set(context, AppLanguage.ENGLISH)
                                RuntimeService.refreshNotification()
                            }) { Text("English") }
                        }
                    }
                }
            }
        }
        authState.activity?.let { activity ->
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    Text(authActivityLabel(activity, language), modifier = Modifier.weight(1f))
                    if (activity == PiAuthActivity.LOGGING_IN && authState.activeLoginId != null) {
                        TextButton(onClick = { auth?.cancelActiveLogin() }) { Text(language.text("取消", "Cancel")) }
                    }
                }
            }
        }
        authState.error?.let { message ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            language.text("AI 服务连接失败", "AI service connection failed"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (authState.providersLoaded && providers.isEmpty() && authState.error == null) {
            item { Text(language.text("PIRT 没有返回可用的 AI 服务。", "PIRT returned no available AI services."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item {
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(language.text("新会话默认模型", "Default model for new conversations"), style = MaterialTheme.typography.titleLarge)
                    val defaultProvider = providers.firstOrNull { it.id == authState.selectedProvider }
                    Text(
                        authState.selectedModel?.let { model ->
                            "${defaultProvider?.name ?: authState.selectedProvider.orEmpty()} · $model"
                        } ?: language.text("尚未设置", "Not set"),
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (connectedProviders.isNotEmpty()) {
                        Button(
                            enabled = !authBusy,
                            onClick = {
                                activeProviderId = null
                                modelsRequested = true
                                auth?.loadModels()
                            },
                        ) { Text(language.text("选择默认模型", "Choose default model")) }
                    } else {
                        Text(language.text("登录一个 AI 服务后即可设置。", "Sign in to an AI service to choose a model."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(language.text("登录 AI 账号", "Sign in to AI"), style = MaterialTheme.typography.titleLarge)
                    when {
                        !authState.providersLoaded ||
                            authState.activity == PiAuthActivity.STARTING ||
                            authState.activity == PiAuthActivity.LOADING_PROVIDERS -> {
                            Text(language.text("正在读取可用 AI 服务……", "Loading available AI services…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        loginProviders.isEmpty() && providers.isNotEmpty() -> {
                            Text(language.text("所有可用服务均已连接。", "All available services are connected."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        loginProviders.isEmpty() -> {
                            Text(language.text("暂无可以登录的 AI 服务。", "No AI services are available for sign-in."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> {
                            selectedLoginProvider?.let { provider ->
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
                                    if ("oauth" in provider.authTypes && provider.id == "openai-codex") {
                                        val overlayActive = overlayGranted && overlayEnabled
                                        Button(
                                            enabled = !authBusy,
                                            colors = if (overlayActive) {
                                                ButtonDefaults.buttonColors()
                                            } else {
                                                ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            },
                                            onClick = {
                                                if (overlayActive) {
                                                    activeProviderId = provider.id
                                                    modelsRequested = true
                                                    auth?.login(provider.id, "oauth", "browser")
                                                } else {
                                                    pendingBrowserLoginProviderId = provider.id
                                                    showOverlayRequiredDialog = true
                                                }
                                            },
                                        ) { Text(language.text("浏览器登录", "Browser sign-in")) }
                                        OutlinedButton(
                                            enabled = !authBusy,
                                            onClick = {
                                                activeProviderId = provider.id
                                                modelsRequested = true
                                                auth?.login(provider.id, "oauth", "device_code")
                                            },
                                        ) { Text(language.text("设备码登录", "Device-code sign-in")) }
                                    } else if ("oauth" in provider.authTypes) {
                                        Button(
                                            enabled = !authBusy,
                                            onClick = {
                                                activeProviderId = provider.id
                                                modelsRequested = true
                                                auth?.login(provider.id, "oauth")
                                            },
                                        ) { Text(language.text("账号登录", "Sign in")) }
                                    }
                                    if ("api_key" in provider.authTypes) {
                                        OutlinedButton(enabled = !authBusy, onClick = {
                                            activeProviderId = provider.id
                                            modelsRequested = true
                                            auth?.login(provider.id, "api_key")
                                        }) { Text(language.text("使用密钥", "Use API key")) }
                                    }
                                }
                                if (
                                    provider.id == "openai-codex" &&
                                    "oauth" in provider.authTypes &&
                                    !(overlayGranted && overlayEnabled)
                                ) {
                                    Text(
                                        language.text("浏览器登录需要先开启悬浮窗后台保活", "Browser sign-in requires the overlay to keep PIRT active in the background."),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } ?: Text(language.text("正在准备登录选项……", "Preparing sign-in options…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(language.text("已登录账号", "Connected accounts"), style = MaterialTheme.typography.titleLarge)
                    when {
                        !authState.providersLoaded ||
                            authState.activity == PiAuthActivity.STARTING ||
                            authState.activity == PiAuthActivity.LOADING_PROVIDERS -> {
                        Text(language.text("正在读取登录状态……", "Loading sign-in status…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        connectedProviders.isEmpty() -> {
                        Text(language.text("尚未登录任何 AI 服务。", "No AI services are connected."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> {
                            connectedProviders.forEach { provider ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(provider.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            when {
                                                provider.custom -> language.text("自定义兼容 API", "Custom compatible API")
                                                provider.id == authState.selectedProvider -> language.text("新会话默认模型来源", "Default provider for new conversations")
                                                else -> language.text("已连接", "Connected")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (provider.custom) {
                                        TextButton(enabled = !authBusy, onClick = { auth?.removeCustomProvider(provider.id) }) {
                                            Text(language.text("移除", "Remove"))
                                        }
                                    } else {
                                    TextButton(enabled = !authBusy, onClick = { auth?.logout(provider.id) }) { Text(language.text("退出", "Sign out")) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(language.text("添加兼容 API", "Add compatible API"), style = MaterialTheme.typography.titleLarge)
                    Text(
                        language.text("OpenAI 兼容接口（中转站、Ollama/vLLM 等）。填写 Base URL 和 API Key 后自动拉取 /models。", "OpenAI-compatible endpoints, including gateways and Ollama/vLLM. PIRT loads /models after you enter a Base URL and API key."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text(language.text("显示名称", "Display name")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !authBusy,
                    )
                    OutlinedTextField(
                        value = customBaseUrl,
                        onValueChange = { customBaseUrl = it },
                        label = { Text("Base URL") },
                        placeholder = { Text("https://api.example.com/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !authBusy,
                    )
                    OutlinedTextField(
                        value = customApiKey,
                        onValueChange = { customApiKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !authBusy,
                    )
                    Button(
                        enabled = !authBusy && customBaseUrl.isNotBlank() && customApiKey.isNotBlank(),
                        onClick = {
                            auth?.configureCustomProvider(
                                name = customName.ifBlank { language.text("兼容 API", "Compatible API") },
                                baseUrl = customBaseUrl.trim(),
                                apiKey = customApiKey.trim(),
                            )
                        },
                    ) {
                        Text(if (authBusy) language.text("正在拉取模型……", "Loading models…") else language.text("拉取模型并添加", "Load models and add"))
                    }
                }
            }
        }
        item(key = "overlay-card") {
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(language.text("悬浮窗保活", "Background overlay"), style = MaterialTheme.typography.titleLarge)
                    Text(
                        language.text("应用切到后台时，PIRT 会话和虚拟电脑中的进程可能被系统冻结。开启悬浮窗可在后台保持这些进程活跃。", "Android may freeze PIRT conversations and virtual-computer processes in the background. Enable the overlay to keep them active."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(language.text("启用悬浮窗", "Enable overlay"), fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    !overlayGranted -> language.text("需要先授予系统悬浮窗权限", "System overlay permission is required")
                                    overlayEnabled -> language.text("浮标已启用，后台会显示保活入口", "Overlay enabled; the keep-alive bubble is visible")
                                    else -> language.text("已授权但未启用，不会显示浮标", "Permission granted but overlay disabled")
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
                        Text(if (overlayGranted) language.text("管理悬浮窗权限", "Manage overlay permission") else language.text("授予悬浮窗权限", "Grant overlay permission"))
                        }
                        Text(
                            if (overlayGranted) language.text("已授权", "Granted") else language.text("未授权", "Not granted"),
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
        if (onboarding) {
            item { TextButton(onClick = onSkip) { Text(language.text("稍后设置，先进入应用", "Set up later and enter the app")) } }
        } else {
            item {
                SettingsCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(language.text("环境信息", "Environment"), style = MaterialTheme.typography.titleLarge)
                        EnvironmentVersionRow(language.text("已安装 Rootfs", "Installed rootfs"), rootfsBuild)
                        EnvironmentVersionRow(
                            language.text("APK 初始 Rootfs", "APK initial rootfs"),
                            RuntimeArtifacts.debianArm64.version,
                        )
                        EnvironmentVersionRow(
                            language.text("初始 Rootfs 镜像 SHA-256", "Initial rootfs image SHA-256"),
                            RuntimeArtifacts.debianArm64.sha256,
                            monospace = true,
                        )
                        EnvironmentVersionRow("Debian", RuntimeArtifacts.DEBIAN_VERSION)
                        EnvironmentVersionRow("Pi", RuntimeArtifacts.PI_VERSION)
                        EnvironmentVersionRow("PRoot", RuntimeArtifacts.PROOT_VERSION)
                        EnvironmentVersionRow(language.text("软件版本", "App version"), appVersion)
                        Text(
                            language.text(
                                "Rootfs 是可写的初始环境基座，升级 PIRT 不会自动重置当前环境。",
                                "The rootfs is a writable initial environment. PIRT upgrades never reset the installed environment automatically.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { showReplaceEnvironmentDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(language.text("重置为 APK 初始 Rootfs", "Reset to APK initial rootfs"))
                        }
                    }
                }
            }
        }
        item {
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(language.text("关于 PIRT", "About PIRT"), style = MaterialTheme.typography.titleLarge)
                    Text(
                        language.text("由 ZIXT 发起并维护", "Created and maintained by ZIXT"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EnvironmentVersionRow(language.text("软件版本", "App version"), appVersion)
                    Text(
                        text = "GitHub · PIRT",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ZIXT233/PIRT")),
                            )
                            }
                            .padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    if (showReplaceEnvironmentDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceEnvironmentDialog = false },
            title = { Text(language.text("重置为 APK 初始 Rootfs？", "Reset to APK initial rootfs?")) },
            text = {
                Text(
                    language.text(
                        "将删除当前 Debian 系统层并重新解压 APK 初始 Rootfs。通过 apt 安装的软件包以及对系统目录的修改都会被清除。/workspace、Pi 会话和登录数据会保留。此操作无法撤销。",
                        "This deletes the current Debian system layer and extracts the APK initial rootfs. Packages installed with apt and changes to system directories will be removed. /workspace, Pi sessions, and sign-in data are preserved. This cannot be undone.",
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReplaceEnvironmentDialog = false
                        onReplaceInitialEnvironment()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(language.text("重置 Rootfs", "Reset rootfs"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceEnvironmentDialog = false }) {
                    Text(language.text("取消", "Cancel"))
                }
            },
        )
    }

    authState.prompt?.let { prompt ->
        AuthPromptDialog(
            prompt = prompt,
            onAnswer = { value -> auth?.answerPrompt(prompt.promptId, value) },
            onCancel = { auth?.cancelLogin(prompt.loginId) },
        )
    }
    if (showOverlayRequiredDialog) {
        AlertDialog(
            onDismissRequest = {
                showOverlayRequiredDialog = false
                pendingBrowserLoginProviderId = null
            },
            title = { Text(language.text("浏览器登录需要后台保活", "Browser sign-in needs background activity")) },
            text = {
                Text(
                    if (overlayGranted) {
                        language.text("PIRT 已获得悬浮窗权限，但后台保活当前处于关闭状态。开启后将继续浏览器登录。", "PIRT has overlay permission, but background keep-alive is disabled. Enable it to continue browser sign-in.")
                    } else {
                        language.text("切到浏览器授权时，PIRT 需要通过悬浮窗保持登录监听运行。授权成功返回后，将自动继续本次登录。", "PIRT uses the overlay to keep the sign-in listener active while you authorize in the browser. Sign-in continues automatically when you return.")
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (overlayGranted) {
                            OverlayPermission.setUserEnabled(context, true)
                            overlayEnabled = true
                            RuntimeService.refreshNotification()
                            val providerId = pendingBrowserLoginProviderId
                            pendingBrowserLoginProviderId = null
                            showOverlayRequiredDialog = false
                            if (providerId != null) {
                                activeProviderId = providerId
                                modelsRequested = true
                                auth?.login(providerId, "oauth", "browser")
                            }
                        } else {
                            showOverlayRequiredDialog = false
                            OverlayPermission.openSettings(context)
                        }
                    },
                ) {
                    Text(if (overlayGranted) language.text("开启并继续登录", "Enable and continue") else language.text("开启悬浮窗权限", "Grant overlay permission"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOverlayRequiredDialog = false
                        pendingBrowserLoginProviderId = null
                    },
                ) { Text(language.text("取消", "Cancel")) }
            },
        )
    }
    authState.notice?.takeIf { it.kind == "device_code" }?.let { notice ->
        AuthNoticeDialog(
            notice = notice,
            onOpen = { link -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) },
            onDismiss = {
                auth?.dismissNotice()
                auth?.cancelLogin(notice.loginId)
            },
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

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = content,
    )
}

@Composable
private fun EnvironmentVersionRow(label: String, value: String, monospace: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}

private fun authActivityLabel(activity: PiAuthActivity, language: AppLanguage): String = when (activity) {
    PiAuthActivity.STARTING -> language.text("正在启动 PIRT 认证服务……", "Starting PIRT authentication…")
    PiAuthActivity.LOADING_PROVIDERS -> language.text("正在读取 AI 服务与登录状态……", "Loading AI services and sign-in status…")
    PiAuthActivity.LOADING_MODELS -> language.text("正在读取模型列表……", "Loading models…")
    PiAuthActivity.LOGGING_IN -> language.text("正在等待登录结果……", "Waiting for sign-in…")
    PiAuthActivity.LOGGING_OUT -> language.text("正在退出账号……", "Signing out…")
    PiAuthActivity.SELECTING_MODEL -> language.text("正在保存新会话默认模型……", "Saving the default model…")
}

@Composable
private fun AuthNoticeDialog(
    notice: PiAuthEvent.Notice,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val link = notice.url ?: notice.verificationUri
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (notice.kind == "device_code") language.text("设备码登录", "Device-code sign-in") else language.text("登录提示", "Sign-in notice")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                notice.message?.let { Text(it) }
                notice.userCode?.let {
                    Text(language.text("设备码", "Device code"), style = MaterialTheme.typography.labelLarge)
                    Text(it, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(language.text("在登录页面输入上面的设备码。", "Enter the device code above on the sign-in page."))
                }
            }
        },
        confirmButton = {
            if (link != null) TextButton(onClick = { onOpen(link) }) { Text(language.text("打开登录页面", "Open sign-in page")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(language.text("关闭", "Close")) } },
    )
}

@Composable
private fun AuthPromptDialog(
    prompt: PiAuthEvent.Prompt,
    onAnswer: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val language = LocalAppLanguage.current
    var value by remember(prompt.promptId) { mutableStateOf("") }
    var showManualCallback by remember(prompt.promptId) { mutableStateOf(false) }
    val waitingForBrowserCallback = prompt.kind == "manual_code"
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(if (waitingForBrowserCallback) language.text("等待浏览器授权", "Waiting for browser authorization") else language.text("连接 AI 服务", "Connect AI service"))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (waitingForBrowserCallback && !showManualCallback) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
                        Text(
                            language.text("请在浏览器中完成登录。授权成功后会自动返回，无需在这里输入内容。", "Complete sign-in in your browser. PIRT will return automatically when authorization succeeds."),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        language.text("可以暂时切到浏览器，PIRT 会继续等待登录结果。", "You can switch to the browser; PIRT will keep waiting for the result."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        language.text("长时间没有自动完成？手动粘贴回调 URL", "Taking too long? Paste the callback URL manually"),
                        modifier = Modifier
                            .clickable { showManualCallback = true }
                            .padding(vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (prompt.kind == "select") {
                    Text(localizedAuthPrompt(prompt, language))
                    prompt.options.forEach { option ->
                        Card(Modifier.fillMaxWidth().clickable { onAnswer(option.id) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(localizedAuthOption(option, language), fontWeight = FontWeight.SemiBold)
                                option.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                } else {
                    Text(
                        if (waitingForBrowserCallback) {
                            language.text("仅在浏览器已显示登录成功、但 PIRT 长时间没有自动完成时使用。", "Use this only if the browser shows success but PIRT does not finish automatically.")
                        } else {
                            localizedAuthPrompt(prompt, language)
                        },
                    )
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = if (waitingForBrowserCallback) {
                            { Text(language.text("回调 URL 或授权码", "Callback URL or authorization code")) }
                        } else null,
                        placeholder = if (waitingForBrowserCallback) {
                            { Text(language.text("粘贴浏览器地址栏中的完整 URL", "Paste the full URL from the browser address bar")) }
                        } else {
                            prompt.placeholder?.let { placeholder -> { Text(placeholder) } }
                        },
                        singleLine = true,
                        visualTransformation = if (prompt.kind == "secret") PasswordVisualTransformation()
                        else androidx.compose.ui.text.input.VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (waitingForBrowserCallback) {
                        Text(
                            language.text("返回自动等待", "Return to automatic waiting"),
                            modifier = Modifier
                                .clickable {
                                value = ""
                                showManualCallback = false
                                }
                                .padding(vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (prompt.kind != "select" && (!waitingForBrowserCallback || showManualCallback)) {
                TextButton(onClick = { onAnswer(value.trim()) }, enabled = value.isNotBlank()) {
                    Text(if (waitingForBrowserCallback) language.text("提交备用回调", "Submit fallback callback") else language.text("继续", "Continue"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(if (waitingForBrowserCallback) language.text("取消登录", "Cancel sign-in") else language.text("取消", "Cancel"))
            }
        },
    )
}

private fun localizedAuthPrompt(prompt: PiAuthEvent.Prompt, language: AppLanguage): String = when {
    "Select OpenAI Codex login method" in prompt.message -> language.text("选择 Codex 登录方式", "Choose a Codex sign-in method")
    prompt.kind == "manual_code" -> language.text("请在浏览器完成登录；如果没有自动返回，请粘贴授权码或完整回调链接。", "Complete sign-in in the browser. If it does not return automatically, paste the authorization code or full callback URL.")
    else -> prompt.message
}

private fun localizedAuthOption(option: PiAuthOption, language: AppLanguage): String = when (option.id) {
    "browser" -> language.text("系统浏览器登录（推荐）", "System browser (recommended)")
    "device_code" -> language.text("设备码登录（可跨设备）", "Device code (works across devices)")
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
    val language = LocalAppLanguage.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(provider?.let { language.text("选择 ${it.name} 的新会话默认模型", "Choose the default model for ${it.name}") } ?: language.text("设置新会话默认模型", "Set default model for new conversations")) },
        text = {
            if (models.isEmpty()) {
                Text(language.text("PIRT 没有找到当前账户可用的模型。可以稍后重试，或检查服务账户权限。", "PIRT found no models available to this account. Try again later or check the service account permissions."))
            } else {
                LazyColumn(Modifier.height(400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(models, key = { "${it.provider}/${it.id}" }) { model ->
                        Card(Modifier.fillMaxWidth().clickable { onSelect(model) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(model.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    buildString {
                                        append(model.id)
                                        if (model.reasoning) append(language.text(" · 支持推理", " · reasoning"))
                                        if (model.provider == selectedProvider && model.id == selectedModel) append(language.text(" · 新会话默认", " · default"))
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(language.text("关闭", "Close")) } },
    )
}
