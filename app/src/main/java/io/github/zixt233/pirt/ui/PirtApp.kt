package io.github.zixt233.pirt.ui

import android.content.ContentValues
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.zixt233.pirt.model.ChatImage
import io.github.zixt233.pirt.R
import io.github.zixt233.pirt.i18n.LocalAppLanguage
import io.github.zixt233.pirt.i18n.AppLanguage
import io.github.zixt233.pirt.i18n.text
import io.github.zixt233.pirt.model.ChatMessage
import io.github.zixt233.pirt.model.MessageRole
import io.github.zixt233.pirt.model.PiSession
import io.github.zixt233.pirt.model.WorkspaceConfig
import io.github.zixt233.pirt.runtime.OverlayPermission
import io.github.zixt233.pirt.runtime.PRootRuntime
import io.github.zixt233.pirt.runtime.RuntimeConnection
import io.github.zixt233.pirt.runtime.InstallState
import io.github.zixt233.pirt.runtime.RuntimeState
import io.github.zixt233.pirt.runtime.RuntimeInstaller
import io.github.zixt233.pirt.runtime.RuntimeService
import io.github.zixt233.pirt.runtime.pi.PiCommand
import io.github.zixt233.pirt.runtime.pi.PiBranchResult
import io.github.zixt233.pirt.runtime.pi.PiExecutionItem
import io.github.zixt233.pirt.runtime.pi.PiExtensionUiRequest
import io.github.zixt233.pirt.runtime.pi.PiThinkingState
import io.github.zixt233.pirt.runtime.pi.PiToolState
import io.github.zixt233.pirt.runtime.pi.PiModel as PiSessionModel
import io.github.zixt233.pirt.runtime.pi.PiSessionSummary
import io.github.zixt233.pirt.runtime.pi.ProcessState
import io.github.zixt233.pirt.runtime.pi.TurnState
import io.github.zixt233.pirt.ui.app.AppViewModel
import io.github.zixt233.pirt.ui.chat.ChatViewModel
import io.github.zixt233.pirt.ui.chat.ChatUiState
import io.github.zixt233.pirt.ui.settings.OverlayPermissionPrompt
import io.github.zixt233.pirt.ui.settings.SettingsPage
import android.widget.Toast
import io.github.zixt233.pirt.data.WorkspaceDocumentsProvider
import io.github.zixt233.pirt.ui.setup.EnvironmentSetupPage
import io.github.zixt233.pirt.ui.tools.GraphicsPage
import io.github.zixt233.pirt.ui.tools.ProcessesPage
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Page { CHAT, GRAPHICS, PROCESSES, SETTINGS }
private const val COLLAPSED_SESSION_COUNT = 10
private data class PendingConversation(val session: PiSession, val piId: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PirtApp() {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val appViewModel: AppViewModel = viewModel()
    val sessions by appViewModel.runtimeConnection.sessions.collectAsState()
    val sessionsLoaded by appViewModel.runtimeConnection.sessionsLoaded.collectAsState()
    val summaries by appViewModel.runtimeConnection.summaries.collectAsState()
    val runtime = appViewModel.runtime
    val installer = remember(runtime) { RuntimeInstaller(context.applicationContext, runtime.paths) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var runtimeState by remember { mutableStateOf(runtime.state()) }
    var installState by remember { mutableStateOf<InstallState>(InstallState.Idle) }
    var installLogs by remember { mutableStateOf(listOf<String>()) }
    var installAttempt by remember { mutableIntStateOf(0) }
    var pageName by rememberSaveable { mutableStateOf(Page.CHAT.name) }
    val uiPrefs = remember { context.getSharedPreferences("pirt_ui", Context.MODE_PRIVATE) }
    var onboardingDone by rememberSaveable {
        mutableStateOf(uiPrefs.getBoolean("onboarding_done", false))
    }
    var newConversation by remember { mutableStateOf(appViewModel.newSession()) }
    var sessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var newConversationText by rememberSaveable { mutableStateOf("") }
    var newConversationPiId by remember { mutableStateOf<String?>(null) }
    val pendingConversations = remember { mutableStateListOf<PendingConversation>() }
    val conversationDrafts = remember { mutableStateMapOf<String, String>() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayGranted by remember { mutableStateOf(OverlayPermission.canDraw(context)) }
    var overlayEnabled by remember { mutableStateOf(OverlayPermission.isUserEnabled(context)) }
    var settingsFocusOverlayToken by rememberSaveable { mutableIntStateOf(0) }
    var requestOverlayPrompt by rememberSaveable { mutableStateOf(false) }
    val page = when (pageName) {
        "FILES", "TERMINAL" -> Page.CHAT
        else -> runCatching { Page.valueOf(pageName) }.getOrDefault(Page.CHAT)
    }
    var requestPiCommands by remember { mutableStateOf<(() -> Unit)?>(null) }
    val session = when (sessionId) {
        newConversation.runtimeKey -> newConversation
        else -> pendingConversations.firstOrNull { it.session.runtimeKey == sessionId }?.session
            ?: sessions.firstOrNull { it.runtimeKey == sessionId }
    }

    LaunchedEffect(sessions, sessionsLoaded, pendingConversations.toList(), newConversation.runtimeKey) {
        if (sessionId == null) {
            sessionId = newConversation.runtimeKey
        }
        pendingConversations.toList().forEach { pending ->
            val persisted = pending.piId?.let { id -> sessions.firstOrNull { it.id == id } } ?: return@forEach
            if (sessionId == pending.session.runtimeKey) sessionId = persisted.runtimeKey
            pendingConversations.remove(pending)
        }
        val selectedExists = sessionId == newConversation.runtimeKey ||
            pendingConversations.any { it.session.runtimeKey == sessionId } ||
            sessions.any { it.runtimeKey == sessionId }
        if (sessionsLoaded && !selectedExists) {
            sessionId = sessions.firstOrNull()?.runtimeKey ?: newConversation.runtimeKey
        }
    }

    LaunchedEffect(session, runtimeState) {
        if (runtimeState !is RuntimeState.Ready || session == null || page != Page.CHAT) return@LaunchedEffect
        appViewModel.runtimeConnection.manager.value?.select(session)
    }

    LaunchedEffect(installAttempt) {
        runtimeState = runtime.state()
        if (runtimeState !is RuntimeState.Ready) {
            installLogs = emptyList()
            installer.install(
                onState = { next ->
                    mainHandler.post {
                        installState = next
                        if (next is InstallState.Complete) {
                            runtimeState = runtime.state()
                            if (runtimeState is RuntimeState.NotInstalled) {
                                installState = InstallState.Failed((runtimeState as RuntimeState.NotInstalled).reason)
                            }
                        }
                    }
                },
                onLog = { line ->
                    mainHandler.post {
                        installLogs = (installLogs + line).takeLast(240)
                    }
                },
            )
        }
    }

    if (runtimeState !is RuntimeState.Ready) {
        EnvironmentSetupPage(
            state = installState,
            logs = installLogs,
            onRetry = { installAttempt += 1 },
        )
        return
    }

    LaunchedEffect(runtimeState, onboardingDone) {
        appViewModel.runtimeConnection.connect()
        appViewModel.runtimeConnection.refreshSessions()
        if (!onboardingDone && pageName == Page.CHAT.name) {
            pageName = Page.SETTINGS.name
        }
        // 环境初始化完成后立刻申请悬浮窗权限（未授权且未点过“稍后再说”时）。
        requestOverlayPrompt = true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val previouslyGranted = overlayGranted
                OverlayPermission.activateAfterGrant(context, previouslyGranted)
                overlayGranted = OverlayPermission.canDraw(context)
                overlayEnabled = OverlayPermission.isUserEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun navigate(target: Page) { pageName = target.name }
    fun finishOnboarding() {
        if (!onboardingDone) {
            onboardingDone = true
            uiPrefs.edit().putBoolean("onboarding_done", true).apply()
        }
        requestOverlayPrompt = true
        navigate(Page.CHAT)
    }
    BackHandler(enabled = drawerState.isOpen || page != Page.CHAT || !onboardingDone) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            // 首次引导设置页：右滑/返回键直接进入应用，并触发悬浮窗申请。
            !onboardingDone && page == Page.SETTINGS -> finishOnboarding()
            else -> navigate(Page.CHAT)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 非聊天页关闭抽屉边缘手势，否则会抢走系统右滑返回。
        gesturesEnabled = page == Page.CHAT || drawerState.isOpen,
        drawerContent = {
            WorkspaceDrawer(
                sessions = (pendingConversations.map(PendingConversation::session) + sessions)
                    .distinctBy(PiSession::runtimeKey),
                sessionsLoaded = sessionsLoaded,
                summaries = summaries,
                transientSessionIds = pendingConversations.mapTo(mutableSetOf()) { it.session.runtimeKey },
                selectedSessionId = sessionId,
                draftSelected = sessionId == newConversation.runtimeKey,
                draftHasText = newConversationText.isNotBlank(),
                onOpenDraft = {
                    appViewModel.runtimeConnection.manager.value?.select(newConversation)
                    sessionId = newConversation.runtimeKey
                    navigate(Page.CHAT)
                    scope.launch { drawerState.close() }
                },
                onOpenSession = { selectedSession ->
                    appViewModel.runtimeConnection.manager.value?.select(selectedSession)
                    sessionId = selectedSession.runtimeKey
                    navigate(Page.CHAT)
                    scope.launch { drawerState.close() }
                },
                onRenameSession = { target, name ->
                    val index = pendingConversations.indexOfFirst { it.session.runtimeKey == target.runtimeKey }
                    if (index >= 0) {
                        pendingConversations[index] = pendingConversations[index].copy(
                            session = pendingConversations[index].session.copy(name = name),
                        )
                    }
                    appViewModel.renameSession(target, name)
                },
                onDeleteConversation = { target ->
                    val pending = pendingConversations.firstOrNull { it.session.runtimeKey == target.runtimeKey }
                    if (pending != null) {
                        pendingConversations.remove(pending)
                    } else {
                        appViewModel.deleteSession(target)
                    }
                    if (sessionId == target.runtimeKey) {
                        sessionId = newConversation.runtimeKey
                        navigate(Page.CHAT)
                    }
                },
                onFiles = {
                    WorkspaceDocumentsProvider.openInSystemFileManager(context)
                        .onFailure { error ->
                            Toast.makeText(context, "无法打开系统文件管理器：${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    scope.launch { drawerState.close() }
                },
                onGraphics = { navigate(Page.GRAPHICS); scope.launch { drawerState.close() } },
                onProcesses = { navigate(Page.PROCESSES); scope.launch { drawerState.close() } },
                onOverlay = {
                    if (overlayGranted) {
                        overlayEnabled = !overlayEnabled
                        OverlayPermission.setUserEnabled(context, overlayEnabled)
                        RuntimeService.refreshNotification()
                    } else {
                        settingsFocusOverlayToken += 1
                        navigate(Page.SETTINGS)
                        scope.launch { drawerState.close() }
                    }
                },
                overlayActive = overlayGranted && overlayEnabled,
                onSettings = {
                    navigate(Page.SETTINGS)
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        OverlayPermissionPrompt(autoPrompt = requestOverlayPrompt)
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        title = {
                            Text(
                                when (page) {
                                    Page.SETTINGS -> language.text("设置", "Settings")
                                    Page.GRAPHICS -> language.text("本地图形桌面", "Local desktop")
                                    Page.PROCESSES -> language.text("进程管理", "Processes")
                                    else -> session?.displayName?.ifBlank { language.text("新会话", "New conversation") }
                                        ?: language.text("新会话", "New conversation")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            if (page != Page.CHAT) {
                                IconButton(onClick = { navigate(Page.CHAT) }) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = language.text("返回", "Back"),
                                    )
                                }
                            } else {
                                TextButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Text("☰", style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                        },
                        actions = {
                            if (page == Page.CHAT && session != null) {
                                IconButton(
                                    onClick = { requestPiCommands?.invoke() },
                                    enabled = requestPiCommands != null,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Code,
                                        contentDescription = language.text("PIRT 工具箱", "PIRT tools"),
                                    )
                                }
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    Page.CHAT -> {
                        if (session != null) {
                            val isNewConversation = session.runtimeKey == newConversation.runtimeKey
                            ConversationPage(
                                session,
                                appViewModel.runtimeConnection,
                                appViewModel.workspace,
                                onRegisterPiCommands = { requestPiCommands = it },
                                composerText = if (isNewConversation) newConversationText else conversationDrafts[session.runtimeKey].orEmpty(),
                                onComposerTextChange = { value ->
                                    if (isNewConversation) {
                                        newConversationText = value
                                    } else if (value.isBlank()) {
                                        conversationDrafts.remove(session.runtimeKey)
                                    } else {
                                        conversationDrafts[session.runtimeKey] = value
                                    }
                                },
                                onPiSessionId = { id ->
                                    if (isNewConversation) {
                                        newConversationPiId = id
                                    } else {
                                        val index = pendingConversations.indexOfFirst { it.session.runtimeKey == session.runtimeKey }
                                        if (index >= 0) pendingConversations[index] = pendingConversations[index].copy(piId = id)
                                    }
                                },
                                onPromptSubmitted = { text, piId ->
                                    if (isNewConversation) {
                                        pendingConversations.add(
                                            0,
                                            PendingConversation(
                                                session = session.copy(
                                                    firstMessage = text,
                                                    updatedAt = System.currentTimeMillis(),
                                                ),
                                                piId = piId ?: newConversationPiId,
                                            ),
                                        )
                                        newConversation = appViewModel.newSession()
                                        newConversationText = ""
                                        newConversationPiId = null
                                    }
                                },
                                onSessionReplaced = { branch ->
                                    pendingConversations.removeAll { it.session.runtimeKey == session.runtimeKey }
                                    pendingConversations.add(0, PendingConversation(branch.session, branch.session.id))
                                    branch.selectedText?.let { conversationDrafts[branch.session.runtimeKey] = it }
                                    sessionId = branch.session.runtimeKey
                                },
                            )
                        }
                    }
                    Page.GRAPHICS -> GraphicsPage(appViewModel.workspace, appViewModel.runtimeConnection)
                    Page.PROCESSES -> ProcessesPage(appViewModel.runtimeConnection)
                    Page.SETTINGS -> SettingsPage(
                        runtime,
                        appViewModel.runtimeConnection,
                        onboarding = !onboardingDone,
                        focusOverlayToken = settingsFocusOverlayToken,
                        onModelSelected = {
                            if (!onboardingDone) finishOnboarding()
                        },
                        onSkip = { finishOnboarding() },
                    )
                }
            }
        }
    }

}

@Composable
private fun WorkspaceDrawer(
    sessions: List<PiSession>,
    sessionsLoaded: Boolean,
    summaries: Map<String, PiSessionSummary>,
    transientSessionIds: Set<String>,
    selectedSessionId: String?,
    draftSelected: Boolean,
    draftHasText: Boolean,
    onOpenDraft: () -> Unit,
    onOpenSession: (PiSession) -> Unit,
    onRenameSession: (PiSession, String) -> Unit,
    onDeleteConversation: (PiSession) -> Unit,
    onFiles: () -> Unit,
    onGraphics: () -> Unit,
    onProcesses: () -> Unit,
    onOverlay: () -> Unit,
    overlayActive: Boolean = false,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    var menuSessionId by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<PiSession?>(null) }
    var infoTarget by remember { mutableStateOf<PiSession?>(null) }
    var deleteTarget by remember { mutableStateOf<PiSession?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val sessionListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.88f),
        drawerContainerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ),
                    tonalElevation = 1.dp,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pirt_mark),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = Color.Unspecified,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "PIRT",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        language.text("掌上虚拟电脑 Agent", "Pi Runtime on PRoot"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledIconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = language.text("设置", "Settings"), modifier = Modifier.size(22.dp))
                }
            }
            DrawerFeatureGrid(
                items = listOf(
                    DrawerFeatureItem(language.text("文件", "Files"), Icons.Outlined.FolderOpen, onFiles),
                    DrawerFeatureItem(language.text("桌面", "Desktop"), Icons.Outlined.DesktopWindows, onGraphics),
                    DrawerFeatureItem(language.text("进程管理", "Processes"), Icons.Outlined.AccountTree, onProcesses),
                    DrawerFeatureItem(language.text("悬浮窗", "Overlay"), Icons.Outlined.PictureInPictureAlt, onOverlay, active = overlayActive),
                ),
            )
            HorizontalDivider(Modifier.padding(top = 18.dp, bottom = 18.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(language.text("会话", "Conversations"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (sessionsLoaded) {
                    Text(
                        "${sessions.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyColumn(
                state = sessionListState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 12.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "new-conversation") {
                    NewConversationDrawerRow(
                        selected = draftSelected,
                        hasText = draftHasText,
                        onOpen = {
                            onOpenDraft()
                            scope.launch { sessionListState.scrollToItem(0) }
                        },
                    )
                }
                if (!sessionsLoaded) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(language.text("正在读取 PIRT 会话……", "Loading PIRT conversations…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (sessions.isEmpty()) {
                    item {
                        Text(
                            language.text("暂无历史会话", "No conversation history"),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(sessions.take(COLLAPSED_SESSION_COUNT), key = { it.runtimeKey }) { session ->
                    ConversationDrawerRow(
                        session = session,
                        selected = session.runtimeKey == selectedSessionId,
                        activity = summaries[session.runtimeKey],
                        menuExpanded = menuSessionId == session.runtimeKey,
                        onOpen = { onOpenSession(session) },
                        onMenu = { menuSessionId = session.runtimeKey },
                        onDismissMenu = { menuSessionId = null },
                        onRename = { menuSessionId = null; renameTarget = session },
                        onInfo = { menuSessionId = null; infoTarget = session },
                        onDelete = { menuSessionId = null; deleteTarget = session },
                    )
                }
                if (sessions.size > COLLAPSED_SESSION_COUNT) {
                    item(key = "session-list-toggle") {
                        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (expanded) language.text("收起", "Collapse") else language.text("显示其余 ${sessions.size - COLLAPSED_SESSION_COUNT} 个会话", "Show ${sessions.size - COLLAPSED_SESSION_COUNT} more"),
                                modifier = Modifier.fillMaxWidth(),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (expanded) {
                    items(sessions.drop(COLLAPSED_SESSION_COUNT), key = { it.runtimeKey }) { session ->
                        ConversationDrawerRow(
                            session = session,
                            selected = session.runtimeKey == selectedSessionId,
                            activity = summaries[session.runtimeKey],
                            menuExpanded = menuSessionId == session.runtimeKey,
                            onOpen = { onOpenSession(session) },
                            onMenu = { menuSessionId = session.runtimeKey },
                            onDismissMenu = { menuSessionId = null },
                            onRename = { menuSessionId = null; renameTarget = session },
                            onInfo = { menuSessionId = null; infoTarget = session },
                            onDelete = { menuSessionId = null; deleteTarget = session },
                        )
                    }
                }
            }
            HorizontalDivider()
            Text(
                text = "PIRT · by ZIXT",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ZIXT233/PIRT")),
                    )
                    }
                    .padding(horizontal = 2.dp, vertical = 14.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    renameTarget?.let { target ->
        NameDialog(
            title = language.text("重命名会话", "Rename conversation"),
            label = language.text("标题", "Title"),
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                onRenameSession(target, title)
                renameTarget = null
            },
        )
    }
    infoTarget?.let { target ->
        SessionInfoDialog(session = target, onDismiss = { infoTarget = null })
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(language.text("删除会话？", "Delete conversation?")) },
            text = {
                Text(
                    if (target.runtimeKey in transientSessionIds) {
                        language.text("该会话尚未保存，将移除临时记录。虚拟电脑中的文件不会删除。", "This conversation has not been saved. Its temporary entry will be removed; files in the virtual computer will not be deleted.")
                    } else {
                        val displayName = target.displayName.ifBlank { language.text("新会话", "New conversation") }
                        language.text("将删除 PIRT 会话“$displayName”。虚拟电脑中的文件不会删除。", "The PIRT conversation “$displayName” will be deleted. Files in the virtual computer will not be deleted.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { deleteTarget = null; onDeleteConversation(target) }) { Text(language.text("删除", "Delete")) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(language.text("取消", "Cancel")) } },
        )
    }
}

@Composable
private fun NewConversationDrawerRow(selected: Boolean, hasText: Boolean, onOpen: () -> Unit) {
    val language = LocalAppLanguage.current
    Button(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 1.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(language.text("新建会话", "New conversation"), fontWeight = FontWeight.Bold)
            Text(
                when {
                    hasText -> language.text("草稿已保留", "Draft saved")
                    selected -> language.text("当前未提交会话", "Current unsent conversation")
                    else -> language.text("开始新的本地任务", "Start a new local task")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun ConversationDrawerRow(
    session: PiSession,
    selected: Boolean,
    activity: PiSessionSummary?,
    menuExpanded: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
) {
    val language = LocalAppLanguage.current
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (selected) 0.dp else 1.dp,
        shadowElevation = if (selected) 0.dp else 1.dp,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Spacer(
                    Modifier
                        .width(4.dp)
                        .height(52.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                )
            }
            Row(
                Modifier.padding(start = if (selected) 12.dp else 16.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Column(Modifier.weight(1f)) {
                Text(
                    session.displayName.ifBlank { language.text("新会话", "New conversation") },
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                sessionActivityLabel(activity, language)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Box {
                TextButton(onClick = onMenu) { Text("⋮") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
                    DropdownMenuItem(text = { Text(language.text("重命名", "Rename")) }, onClick = onRename)
                    DropdownMenuItem(text = { Text(language.text("会话信息", "Conversation info")) }, onClick = onInfo)
                    DropdownMenuItem(text = { Text(language.text("删除", "Delete")) }, onClick = onDelete)
                }
            }
            }
        }
    }
}

@Composable
private fun SessionInfoDialog(session: PiSession, onDismiss: () -> Unit) {
    val language = LocalAppLanguage.current
    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(language.text("会话信息", "Conversation info")) },
        text = {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SessionInfoRow(language.text("标题", "Title"), session.displayName.ifBlank { language.text("新会话", "New conversation") })
                    SessionInfoRow(language.text("会话 ID", "Session ID"), session.id ?: session.runtimeKey, monospace = true)
                    SessionInfoRow(language.text("消息数", "Messages"), session.messageCount.toString())
                    if (session.createdAt > 0L) SessionInfoRow(language.text("创建时间", "Created"), dateFormatter.format(Date(session.createdAt)))
                    if (session.updatedAt > 0L) SessionInfoRow(language.text("更新时间", "Updated"), dateFormatter.format(Date(session.updatedAt)))
                    session.path?.let { SessionInfoRow(language.text("会话文件", "Session file"), it, monospace = true) }
                    session.firstMessage?.let { SessionInfoRow(language.text("首条消息", "First message"), it) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(language.text("关闭", "Close")) } },
    )
}

@Composable
private fun SessionInfoRow(label: String, value: String, monospace: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default)
    }
}

private fun sessionActivityLabel(activity: PiSessionSummary?, language: AppLanguage): String? = when {
    activity == null -> null
    activity.turn == TurnState.COMPACTING -> language.text("正在压缩上下文", "Compacting context")
    activity.turn in setOf(TurnState.QUEUED, TurnState.GENERATING, TurnState.RUNNING_TOOL, TurnState.STOPPING) -> language.text("AI 正在运行", "AI is running")
    else -> null
}

private fun chatStatus(state: ChatUiState, language: AppLanguage): String = when {
    state.error != null -> state.error.orEmpty()
    state.process == ProcessState.STARTING -> language.text("正在连接 PIRT", "Connecting to PIRT")
    state.process == ProcessState.CRASHED || state.process == ProcessState.EXITED -> language.text("PIRT 未就绪", "PIRT is not ready")
    !state.historyLoaded -> language.text("正在加载对话", "Loading conversation")
    state.turn == TurnState.QUEUED -> language.text("消息等待发送", "Message queued")
    state.turn == TurnState.GENERATING -> language.text("模型正在思考", "Model is thinking")
    state.turn == TurnState.RUNNING_TOOL -> language.text("正在执行工具", "Running tool")
    state.turn == TurnState.COMPACTING -> language.text("正在压缩上下文", "Compacting context")
    state.turn == TurnState.STOPPING -> language.text("正在停止", "Stopping")
    state.turn == TurnState.FAILED -> language.text("执行失败", "Run failed")
    else -> language.text("就绪", "Ready")
}

private fun isDuplicatePiCommand(command: PiCommand): Boolean = command.name.lowercase() in setOf(
    "model", "models", "thinking", "thinking-level", "compact", "new", "fork", "clone", "tree",
)

@Composable
private fun ComposerShortcut(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            label,
            color = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private data class DrawerFeatureItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val active: Boolean = false,
)

@Composable
private fun IconGridCell(label: String, icon: ImageVector, onClick: () -> Unit, active: Boolean = false) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Surface(
            shape = shape,
            color = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            tonalElevation = 2.dp,
            shadowElevation = 1.dp,
            border = BorderStroke(
                1.dp,
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DrawerFeatureGrid(items: List<DrawerFeatureItem>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(items, key = { it.label }) { item ->
            IconGridCell(item.label, item.icon, item.onClick, item.active)
        }
    }
}

private data class ComposerAttachmentOption(
    val label: String,
    val icon: ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerAttachmentSheet(
    onPickImage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val options = listOf(
        ComposerAttachmentOption(language.text("图片", "Image"), Icons.Outlined.Image),
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 168.dp)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(options, key = { it.label }) { option ->
                IconGridCell(
                    label = option.label,
                    icon = option.icon,
                    onClick = {
                        when (option.label) {
                            language.text("图片", "Image") -> onPickImage()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    placeholder: String,
    canSend: Boolean,
    agentBusy: Boolean,
    streaming: Boolean,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    onSteer: () -> Unit,
    onAbort: () -> Unit,
) {
    val language = LocalAppLanguage.current
    var expandedInput by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(draft)) }
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)

    LaunchedEffect(draft) {
        if (fieldValue.text != draft) {
            fieldValue = TextFieldValue(draft, selection = TextRange(draft.length))
        }
    }

    val updateField: (TextFieldValue) -> Unit = updateField@{ newValue ->
        if (expandedInput && fieldValue.text.isEmpty() && newValue.text.isEmpty()) {
            if (newValue.selection == fieldValue.selection) {
                expandedInput = false
            } else {
                fieldValue = newValue
            }
            return@updateField
        }
        if (newValue.text.contains('\n')) expandedInput = true
        fieldValue = newValue
        onDraftChange(newValue.text)
    }

    val collapseIfEmptyBackspace: (KeyEvent) -> Boolean = { event ->
        if (
            expandedInput &&
            fieldValue.text.isEmpty() &&
            event.type == KeyEventType.KeyDown &&
            event.key == Key.Backspace
        ) {
            expandedInput = false
            true
        } else {
            false
        }
    }

    @Composable
    fun ActionButtons(modifier: Modifier = Modifier) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            if (agentBusy) {
                if (streaming) {
                    TextButton(onClick = onSteer, enabled = canSend) { Text(language.text("引导", "Guide")) }
                }
                IconButton(onClick = onAbort, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Stop, contentDescription = language.text("停止", "Stop"))
                }
            } else {
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = language.text("发送", "Send"),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (expandedInput) 4.dp else 2.dp,
                    end = 6.dp,
                    top = if (expandedInput) 8.dp else 4.dp,
                    bottom = 4.dp,
                ),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = if (expandedInput) Alignment.Top else Alignment.CenterVertically,
            ) {
                if (!expandedInput) {
                    IconButton(onClick = onAttach, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Add, contentDescription = language.text("添加附件", "Add attachment"))
                    }
                }
                BasicTextField(
                    value = fieldValue,
                    onValueChange = updateField,
                    onTextLayout = { layout ->
                        if (!expandedInput && layout.lineCount > 1) expandedInput = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(
                            min = if (expandedInput) 36.dp else 24.dp,
                            max = if (expandedInput) 120.dp else 24.dp,
                        )
                        .padding(horizontal = 4.dp, vertical = if (expandedInput) 4.dp else 0.dp)
                        .onPreviewKeyEvent(collapseIfEmptyBackspace)
                        .onKeyEvent(collapseIfEmptyBackspace),
                    textStyle = textStyle,
                    maxLines = if (expandedInput) 6 else 1,
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            if (fieldValue.text.isEmpty()) {
                                Text(
                                    placeholder,
                                    style = textStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                )
                            }
                            inner()
                        }
                    },
                )
                if (!expandedInput) {
                    ActionButtons()
                }
            }
            if (expandedInput) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onAttach, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Add, contentDescription = language.text("添加附件", "Add attachment"))
                    }
                    Spacer(Modifier.weight(1f))
                    ActionButtons()
                }
            }
        }
    }
}

private data class ConversationProgress(
    val provider: String = "",
    val model: String = "",
    val thinkingLevel: String? = null,
    val execution: List<PiExecutionItem> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationPage(
    session: PiSession,
    runtime: RuntimeConnection,
    workspace: WorkspaceConfig,
    composerText: String? = null,
    onComposerTextChange: (String) -> Unit = {},
    onPiSessionId: (String) -> Unit = {},
    onPromptSubmitted: (String, String?) -> Unit = { _, _ -> },
    onSessionReplaced: (PiBranchResult) -> Unit = {},
    onRegisterPiCommands: ((() -> Unit)?) -> Unit = {},
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val chat: ChatViewModel = viewModel(
        key = "chat:${session.runtimeKey}",
        factory = ChatViewModel.factory(session, runtime),
    )
    val ui by chat.state.collectAsState()
    val authState by runtime.authState.collectAsState()
    val messages = ui.messages
    var localComposerText by rememberSaveable(session.runtimeKey) { mutableStateOf("") }
    val draft = composerText ?: localComposerText
    val updateDraft: (String) -> Unit = { value ->
        if (composerText != null) onComposerTextChange(value) else localComposerText = value
    }
    val isFreshSession = session.path == null
    val status = chatStatus(ui, language)
    val agentBusy = ui.busy
    val historyLoaded = ui.historyLoaded
    val modelUnset = isUnsetModel(ui.modelId, ui.modelName)
    val progress = ConversationProgress(
        provider = ui.provider,
        model = if (modelUnset) "" else ui.modelName.ifBlank { ui.modelId },
        thinkingLevel = ui.thinkingLevel,
        execution = ui.execution,
    )
    var progressExpanded by rememberSaveable(session.runtimeKey) { mutableStateOf(false) }
    val availableModels = ui.models.sortedWith(
        compareBy<PiSessionModel>(
            { if (it.provider == ui.provider && it.id == ui.modelId) 0 else 1 },
            { providerPriority(it.provider) },
            { it.name },
        )
    )
    var modelMenuExpanded by remember(session.runtimeKey) { mutableStateOf(false) }
    var modelMenuRequested by remember(session.runtimeKey) { mutableStateOf(false) }
    val commands = ui.commands.filterNot(::isDuplicatePiCommand)
    val extensionUiRequest = ui.extensionUiRequests.firstOrNull()
    val thinkingLevels = ui.thinkingLevels
    var showPiControls by remember(session.runtimeKey) { mutableStateOf(false) }
    var showThinkingLevels by remember(session.runtimeKey) { mutableStateOf(false) }
    var thinkingLevelsRequested by remember(session.runtimeKey) { mutableStateOf(false) }
    var showAttachmentSheet by remember(session.runtimeKey) { mutableStateOf(false) }
    var showSessionStats by remember(session.runtimeKey) { mutableStateOf(false) }
    var exportingHtml by remember(session.runtimeKey) { mutableStateOf(false) }
    var autoCompaction by rememberSaveable(session.runtimeKey) { mutableStateOf(true) }
    var autoRetry by rememberSaveable(session.runtimeKey) { mutableStateOf(true) }
    val pendingImages = remember(session.runtimeKey) { mutableStateListOf<ChatImage>() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val listState = remember(session.runtimeKey) { LazyListState() }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            Thread({
                val prepared = uris.mapNotNull { uri -> runCatching { prepareChatImage(context, uri) }.getOrNull() }
                mainHandler.post { pendingImages += prepared }
            }, "pirt-images").start()
        }
    }

    LaunchedEffect(chat) {
        chat.activate()
    }
    LaunchedEffect(extensionUiRequest?.id) {
        when (extensionUiRequest?.method) {
            "notify" -> {
                Toast.makeText(context, extensionUiRequest.message, Toast.LENGTH_LONG).show()
                chat.dismissExtensionUi(extensionUiRequest.id)
            }
            "set_editor_text" -> {
                updateDraft(extensionUiRequest.value.orEmpty())
                chat.dismissExtensionUi(extensionUiRequest.id)
            }
        }
    }
    LaunchedEffect(ui.agentLoaded, ui.modelId, ui.modelName, authState.selectedProvider, authState.selectedModel, authState.selectionRevision) {
        if (!ui.agentLoaded) return@LaunchedEffect
        if (!isUnsetModel(ui.modelId, ui.modelName)) return@LaunchedEffect
        val provider = authState.selectedProvider ?: return@LaunchedEffect
        val modelId = authState.selectedModel ?: return@LaunchedEffect
        chat.setModel(provider, modelId)
    }
    DisposableEffect(session.runtimeKey, chat) {
        onRegisterPiCommands {
            showPiControls = true
            chat.requestCommands()
        }
        onDispose { onRegisterPiCommands(null) }
    }
    LaunchedEffect(ui.sessionId) {
        ui.sessionId?.let(onPiSessionId)
    }
    LaunchedEffect(ui.modelsRevision) {
        if (modelMenuRequested && ui.modelsRevision > 0) {
            modelMenuRequested = false
            modelMenuExpanded = true
        }
    }
    LaunchedEffect(ui.thinkingLevelsRevision) {
        if (thinkingLevelsRequested && ui.thinkingLevelsRevision > 0) {
            thinkingLevelsRequested = false
            showPiControls = false
            showThinkingLevels = true
        }
    }
    val newestMessage = messages.lastOrNull()
    LaunchedEffect(
        historyLoaded,
        newestMessage?.id,
        newestMessage?.text?.length,
        newestMessage?.images?.size,
    ) {
        if (historyLoaded && newestMessage != null) {
            // reverseLayout 下索引 0 就是会话底部；发送和流式更新时持续跟随最新内容。
            listState.scrollToItem(0)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages.asReversed(), key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(language.text("PIRT 对话", "PIRT conversation"), message.text))
                            },
                            onResend = if (message.role == MessageRole.USER && !agentBusy && historyLoaded) {
                                {
                                    chat.prompt(message.text, message.images)
                                }
                            } else null,
                            onFork = if (
                                message.role == MessageRole.USER &&
                                message.entryId != null &&
                                !agentBusy &&
                                historyLoaded &&
                                session.path != null
                            ) {
                                {
                                    chat.fork(message.entryId) { result ->
                                        result.getOrNull()?.let(onSessionReplaced)
                                    }
                                }
                            } else null,
                        )
                    }
                    if (!historyLoaded && !isFreshSession) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(language.text("正在加载对话记录……", "Loading conversation history…"))
                            }
                        }
                    } else if (messages.isEmpty()) {
                        item {
                            EmptyState(
                                language.text("开始一个会话", "Start a conversation"),
                                language.text("PIRT 会在虚拟电脑中完成工作。", "PIRT works in the virtual computer."),
                            )
                        }
                    }
                }
                if (
                    agentBusy || progress.execution.isNotEmpty() ||
                    status != language.text("就绪", "Ready")
                ) {
                    ExecutionTrace(
                        progress = progress,
                        status = status,
                        active = agentBusy || ui.process == ProcessState.STARTING,
                        expanded = progressExpanded,
                        onToggle = { progressExpanded = !progressExpanded },
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val modelLabel = when {
                        !ui.agentLoaded -> language.text("模型：加载中…", "Model: loading…")
                        modelUnset && authState.selectedModel != null ->
                            language.text("模型：${authState.selectedModel}", "Model: ${authState.selectedModel}")
                        modelUnset || progress.model.isBlank() -> language.text("模型：选择", "Model: select")
                        else -> language.text("模型：${progress.model}", "Model: ${progress.model}")
                    }
                    ComposerShortcut(modelLabel, enabled = ui.agentLoaded) {
                        modelMenuRequested = true
                        chat.requestModels()
                    }
                    ComposerShortcut(language.text("思考：", "Thinking: ") + (progress.thinkingLevel?.let { thinkingLevelLabel(it, language) } ?: language.text("默认", "Default"))) {
                        thinkingLevelsRequested = true
                        chat.requestThinkingLevels()
                    }
                    ui.stats?.let { stats ->
                        ComposerShortcut(sessionStatsLabel(stats, language)) {
                            showSessionStats = true
                            chat.requestStats()
                        }
                    }
                }
                if (pendingImages.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        pendingImages.forEach { image ->
                            ChatImagePreview(
                                image = image,
                                modifier = Modifier.size(60.dp),
                                onClick = { pendingImages.remove(image) },
                            )
                        }
                        Text(language.text("点按移除", "Tap to remove"), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (ui.steeringMessages.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ui.steeringMessages.forEach { message ->
                            Text(
                                language.text("待引导：$message", "Queued guidance: $message"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (ui.extensionStatuses.isNotEmpty() || ui.extensionWidgets.isNotEmpty()) {
                    ExtensionIndicators(ui.extensionStatuses, ui.extensionWidgets)
                }
                ChatInputBar(
                    draft = draft,
                    onDraftChange = updateDraft,
                    placeholder = language.text("发消息给 PIRT", "Message PIRT"),
                    canSend = (draft.isNotBlank() || pendingImages.isNotEmpty()) &&
                        (if (agentBusy) true else ui.ready),
                    agentBusy = agentBusy,
                    streaming = ui.streaming,
                    onAttach = { showAttachmentSheet = true },
                    onSend = {
                        val text = draft.trim().ifBlank { language.text("请查看这些图片", "Please review these images") }
                        val images = pendingImages.toList()
                        updateDraft("")
                        pendingImages.clear()
                        onPromptSubmitted(text, ui.sessionId)
                        chat.prompt(text, images)
                    },
                    onSteer = {
                        val text = draft.trim().ifBlank { language.text("请查看这些图片", "Please review these images") }
                        val images = pendingImages.toList()
                        updateDraft("")
                        pendingImages.clear()
                        chat.steer(text, images)
                    },
                    onAbort = chat::abort,
                )
    }

    if (showAttachmentSheet) {
        ComposerAttachmentSheet(
            onPickImage = {
                showAttachmentSheet = false
                imagePicker.launch("image/*")
            },
            onDismiss = { showAttachmentSheet = false },
        )
    }

    extensionUiRequest?.takeIf { it.method in setOf("select", "confirm", "input", "editor") }?.let { request ->
        ExtensionRequestDialog(
            request = request,
            onValue = { value -> chat.respondExtensionUi(request.id, value = value) },
            onConfirm = { confirmed -> chat.respondExtensionUi(request.id, confirmed = confirmed) },
            onCancel = { chat.respondExtensionUi(request.id, cancelled = true) },
        )
    }

    if (modelMenuExpanded) {
        ChoiceDialog(
            title = language.text("切换模型", "Switch model"),
            values = availableModels,
            label = { model ->
                val selected = model.provider == progress.provider &&
                    (model.name == progress.model || model.id == progress.model)
                "${model.name} · ${model.provider}${if (selected) language.text(" · 当前", " · current") else ""}"
            },
            onSelect = { model ->
                modelMenuExpanded = false
                chat.setModel(model.provider, model.id)
            },
            onDismiss = { modelMenuExpanded = false },
        )
    }

    if (showPiControls) {
        PiControlsDialog(
            commands = commands,
            autoCompaction = autoCompaction,
            autoRetry = autoRetry,
            canClone = !agentBusy && historyLoaded && session.path != null,
            onCommand = {
                updateDraft("/${it.name} ")
                showPiControls = false
            },
            onClone = {
                showPiControls = false
                chat.cloneSession { result ->
                    result.getOrNull()?.let(onSessionReplaced)
                }
            },
            canExport = !agentBusy && historyLoaded && messages.isNotEmpty() && !exportingHtml,
            onExport = {
                exportingHtml = true
                showPiControls = false
                chat.exportHtml { result ->
                    exportingHtml = false
                    result.onSuccess { guestPath ->
                        runCatching { shareConversationExport(context, workspace, guestPath) }
                            .onFailure { Toast.makeText(context, it.message ?: "无法分享会话", Toast.LENGTH_LONG).show() }
                    }.onFailure {
                        Toast.makeText(context, it.message ?: "导出会话失败", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onToggleAutoCompaction = {
                autoCompaction = !autoCompaction
                chat.setAutoCompaction(autoCompaction)
            },
            onToggleAutoRetry = {
                autoRetry = !autoRetry
                chat.setAutoRetry(autoRetry)
            },
            onDismiss = { showPiControls = false },
        )
    }
    if (showThinkingLevels) {
        ChoiceDialog(
            title = language.text("思考强度", "Thinking level"),
            values = thinkingLevels,
            label = { thinkingLevelLabel(it, language) },
            onSelect = {
                chat.setThinkingLevel(it)
                showThinkingLevels = false
            },
            onDismiss = { showThinkingLevels = false },
        )
    }
    if (showSessionStats) {
        SessionStatsDialog(
            stats = ui.stats,
            compacting = ui.turn == TurnState.COMPACTING,
            canCompact = ui.ready && !agentBusy,
            onCompact = chat::compact,
            onDismiss = { showSessionStats = false },
        )
    }
}

private fun toolDisplayName(name: String, language: AppLanguage): String = when (name) {
    "bash" -> language.text("运行命令", "Run command")
    "read" -> language.text("读取文件", "Read file")
    "write" -> language.text("写入文件", "Write file")
    "edit" -> language.text("修改文件", "Edit file")
    "grep", "find" -> language.text("搜索文件", "Search files")
    "ls" -> language.text("查看目录", "List directory")
    else -> language.text("使用 $name", "Use $name")
}

@Composable
private fun ExecutionTrace(
    progress: ConversationProgress,
    status: String,
    active: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val current = progress.execution.lastOrNull { item ->
        when (item) {
            is PiThinkingState -> !item.finished
            is PiToolState -> !item.finished
        }
    } ?: progress.execution.lastOrNull()
    val collapsedText = when (current) {
        is PiThinkingState -> current.text.replace(Regex("\\s+"), " ").trim().ifBlank { status }
        is PiToolState -> buildString {
            append(if (current.finished) toolDisplayName(current.name, language) else language.text("正在${toolDisplayName(current.name, language)}", "Running ${toolDisplayName(current.name, language)}"))
            current.summary.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
        }
        null -> status
    }
    if (!expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                collapsedText,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(language.text("展开 ﹀", "Expand ﹀"), style = MaterialTheme.typography.labelMedium)
        }
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(language.text("执行流", "Execution"), fontWeight = FontWeight.SemiBold)
                    Text(
                        status,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(language.text("收起 ︿", "Collapse ︿"), style = MaterialTheme.typography.labelMedium)
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                progress.execution.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider()
                    when (item) {
                        is PiThinkingState -> ThinkingTraceItem(item)
                        is PiToolState -> ToolTraceItem(item)
                    }
                }
                if (progress.execution.isEmpty()) {
                    Text(language.text("等待 PIRT 执行事件…", "Waiting for PIRT execution events…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ThinkingTraceItem(item: PiThinkingState) {
    val language = LocalAppLanguage.current
    Text(
        if (item.finished) language.text("思考 · 完成", "Thinking · complete") else language.text("思考 · 进行中", "Thinking · in progress"),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
    )
    if (item.text.isNotBlank()) {
        SelectionContainer {
            Markdown(
                markdownState = rememberMarkdownState(item.text, retainState = true),
                typography = pirtMarkdownTypography(),
                imageTransformer = rememberPirtMarkdownImageTransformer(),
                components = rememberPirtMarkdownComponents(),
            )
        }
    }
}

@Composable
private fun ToolTraceItem(item: PiToolState) {
    val language = LocalAppLanguage.current
    val state = when {
        item.failed -> language.text("失败", "Failed")
        item.finished -> language.text("完成", "Complete")
        else -> language.text("进行中", "In progress")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF101214),
        contentColor = Color(0xFFF3F4F6),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${toolDisplayName(item.name, language)} · $state",
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            val invocation = if (item.name == "bash") item.summary else item.input
            if (invocation.isNotBlank()) {
                TerminalTraceText(if (item.name == "bash") language.text("命令", "Command") else language.text("调用", "Invocation"), invocation)
            }
            if (item.output.isNotBlank()) {
                TerminalTraceText(language.text("输出", "Output"), item.output)
            }
            if (item.images.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item.images.forEach { image ->
                        ChatImagePreview(
                            ChatImage(image.data, image.mimeType),
                            Modifier.width(240.dp).heightIn(max = 320.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalTraceText(label: String, value: String) {
    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
    SelectionContainer {
        Text(
            value,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            color = Color(0xFFF3F4F6),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MessageBubbleActionIcon(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onCopy: () -> Unit,
    onResend: (() -> Unit)?,
    onFork: (() -> Unit)?,
) {
    val language = LocalAppLanguage.current
    val isUser = message.role == MessageRole.USER
    val color = when (message.role) {
        MessageRole.USER -> MaterialTheme.colorScheme.primaryContainer
        MessageRole.ASSISTANT -> Color.Transparent
        MessageRole.SYSTEM -> MaterialTheme.colorScheme.errorContainer
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        val bubbleModifier = if (message.role == MessageRole.ASSISTANT) {
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)
        } else {
            Modifier.fillMaxWidth(0.88f).background(color, RoundedCornerShape(16.dp)).padding(14.dp)
        }
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            if (message.images.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    message.images.take(4).forEach {
                        ChatImagePreview(it, Modifier.fillMaxWidth().widthIn(max = 520.dp).heightIn(max = 420.dp))
                    }
                }
            }
            if (message.role == MessageRole.ASSISTANT) {
                val markdownState = rememberMarkdownState(message.text, retainState = true)
                SelectionContainer {
                    Markdown(
                        markdownState = markdownState,
                        modifier = bubbleModifier,
                        typography = pirtMarkdownTypography(),
                        imageTransformer = rememberPirtMarkdownImageTransformer(),
                        components = rememberPirtMarkdownComponents(),
                    )
                }
            } else {
                SelectionContainer {
                    Text(
                        text = message.text,
                        modifier = bubbleModifier,
                        color = if (message.role == MessageRole.SYSTEM) MaterialTheme.colorScheme.onErrorContainer else Color.Unspecified,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                MessageBubbleActionIcon(Icons.Outlined.ContentCopy, language.text("复制", "Copy"), onCopy)
                onResend?.let { MessageBubbleActionIcon(Icons.Outlined.Refresh, language.text("重发", "Resend"), it) }
                onFork?.let { MessageBubbleActionIcon(Icons.AutoMirrored.Outlined.CallSplit, "Fork", it) }
            }
        }
    }
}

@Composable
private fun ChatImagePreview(
    image: ChatImage,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val language = LocalAppLanguage.current
    var viewerOpen by remember { mutableStateOf(false) }
    val bitmap = remember(image.data) {
        runCatching {
            val bytes = Base64.decode(image.data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        val imageAspectRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        Image(
            bitmap = bitmap,
            contentDescription = language.text("对话图片", "Conversation image"),
            modifier = modifier
                .aspectRatio(imageAspectRatio)
                .clip(RoundedCornerShape(8.dp))
                .clickable { if (onClick != null) onClick() else viewerOpen = true },
            contentScale = ContentScale.Fit,
        )
        if (viewerOpen) {
            FullScreenImageViewer(image = image, bitmap = bitmap, onDismiss = { viewerOpen = false })
        }
    } else {
        Text(language.text("图片无法显示", "Image cannot be displayed"), modifier = modifier)
    }
}

@Composable
internal fun FullScreenImageViewer(
    image: ChatImage,
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val saveScope = rememberCoroutineScope()
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var pendingLegacySave by remember { mutableStateOf(false) }
    fun saveToGallery() {
        saveScope.launch {
            withContext(Dispatchers.IO) { saveImageToGallery(context, image) }.onSuccess {
                Toast.makeText(context, language.text("已保存到相册/PIRT", "Saved to Gallery/PIRT"), Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(context, error.message ?: language.text("图片保存失败", "Failed to save image"), Toast.LENGTH_LONG).show()
            }
        }
    }
    val requestLegacyStorage = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingLegacySave) saveToGallery()
        else if (!granted) Toast.makeText(context, language.text("需要存储权限才能保存到相册", "Storage permission is required to save to the gallery"), Toast.LENGTH_LONG).show()
        pendingLegacySave = false
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF2111412))
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    })
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = nextScale
                        offset = if (nextScale == 1f) Offset.Zero else offset + pan
                    }
                },
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = language.text("查看原图", "View full image"),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(onClick = {
                    if (
                        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    ) {
                        pendingLegacySave = true
                        requestLegacyStorage.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        saveToGallery()
                    }
                }) {
                    Text(language.text("保存", "Save"), color = Color.White)
                }
                TextButton(onClick = onDismiss) {
                    Text(language.text("关闭", "Close"), color = Color.White)
                }
            }
        }
    }
}

private fun saveImageToGallery(context: Context, image: ChatImage): Result<Uri> = runCatching {
    val language = io.github.zixt233.pirt.i18n.AppLanguageStore.current(context)
    val resolver = context.contentResolver
    val fileName = "PIRT-${System.currentTimeMillis()}.${imageFileExtension(image.mimeType)}"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, image.mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PIRT")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PIRT")
                .apply { check(exists() || mkdirs()) { language.text("无法创建相册目录", "Could not create gallery directory") } }
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, File(directory, fileName).absolutePath)
        }
    }
    val uri = checkNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
        language.text("无法在系统相册中创建图片", "Could not create image in the system gallery")
    }
    try {
        resolver.openOutputStream(uri, "w").use { output ->
            checkNotNull(output) { language.text("无法写入图片", "Could not write image") }
            output.write(Base64.decode(image.data, Base64.DEFAULT))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
        }
        uri
    } catch (error: Throwable) {
        resolver.delete(uri, null, null)
        throw error
    }
}

private fun imageFileExtension(mimeType: String): String = when (mimeType.lowercase()) {
    "image/jpeg" -> "jpg"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    else -> "png"
}

@Composable
private fun pirtMarkdownTypography() = markdownTypography(
    h1 = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold),
    h2 = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    h3 = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    h4 = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
    h5 = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
    h6 = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
)

private fun prepareChatImage(context: Context, uri: Uri): ChatImage {
    val resolver = context.contentResolver
    val original = resolver.openInputStream(uri).use { input ->
        checkNotNull(input) { "无法读取图片" }
        input.readBytes()
    }
    check(original.size <= 20 * 1024 * 1024) { "图片不能超过 20 MB" }
    val decoded = BitmapFactory.decodeByteArray(original, 0, original.size)
    if (decoded == null) {
        val mime = resolver.getType(uri).orEmpty()
        check(mime == "image/gif" || mime == "image/webp") { "不支持的图片格式" }
        return ChatImage(Base64.encodeToString(original, Base64.NO_WRAP), mime)
    }
    val maxEdge = 1800
    val scale = minOf(1f, maxEdge.toFloat() / maxOf(decoded.width, decoded.height))
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
    } else decoded
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
    if (bitmap !== decoded) bitmap.recycle()
    decoded.recycle()
    return ChatImage(Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP), "image/jpeg")
}

@Composable
private fun PiControlsDialog(
    commands: List<PiCommand>,
    autoCompaction: Boolean,
    autoRetry: Boolean,
    canClone: Boolean,
    canExport: Boolean,
    onCommand: (PiCommand) -> Unit,
    onClone: () -> Unit,
    onExport: () -> Unit,
    onToggleAutoCompaction: () -> Unit,
    onToggleAutoRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val actionCommands = commands.filter { it.source == "extension" }
    val promptTemplates = commands.filter { it.source == "prompt" }
    val workflows = commands.filter { it.source == "skill" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(language.text("PIRT 工具箱", "PIRT tools")) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(430.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { Text(language.text("会话控制", "Conversation controls"), fontWeight = FontWeight.SemiBold) }
                item { TextButton(onClick = onClone, enabled = canClone) { Text(language.text("克隆当前会话", "Clone current conversation")) } }
                item {
                    TextButton(onClick = onExport, enabled = canExport) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(language.text("导出并分享会话", "Export and share conversation"))
                    }
                }
                item {
                    TextButton(onClick = onToggleAutoCompaction) {
                        Text(language.text("自动压缩：${if (autoCompaction) "已开启" else "已关闭"}", "Auto compaction: ${if (autoCompaction) "on" else "off"}"))
                    }
                }
                item {
                    TextButton(onClick = onToggleAutoRetry) {
                        Text(language.text("自动重试：${if (autoRetry) "已开启" else "已关闭"}", "Auto retry: ${if (autoRetry) "on" else "off"}"))
                    }
                }
                item { Text(language.text("PIRT 扩展能力", "PIRT extensions"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
                if (commands.isEmpty()) {
                    item { Text(language.text("当前 workspace 没有额外的快捷操作、提示词或工作流。", "This workspace has no additional actions, prompt templates, or workflows.")) }
                } else {
                    commandGroup(language.text("快捷操作", "Actions"), language.text("执行扩展提供的功能", "Run features provided by extensions"), actionCommands, onCommand)
                    commandGroup(language.text("提示词", "Prompts"), language.text("展开可复用的提示内容", "Insert reusable prompt content"), promptTemplates, onCommand)
                    commandGroup(language.text("工作流", "Workflows"), language.text("让 AI 按专业流程完成任务", "Let AI follow a specialized workflow"), workflows, onCommand)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(language.text("关闭", "Close")) } },
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.commandGroup(
    title: String,
    subtitle: String,
    commands: List<PiCommand>,
    onCommand: (PiCommand) -> Unit,
) {
    if (commands.isEmpty()) return
    item(key = "group:$title") {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    items(commands, key = { "${it.source}:${it.name}" }) { command ->
        TextButton(onClick = { onCommand(command) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Text("/${command.name}")
                if (command.description.isNotBlank()) {
                    Text(command.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ExtensionIndicators(statuses: Map<String, String>, widgets: Map<String, List<String>>) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        statuses.values.forEach { status ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(status, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
        widgets.values.forEach { lines ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(lines.joinToString("\n"), modifier = Modifier.fillMaxWidth().padding(10.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ExtensionRequestDialog(
    request: PiExtensionUiRequest,
    onValue: (String) -> Unit,
    onConfirm: (Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val language = LocalAppLanguage.current
    var value by remember(request.id) { mutableStateOf(request.prefill) }
    when (request.method) {
        "select" -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(request.title.ifBlank { language.text("请选择", "Select an option") }) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(request.options) { option ->
                        TextButton(onClick = { onValue(option) }, modifier = Modifier.fillMaxWidth()) {
                            Text(option, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCancel) { Text(language.text("取消", "Cancel")) } },
        )
        "confirm" -> AlertDialog(
            onDismissRequest = { onConfirm(false) },
            title = { Text(request.title.ifBlank { language.text("请确认", "Please confirm") }) },
            text = { Text(request.message) },
            confirmButton = { TextButton(onClick = { onConfirm(true) }) { Text(language.text("确认", "Confirm")) } },
            dismissButton = { TextButton(onClick = { onConfirm(false) }) { Text(language.text("取消", "Cancel")) } },
        )
        "input", "editor" -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(request.title.ifBlank { if (request.method == "editor") language.text("编辑内容", "Edit content") else language.text("请输入", "Enter a value") }) },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { request.placeholder.takeIf(String::isNotBlank)?.let { Text(it) } },
                    singleLine = request.method == "input",
                    minLines = if (request.method == "editor") 6 else 1,
                    maxLines = if (request.method == "editor") 14 else 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { onValue(value) }) { Text(language.text("确定", "OK")) } },
            dismissButton = { TextButton(onClick = onCancel) { Text(language.text("取消", "Cancel")) } },
        )
    }
}

@Composable
private fun SessionStatsDialog(
    stats: io.github.zixt233.pirt.runtime.pi.PiSessionStats?,
    compacting: Boolean,
    canCompact: Boolean,
    onCompact: () -> Unit,
    onDismiss: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val usage = stats?.tokens
    val context = stats?.contextUsage
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(language.text("上下文与 Token", "Context and tokens")) },
        text = {
            if (stats == null) {
                Text(language.text("正在读取统计…", "Loading statistics…"))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        context?.percent?.let { language.text("当前上下文 ${it.roundToInt().coerceIn(0, 100)}%", "Current context ${it.roundToInt().coerceIn(0, 100)}%") }
                            ?: language.text("当前上下文暂不可用", "Current context unavailable"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    context?.tokens?.let { used ->
                        Text("${formatTokenCount(used)} / ${formatTokenCount(context.contextWindow)}")
                    }
                    HorizontalDivider()
                    Text(language.text("累计 Token ${formatTokenCount(usage?.total ?: 0)}", "Total tokens ${formatTokenCount(usage?.total ?: 0)}"))
                    Text(language.text("输入 ${formatTokenCount(usage?.input ?: 0)} · 输出 ${formatTokenCount(usage?.output ?: 0)}", "Input ${formatTokenCount(usage?.input ?: 0)} · output ${formatTokenCount(usage?.output ?: 0)}"))
                    Text(language.text("缓存读取 ${formatTokenCount(usage?.cacheRead ?: 0)} · 缓存写入 ${formatTokenCount(usage?.cacheWrite ?: 0)}", "Cache read ${formatTokenCount(usage?.cacheRead ?: 0)} · cache write ${formatTokenCount(usage?.cacheWrite ?: 0)}"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(language.text("关闭", "Close")) } },
        dismissButton = {
            TextButton(onClick = onCompact, enabled = canCompact && !compacting) {
                Text(if (compacting) language.text("正在压缩…", "Compacting…") else language.text("立即压缩", "Compact now"))
            }
        },
    )
}

private fun sessionStatsLabel(stats: io.github.zixt233.pirt.runtime.pi.PiSessionStats, language: AppLanguage): String {
    val context = stats.contextUsage?.percent?.let { language.text("上下文 ${it.roundToInt().coerceIn(0, 100)}%", "Context ${it.roundToInt().coerceIn(0, 100)}%") } ?: language.text("上下文 --", "Context --")
    return "$context · Token ${formatTokenCount(stats.tokens.total)}"
}

private fun formatTokenCount(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}

private fun shareConversationExport(context: Context, workspace: WorkspaceConfig, guestPath: String) {
    val language = io.github.zixt233.pirt.i18n.AppLanguageStore.current(context)
    require(guestPath.startsWith("/workspace/")) { "PIRT 返回了无效的导出路径" }
    val workspaceRoot = File(workspace.rootPath).canonicalFile
    val export = File(workspaceRoot, guestPath.removePrefix("/workspace/")).canonicalFile
    require(export.path.startsWith(workspaceRoot.path + File.separator) && export.isFile) {
        language.text("找不到导出的会话文件", "Exported conversation file not found")
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", export)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/html"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, language.text("分享会话", "Share conversation")))
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    values: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val language = LocalAppLanguage.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().height(400.dp)) {
                if (values.isEmpty()) item { Text(language.text("没有可用选项", "No options available")) }
                items(values) { value ->
                    TextButton(onClick = { onSelect(value) }, modifier = Modifier.fillMaxWidth()) {
                        Text(label(value), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(language.text("取消", "Cancel")) } },
    )
}

private fun isUnsetModel(modelId: String, modelName: String): Boolean {
    val label = modelName.ifBlank { modelId }.trim()
    return label.isEmpty() || label.equals("unknown", ignoreCase = true) || label.equals("none", ignoreCase = true)
}

private fun thinkingLevelLabel(level: String, language: AppLanguage): String = when (level) {
    "off" -> language.text("关闭", "Off")
    "minimal" -> language.text("最少", "Minimal")
    "low" -> language.text("较低", "Low")
    "medium" -> language.text("中等", "Medium")
    "high" -> language.text("较高", "High")
    "xhigh" -> language.text("很高", "Very high")
    "max" -> language.text("最高", "Maximum")
    else -> level
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NameDialog(title: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val language = LocalAppLanguage.current
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(language.text("创建", "Create")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(language.text("取消", "Cancel")) } },
    )
}
