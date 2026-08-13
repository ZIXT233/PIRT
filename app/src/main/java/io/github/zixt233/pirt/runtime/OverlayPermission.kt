package io.github.zixt233.pirt.runtime

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object OverlayPermission {
    private const val PREFS = "pirt_overlay"
    private const val KEY_DISMISSED = "prompt_dismissed"
    private const val KEY_ENABLED = "enabled"

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isUserEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setUserEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun isActive(context: Context): Boolean = canDraw(context) && isUserEnabled(context)

    /**
     * 从系统设置返回时调用：若刚拿到悬浮窗权限，自动打开浮标，避免再手动拨开关。
     * @return 当前是否应显示浮标
     */
    fun activateAfterGrant(context: Context, previouslyGranted: Boolean): Boolean {
        val granted = canDraw(context)
        if (granted && !previouslyGranted) {
            setUserEnabled(context, true)
            RuntimeService.refreshNotification()
        }
        return isActive(context)
    }

    fun openSettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun isPromptDismissed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DISMISSED, false)

    fun dismissPrompt(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DISMISSED, true)
            .apply()
    }
}
