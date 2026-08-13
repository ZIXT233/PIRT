package io.github.zixt233.pirt.runtime

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.security.SecureRandom

object GraphicsPasswordStore {
    private const val PREFS = "pirt_graphics"
    private const val KEY_PASSWORD = "vnc_password"
    private const val MAX_LENGTH = 8
    private val random = SecureRandom()

    fun loadOrCreateDefault(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_PASSWORD, null)?.trim().orEmpty()
        if (saved.isNotEmpty() && validate(saved) == null) return saved
        val generated = generateRandom()
        prefs.edit().putString(KEY_PASSWORD, generated).apply()
        return generated
    }

    fun save(context: Context, password: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PASSWORD, password.trim())
            .apply()
    }

    fun validate(password: String): String? {
        val value = password.trim()
        if (value.isEmpty()) return "请输入 VNC 密码"
        if (value.length > MAX_LENGTH) return "VNC 密码最多 $MAX_LENGTH 个字符"
        if (value.any { it == '\'' || it == '\n' || it == '\r' }) return "密码不能包含引号或换行"
        return null
    }

    fun openAvnc(context: Context, port: Int, password: String): Result<Unit> = runCatching {
        val uri = Uri.parse(
            "vnc://127.0.0.1:$port?VncPassword=${Uri.encode(password)}" +
                "&ConnectionName=${Uri.encode("PIRT")}&SaveConnection=true",
        )
        val base = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val avnc = Intent(base).setPackage("com.gaurav.avnc")
        try {
            context.startActivity(avnc)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(Intent.createChooser(base, "打开 VNC 客户端"))
            } catch (_: ActivityNotFoundException) {
                error("未找到 VNC 客户端，请用浏览器打开")
            }
        }
    }

    private fun generateRandom(): String = buildString(MAX_LENGTH) {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        repeat(MAX_LENGTH) { append(alphabet[random.nextInt(alphabet.length)]) }
    }
}
