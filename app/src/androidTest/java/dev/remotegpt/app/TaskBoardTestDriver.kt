package dev.remotegpt.app

import android.app.Activity
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Drives the production WebView and its public bridge, without replacing the board UI. */
internal class TaskBoardTestDriver(
    private val compose: ComposeTestRule,
    private val activity: () -> Activity,
) {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    fun withView(action: (WebView) -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                val view = find(activity().window.decorView) ?: throw AssertionError("Task-board WebView is not mounted")
                if (view.webChromeClient !is BoardTestConsole) {
                    view.webChromeClient = BoardTestConsole()
                    Log.i("TaskBoardTest", "Observing WebView ${System.identityHashCode(view)}")
                }
                action(view)
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }

    fun evaluate(script: String): String {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Wait for WebView callbacks off the UI thread" }
        val completed = CountDownLatch(1)
        val answer = AtomicReference<String>()
        withView { view ->
            view.evaluateJavascript(script) { result -> answer.set(result); completed.countDown() }
        }
        assertTrue("JavaScript callback did not complete", completed.await(10, TimeUnit.SECONDS))
        return answer.get()
    }

    fun awaitReady(timeoutMillis: Long = 20_000) = awaitJavascript(
        "Boolean(window.TaskBoard && typeof window.TaskBoard.render === 'function' && document.getElementById('board'))",
        timeoutMillis,
    )

    fun awaitJavascript(predicate: String, timeoutMillis: Long = 10_000) {
        var failure: Throwable? = null
        try {
            // waitUntil advances mainClock for the native LaunchedEffect snapshot collector.
            // Do not nest waitForIdle: a replaced Activity root can lose its awaited frame,
            // preventing this predicate from returning to the bounded wait.
            compose.waitUntil(timeoutMillis) {
                try { evaluate(predicate) == "true" }
                catch (error: AssertionError) { failure = error; false }
            }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("Board condition was not met: $predicate", failure ?: error)
        }
    }

    fun awaitText(text: String) = awaitJavascript("document.body.innerText.includes(${JSONObject.quote(text)})")

    fun click(selector: String) {
        assertEquals("DOM control not found/enabled: $selector", "true", evaluate("""
            (function(){var node=document.querySelector(${JSONObject.quote(selector)});
            if(!node || node.disabled)return false;node.click();return true;})()
        """.trimIndent()))
    }

    fun clickText(text: String) {
        assertEquals("DOM button not found/enabled: $text", "true", evaluate("""
            (function(){var node=Array.from(document.querySelectorAll('button')).find(function(button){
              return button.textContent.trim()===${JSONObject.quote(text)} && !button.disabled &&
                !button.closest('[hidden]') && button.getClientRects().length>0;
            });if(!node)return false;node.click();return true;})()
        """.trimIndent()))
    }

    fun post(action: JSONObject) {
        assertEquals("true", evaluate("window.GHubBoard.postMessage(${JSONObject.quote(action.toString())}); true"))
        instrumentation.waitForIdleSync()
    }

    fun openProjects(hostId: String) {
        awaitReady()
        // A saved profile can reach the native state before the throttled HTML snapshot.
        awaitJavascript("""
            (function(){if(document.getElementById('modal-backdrop').hidden)document.getElementById('filter').click();
            var node=document.querySelector('#modal select');
            if(node && Array.from(node.options).some(function(option){return option.value===${JSONObject.quote(hostId)};}))return true;
            window.TaskBoard.closeDialog();return false;})()
        """.trimIndent())
        selectHost(hostId)
        clickText("项目与会话")
        instrumentation.waitForIdleSync()
    }

    fun observeNativeSnapshots() {
        awaitReady()
        evaluate("""
            (function(){if(window.boardObservedNativeSnapshotHook)return true;
            var render=window.TaskBoard.render;window.boardObservedNativeSnapshotHook=true;
            window.TaskBoard.render=function(snapshot){window.boardObservedNativeSnapshot=snapshot;return render(snapshot);};return true;})()
        """.trimIndent())
    }

    fun selectHost(hostId: String) {
        assertEquals("true", evaluate("""
            (function(){var node=document.querySelector('#modal select');
            if(!node || !Array.from(node.options).some(function(option){return option.value===${JSONObject.quote(hostId)};}))return false;
            node.value=${JSONObject.quote(hostId)};node.dispatchEvent(new Event('change',{bubbles:true}));return true;})()
        """.trimIndent()))
    }

    private fun find(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) repeat(view.childCount) { find(view.getChildAt(it))?.let { found -> return found } }
        return null
    }
}

/** Keep production JavaScript exceptions visible in the device test's logcat artifact. */
private class BoardTestConsole : WebChromeClient() {
    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        val priority = when (message.messageLevel()) {
            ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
            ConsoleMessage.MessageLevel.WARNING -> Log.WARN
            else -> Log.INFO
        }
        Log.println(priority, "TaskBoardTest", "JavaScript ${message.sourceId()}:${message.lineNumber()}: ${message.message()}")
        return true
    }
}
