package dev.remotegpt.app

import android.content.pm.ApplicationInfo
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import dev.remotegpt.client.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.Assert.*
import java.util.Base64

/** Run against the actual non-debuggable, R8-optimized release build. */
class ReleaseRuntimeDeviceTest {
    @Test fun optimizedReleaseStartsAndUsesEncryptedKeysSftpAndPty() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue("Requires isolated OpenSSH fixture", args.containsKey("releaseSshPort"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        assertFalse(activity.isFinishing)
        instrumentation.runOnMainSync { activity.finish() }
        val endpoint = SshEndpoint(args.getString("releaseSshHost", "10.0.2.2"), args.getString("releaseSshPort")!!.toInt(), args.getString("releaseSshUser")!!)
        val profile = HostProfile(name = "R8 fixture", endpoint = endpoint)
        assertEquals(profile, wireJson.decodeFromString<HostProfile>(wireJson.encodeToString(profile)))
        for (name in listOf("releaseEd25519", "releaseRsa")) {
            val prompts = object : SshPrompts {
                override suspend fun credential(id: String) = Credential(
                    privateKey = Base64.getDecoder().decode(args.getString(name)).decodeToString(), passphrase = "fixture-passphrase")
                override suspend fun verifyHostKey(host: String, port: Int, algorithm: String, fingerprint: String) = fingerprint == args.getString("releaseFingerprint")
                override suspend fun keyboard(prompt: KeyboardPrompt): List<String> = error("Unexpected challenge")
            }
            withContext(Dispatchers.IO) {
                SshConnection.connect(profile, prompts).use { ssh ->
                    assertEquals("RELEASE_OK", ssh.command("printf RELEASE_OK"))
                    val files = RemoteFiles(ssh)
                    val path = files.upload(args.getString("releaseRoot")!!, "release.txt", "R8 SFTP 中文".byteInputStream())
                    val original = files.readText(path)
                    assertEquals("R8 SFTP 中文", original.text)
                    files.saveText(original, "saved")
                    assertEquals("saved", files.readText(path).text)
                    ssh.terminal().use { terminal ->
                        terminal.resize(100, 30)
                        terminal.write("printf 'RELEASE_%s_OK\\n' PTY\rexit\r".toByteArray())
                        val output = terminal.input.bufferedReader().readText()
                        assertTrue(output, output.contains("RELEASE_PTY_OK"))
                    }
                }
            }
        }
        Unit
    }
}
