package com.example.pirt.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.pirt.MainActivity
import com.example.pirt.model.WorkspaceConfig
import com.example.pirt.runtime.pi.PiSessionCatalog
import com.example.pirt.runtime.pi.PiSessionManager
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
        sessions = PiSessionManager(authManager.control, sessionCatalog, ::refresh)
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
        promote()
        overlay.sync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
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

    private fun promote() {
        val serviceType = if (android.os.Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            serviceType,
        )
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RuntimeService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val active = sessionManager.activeCount() + terminalManager.activeCount() +
            graphicsManager.activeCount() + authManager.activeCount()
        val busy = sessionManager.busyCount() + terminalManager.busyCount()
        val workspace = processManager.workspaceCount()
        val body = when {
            busy > 0 && workspace > 0 -> "$busy 个 Agent 正在工作，$workspace 个 workspace 进程运行中"
            busy > 0 -> "$busy 个 Agent 正在工作，$active 个托管组件保持运行"
            workspace > 0 -> "$workspace 个 workspace 进程运行中"
            active > 0 -> "$active 个托管组件保持运行"
            else -> "正在准备本地开发环境"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.example.pirt.R.mipmap.ic_launcher)
            .setContentTitle("PIRT 本地开发环境")
            .setContentText(body)
            .setContentIntent(open)
            .setOngoing(active > 0)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "停止全部", stop)
        if (!Settings.canDrawOverlays(this)) {
            val overlaySettings = PendingIntent.getActivity(
                this,
                2,
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "开启悬浮窗保活", overlaySettings)
        }
        return builder.build()
    }

    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "本地开发会话", NotificationManager.IMPORTANCE_LOW).apply {
                description = "保持 Pi、Terminal 和 Linux 图形会话在后台运行"
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
        private const val CHANNEL_ID = "pirt_runtime"
        private const val NOTIFICATION_ID = 41
        private const val ACTION_START = "com.example.pirt.runtime.START"
        private const val ACTION_STOP_ALL = "com.example.pirt.runtime.STOP_ALL"
        @Volatile private var instance: RuntimeService? = null

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RuntimeService::class.java).setAction(ACTION_START),
            )
        }

        internal fun refreshNotification() = instance?.refresh()
    }
}
