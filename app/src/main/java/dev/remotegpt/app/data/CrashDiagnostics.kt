package dev.remotegpt.app.data

import android.content.Context
import android.os.Build
import dev.remotegpt.app.BuildConfig
import dev.remotegpt.app.R
import java.io.File

enum class CrashScreen { STARTUP, HOST_EDITOR, SETTINGS, HOSTS, HOST_DETAILS, PROJECTS, SESSIONS, CHAT, FILES, TERMINAL, WORKSPACE, PROJECT_PICKER, PROJECT_EDITOR }

/** Local-only crash evidence. Never serialize exception messages, causes' values or user data. */
class CrashDiagnostics(context: Context) {
    private val appName = context.getString(R.string.app_name)
    private val file = File(context.filesDir, "last-crash.txt")
    @Volatile private var screen = CrashScreen.STARTUP
    fun screen(value: CrashScreen) { screen = value }

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val report = buildString {
                    appendLine("$appName ${BuildConfig.VERSION_NAME}; Android API ${Build.VERSION.SDK_INT}")
                    appendLine("time=${System.currentTimeMillis()}")
                    appendLine("screen=${screen.name}")
                    var cause: Throwable? = error
                    val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
                    repeat(6) {
                        val current = cause ?: return@repeat
                        if (!seen.add(current)) return@repeat
                        appendLine(current.javaClass.name)
                        // Read only to classify this known framework error. Never retain the key
                        // or the message itself: it can contain a remote path or other user data.
                        if (current is IllegalArgumentException &&
                            current.stackTrace.any { it.className == "androidx.compose.ui.layout.LayoutNodeSubcompositionsState" && it.methodName == "subcompose" } &&
                            current.message?.let { it.startsWith("Key \"") && it.endsWith("was already used. If you are using LazyColumn/Row please make sure you provide a unique key for each item.") } == true) {
                            appendLine("category=compose_duplicate_list_key")
                        }
                        current.stackTrace.take(32).forEach { frame ->
                            appendLine("  ${frame.className}.${frame.methodName}:${frame.lineNumber}")
                        }
                        cause = current.cause
                    }
                }
                file.writeText(report.take(24_000))
            }
            // Preserve Android's normal crash handling; never pretend the process recovered.
            if (previous != null) previous.uncaughtException(thread, error)
            else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(10) }
        }
    }
    fun read(): String = if (file.exists()) file.inputStream().use { val bytes = ByteArray(24_000); var length = 0; while (length < bytes.size) { val n = it.read(bytes, length, bytes.size - length); if (n < 0) break; length += n }; String(bytes, 0, length, Charsets.UTF_8) } else "No crash recorded."
}
