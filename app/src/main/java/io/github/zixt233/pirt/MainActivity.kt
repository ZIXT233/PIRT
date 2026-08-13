package io.github.zixt233.pirt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.zixt233.pirt.i18n.AppLanguageStore
import io.github.zixt233.pirt.i18n.LocalAppLanguage
import io.github.zixt233.pirt.ui.PirtApp
import io.github.zixt233.pirt.ui.theme.PIRTTheme

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        visible = true
    }

    override fun onStop() {
        visible = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent {
            AppLanguageStore.initialize(applicationContext)
            val language by AppLanguageStore.language.collectAsState()
            CompositionLocalProvider(LocalAppLanguage provides language) {
                PIRTTheme {
                    PirtApp()
                }
            }
        }
    }

    companion object {
        @Volatile private var visible = false
        fun isVisible(): Boolean = visible
    }
}
