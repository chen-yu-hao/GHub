package dev.remotegpt.app

import android.graphics.Bitmap
import org.junit.runners.MethodSorters
import java.io.FileOutputStream
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.remotegpt.client.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4::class)
class WorkbenchDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun emptyWorkspaceOffersHostSetupAndAccessibleEditor() {
        compose.onNodeWithText(tr(R.string.ui_open_settings)).assertIsDisplayed().performClick()
        compose.onAllNodesWithText(tr(R.string.ui_servers)).onLast().assertIsDisplayed().performClick()
        compose.onNodeWithText(tr(R.string.ui_add_server)).assertIsDisplayed().performClick()
        compose.onNodeWithText(tr(R.string.ui_connection_name)).assertIsDisplayed()
        compose.onNodeWithText(tr(R.string.ui_host_address)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(tr(R.string.ui_username)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(tr(R.string.ui_save_connection)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(tr(R.string.ui_back_to_servers)).performClick()
        compose.onNodeWithContentDescription(tr(R.string.ui_back)).performClick()
        compose.onNodeWithContentDescription(tr(R.string.ui_back)).performClick()
        compose.onNodeWithText(tr(R.string.ui_connect_your_development_machine)).assertIsDisplayed()
    }

    @Test fun androidSshAndProxyReadExistingDaemon() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue("Pass fixture arguments to run real SSH device test", args.containsKey("sshPort"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val key = File(context.filesDir, "device-test-key").readText()
        val endpoint = SshEndpoint(args.getString("sshHost", "10.0.2.2"), args.getString("sshPort")!!.toInt(), args.getString("sshUser")!!)
        val jumps = args.getString("jumpPorts")?.split(',')?.mapIndexed { i, port -> SshEndpoint(if (i == 0) args.getString("sshHost", "10.0.2.2") else "127.0.0.1", port.toInt(), endpoint.username) }.orEmpty()
        val profile = HostProfile(name = "device fixture", endpoint = if (jumps.isEmpty()) endpoint else endpoint.copy(host = "127.0.0.1"), jumps = jumps, codexPath = args.getString("codexPath")!!)
        val prompts = object : SshPrompts {
            override suspend fun credential(id: String) = Credential(privateKey = key, passphrase = args.getString("keyPassphrase").orEmpty())
            override suspend fun verifyHostKey(host: String, port: Int, algorithm: String, fingerprint: String) = fingerprint == args.getString("fingerprint")
            override suspend fun keyboard(prompt: KeyboardPrompt): List<String> = error("Unexpected keyboard prompt")
        }
        SshConnection.connect(profile, prompts).use { ssh ->
            assertTrue(Regex("\\\"appServerVersion\\\"\\s*:\\s*\\\"0\\.\\d+\\.\\d+\\\"").containsMatchIn(ssh.version()))
            RpcClient(ProxyTransport(ssh)).use { rpc ->
                assertNotNull(rpc.connect().string("userAgent"))
                assertNotNull(rpc.request("thread/loaded/list")["data"])
            }
            val files = RemoteFiles(ssh)
            assertTrue(files.home().startsWith('/'))
        }
        Unit
    }
    @Test fun workbenchResumesPendingApprovalAndRunsRemoteTerminal() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue("Requires a disposable daemon fixture", args.containsKey("threadId"))
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RemoteApplication
        val endpoint = SshEndpoint(args.getString("sshHost", "10.0.2.2"), args.getString("sshPort")!!.toInt(), args.getString("sshUser")!!)
        val profile = HostProfile(name = "验证主机", endpoint = endpoint, codexPath = args.getString("codexPath")!!)
        val thread = args.getString("threadId")!!
        lateinit var vm: WorkbenchViewModel
        compose.runOnIdle { vm = app.workbench() }
        app.settings.trust(endpoint.host, endpoint.port, args.getString("fingerprint")!!)
        val key = File(app.filesDir, "device-test-key").readText()
        lateinit var save: kotlinx.coroutines.Job
        compose.runOnIdle { save = vm.saveProfile(profile, mapOf(endpoint.credentialId to Credential(privateKey = key, passphrase = args.getString("keyPassphrase").orEmpty()))) }
        save.join()
        compose.waitUntil(10_000) { vm.state.value.profiles.any { it.id == profile.id } }
        val notices = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val noticesJob = launch(Dispatchers.Default) { vm.state.map { it.message }.distinctUntilChanged().collect { if (it != null) notices.add(it) } }
        try {
            android.util.Log.i("WorkbenchCheck", "connecting")
            compose.runOnIdle { vm.connect(profile.id) }
            compose.waitUntil(60_000) { vm.state.value.workspace.connection in setOf(ConnectionStatus.ONLINE, ConnectionStatus.ERROR) }
            assertEquals(vm.state.value.workspace.error, ConnectionStatus.ONLINE, vm.state.value.workspace.connection)
            android.util.Log.i("WorkbenchCheck", "connected; opening fixture")
            compose.runOnIdle { vm.openThread(thread) }
            compose.waitUntil(30_000) { vm.state.value.workspace.approvals.any { it.request.threadId == thread } }
            android.util.Log.i("WorkbenchCheck", "approval received")
            val first = vm.state.value.workspace.approvals.single { it.request.threadId == thread }.request
            compose.runOnIdle { vm.disconnect(profile.id) }
            compose.waitUntil { vm.state.value.workspace.approvals.isEmpty() }
            compose.runOnIdle { vm.connect(profile.id) }
            compose.waitUntil(60_000) { vm.state.value.workspace.approvals.any { it.request.threadId == thread } }
            android.util.Log.i("WorkbenchCheck", "approval replayed")
            val replay = vm.state.value.workspace.approvals.single { it.request.threadId == thread }.request
            assertEquals(first.id, replay.id)
            assertNotEquals(first.epoch, replay.epoch)
            android.util.Log.i("WorkbenchCheck", "approving via UI")
            compose.onNodeWithText("允许一次").performScrollTo().performClick()
            compose.waitUntil(60_000) { vm.state.value.conversation?.items?.any { it.text.contains("Remote GPT approval probe completed.") } == true }
            compose.waitUntil { vm.state.value.workspace.approvals.isEmpty() }
            android.util.Log.i("WorkbenchCheck", "turn completed")
            compose.runOnIdle { vm.draftText("保留的中文草稿"); vm.selectTab(WorkspaceTab.FILES); vm.listFiles(args.getString("fixtureRoot")) }
            compose.waitUntil(20_000) { vm.state.value.workspace.files.any { it.name == "device-note.txt" } }
            compose.runOnIdle { vm.editFile(args.getString("fixtureRoot") + "/device-note.txt") }
            compose.waitUntil(20_000) { vm.state.value.workspace.editor != null }
            compose.runOnIdle { vm.editorText("手机编辑成功"); vm.saveFile() }
            compose.waitUntil(20_000) { vm.state.value.workspace.editor?.text == "手机编辑成功" }
            compose.runOnIdle { vm.closeEditor(); vm.selectTab(WorkspaceTab.TERMINAL); vm.newTerminal() }
            compose.waitUntil(20_000) { vm.state.value.workspace.terminals.isNotEmpty() }
            android.util.Log.i("WorkbenchCheck", "terminal opened")
            val terminal = vm.state.value.workspace.terminals.last().session
            compose.waitUntil(10_000) { terminal.getEmulator() != null }
            compose.runOnIdle { terminal.write("printf '手机%s OK\\n' '终端'\r") }
            compose.waitUntil(15_000) { terminal.getEmulator()?.screen?.transcriptText?.contains("手机终端 OK") == true }
            compose.runOnIdle { terminal.write("vim -Nu NONE -i NONE " + shellQuote(args.getString("fixtureRoot") + "/device-note.txt") + "\r") }
            compose.waitUntil(20_000) { terminal.getEmulator()?.isAlternateBufferActive == true }
            compose.runOnIdle { terminal.write("\u001b:q!\r") }
            compose.waitUntil(15_000) { terminal.getEmulator()?.isAlternateBufferActive == false }
            val socketName = "remote-gpt-device-" + args.getString("sshPort")
            compose.runOnIdle { terminal.write("tmux -L $socketName -f /dev/null new-session -s probe\r") }
            compose.waitUntil(20_000) { terminal.getEmulator()?.isAlternateBufferActive == true }
            compose.runOnIdle { terminal.write("printf 'TMUX_%s_OK\\n' DEVICE\r") }
            compose.waitUntil(15_000) { terminal.getEmulator()?.screen?.transcriptText?.contains("TMUX_DEVICE_OK") == true }
            compose.runOnIdle { terminal.write("\u0002d") }
            compose.waitUntil(15_000) { terminal.getEmulator()?.isAlternateBufferActive == false }
            compose.runOnIdle { vm.closeTerminal(vm.state.value.workspace.terminals.last().id); vm.newTerminal() }
            compose.waitUntil(20_000) { vm.state.value.workspace.terminals.isNotEmpty() }
            val restoredTerminal = vm.state.value.workspace.terminals.last().session
            compose.runOnIdle { restoredTerminal.write("tmux -L $socketName attach-session -t probe\r") }
            compose.waitUntil(20_000) { restoredTerminal.getEmulator()?.screen?.transcriptText?.contains("TMUX_DEVICE_OK") == true }
            compose.runOnIdle { restoredTerminal.write("\u0002d") }
            compose.waitUntil(15_000) { restoredTerminal.getEmulator()?.isAlternateBufferActive == false }
            compose.runOnIdle { restoredTerminal.write("tmux -L $socketName kill-server\r") }
            android.util.Log.i("WorkbenchCheck", "vim and tmux reconnect passed")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(20_000) { vm.state.value.workspace.terminals.isNotEmpty() }
            compose.runOnIdle { assertSame(vm, app.workbench()) }
            compose.runOnIdle { vm.selectTab(WorkspaceTab.SESSIONS) }
            assertEquals("保留的中文草稿", vm.state.value.draft.text)
            val image = File(app.cacheDir, "shared/device-image.png").apply { parentFile!!.mkdirs() }
            val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            val uri = androidx.core.content.FileProvider.getUriForFile(app, app.packageName + ".files", image)
            compose.runOnIdle { vm.upload(uri, true) }
            compose.waitUntil(20_000) { vm.state.value.draft.attachments.singleOrNull()?.state == "ready" }
            compose.runOnIdle { vm.message(null) }
            compose.waitForIdle()
            android.util.Log.i("WorkbenchCheck", "sending image; connection=${vm.state.value.workspace.connection}; draft=${vm.state.value.draft.delivery}; archived=${vm.state.value.thread?.archived}")
            compose.onNodeWithContentDescription("发送消息").assertIsEnabled().performClick()
            compose.waitUntil(30_000) { vm.state.value.draft.text.isEmpty() && vm.state.value.draft.attachments.isEmpty() }
            compose.waitUntil(30_000) { vm.state.value.conversation?.items?.any { it.kind == "userMessage" && it.imagePaths.isNotEmpty() } == true }
            compose.runOnIdle { vm.message(null); vm.draftText("可继续编辑的草稿") }
            compose.waitForIdle()
            android.util.Log.i("WorkbenchCheck", "image sent; activity recreation passed")
            compose.runOnIdle { vm.setBackgroundKeepAlive(false); vm.onBackground() }
            compose.waitUntil(40_000) { vm.state.value.workspace.connection == ConnectionStatus.OFFLINE }
            assertTrue(vm.state.value.workspace.terminals.isEmpty())
            assertFalse(restoredTerminal.isRunning())
            assertEquals("可继续编辑的草稿", vm.state.value.draft.text)
            compose.runOnIdle { vm.onForeground() }
            compose.waitUntil(60_000) { vm.state.value.workspace.connection == ConnectionStatus.ONLINE }
            assertEquals("可继续编辑的草稿", vm.state.value.draft.text)
            android.util.Log.i("WorkbenchCheck", "background release and foreground reconnect passed")
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            FileOutputStream(File(app.filesDir, "device-workbench.png")).use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
        } catch (error: Throwable) {
            android.util.Log.e("WorkbenchCheck", "device workflow failed; connection=${vm.state.value.workspace.connection}; delivery=${vm.state.value.draft.delivery}; message=${vm.state.value.message}; notices=$notices", error)
            val failure = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            FileOutputStream(File(app.filesDir, "device-workbench.png")).use { failure.compress(Bitmap.CompressFormat.PNG, 100, it) }; failure.recycle()
            throw error
        } finally {
            noticesJob.cancel()
            lateinit var remove: kotlinx.coroutines.Job
            compose.runOnIdle { remove = vm.deleteProfile(profile) }
            remove.join()
        }
        Unit
    }

}
