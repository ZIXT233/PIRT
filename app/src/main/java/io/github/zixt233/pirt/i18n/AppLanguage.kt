package io.github.zixt233.pirt.i18n

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    SIMPLIFIED_CHINESE("zh-CN"),
}

internal fun defaultLanguageFor(locale: Locale): AppLanguage =
    if (locale.language.equals("zh", ignoreCase = true)) {
        AppLanguage.SIMPLIFIED_CHINESE
    } else {
        AppLanguage.ENGLISH
    }

object AppLanguageStore {
    private const val PREFS = "pirt_ui"
    private const val KEY = "app_language"
    private var initialized = false
    private val mutable = MutableStateFlow(AppLanguage.ENGLISH)
    val language: StateFlow<AppLanguage> = mutable

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            mutable.value = when (saved) {
                AppLanguage.SIMPLIFIED_CHINESE.tag -> AppLanguage.SIMPLIFIED_CHINESE
                AppLanguage.ENGLISH.tag -> AppLanguage.ENGLISH
                else -> defaultLanguageFor(Locale.getDefault())
            }
            initialized = true
        }
    }

    fun current(context: Context): AppLanguage {
        initialize(context.applicationContext)
        return mutable.value
    }

    fun set(context: Context, language: AppLanguage) {
        initialize(context.applicationContext)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, language.tag).apply()
        mutable.value = language
    }
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.ENGLISH }

fun AppLanguage.text(zh: String, en: String): String =
    if (this == AppLanguage.SIMPLIFIED_CHINESE) zh else en
