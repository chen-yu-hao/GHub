package dev.remotegpt.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.remotegpt.app.ConnectionStatus
import dev.remotegpt.app.BuildConfig
import dev.remotegpt.app.WorkbenchState
import dev.remotegpt.app.WorkbenchViewModel
import dev.remotegpt.app.board.TaskBoardAction
import dev.remotegpt.app.board.parseTaskBoardAction
import dev.remotegpt.app.board.taskBoardSnapshot
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.JsonPrimitive

private const val BOARD_URL = "https://appassets.androidplatform.net/taskboard/index.html"
private const val BOARD_NAVIGATION_TAG = "TaskBoardNavigation"

/** The supplied HTML is the actual app UI, served entirely from packaged assets. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun TaskBoardScreen(
    vm: WorkbenchViewModel,
    state: WorkbenchState,
    snackbars: SnackbarHostState,
    onAction: (TaskBoardAction) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestAction by rememberUpdatedState(onAction)
    val snapshot by rememberUpdatedState(remember(state) { taskBoardSnapshot(state).toString() })
    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var dialogOpen by remember { mutableStateOf(false) }
    val bridge: TaskBoardBridge = remember {
        TaskBoardBridge { message ->
            // Dialog visibility has no native side effect; keep Android Back inside the filter/approval dialog.
            if (message == "{\"type\":\"dialogOpened\"}") {
                dialogOpen = true
                if (BuildConfig.DEBUG) Log.d(BOARD_NAVIGATION_TAG, "bridge.dialog type=dialogOpened dialogOpen=true")
            } else if (message == "{\"type\":\"dialogClosed\"}") {
                dialogOpen = false
                if (BuildConfig.DEBUG) Log.d(BOARD_NAVIGATION_TAG, "bridge.dialog type=dialogClosed dialogOpen=false")
            } else {
                val action = parseTaskBoardAction(message)
                if (BuildConfig.DEBUG) Log.d(BOARD_NAVIGATION_TAG,
                    if (action == null) "bridge.parse accepted=false" else "bridge.parse accepted=true type=${action.type}")
                action?.let {
                    if (it.type == "refresh") webView?.invalidate()
                    latestAction(it)
                    if (BuildConfig.DEBUG) Log.d(BOARD_NAVIGATION_TAG, "bridge.dispatch.complete type=${it.type}")
                }
            }
        }
    }

    BackHandler(dialogOpen) {
        webView?.evaluateJavascript("window.TaskBoard && window.TaskBoard.closeDialog && window.TaskBoard.closeDialog()", null)
        dialogOpen = false
    }

    // The five-minute interval runs only while this screen is visible and the Activity is started.
    LaunchedEffect(lifecycle, vm) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refreshBoard()
                delay(5 * 60_000L)
            }
        }
    }
    val onlineHosts = state.workspaces.filterValues { it.connection == ConnectionStatus.ONLINE }.keys
    LaunchedEffect(onlineHosts) { if (onlineHosts.isNotEmpty()) vm.refreshBoard() }

    // Coalesce streaming output so an e-ink panel is not repainted for each token.
    LaunchedEffect(webView, ready, lifecycle) {
        if (!ready) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow { snapshot }.distinctUntilChanged().conflate().collect { data ->
                webView?.evaluateJavascript("window.TaskBoard && window.TaskBoard.render(JSON.parse(${JsonPrimitive(data)}))", null)
                delay(1_000L)
            }
        }
    }
    DisposableEffect(webView, lifecycle) {
        val view = webView
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) view?.onPause()
            if (event == Lifecycle.Event.ON_START) view?.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(bridge) { onDispose { bridge.close() } }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White).safeDrawingPadding()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { hostContext ->
                WebView(hostContext).apply {
                    // Chromium treats WRAP_CONTENT as zero layout height even when Compose fills the view.
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(Color.WHITE)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                        textZoom = 100
                        // All permitted requests are intercepted below. No network fallback is permitted.
                        blockNetworkLoads = true
                        if (Build.VERSION.SDK_INT >= 33) isAlgorithmicDarkeningAllowed = false
                        @Suppress("DEPRECATION")
                        if (Build.VERSION.SDK_INT in 29..32) forceDark = WebSettings.FORCE_DARK_OFF
                    }
                    installTaskBoardBridge(bridge)
                    webViewClient = BoardAssetsClient(hostContext) {
                        ready = true
                    }
                    webView = this
                    loadUrl(BOARD_URL)
                }
            },
            onReset = null,
            onRelease = { view ->
                ready = false
                webView = null
                view.removeJavascriptInterface("GHubBoard")
                view.stopLoading()
                view.destroy()
            },
        )
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
    }
}

// Give Lint a concrete interface type instead of Compose remember's generic return type.
private fun WebView.installTaskBoardBridge(bridge: TaskBoardBridge) {
    addJavascriptInterface(bridge, "GHubBoard")
}

/** Only packaged assets at one synthetic HTTPS origin are readable by this WebView. */
private class BoardAssetsClient(private val context: Context, private val loaded: () -> Unit) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse {
        val uri = request?.url
        val path = uri?.path.orEmpty()
        val permitted = request?.method == "GET" && uri?.scheme == "https" &&
            uri.host == "appassets.androidplatform.net" && uri.port == -1 && uri.userInfo == null &&
            path.startsWith("/taskboard/") && path.split('/').none { it == ".." || it == "." } &&
            path.all { it.isLetterOrDigit() || it in "/-_." }
        if (!permitted) return denied()
        val mime = when (path.substringAfterLast('.')) {
            "html" -> "text/html"
            "js" -> "application/javascript"
            "css" -> "text/css"
            "woff2" -> "font/woff2"
            "svg" -> "image/svg+xml"
            "txt" -> "text/plain"
            else -> return denied()
        }
        return runCatching {
            WebResourceResponse(mime, if (mime == "font/woff2") null else "UTF-8", 200, "OK", mapOf(
                "Content-Security-Policy" to "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'",
                "X-Content-Type-Options" to "nosniff",
                "Cache-Control" to "no-cache",
            ), context.assets.open(path.removePrefix("/")))
        }.getOrElse { denied() }
    }

    override fun onPageFinished(view: WebView?, url: String?) { if (url == BOARD_URL) loaded() }
    private fun denied() = WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}

private class TaskBoardBridge(private val receive: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    @JavascriptInterface fun postMessage(message: String) {
        if (BuildConfig.DEBUG) Log.d(BOARD_NAVIGATION_TAG, "bridge.received length=${message.length} closed=$closed")
        if (closed || message.length > 16_384) return
        main.post {
            if (BuildConfig.DEBUG) Log.d(BOARD_NAVIGATION_TAG, "bridge.main closed=$closed")
            if (!closed) receive(message)
        }
    }
    fun close() {
        if (BuildConfig.DEBUG) Log.d(BOARD_NAVIGATION_TAG, "bridge.close alreadyClosed=$closed")
        closed = true
        main.removeCallbacksAndMessages(null)
    }
}
