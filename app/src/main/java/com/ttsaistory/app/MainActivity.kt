package com.ttsaistory.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ttsaistory.app.ui.AppTabs
import com.ttsaistory.app.ui.core.AppThemeShapes

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val t0 = AnrDiagLog.begin("MainActivity.onCreate")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Android 15+ (adb: EDGE_TO_EDGE_ENFORCED + FIT_INSETS_CONTROLLED): adjustResize
        // thường không co nội dung Compose — dùng adjustNothing + imePadding ở gốc.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        setContent {
            MaterialTheme(shapes = AppThemeShapes) {
                Surface(modifier = Modifier.fillMaxSize().imePadding()) {
                    AppTabs()
                }
            }
        }
        AnrDiagLog.end("MainActivity.onCreate", t0)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
