package com.example.pirt.ui

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pirt.model.ChatImage
import com.example.pirt.model.ChatMessage
import com.example.pirt.model.MessageRole
import com.example.pirt.model.PiSession
import com.example.pirt.model.WorkspaceConfig
import com.example.pirt.runtime.OverlayPermission
import com.example.pirt.runtime.PRootRuntime
import com.example.pirt.runtime.RuntimeConnection
import com.example.pirt.runtime.InstallState
import com.example.pirt.runtime.RuntimeState
import com.example.pirt.runtime.RuntimeInstaller
import com.example.pirt.runtime.RuntimeService
import com.example.pirt.runtime.pi.PiCommand
import com.example.pirt.runtime.pi.PiBranchResult
import com.example.pirt.runtime.pi.PiExecutionItem
import com.example.pirt.runtime.pi.PiThinkingState
import com.example.pirt.runtime.pi.PiToolState
import com.example.pirt.runtime.pi.PiModel as PiSessionModel
import com.example.pirt.runtime.pi.PiSessionSummary
import com.example.pirt.runtime.pi.ProcessState
import com.example.pirt.runtime.pi.TurnState
import com.example.pirt.ui.app.AppViewModel
import com.example.pirt.ui.chat.ChatViewModel
import com.example.pirt.ui.chat.ChatUiState
import com.example.pirt.ui.settings.OverlayPermissionPrompt
import com.example.pirt.ui.settings.SettingsPage
import android.widget.Toast
import com.example.pirt.data.WorkspaceDocumentsProvider
import com.example.pirt.ui.setup.EnvironmentSetupPage
import com.example.pirt.ui.tools.GraphicsPage
import com.example.pirt.ui.tools.ProcessesPage
import com.example.pirt.ui.tools.TerminalPage
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch

private enum class Page { CHAT, TERMINAL, GRAPHICS, PROCESSES, SETTINGS }
private const val COLLAPSED_SESSION_COUNT = 10
private data class PendingConversation(val session: PiSession, val piId: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PirtApp() {
    val context = LocalContext.current
    val appViewModel: AppViewModel = viewModel()
    val sessions by appViewModel.runtimeConnection.sessions.collectAsState()
    val sessionsLoaded by appViewModel.runtimeConnection.sessionsLoaded.collectAsState()
    val summaries by appViewModel.runtimeConnection.summaries.collectAsState()
    val runtime = appViewModel.runtime
    val installer = remember(runtime) { RuntimeInstaller(context.applicationContext, runtime.paths) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var runtimeState by remember { mutableStateOf(runtime.state()) }
    var installState by remember { mutableStateOf<InstallState>(InstallState.Idle) }
    var installAttempt by remember { mutableIntStateOf(0) }
    var pageName by rememberSaveable { mutableStateOf(Page.CHAT.name) }
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
    var settingsFocusOverlay by rememberSaveable { mutableStateOf(false) }
    val page = when (pageName) {
        "FILES" -> Page.CHAT
        else -> Page.valueOf(pageName)
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
            installer.install { next ->
                mainHandler.post {
                    installState = next
                    if (next is InstallState.Complete) {
                        runtimeState = runtime.state()
                        if (runtimeState is RuntimeState.NotInstalled) {
                            installState = InstallState.Failed((runtimeState as RuntimeState.NotInstalled).reason)
                        }
                    }
                }
            }
        }
    }

    if (runtimeState !is RuntimeState.Ready) {
        EnvironmentSetupPage(
            state = installState,
            onRetry = { installAttempt += 1 },
        )
        return
    }

    LaunchedEffect(runtimeState) {
        appViewModel.runtimeConnection.connect()
        appViewModel.runtimeConnection.refreshSessions()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = OverlayPermission.canDraw(context)
                overlayEnabled = OverlayPermission.isUserEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun navigate(target: Page) { pageName = target.name }
    BackHandler(enabled = drawerState.isOpen || page != Page.CHAT) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            else -> navigate(Page.CHAT)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
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
                onTerminal = { navigate(Page.TERMINAL); scope.launch { drawerState.close() } },
                onGraphics = { navigate(Page.GRAPHICS); scope.launch { drawerState.close() } },
                onProcesses = { navigate(Page.PROCESSES); scope.launch { drawerState.close() } },
                onOverlay = {
                    if (overlayGranted) {
                        overlayEnabled = !overlayEnabled
                        OverlayPermission.setUserEnabled(context, overlayEnabled)
                        RuntimeService.refreshNotification()
                    } else {
                        settingsFocusOverlay = true
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
        OverlayPermissionPrompt()
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (page) {
                                Page.SETTINGS -> "设置"
                                Page.TERMINAL -> "终端"
                                Page.GRAPHICS -> "图形桌面"
                                Page.PROCESSES -> "进程管理"
                                else -> session?.displayName ?: "新会话"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        TextButton(onClick = {
                            if (page != Page.CHAT) navigate(Page.CHAT)
                            else scope.launch { drawerState.open() }
                        }) { Text(if (page != Page.CHAT) "返回" else "☰", style = MaterialTheme.typography.headlineSmall) }
                    },
                    actions = {
                        if (page == Page.CHAT && session != null) {
                            IconButton(
                                onClick = { requestPiCommands?.invoke() },
                                enabled = requestPiCommands != null,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Code,
                                    contentDescription = "Pi 命令",
                                )
                            }
                        }
                    },
                )
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
                    Page.TERMINAL -> TerminalPage(appViewModel.workspace, appViewModel.runtimeConnection)
                    Page.GRAPHICS -> GraphicsPage(appViewModel.workspace, appViewModel.runtimeConnection)
                    Page.PROCESSES -> ProcessesPage(appViewModel.runtimeConnection)
                    Page.SETTINGS -> SettingsPage(
                        runtime,
                        appViewModel.runtimeConnection,
                        focusOverlaySection = settingsFocusOverlay,
                        onOverlaySectionFocused = { settingsFocusOverlay = false },
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
    onTerminal: () -> Unit,
    onGraphics: () -> Unit,
    onProcesses: () -> Unit,
    onOverlay: () -> Unit,
    overlayActive: Boolean = false,
    onSettings: () -> Unit,
) {
    var menuSessionId by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<PiSession?>(null) }
    var deleteTarget by remember { mutableStateOf<PiSession?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val sessionListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    ModalDrawerSheet(Modifier.fillMaxWidth(0.86f)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("PIRT", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "设置")
                }
            }
            DrawerFeatureGrid(
                items = listOf(
                    DrawerFeatureItem("文件", Icons.Outlined.FolderOpen, onFiles),
                    DrawerFeatureItem("终端", Icons.Outlined.Terminal, onTerminal),
                    DrawerFeatureItem("图形桌面", Icons.Outlined.DesktopWindows, onGraphics),
                    DrawerFeatureItem("进程管理", Icons.Outlined.AccountTree, onProcesses),
                    DrawerFeatureItem("悬浮窗", Icons.Outlined.PictureInPictureAlt, onOverlay, active = overlayActive),
                ),
            )
            HorizontalDivider(Modifier.padding(top = 10.dp, bottom = 14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("会话", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    if (sessionsLoaded) {
                        Text(
                            "${sessions.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            LazyColumn(
                state = sessionListState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 10.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
                            Text("正在读取 Pi 会话……", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (sessions.isEmpty()) {
                    item {
                        Text(
                            "暂无历史会话",
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
                        onDelete = { menuSessionId = null; deleteTarget = session },
                    )
                }
                if (sessions.size > COLLAPSED_SESSION_COUNT) {
                    item(key = "session-list-toggle") {
                        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (expanded) "收起" else "显示其余 ${sessions.size - COLLAPSED_SESSION_COUNT} 个会话",
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
                            onDelete = { menuSessionId = null; deleteTarget = session },
                        )
                    }
                }
            }
        }
    }
    renameTarget?.let { target ->
        NameDialog(
            title = "重命名会话",
            label = "标题",
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                onRenameSession(target, title)
                renameTarget = null
            },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除会话？") },
            text = {
                Text(
                    if (target.runtimeKey in transientSessionIds) {
                        "该会话尚未写入 Pi JSONL，将移除临时表项。workspace 文件不会删除。"
                    } else {
                        "将删除 Pi 会话“${target.displayName}”的 JSONL。workspace 文件不会删除。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { deleteTarget = null; onDeleteConversation(target) }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun NewConversationDrawerRow(selected: Boolean, hasText: Boolean, onOpen: () -> Unit) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("新会话", fontWeight = FontWeight.SemiBold)
            Text(
                if (hasText) "草稿已保留" else "预提交区域",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
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
    onDelete: () -> Unit,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(session.displayName)
                sessionActivityLabel(activity)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Box {
                TextButton(onClick = onMenu) { Text("⋮") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
                    DropdownMenuItem(text = { Text("重命名") }, onClick = onRename)
                    DropdownMenuItem(text = { Text("删除") }, onClick = onDelete)
                }
            }
        }
    }
}

private fun sessionActivityLabel(activity: PiSessionSummary?): String? = when {
    activity == null -> null
    activity.turn in setOf(TurnState.QUEUED, TurnState.GENERATING, TurnState.RUNNING_TOOL, TurnState.STOPPING) -> "AI 正在运行"
    else -> null
}

private fun chatStatus(state: ChatUiState): String = when {
    state.error != null -> state.error.orEmpty()
    state.process == ProcessState.STARTING -> "正在连接 Pi"
    state.process == ProcessState.CRASHED || state.process == ProcessState.EXITED -> "Pi 未就绪"
    !state.historyLoaded -> "正在加载对话"
    state.turn == TurnState.QUEUED -> "消息等待发送"
    state.turn == TurnState.GENERATING -> "模型正在思考"
    state.turn == TurnState.RUNNING_TOOL -> "正在执行工具"
    state.turn == TurnState.STOPPING -> "正在停止"
    state.turn == TurnState.FAILED -> "执行失败"
    else -> "就绪"
}

private fun isDuplicatePiCommand(command: PiCommand): Boolean = command.name.lowercase() in setOf(
    "model", "models", "thinking", "thinking-level", "compact", "new", "fork", "clone", "tree",
)

@Composable
private fun ComposerShortcut(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
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
            .heightIn(min = 168.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        userScrollEnabled = false,
    ) {
        items(items, key = { it.label }) { item ->
            IconGridCell(
                label = item.label,
                icon = item.icon,
                onClick = item.onClick,
                active = item.active,
            )
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
    val options = listOf(
        ComposerAttachmentOption("图片", Icons.Outlined.Image),
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
                            "图片" -> onPickImage()
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
                    TextButton(onClick = onSteer, enabled = canSend) { Text("引导") }
                }
                IconButton(onClick = onAbort, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Stop, contentDescription = "停止")
                }
            } else {
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
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
                        Icon(Icons.Outlined.Add, contentDescription = "添加附件")
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
                        Icon(Icons.Outlined.Add, contentDescription = "添加附件")
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
    composerText: String? = null,
    onComposerTextChange: (String) -> Unit = {},
    onPiSessionId: (String) -> Unit = {},
    onPromptSubmitted: (String, String?) -> Unit = { _, _ -> },
    onSessionReplaced: (PiBranchResult) -> Unit = {},
    onRegisterPiCommands: ((() -> Unit)?) -> Unit = {},
) {
    val context = LocalContext.current
    val chat: ChatViewModel = viewModel(
        key = "chat:${session.runtimeKey}",
        factory = ChatViewModel.factory(session, runtime),
    )
    val ui by chat.state.collectAsState()
    val messages = ui.messages
    var localComposerText by rememberSaveable(session.runtimeKey) { mutableStateOf("") }
    val draft = composerText ?: localComposerText
    val updateDraft: (String) -> Unit = { value ->
        if (composerText != null) onComposerTextChange(value) else localComposerText = value
    }
    val isFreshSession = session.path == null
    val status = chatStatus(ui)
    val agentBusy = ui.busy
    val historyLoaded = ui.historyLoaded
    val progress = ConversationProgress(
        provider = ui.provider,
        model = ui.modelName.ifBlank { ui.modelId },
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
    val thinkingLevels = ui.thinkingLevels
    var showPiControls by remember(session.runtimeKey) { mutableStateOf(false) }
    var showThinkingLevels by remember(session.runtimeKey) { mutableStateOf(false) }
    var showAttachmentSheet by remember(session.runtimeKey) { mutableStateOf(false) }
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
        if (ui.thinkingLevelsRevision > 0) {
            showPiControls = false
            showThinkingLevels = true
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages.asReversed(), key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("PIRT 对话", message.text))
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
                                Text("正在加载对话记录……")
                            }
                        }
                    } else if (messages.isEmpty()) {
                        item {
                            EmptyState("开始一个会话", "Pi 会在共享 workspace 中完成工作。")
                        }
                    }
                }
                if (
                    agentBusy || progress.execution.isNotEmpty() ||
                    status != "就绪"
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
                        !ui.agentLoaded -> "模型：加载中…"
                        progress.model.isBlank() -> "模型：选择"
                        else -> "模型：${progress.model}"
                    }
                    ComposerShortcut(modelLabel, enabled = ui.agentLoaded) {
                        modelMenuRequested = true
                        chat.requestModels()
                    }
                    ComposerShortcut("思考：${progress.thinkingLevel?.let(::thinkingLevelLabel) ?: "默认"}") {
                        chat.requestThinkingLevels()
                    }
                }
                if (pendingImages.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        pendingImages.forEach { image ->
                            ChatImagePreview(image, Modifier.size(60.dp).clickable { pendingImages.remove(image) })
                        }
                        Text("点按移除", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (ui.steeringMessages.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ui.steeringMessages.forEach { message ->
                            Text(
                                "待引导：$message",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                ChatInputBar(
                    draft = draft,
                    onDraftChange = updateDraft,
                    placeholder = "发消息给 Pi",
                    canSend = (draft.isNotBlank() || pendingImages.isNotEmpty()) &&
                        (if (agentBusy) true else ui.ready),
                    agentBusy = agentBusy,
                    streaming = ui.streaming,
                    onAttach = { showAttachmentSheet = true },
                    onSend = {
                        val text = draft.trim().ifBlank { "请查看这些图片" }
                        val images = pendingImages.toList()
                        updateDraft("")
                        pendingImages.clear()
                        onPromptSubmitted(text, ui.sessionId)
                        chat.prompt(text, images)
                    },
                    onSteer = {
                        val text = draft.trim().ifBlank { "请查看这些图片" }
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

    if (modelMenuExpanded) {
        ChoiceDialog(
            title = "切换模型",
            values = availableModels,
            label = { model ->
                val selected = model.provider == progress.provider &&
                    (model.name == progress.model || model.id == progress.model)
                "${model.name} · ${model.provider}${if (selected) " · 当前" else ""}"
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
            onCompact = {
                chat.compact()
                showPiControls = false
            },
            onClone = {
                showPiControls = false
                chat.cloneSession { result ->
                    result.getOrNull()?.let(onSessionReplaced)
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
            title = "思考强度",
            values = thinkingLevels,
            label = { thinkingLevelLabel(it) },
            onSelect = {
                chat.setThinkingLevel(it)
                showThinkingLevels = false
            },
            onDismiss = { showThinkingLevels = false },
        )
    }
}

private fun toolDisplayName(name: String): String = when (name) {
    "bash" -> "运行命令"
    "read" -> "读取文件"
    "write" -> "写入文件"
    "edit" -> "修改文件"
    "grep", "find" -> "搜索文件"
    "ls" -> "查看目录"
    else -> "使用 $name"
}

@Composable
private fun ExecutionTrace(
    progress: ConversationProgress,
    status: String,
    active: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val current = progress.execution.lastOrNull { item ->
        when (item) {
            is PiThinkingState -> !item.finished
            is PiToolState -> !item.finished
        }
    } ?: progress.execution.lastOrNull()
    val collapsedText = when (current) {
        is PiThinkingState -> current.text.replace(Regex("\\s+"), " ").trim().ifBlank { status }
        is PiToolState -> buildString {
            append(if (current.finished) toolDisplayName(current.name) else "正在${toolDisplayName(current.name)}")
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
            Text("展开 ﹀", style = MaterialTheme.typography.labelMedium)
        }
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
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
                    Text("执行流", fontWeight = FontWeight.SemiBold)
                    Text(
                        status,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("收起 ︿", style = MaterialTheme.typography.labelMedium)
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
                    Text("等待 Pi 执行事件…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ThinkingTraceItem(item: PiThinkingState) {
    Text(
        if (item.finished) "思考 · 完成" else "思考 · 进行中",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
    )
    if (item.text.isNotBlank()) {
        Markdown(markdownState = rememberMarkdownState(item.text, retainState = true))
    }
}

@Composable
private fun ToolTraceItem(item: PiToolState) {
    val state = when {
        item.failed -> "失败"
        item.finished -> "完成"
        else -> "进行中"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF101214),
        contentColor = Color(0xFFF3F4F6),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${toolDisplayName(item.name)} · $state",
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            val invocation = if (item.name == "bash") item.summary else item.input
            if (invocation.isNotBlank()) {
                TerminalTraceText(if (item.name == "bash") "命令" else "调用", invocation)
            }
            if (item.output.isNotBlank()) {
                TerminalTraceText("输出", item.output)
            }
        }
    }
}

@Composable
private fun TerminalTraceText(label: String, value: String) {
    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
    Text(
        value,
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        color = Color(0xFFF3F4F6),
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
    )
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    message.images.take(4).forEach { ChatImagePreview(it, Modifier.size(160.dp)) }
                }
            }
            if (message.role == MessageRole.ASSISTANT) {
                val markdownState = rememberMarkdownState(message.text, retainState = true)
                Markdown(
                    markdownState = markdownState,
                    modifier = bubbleModifier,
                )
            } else {
                Text(
                    text = message.text,
                    modifier = bubbleModifier,
                    color = if (message.role == MessageRole.SYSTEM) MaterialTheme.colorScheme.onErrorContainer else Color.Unspecified,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                MessageBubbleActionIcon(Icons.Outlined.ContentCopy, "复制", onCopy)
                onResend?.let { MessageBubbleActionIcon(Icons.Outlined.Refresh, "重发", it) }
                onFork?.let { MessageBubbleActionIcon(Icons.AutoMirrored.Outlined.CallSplit, "Fork", it) }
            }
        }
    }
}

@Composable
private fun ChatImagePreview(image: ChatImage, modifier: Modifier = Modifier) {
    val bitmap = remember(image.data) {
        runCatching {
            val bytes = Base64.decode(image.data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "对话图片",
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Text("图片无法显示", modifier = modifier)
    }
}

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
    onCommand: (PiCommand) -> Unit,
    onCompact: () -> Unit,
    onClone: () -> Unit,
    onToggleAutoCompaction: () -> Unit,
    onToggleAutoRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pi 命令") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(430.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { Text("会话控制", fontWeight = FontWeight.SemiBold) }
                item { TextButton(onClick = onClone, enabled = canClone) { Text("克隆当前会话") } }
                item { TextButton(onClick = onCompact) { Text("立即压缩上下文") } }
                item {
                    TextButton(onClick = onToggleAutoCompaction) {
                        Text("自动压缩：${if (autoCompaction) "已开启" else "已关闭"}")
                    }
                }
                item {
                    TextButton(onClick = onToggleAutoRetry) {
                        Text("自动重试：${if (autoRetry) "已开启" else "已关闭"}")
                    }
                }
                item { Text("命令、模板和技能", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
                if (commands.isEmpty()) {
                    item { Text("当前 workspace 没有额外命令。Pi 内置能力已列在上方。") }
                } else {
                    items(commands, key = { "${it.source}:${it.name}" }) { command ->
                        TextButton(onClick = { onCommand(command) }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text("/${command.name}")
                                if (command.description.isNotBlank()) {
                                    Text(command.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    values: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().height(400.dp)) {
                if (values.isEmpty()) item { Text("没有可用选项") }
                items(values) { value ->
                    TextButton(onClick = { onSelect(value) }, modifier = Modifier.fillMaxWidth()) {
                        Text(label(value), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun thinkingLevelLabel(level: String): String = when (level) {
    "off" -> "关闭"
    "minimal" -> "最少"
    "low" -> "较低"
    "medium" -> "中等"
    "high" -> "较高"
    "xhigh" -> "很高"
    "max" -> "最高"
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
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
