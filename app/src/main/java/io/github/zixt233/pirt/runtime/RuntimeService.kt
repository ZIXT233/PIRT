package io.github.zixt233.pirt.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.zixt233.pirt.MainActivity
import io.github.zixt233.pirt.i18n.AppLanguageStore
import io.github.zixt233.pirt.i18n.text
import io.github.zixt233.pirt.model.WorkspaceConfig
import io.github.zixt233.pirt.runtime.pi.PiSessionCatalog
import io.github.zixt233.pirt.runtime.pi.PiSessionManager
import java.io.File

/**
 * Termux-style process owner. Compose pages only subscribe; leaving a page must not kill Pi.
 * The service and Pi still share one Android process, while the foreground notification raises
 * its importance and makes the running work explicit to the user.
 */
class RuntimeService : Service() {
    inner class RuntimeBinder : Binder() {
        val sessions: PiSessionManager get() = sessionManager
        val catalog: PiSessionCatalog get() = sessionCatalog
        val auth: PiAuthManager get() = authManager
        val terminal: TerminalManager get() = terminalManager
        val graphics: GraphicsManager get() = graphicsManager
        val processes: ProcessManager get() = processManager
    }

    private val binder = RuntimeBinder()
    private lateinit var sessionManager: PiSessionManager
    private lateinit var sessionCatalog: PiSessionCatalog
    private lateinit var authManager: PiAuthManager
    private lateinit var terminalManager: TerminalManager
    private lateinit var graphicsManager: GraphicsManager
    private lateinit var processManager: ProcessManager
    private lateinit var overlay: OverlayKeepAlive
    private var notificationFeedback: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var shuttingDown = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        overlay = OverlayKeepAlive(applicationContext)
        val workspace = WorkspaceConfig(File(filesDir, "pirt/workspace").apply { mkdirs() }.absolutePath)
        var sessions: PiSessionManager? = null
        authManager = PiAuthManager(
            context = applicationContext,
            workspace = workspace,
            onActivityChanged = ::refresh,
            sessionListener = { key, event -> sessions?.accept(key, event) },
            sessionFailure = { message -> sessions?.failAll(message) },
        )
        sessionCatalog = PiSessionCatalog(applicationContext, authManager.control)
        sessions = PiSessionManager(
            authManager.control,
            sessionCatalog,
            ::refresh,
            ::notifyReplyCompleted,
        )
        sessionManager = checkNotNull(sessions)
        terminalManager = TerminalManager(applicationContext, ::refresh)
        graphicsManager = GraphicsManager(applicationContext, ::refresh)
        processManager = ProcessManager(::refresh)
        overlay.bind(object : OverlayChatHost {
            override fun snapshot() = sessionManager.overlaySnapshot()
            override fun send(message: String) = runCatching { sessionManager.overlayPrompt(message) }
        })
        sessionCatalog.refresh()
        createChannel()
        getSystemService(NotificationManager::class.java).cancel(LEGACY_REPLY_NOTIFICATION_ID)
        promote()
        overlay.sync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_NOTIFICATION_REPLY -> {
                val message = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_NOTIFICATION_REPLY)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                if (message.isNotEmpty()) {
                    runCatching { sessionManager.overlayPrompt(message) }
                        .onFailure { notificationFeedback = "发送失败：${it.message ?: "未知错误"}" }
                }
                refresh()
            }
            ACTION_STOP_ALL -> {
                shuttingDown = true
                overlay.detach()
                sessionManager.shutdown()
                terminalManager.close()
                graphicsManager.close()
                authManager.close()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> refresh()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        shuttingDown = true
        instance = null
        overlay.detach()
        releaseWakeLock()
        sessionManager.shutdown()
        terminalManager.close()
        graphicsManager.close()
        authManager.close()
        super.onDestroy()
    }

    fun refresh() {
        if (shuttingDown) return
        processManager.refresh()
        promote()
        overlay.sync()
        overlay.renderIfExpanded()
        if (sessionManager.hasBusySession() || terminalManager.busyCount() > 0) acquireWakeLock() else releaseWakeLock()
    }

    private fun promote(alert: Boolean = false) {
        val serviceType = if (android.os.Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(alert),
            serviceType,
        )
    }

    private fun buildNotification(alert: Boolean = false): Notification {
        val language = AppLanguageStore.current(this)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val replyIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RuntimeService::class.java).setAction(ACTION_NOTIFICATION_REPLY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val remoteInput = RemoteInput.Builder(KEY_NOTIFICATION_REPLY)
            .setLabel(language.text("发消息给 PIRT", "Message PIRT"))
            .build()
        val active = sessionManager.activeCount() + terminalManager.activeCount() +
            graphicsManager.activeCount() + authManager.activeCount()
        val chat = sessionManager.overlaySnapshot()
        val body = notificationFeedback?.also { notificationFeedback = null }
            ?: chat?.reply?.let(::notificationLine)
            ?: chat?.status
            ?: if (active > 0) language.text("$active 个托管组件保持运行", "$active managed components running")
            else language.text("请先在应用内打开一个会话", "Open a conversation in the app first")
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(io.github.zixt233.pirt.R.drawable.ic_pirt_mark)
            .setColor(0xFF1F5D42.toInt())
            .setContentTitle(chat?.title?.takeIf(String::isNotBlank)?.let { "PIRT · $it" } ?: "PIRT")
            .setContentText(body)
            .setContentIntent(open)
            .setOngoing(active > 0)
            .setOnlyAlertOnce(!alert)
            .setSilent(!alert)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(if (alert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
        if (chat?.canSend == true) {
            builder.addAction(
                NotificationCompat.Action.Builder(0, language.text("回复", "Reply"), replyIntent)
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(false)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .build()
            )
        }
        return builder.build()
    }

    private fun notificationLine(value: String): String {
        val singleLine = value.replace(Regex("\\s+"), " ").trim()
        return if (singleLine.length <= MAX_NOTIFICATION_TEXT_LENGTH) {
            singleLine
        } else {
            singleLine.take(MAX_NOTIFICATION_TEXT_LENGTH - 1) + "…"
        }
    }

    private fun notifyReplyCompleted(
        @Suppress("UNUSED_PARAMETER") title: String,
        @Suppress("UNUSED_PARAMETER") reply: String?,
    ) {
        // The resident notification already projects the selected session's
        // latest reply. Re-post the same ID instead of creating another item.
        promote(alert = !MainActivity.isVisible())
    }

    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val language = AppLanguageStore.current(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(LEGACY_RUNTIME_CHANNEL_ID)
        manager.deleteNotificationChannel(LEGACY_REPLY_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, language.text("PIRT 会话与回复", "PIRT conversations and replies"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = language.text("保持本地开发会话运行，并在 AI 完成回复时提醒", "Keeps local development conversations active and alerts you when AI replies")
            }
        )
    }

    private fun acquireWakeLock() {
        val current = wakeLock
        if (current?.isHeld == true) return
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PIRT:active-agent")
            .apply { acquire(10 * 60 * 1_000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        // Android does not allow raising an existing channel's importance.
        private const val CHANNEL_ID = "pirt_runtime_v2"
        private const val LEGACY_RUNTIME_CHANNEL_ID = "pirt_runtime"
        private const val LEGACY_REPLY_CHANNEL_ID = "pirt_ai_replies"
        private const val NOTIFICATION_ID = 41
        private const val LEGACY_REPLY_NOTIFICATION_ID = 42
        private const val KEY_NOTIFICATION_REPLY = "pirt_notification_reply"
        private const val MAX_NOTIFICATION_TEXT_LENGTH = 64
        private const val ACTION_START = "io.github.zixt233.pirt.runtime.START"
        private const val ACTION_STOP_ALL = "io.github.zixt233.pirt.runtime.STOP_ALL"
        private const val ACTION_NOTIFICATION_REPLY = "io.github.zixt233.pirt.runtime.NOTIFICATION_REPLY"
        @Volatile private var instance: RuntimeService? = null

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RuntimeService::class.java).setAction(ACTION_START),
            )
        }

        internal fun refreshNotification() = instance?.run {
            createChannel()
            overlay.detach()
            overlay.sync()
            refresh()
        }
    }
}
