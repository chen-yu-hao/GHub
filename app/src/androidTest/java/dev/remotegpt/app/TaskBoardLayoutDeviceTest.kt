package dev.remotegpt.app

import android.app.UiAutomation
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.remotegpt.app.data.ConversationRow
import dev.remotegpt.app.data.ThreadRow
import dev.remotegpt.client.CommandExecutionDetails
import dev.remotegpt.client.Conversation
import dev.remotegpt.client.HostProfile
import dev.remotegpt.client.SshEndpoint
import dev.remotegpt.client.ThreadSummary
import dev.remotegpt.client.TimelineItem
import dev.remotegpt.client.wireJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Real MainActivity, Room/Settings fixtures, Android display rotation and touchscreen input. */
@RunWith(AndroidJUnit4::class)
class TaskBoardLayoutDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val board get() = TaskBoardTestDriver(compose) { compose.activity }
    private val geometry by lazy { TaskBoardGeometryDriver(compose, board) { compose.activity } }
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    @Test fun nativeViewportAndReachableControlsSurviveTallAndShortDisplayRotations() = runBlocking {
        // wm overrides belong only on a disposable emulator, never a developer's physical device.
        assumeTrue("Controlled display profiles require an emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RemoteApplication
        val vm = app.workbench()
        val display = DisplaySettings()
        val profile = HostProfile(name = "布局回归服务器", endpoint = SshEndpoint("geometry.invalid", username = "fixture"))
        var failure: Throwable? = null
        var stage = "setup"
        try {
            seedCachedTasks(app, profile)
            board.observeNativeSnapshots()
            withContext(Dispatchers.Main) { vm.saveProfile(profile, emptyMap()) }.join()
            compose.waitUntil(10_000) {
                vm.state.value.workspaces[profile.id]?.conversations?.get("geometry-failed-long")?.items?.isNotEmpty() == true
            }
            compose.runOnIdle { vm.selectHost(profile.id); vm.message(null) }
            board.awaitJavascript("""
                Boolean(window.boardObservedNativeSnapshot && window.boardObservedNativeSnapshot.tasks.some(function(task){
                  return task.hostId===${JSONObject.quote(profile.id)} && task.threadId==='geometry-failed-long' && task.command.indexOf('GEOMETRY_LINE_40')>=0;
                }))
            """.trimIndent())
            // Select the fixture using the production filter; subsequent recreations restore its real preferences.
            board.click("#filter")
            board.selectHost(profile.id)
            board.clickText("应用筛选")
            val observedModes = mutableSetOf<String>()
            for (size in listOf(DisplayProfile("reference", 1264, 1680, 632, 840, false),
                DisplayProfile("short", 720, 1280, 360, 640, true))) {
                stage = "${size.name}-configure"
                display.configure(size.widthPx, size.heightPx)
                // wm size and density can each recreate the Activity and reconfigure SystemUI.
                // First wait for the final display, then let the normal onCreate reapply immersion.
                geometry.awaitViewport(size.widthDp, size.heightDp)
                val configuredViewId = geometry.nativeGeometry().getInt("viewId")
                compose.activityRule.scenario.recreate()
                geometry.awaitViewport(size.widthDp, size.heightDp, configuredViewId)
                for ((index, rotation) in listOf(UiAutomation.ROTATION_FREEZE_0, UiAutomation.ROTATION_FREEZE_90, UiAutomation.ROTATION_FREEZE_0).withIndex()) {
                    val landscape = rotation == UiAutomation.ROTATION_FREEZE_90
                    stage = "${size.name}-" + if (landscape) "landscape" else if (index == 0) "portrait" else "portrait-return"
                    val oldId = if (index == 0) null else geometry.nativeGeometry().getInt("viewId")
                    assertTrue("Emulator did not accept display rotation", automation.setRotation(rotation))
                    val width = if (landscape) size.heightDp else size.widthDp
                    val height = if (landscape) size.widthDp else size.heightDp
                    geometry.awaitViewport(width, height, oldId)
                    geometry.resetScroll()
                    assertLayout(geometry.geometry(), width, height, size.documentScroll)
                    observedModes += if (size.documentScroll) "document" else "tasks"

                    // Both real paging buttons and the newly rendered first row must be reachable.
                    geometry.tap("#tabs button[data-state='done']")
                    board.awaitJavascript("document.querySelectorAll('#tasks .completed-task').length===7")
                    geometry.awaitScrollStable()
                    geometry.scrollUntilVisible("#next", size.documentScroll)
                    geometry.tap("#next")
                    board.awaitJavascript("document.getElementById('page-label').textContent.indexOf('2 / 2')>=0")
                    geometry.awaitScrollStable()
                    geometry.assertVisibleAndHit("#tasks .completed-task:first-child")
                    geometry.scrollUntilVisible("#previous", size.documentScroll)
                    geometry.tap("#previous")
                    board.awaitJavascript("document.getElementById('page-label').textContent.indexOf('1 / 2')>=0")
                    geometry.awaitScrollStable()
                    geometry.assertVisibleAndHit("#tasks .completed-task:first-child")

                    geometry.resetScroll()
                    geometry.tap("#tabs button[data-state='failed']")
                    board.awaitJavascript("document.querySelectorAll('#tasks .task-card').length===2 && document.getElementById('tasks').textContent.indexOf('GEOMETRY_LINE_40')>=0")
                    geometry.resetScroll()
                    val before = geometry.geometry()
                    assertLayout(before, width, height, size.documentScroll)
                    geometry.capture("$stage-top", before.put("expectedWidthDp", width).put("expectedHeightDp", height), display.diagnostics)
                    val scrollport = before.getJSONObject("web").getJSONObject(if (size.documentScroll) "document" else "tasks")
                    assertTrue("Fixture must exercise real overflow: $before", scrollport.getDouble("scrollHeight") > scrollport.getDouble("clientHeight") + 20)
                    val start = geometry.scrollPosition(size.documentScroll)
                    geometry.swipeUp(size.documentScroll)
                    assertTrue("A real swipe must scroll the intended container", geometry.scrollPosition(size.documentScroll) > start + 2)
                    val lastAction = "#tasks .task-card:last-child .card-actions button"
                    geometry.scrollUntilVisible(lastAction, size.documentScroll)
                    geometry.scrollUntilVisible(".footer", size.documentScroll)
                    geometry.assertVisibleAndHit(lastAction)
                    geometry.assertVisibleAndHit("#previous")
                    geometry.assertVisibleAndHit("#next")
                    val after = geometry.geometry()
                    assertNoHorizontalOverflow(after)
                    if (size.documentScroll) {
                        near("Short-screen tasks must not become a nested vertical scroller", 0.0, after.web("tasks").getDouble("scrollTop"))
                    } else {
                        near("Only the task list scrolls on the reference viewport", 0.0, after.web("document").getDouble("scrollTop"))
                        for (name in listOf("header", "tabs", "footer")) near("$name moved with task content", before.web(name).getDouble("top"), after.web(name).getDouble("top"))
                    }
                    geometry.capture("$stage-scrolled", after.put("expectedWidthDp", width).put("expectedHeightDp", height), display.diagnostics)
                    geometry.resetScroll()
                }
            }
            assertEquals("Both actual scrolling modes must be exercised", setOf("document", "tasks"), observedModes)
        } catch (error: Throwable) {
            failure = error
            runCatching { geometry.capture("$stage-failure", displayConfiguration = display.diagnostics) }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        } finally {
            val cleanupErrors = mutableListOf<Throwable>()
            runCatching { display.restore() }.exceptionOrNull()?.let(cleanupErrors::add)
            runCatching {
                withContext(Dispatchers.Main) { vm.deleteProfile(profile) }.join()
                app.settings.results(profile.id, emptyMap())
                app.settings.lastThread(profile.id, null)
                app.settings.lastProject(profile.id, null)
                app.settings.projects(profile.id, emptyList())
            }.exceptionOrNull()?.let(cleanupErrors::add)
            if (failure != null) cleanupErrors.forEach(failure::addSuppressed)
            else if (cleanupErrors.isNotEmpty()) {
                cleanupErrors.drop(1).forEach(cleanupErrors.first()::addSuppressed)
                throw cleanupErrors.first()
            }
        }
    }

    private suspend fun seedCachedTasks(app: RemoteApplication, profile: HostProfile) {
        val path = "/tmp/geometry/" + "LongUnbrokenProjectPath".repeat(6)
        val now = System.currentTimeMillis() / 1_000
        val failed = ThreadSummary("geometry-failed-long", "长命令布局检查 · " + "应保持完整页面宽度".repeat(8), path, updatedAt = now)
        val second = ThreadSummary("geometry-failed-last", "最后一张任务卡片及操作按钮", path, updatedAt = now - 1)
        val completed = (1..8).map { ThreadSummary("geometry-done-$it", "布局完成任务 $it · " + "长任务标题".repeat(12), path, updatedAt = now - it) }
        val all = listOf(failed, second) + completed
        app.database.workbench().putThreads(all.map { ThreadRow(profile.id, it.id, wireJson.encodeToString(it)) })
        val command = (1..40).joinToString("\n") { "printf GEOMETRY_LINE_$it " + "long_argument_".repeat(4) }
        val conversation = Conversation(failed.id, items = listOf(TimelineItem(
            "geometry-command", "geometry-turn", "commandExecution", "Fixture command failed", complete = true, failed = true,
            executionStatus = "failed", commandExecution = CommandExecutionDetails(command, path, 7, 123),
        )), status = "failed", latestTurnId = "geometry-turn")
        app.database.workbench().putConversation(ConversationRow(profile.id, failed.id, wireJson.encodeToString(conversation)))
        app.settings.results(profile.id, all.associate { summary ->
            val done = summary.id.startsWith("geometry-done-")
            val turn = if (summary == failed) "geometry-turn" else "${summary.id}-turn"
            summary.id to ResultReceipt(turn, if (done) "completed" else "failed", summary.updatedAt,
                completedTurnId = turn.takeIf { done }, seenTurnId = turn.takeIf { done })
        })
        // The normal VM host observer loads cached history for its remembered thread.
        app.settings.lastThread(profile.id, failed.id)
    }

    private fun assertLayout(data: JSONObject, widthDp: Int, heightDp: Int, documentScroll: Boolean) {
        val native = data.getJSONObject("native")
        val view = native.getJSONObject("view")
        val content = native.getJSONObject("content")
        val insets = native.getJSONObject("insets")
        val density = native.getDouble("density")
        val visible = native.getJSONObject("visible")
        val viewport = data.web("viewport")
        val boardRect = data.web("board")
        near("Effective native width must match the requested display profile", widthDp.toDouble(), native.getJSONObject("root").getDouble("width") / density)
        near("Effective native height must match the requested display profile", heightDp.toDouble(), native.getJSONObject("root").getDouble("height") / density)
        near("WebView must fill available native width", content.getDouble("width") - insets.getInt("left") - insets.getInt("right"), view.getDouble("width"), density * 2)
        near("WebView must fill available native height", content.getDouble("height") - insets.getInt("top") - insets.getInt("bottom"), view.getDouble("height"), density * 2)
        near("WebView must start at available native left edge", content.getDouble("left") + insets.getInt("left"), view.getDouble("left"), density * 2)
        near("WebView must start at available native top edge", content.getDouble("top") + insets.getInt("top"), view.getDouble("top"), density * 2)
        assertTrue("WebView must be visible", visible.getBoolean("present"))
        near("Native clipping removed part of WebView", view.getDouble("height"), (visible.getInt("bottom") - visible.getInt("top")).toDouble(), density * 2)
        near("Native clipping removed WebView width", view.getDouble("width"), (visible.getInt("right") - visible.getInt("left")).toDouble(), density * 2)
        near("Unexpected viewport zoom", 1.0, viewport.getDouble("scale"), 0.02)
        near("CSS width must match actual WebView dp", view.getDouble("width") / density, viewport.getDouble("width"))
        near("CSS height must match actual WebView dp (WRAP_CONTENT regression)", view.getDouble("height") / density, viewport.getDouble("height"))
        near("Layout viewport height must match actual WebView dp", view.getDouble("height") / density, viewport.getDouble("innerHeight"))
        near("documentElement viewport height must match actual WebView dp", view.getDouble("height") / density, viewport.getDouble("clientHeight"))
        near("Device pixel ratio must agree with native density", density, viewport.getDouble("dpr"), 0.05)
        near("Board must use the entire viewport width", viewport.getDouble("width"), boardRect.getDouble("width"))
        near("Board must start at the viewport left edge", 0.0, boardRect.getDouble("left"))
        assertEquals("Four visible state tabs", 4, data.getJSONObject("web").getJSONArray("tabRects").length())
        for (selector in listOf(".header", ".filter-row", "#tabs", "#sync", "#refresh", "#filter")) geometry.assertVisibleAndHit(selector)
        for (state in listOf("wait", "run", "done", "failed")) geometry.assertVisibleAndHit("#tabs button[data-state='$state']")
        val overflow = data.web("tasks").getString("overflowY")
        if (documentScroll) {
            assertEquals("Short viewport must use document scrolling", "visible", overflow)
            assertTrue("Natural page must cover at least the viewport", boardRect.getDouble("height") >= viewport.getDouble("height") - 2)
        } else {
            assertTrue("Reference viewport must use a scrollable task area", overflow in setOf("auto", "scroll"))
            near("Normal board must fill exactly one viewport", viewport.getDouble("height"), boardRect.getDouble("height"))
            geometry.assertVisibleAndHit(".footer")
            assertTrue("Task scrollport collapsed", data.web("tasks").getDouble("height") > 40)
        }
        assertNoHorizontalOverflow(data)
    }

    private fun assertNoHorizontalOverflow(data: JSONObject) {
        val viewport = data.web("viewport")
        for (name in listOf("document", "board", "tasks")) {
            val node = data.web(name)
            assertTrue("Horizontal overflow in $name: $node", node.getDouble("scrollWidth") <= node.getDouble("clientWidth") + 2)
        }
        for (name in listOf("header", "filter", "tabs", "footer")) {
            val rect = data.web(name)
            assertTrue("$name is horizontally clipped: $rect", rect.getDouble("left") >= -2 && rect.getDouble("right") <= viewport.getDouble("width") + 2)
        }
        val cards = data.getJSONObject("web").getJSONArray("cards")
        repeat(cards.length()) { index ->
            val card = cards.getJSONObject(index)
            assertTrue("Task card exceeds the viewport width: $card", card.getDouble("left") >= -2 && card.getDouble("right") <= viewport.getDouble("width") + 2)
        }
    }

    private fun JSONObject.web(name: String): JSONObject = getJSONObject("web").getJSONObject(name)
    private fun near(message: String, expected: Double, actual: Double, tolerance: Double = 2.0) =
        assertTrue("$message; expected=$expected actual=$actual tolerance=$tolerance", abs(expected - actual) <= tolerance)

    private data class DisplayProfile(val name: String, val widthPx: Int, val heightPx: Int, val widthDp: Int, val heightDp: Int, val documentScroll: Boolean)

    private inner class DisplaySettings {
        private val initialSizeReport = shell("wm size").trim()
        private val initialDensityReport = shell("wm density").trim()
        private val oldSize = Regex("Override size: (\\d+x\\d+)").find(initialSizeReport)?.groupValues?.get(1)
        private val oldDensity = Regex("Override density: (\\d+)").find(initialDensityReport)?.groupValues?.get(1)
        private val oldAutoRotate = shell("settings get system accelerometer_rotation").trim()
        private val oldUserRotation = shell("settings get system user_rotation").trim()
        var diagnostics: JSONObject = JSONObject().put("initialSizeReport", initialSizeReport).put("initialDensityReport", initialDensityReport)
            private set
        fun configure(width: Int, height: Int) {
            val requestedSize = "${width}x$height"
            diagnostics = JSONObject().put("initialSizeReport", initialSizeReport).put("initialDensityReport", initialDensityReport)
                .put("requestedSize", requestedSize).put("requestedDensity", 320)
            assertTrue(automation.setRotation(UiAutomation.ROTATION_FREEZE_0))
            diagnostics.put("sizeCommandOutput", shell("wm size $requestedSize").trim())
            diagnostics.put("densityCommandOutput", shell("wm density 320").trim())
            val sizeReport = shell("wm size").trim()
            val densityReport = shell("wm density").trim()
            val effectiveSize = effectiveValue(sizeReport, "size", "\\d+x\\d+")
            val effectiveDensity = effectiveValue(densityReport, "density", "\\d+")
            diagnostics.put("sizeReport", sizeReport).put("densityReport", densityReport)
                .put("effectiveSize", effectiveSize).put("effectiveDensity", effectiveDensity)
            Log.i("TaskBoardTest", "Display configuration: $diagnostics")
            assertEquals("wm size was rejected or clamped; use a larger physical AVD profile. $diagnostics", requestedSize, effectiveSize)
            assertEquals("wm density did not accept the requested profile. $diagnostics", "320", effectiveDensity)
        }
        private fun effectiveValue(report: String, kind: String, pattern: String): String? =
            Regex("Override $kind: ($pattern)").find(report)?.groupValues?.get(1)
                ?: Regex("Physical $kind: ($pattern)").find(report)?.groupValues?.get(1)
        fun restore() {
            val errors = mutableListOf<Throwable>()
            for (operation in listOf<() -> Unit>(
                { shell("wm size ${oldSize ?: "reset"}") },
                { shell("wm density ${oldDensity ?: "reset"}") },
                { check(automation.setRotation(UiAutomation.ROTATION_UNFREEZE)) },
                { restoreSetting("user_rotation", oldUserRotation) },
                { restoreSetting("accelerometer_rotation", oldAutoRotate) },
            )) runCatching(operation).exceptionOrNull()?.let(errors::add)
            if (errors.isNotEmpty()) { errors.drop(1).forEach(errors.first()::addSuppressed); throw errors.first() }
        }
        private fun restoreSetting(name: String, value: String) {
            if (value == "null" || value.isEmpty()) shell("settings delete system $name")
            else { check(value.matches(Regex("\\d+"))); shell("settings put system $name $value") }
        }
        private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    }
}
