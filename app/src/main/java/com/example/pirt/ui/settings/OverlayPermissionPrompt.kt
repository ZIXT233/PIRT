package com.example.pirt.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pirt.runtime.OverlayPermission
import com.example.pirt.runtime.RuntimeService

@Composable
fun OverlayPermissionPrompt() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var overlayGranted by rememberSaveable { mutableStateOf(OverlayPermission.canDraw(context)) }
    var backgroundPrompted by rememberSaveable { mutableStateOf(false) }
    var launchPrompted by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
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

    DisposableEffect(Unit) {
        if (
            !launchPrompted &&
            !OverlayPermission.canDraw(context) &&
            !OverlayPermission.isPromptDismissed(context)
        ) {
            launchPrompted = true
            showDialog = true
        }
        onDispose { }
    }

    if (showDialog && !overlayGranted) {
        AlertDialog(
            onDismissRequest = {
                OverlayPermission.dismissPrompt(context)
                showDialog = false
            },
            title = { Text("开启悬浮窗保活") },
            text = {
                Text(
                    "PIRT 需要「显示在其他应用上层」权限，才能在后台保持 Pi 和游戏服等本地进程的调度优先级。" +
                        "授权后会出现可拖动的 Pi 浮标，点击可查看当前进程。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    OverlayPermission.openSettings(context)
                }) { Text("去开启") }
            },
            dismissButton = {
                TextButton(onClick = {
                    OverlayPermission.dismissPrompt(context)
                    showDialog = false
                }) { Text("稍后再说") }
            },
        )
    }
}
