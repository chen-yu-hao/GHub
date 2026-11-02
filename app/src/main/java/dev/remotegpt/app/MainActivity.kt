package dev.remotegpt.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.remotegpt.app.ui.*

class MainActivity : AppCompatActivity() {
    // The connection owner is process scoped so the foreground service and
    // Activity always observe the same SSH/RPC state.
    private val vm: WorkbenchViewModel by lazy { (application as RemoteApplication).workbench() }
    private var isBoardVisible = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            var boardVisible by remember { mutableStateOf(true) }
            // Keep the composable call structure stable when navigation changes system bars.
            val systemDark = isSystemInDarkTheme()
            val dark = !boardVisible && (state.theme == "dark" || (state.theme == "system" && systemDark))
            DisposableEffect(dark, boardVisible) {
                isBoardVisible = boardVisible
                val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                applySystemBarVisibility()
                onDispose {}
            }
            WorkbenchTheme(state.theme) {
                Workbench(vm, state, taskBoardEnabled = true, onBoardVisibility = { boardVisible = it })
            }
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // A rotated or resumed window can regain focus after the initial Compose effect.
        if (hasFocus) applySystemBarVisibility()
    }
    private fun applySystemBarVisibility() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isBoardVisible) hide(WindowInsetsCompat.Type.systemBars())
            else show(WindowInsetsCompat.Type.systemBars())
        }
    }
    override fun onStart() { super.onStart(); vm.onForeground() }
    override fun onStop() { vm.onBackground(); super.onStop() }
}
