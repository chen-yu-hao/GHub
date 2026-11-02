package dev.remotegpt.app

import android.os.Bundle
import android.os.Build
import android.util.Log
import android.view.View
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
    private val restoreLegacySystemBars = Runnable {
        // Recheck the current destination and flags after the platform dispatch completes.
        if (needsLegacySystemBarRestore()) applySystemBarVisibility("legacy-post")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        installLegacySystemUiListener()
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
                applySystemBarVisibility("destination")
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
        if (hasFocus) {
            applySystemBarVisibility("focus")
            // API 29 can deliver a queued visibility reset after the focus callback.
            scheduleLegacySystemBarRestore("focus")
        } else {
            window.decorView.removeCallbacks(restoreLegacySystemBars)
        }
    }
    @Suppress("DEPRECATION")
    private fun installLegacySystemUiListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return
        window.decorView.setOnSystemUiVisibilityChangeListener {
            logSystemBars("legacy-visibility")
            // Sticky edge swipes leave these flags set. Restore only a real flag reset,
            // without interfering with the user's temporary system-bar overlay.
            if (needsLegacySystemBarRestore()) scheduleLegacySystemBarRestore("visibility")
        }
    }
    @Suppress("DEPRECATION")
    private fun needsLegacySystemBarRestore(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || !isBoardVisible ||
            !hasWindowFocus() || isFinishing || isDestroyed) return false
        val hidden = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        return (window.decorView.systemUiVisibility and hidden) != hidden
    }
    private fun scheduleLegacySystemBarRestore(reason: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || !isBoardVisible ||
            !hasWindowFocus() || isFinishing || isDestroyed) return
        window.decorView.removeCallbacks(restoreLegacySystemBars)
        logSystemBars("schedule-$reason")
        window.decorView.post(restoreLegacySystemBars)
    }
    private fun applySystemBarVisibility(reason: String) {
        if (!isBoardVisible) window.decorView.removeCallbacks(restoreLegacySystemBars)
        logSystemBars("$reason-before")
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isBoardVisible) hide(WindowInsetsCompat.Type.systemBars())
            else show(WindowInsetsCompat.Type.systemBars())
        }
        logSystemBars("$reason-after")
    }
    @Suppress("DEPRECATION")
    private fun logSystemBars(reason: String) {
        if (BuildConfig.DEBUG) Log.d("TaskBoardWindow", "activity=${System.identityHashCode(this)} reason=$reason board=$isBoardVisible focus=${hasWindowFocus()} flags=0x${window.decorView.systemUiVisibility.toString(16)}")
    }
    @Suppress("DEPRECATION")
    override fun onDestroy() {
        window.decorView.removeCallbacks(restoreLegacySystemBars)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) window.decorView.setOnSystemUiVisibilityChangeListener(null)
        super.onDestroy()
    }
    override fun onStart() { super.onStart(); vm.onForeground() }
    override fun onStop() { vm.onBackground(); super.onStop() }
}
