package dev.remotegpt.app

import android.webkit.WebSettings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.remotegpt.client.AuthKind
import dev.remotegpt.client.Credential
import dev.remotegpt.client.HostProfile
import dev.remotegpt.client.SshEndpoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskBoardDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val board get() = TaskBoardTestDriver(compose) { compose.activity }

    @Test fun packagedPageLoadsAndActualDomButtonOpensNativeHostManagement() {
        board.awaitReady()
        board.awaitText("任务看板")
        assertEquals("4", board.evaluate("document.querySelectorAll('#tabs button[data-state]').length"))
        assertEquals("true", board.evaluate("typeof window.GHubBoard.postMessage === 'function'"))
        board.click("#filter")
        board.clickText("管理 SSH 服务器")
        compose.waitUntil { compose.onAllNodesWithText(tr(R.string.ui_add_server)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(tr(R.string.ui_add_server)).assertIsDisplayed()
        compose.onNodeWithContentDescription(tr(R.string.ui_back)).performClick()
        compose.onNodeWithContentDescription(tr(R.string.ui_back)).performClick()
        board.awaitReady()
        board.awaitText("任务看板")
    }

    @Test fun nativeSnapshotAppearsAndCredentialsStayOutsideWebViewAcrossRecreation() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RemoteApplication
        val vm = app.workbench()
        val secret = "task-board-device-secret-not-for-webview"
        val profile = HostProfile(name = "Board snapshot fixture", endpoint = SshEndpoint("board.invalid", 22, "fixture", auth = AuthKind.PASSWORD))
        lateinit var operation: Job
        try {
            board.observeNativeSnapshots()
            compose.runOnIdle { operation = vm.saveProfile(profile, mapOf(profile.endpoint.credentialId to Credential(password = secret))) }
            operation.join()
            compose.waitUntil(10_000) { vm.state.value.profiles.any { it.id == profile.id } }
            compose.runOnIdle { vm.selectHost(profile.id); vm.saveProject("Native project fixture", "/tmp/board-fixture") }
            compose.waitUntil(10_000) { vm.state.value.workspace.projects.any { it.path == "/tmp/board-fixture" } }
            // Observe a real native snapshot, then inspect the DOM it produced; no fixture snapshot is injected.
            board.awaitJavascript("""
                Boolean(window.boardObservedNativeSnapshot && window.boardObservedNativeSnapshot.hosts.some(function(host){
                  return host.id===${JSONObject.quote(profile.id)} && host.projects.some(function(project){return project.path==='/tmp/board-fixture';});
                }))
            """.trimIndent())
            board.click("#filter")
            board.selectHost(profile.id)
            assertEquals("true", board.evaluate("document.querySelector('#modal').textContent.includes('Native project fixture')"))
            assertEquals("false", board.evaluate("JSON.stringify(window.boardObservedNativeSnapshot).includes(${JSONObject.quote(secret)})"))
            assertEquals("false", board.evaluate("document.documentElement.outerHTML.includes(${JSONObject.quote(secret)})"))
            assertEquals("true", board.evaluate("typeof window.GHubBoard.credential === 'undefined' && typeof window.GHubBoard.privateKey === 'undefined'"))
            board.clickText("应用筛选")
            board.awaitText("Board snapshot fixture · 全部项目")
            compose.activityRule.scenario.recreate()
            board.awaitReady()
            board.awaitText("Board snapshot fixture · 全部项目")
            assertEquals(profile.id, vm.state.value.activeHost)
            assertEquals("false", board.evaluate("document.documentElement.outerHTML.includes(${JSONObject.quote(secret)})"))
            // A successful save does not imply a successful SSH connection or task synchronization.
            board.awaitText("尚未同步")
        } finally {
            compose.runOnIdle { operation = vm.deleteProfile(profile) }
            operation.join()
        }
    }

    @Test fun unknownBridgeActionsAreIgnoredAndExternalNavigationIsBlocked() {
        board.awaitReady()
        val originalUrl = board.evaluate("location.href")
        board.post(JSONObject().put("type", "runCommand").put("command", "forbidden command"))
        board.awaitText("任务看板")
        assertEquals(originalUrl, board.evaluate("location.href"))
        board.withView { view ->
            assertFalse(view.settings.allowFileAccess)
            assertFalse(view.settings.allowContentAccess)
            assertTrue(view.settings.blockNetworkLoads)
            assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, view.settings.mixedContentMode)
        }
        board.evaluate("""
            (function(){var link=document.createElement('a');link.href='https://example.invalid/forbidden';
            document.body.appendChild(link);link.click();link.remove();return true;})()
        """.trimIndent())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(originalUrl, board.evaluate("location.href"))
        board.evaluate("""
            window.boardBlockedFetch='pending';fetch('https://example.invalid/forbidden')
              .then(function(){window.boardBlockedFetch='allowed';})
              .catch(function(){window.boardBlockedFetch='blocked';});true
        """.trimIndent())
        board.awaitJavascript("window.boardBlockedFetch === 'blocked'")
    }

    @Test fun androidBackClosesHtmlFilterInsteadOfFinishingActivity() {
        board.awaitReady()
        board.click("#filter")
        board.awaitJavascript("document.getElementById('modal-backdrop').hidden === false")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        board.awaitJavascript("document.getElementById('modal-backdrop').hidden === true")
        assertFalse(compose.activity.isFinishing)
        board.awaitText("任务看板")
    }
}
