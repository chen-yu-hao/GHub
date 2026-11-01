package dev.remotegpt.app

import androidx.test.platform.app.InstrumentationRegistry
import dev.remotegpt.app.data.decodeConversationCache
import dev.remotegpt.app.data.encodeConversationCache
import dev.remotegpt.client.*
import kotlinx.coroutines.*
import org.junit.Assume
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Runs with COMMAND_OUTPUT_FIXTURE=1 scripts/device_check.py against an isolated
 * real SSH server and Codex daemon; never uses a user's model or conversations. */
class CommandHistoryDeviceTest {
    @Test fun failedStreamingCommandSurvivesSshReconnectAndDiskCache() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue("Requires isolated command-output fixture", args.getString("commandOutputFixture") == "1")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val thread = args.getString("threadId")!!
        val profile = HostProfile(name = "command fixture", codexPath = args.getString("codexPath")!!,
            endpoint = SshEndpoint(args.getString("sshHost")!!, args.getString("sshPort")!!.toInt(), args.getString("sshUser")!!))
        val key = File(context.filesDir, "device-test-key").readText()
        val prompts = object : SshPrompts {
            override suspend fun credential(id: String) = Credential(privateKey = key, passphrase = args.getString("keyPassphrase")!!)
            override suspend fun verifyHostKey(host: String, port: Int, algorithm: String, fingerprint: String) = fingerprint == args.getString("fingerprint")
            override suspend fun keyboard(prompt: KeyboardPrompt): List<String> = error("Unexpected challenge")
        }
        val expected = (0 until 180).joinToString("\n", postfix = "\n") { "GHUB_ROW_${it.toString().padStart(3, '0')} 中文" }
        var current = Conversation(thread)
        var deltaCount = 0
        withContext(Dispatchers.IO) {
            SshConnection.connect(profile, prompts).use { ssh ->
                RpcClient(ProxyTransport(ssh)).use { rpc ->
                    rpc.connect()
                    val api = CodexApi(rpc)
                    api.resume(thread)
                    current = Timeline.history(thread, api.history(thread))
                    withTimeout(60_000) {
                        while (true) when (val event = rpc.events.receive()) {
                            is RpcEvent.Request -> if (event.request.threadId == thread) {
                                assertTrue(event.request.command.orEmpty().contains("GHUB_ROW_"))
                                rpc.approve(event.request, "accept")
                            }
                            is RpcEvent.Notification -> {
                                current = Timeline.notification(current, event.method, event.params)
                                if (event.method == "item/commandExecution/outputDelta") deltaCount++
                                if (event.method == "turn/completed" && event.params.string("threadId") == thread) break
                            }
                            is RpcEvent.Disconnected -> throw AssertionError("Unexpected disconnect", event.reason)
                            is RpcEvent.Barrier -> event.applied.complete(Unit)
                        }
                    }
                }
            }
            assertTrue("Expected live output deltas", deltaCount > 0)
            val live = current.items.single { it.kind == "commandExecution" }
            assertEquals(expected, live.text)
            assertEquals(7, live.commandExecution?.exitCode)
            assertTrue(live.failed)
            assertTrue(live.commandExecution?.command.orEmpty().contains("GHUB_ROW_"))
            SshConnection.connect(profile, prompts).use { ssh ->
                RpcClient(ProxyTransport(ssh)).use { rpc ->
                    rpc.connect()
                    val api = CodexApi(rpc)
                    api.resume(thread)
                    val history = Timeline.history(thread, api.history(thread))
                    val restored = history.items.single { it.kind == "commandExecution" }
                    assertEquals(live.commandExecution, restored.commandExecution)
                    assertEquals(expected, restored.text)
                    assertTrue(restored.failed)
                    val cached = decodeConversationCache(encodeConversationCache(history))!!
                    assertEquals(restored, cached.items.single { it.kind == "commandExecution" })
                }
            }
        }
        Unit
    }
}
