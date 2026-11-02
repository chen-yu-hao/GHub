package dev.remotegpt.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertTrue
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Measures the installed app's real native view and CSS layout; never changes their sizes. */
internal class TaskBoardGeometryDriver(
    private val compose: ComposeTestRule,
    private val board: TaskBoardTestDriver,
    private val activity: () -> Activity,
) {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    fun nativeGeometry(): JSONObject {
        lateinit var result: JSONObject
        board.withView { view ->
            fun bounds(node: View): JSONObject {
                val location = IntArray(2)
                node.getLocationOnScreen(location)
                return JSONObject().put("left", location[0]).put("top", location[1])
                    .put("width", node.width).put("height", node.height)
            }
            val owner = activity()
            val visible = Rect()
            val hasVisibleRect = view.getLocalVisibleRect(visible)
            val insets = ViewCompat.getRootWindowInsets(view)?.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
            )
            result = JSONObject()
                .put("viewId", System.identityHashCode(view))
                .put("activityId", System.identityHashCode(owner))
                .put("orientation", owner.resources.configuration.orientation)
                .put("density", owner.resources.displayMetrics.density.toDouble())
                .put("view", bounds(view)).put("root", bounds(owner.window.decorView))
                .put("content", bounds(owner.findViewById(android.R.id.content)))
                .put("layoutWidth", view.layoutParams.width).put("layoutHeight", view.layoutParams.height)
                .put("measuredWidth", view.measuredWidth).put("measuredHeight", view.measuredHeight)
                .put("visible", JSONObject().put("present", hasVisibleRect)
                    .put("left", visible.left).put("top", visible.top).put("right", visible.right).put("bottom", visible.bottom))
                .put("insets", JSONObject().put("left", insets?.left ?: 0).put("top", insets?.top ?: 0)
                    .put("right", insets?.right ?: 0).put("bottom", insets?.bottom ?: 0))
                .put("webViewVersion", WebView.getCurrentWebViewPackage()?.versionName ?: "unknown")
        }
        return result
    }

    /** A rotation must finish on the new live Activity/WebView before reading CSS. */
    fun awaitViewport(widthDp: Int, heightDp: Int, previousViewId: Int? = null) {
        var previous = ""
        var stable = 0
        compose.waitUntil(20_000) {
            compose.waitForIdle()
            val native = runCatching { nativeGeometry() }.getOrNull()
            if (native == null) false else {
                val root = native.getJSONObject("root")
                val density = native.getDouble("density")
                val view = native.getJSONObject("view")
                val key = "${native.getInt("viewId")}:${root.getInt("width")}:${root.getInt("height")}:${view.getInt("width")}:${view.getInt("height")}:$density"
                stable = if (key == previous) stable + 1 else 0
                previous = key
                stable >= 2 && abs(root.getInt("width") / density - widthDp) <= 2 &&
                    abs(root.getInt("height") / density - heightDp) <= 2 &&
                    view.getInt("width") > 0 && view.getInt("height") > 0 &&
                    (previousViewId == null || native.getInt("viewId") != previousViewId)
            }
        }
        board.awaitReady()
        board.awaitJavascript("!document.fonts || document.fonts.status === 'loaded'")
        awaitFrame()
    }

    fun awaitFrame() {
        board.evaluate("window.boardGeometryFrameReady=false;requestAnimationFrame(function(){requestAnimationFrame(function(){window.boardGeometryFrameReady=true;});});true")
        board.awaitJavascript("window.boardGeometryFrameReady === true")
    }

    private fun objectResult(script: String): JSONObject =
        JSONObject(JSONTokener(board.evaluate("JSON.stringify($script)")).nextValue() as String)

    fun geometry(): JSONObject = JSONObject().put("native", nativeGeometry()).put("web", objectResult("""
        (function(){
          function rect(node){if(!node)return null;var r=node.getBoundingClientRect(),s=getComputedStyle(node);
            return {left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height,
              clientWidth:node.clientWidth,clientHeight:node.clientHeight,scrollWidth:node.scrollWidth,
              scrollHeight:node.scrollHeight,scrollTop:node.scrollTop,scrollLeft:node.scrollLeft,
              overflowX:s.overflowX,overflowY:s.overflowY,display:s.display};}
          var v=window.visualViewport,scroll=document.scrollingElement||document.documentElement;
          return {viewport:{width:v?v.width:innerWidth,height:v?v.height:innerHeight,
              offsetLeft:v?v.offsetLeft:0,offsetTop:v?v.offsetTop:0,scale:v?v.scale:1,
              innerWidth:innerWidth,innerHeight:innerHeight,dpr:devicePixelRatio,
              clientWidth:document.documentElement.clientWidth,clientHeight:document.documentElement.clientHeight},
            support:{dvh:!!(window.CSS&&CSS.supports&&CSS.supports('height','100dvh')),
              flexGap:document.documentElement.getAttribute('data-flex-gap')},
            document:rect(scroll),board:rect(document.getElementById('board')),
            header:rect(document.querySelector('.header')),filter:rect(document.querySelector('.filter-row')),
            tabs:rect(document.getElementById('tabs')),tasks:rect(document.getElementById('tasks')),
            footer:rect(document.querySelector('.footer')),
            tabRects:Array.from(document.querySelectorAll('#tabs button[data-state]')).map(rect),
            cards:Array.from(document.querySelectorAll('#tasks .task-card,#tasks .completed-task')).map(rect)};
        })()
    """.trimIndent()))

    fun inspect(selector: String): JSONObject = objectResult("""
        (function(){var node=document.querySelector(${JSONObject.quote(selector)});if(!node)return {present:false};
          var r=node.getBoundingClientRect(),v=window.visualViewport;
          var left=v?v.offsetLeft:0,top=v?v.offsetTop:0,w=v?v.width:innerWidth,h=v?v.height:innerHeight;
          var clip={left:left,top:top,right:left+w,bottom:top+h};
          for(var p=node.parentElement;p;p=p.parentElement){var s=getComputedStyle(p),b=p.getBoundingClientRect();
            if(/auto|scroll|hidden|clip/.test(s.overflowX)){clip.left=Math.max(clip.left,b.left+p.clientLeft);clip.right=Math.min(clip.right,b.left+p.clientLeft+p.clientWidth);}
            if(/auto|scroll|hidden|clip/.test(s.overflowY)){clip.top=Math.max(clip.top,b.top+p.clientTop);clip.bottom=Math.min(clip.bottom,b.top+p.clientTop+p.clientHeight);}}
          var x=(r.left+r.right)/2,y=(r.top+r.bottom)/2,hit=document.elementFromPoint(x,y);
          return {present:true,left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height,
            clip:clip,full:r.width>0&&r.height>0&&r.left>=clip.left-1&&r.top>=clip.top-1&&r.right<=clip.right+1&&r.bottom<=clip.bottom+1,
            hit:!!hit&&(node===hit||node.contains(hit)),disabled:!!node.disabled};
        })()
    """.trimIndent())

    fun fullyVisible(selector: String): Boolean = inspect(selector).optBoolean("full")

    fun assertVisibleAndHit(selector: String) {
        val node = inspect(selector)
        assertTrue("Control must be fully inside the visible viewport: $selector; $node", node.optBoolean("full"))
        assertTrue("Control is clipped or covered: $selector; $node", node.optBoolean("hit"))
    }

    /** Real touchscreen events, as opposed to HTMLElement.click() on an offscreen control. */
    fun tap(selector: String) {
        assertVisibleAndHit(selector)
        val node = inspect(selector)
        assertTrue("Control is disabled: $selector", !node.optBoolean("disabled"))
        val point = screenPoint((node.getDouble("left") + node.getDouble("right")) / 2,
            (node.getDouble("top") + node.getDouble("bottom")) / 2)
        val started = SystemClock.uptimeMillis()
        pointer(started, MotionEvent.ACTION_DOWN, point.first, point.second)
        SystemClock.sleep(32)
        pointer(started, MotionEvent.ACTION_UP, point.first, point.second)
        compose.waitForIdle()
        awaitFrame()
    }

    private fun screenPoint(x: Double, y: Double): Pair<Float, Float> {
        val data = geometry()
        val view = data.getJSONObject("native").getJSONObject("view")
        val viewport = data.getJSONObject("web").getJSONObject("viewport")
        return (view.getDouble("left") + (x - viewport.getDouble("offsetLeft")) * view.getDouble("width") / viewport.getDouble("width")).toFloat() to
            (view.getDouble("top") + (y - viewport.getDouble("offsetTop")) * view.getDouble("height") / viewport.getDouble("height")).toFloat()
    }

    fun swipeUp(documentScroll: Boolean) {
        val web = geometry().getJSONObject("web")
        val viewport = web.getJSONObject("viewport")
        val region = if (documentScroll) JSONObject().put("top", 0).put("bottom", viewport.getDouble("height")) else web.getJSONObject("tasks")
        val top = max(0.0, region.getDouble("top"))
        val bottom = min(viewport.getDouble("height"), region.getDouble("bottom"))
        assertTrue("Scrollable region has no usable height: $web", bottom - top > 40)
        val start = screenPoint(viewport.getDouble("width") / 2, top + (bottom - top) * 0.82)
        val end = screenPoint(viewport.getDouble("width") / 2, top + (bottom - top) * 0.18)
        val started = SystemClock.uptimeMillis()
        pointer(started, MotionEvent.ACTION_DOWN, start.first, start.second)
        for (step in 1..12) {
            SystemClock.sleep(20)
            pointer(started, MotionEvent.ACTION_MOVE, start.first, start.second + (end.second - start.second) * step / 12)
        }
        // End with a stationary finger, then observe actual scroll positions;
        // two animation frames alone do not imply that WebView momentum stopped.
        SystemClock.sleep(100)
        pointer(started, MotionEvent.ACTION_UP, end.first, end.second)
        awaitScrollStable()
    }

    fun awaitScrollStable() {
        var previous = ""
        var stableSince = SystemClock.uptimeMillis()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            val current = board.evaluate("[window.scrollX,window.scrollY,document.getElementById('tasks').scrollTop].join(',')")
            if (current != previous) { previous = current; stableSince = SystemClock.uptimeMillis() }
            SystemClock.uptimeMillis() - stableSince >= 120
        }
        awaitFrame()
    }

    private fun pointer(started: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(started, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try { assertTrue("Touch injection failed", instrumentation.uiAutomation.injectInputEvent(event, true)) }
        finally { event.recycle() }
    }

    /** Only resets the scroll position between scenarios; does not resize or replace the UI. */
    fun resetScroll() {
        board.evaluate("window.scrollTo(0,0);document.getElementById('tasks').scrollTop=0;true")
        awaitFrame()
    }

    fun scrollPosition(documentScroll: Boolean): Double = geometry().getJSONObject("web")
        .getJSONObject(if (documentScroll) "document" else "tasks").getDouble("scrollTop")

    fun scrollUntilVisible(selector: String, documentScroll: Boolean) {
        repeat(18) {
            if (fullyVisible(selector)) return
            swipeUp(documentScroll)
        }
        assertVisibleAndHit(selector)
    }

    fun capture(label: String, measured: JSONObject? = null, displayConfiguration: JSONObject? = null) {
        val args = InstrumentationRegistry.getArguments()
        val folder = args.getString("additionalTestOutputDir")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(instrumentation.targetContext.externalMediaDirs.first(), "additional_test_output")
        check(folder.isDirectory || folder.mkdirs()) { "Cannot create test evidence directory: $folder" }
        val name = "taskboard-api${Build.VERSION.SDK_INT}-" + label.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val data = measured ?: runCatching { geometry() }.getOrElse { JSONObject().put("geometryError", it.toString()) }
        displayConfiguration?.let { data.put("displayConfiguration", it) }
        File(folder, "$name.json").writeText(data.put("label", label).put("api", Build.VERSION.SDK_INT).toString(2))
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Screenshot unavailable: $name" }
        try { File(folder, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
    }
}
