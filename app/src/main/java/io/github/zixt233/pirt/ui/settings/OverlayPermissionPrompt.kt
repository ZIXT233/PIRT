package io.github.zixt233.pirt.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.zixt233.pirt.runtime.OverlayPermission
import io.github.zixt233.pirt.runtime.RuntimeService
import io.github.zixt233.pirt.i18n.LocalAppLanguage
import io.github.zixt233.pirt.i18n.text
import kotlinx.coroutines.delay

@Composable
fun OverlayPermissionPrompt(autoPrompt: Boolean = false) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var overlayGranted by rememberSaveable { mutableStateOf(OverlayPermission.canDraw(context)) }
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    var backgroundPrompted by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val previouslyGranted = overlayGranted
                    OverlayPermission.activateAfterGrant(context, previouslyGranted)
                    overlayGranted = OverlayPermission.canDraw(context)
                    if (overlayGranted) showDialog = false
                    RuntimeService.refreshNotification()
                }
                Lifecycle.Event.ON_STOP -> {
                    if (
                        !OverlayPermission.canDraw(context) &&
                        !OverlayPermission.isPromptDismissed(context) &&
                        !backgroundPrompted
                    ) {
                        backgroundPrompted = true
                        showDialog = true
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(autoPrompt) {
        if (
            autoPrompt &&
            !autoPrompted &&
            !OverlayPermission.canDraw(context) &&
            !OverlayPermission.isPromptDismissed(context)
        ) {
            autoPrompted = true
            delay(450)
            overlayGranted = OverlayPermission.canDraw(context)
            if (!overlayGranted) showDialog = true
        }
    }

    if (showDialog && !overlayGranted) {
        AlertDialog(
            onDismissRequest = {
                OverlayPermission.dismissPrompt(context)
                showDialog = false
            },
            title = { Text(language.text("开启悬浮窗保活", "Enable background overlay")) },
            text = {
                Text(
                    language.text(
                        "PIRT 需要「显示在其他应用上层」权限，才能在后台保持虚拟电脑中本地进程的调度优先级。授权后会出现可拖动的 PIRT 浮标，点击可查看当前进程。",
                        "PIRT needs permission to display over other apps to keep local virtual-computer processes active in the background. A draggable PIRT bubble will appear after permission is granted.",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    OverlayPermission.openSettings(context)
                }) { Text(language.text("去开启", "Open settings")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    OverlayPermission.dismissPrompt(context)
                    showDialog = false
                }) { Text(language.text("稍后再说", "Not now")) }
            },
        )
    }
}
