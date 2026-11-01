package dev.remotegpt.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.termux.terminal.TerminalBackend
import com.termux.terminal.TerminalSession
import dev.remotegpt.app.ui.TerminalPane
import dev.remotegpt.terminal.SessionCallbacks

/** Offline preview; all input stays in the disposable local emulator buffer. */
@Composable internal fun TerminalGallery(multiple: Boolean) {
    fun terminal(index: Int): TerminalTab {
        val session = TerminalSession(object : TerminalBackend {
            override fun write(bytes: ByteArray) = Unit
            override fun resize(columns: Int, rows: Int) = Unit
            override fun close() = Unit
        }, SessionCallbacks())
        session.append(("\u001b[32miaw@dev\u001b[0m:~/project/remote_gpt$ git status --short\r\n" +
            " M app/src/main/ui/TerminalsScreen.kt\r\n" +
            "\u001b[32miaw@dev\u001b[0m:~/project/remote_gpt$ ").toByteArray())
        return TerminalTab(index.toString(), tr(R.string.ui_terminal, index), session, "/home/iaw/project/remote_gpt")
    }
    var tabs by remember { mutableStateOf((1..if (multiple) 3 else 1).map(::terminal)) }
    var selected by remember { mutableStateOf(tabs.first().id) }
    var next by remember { mutableIntStateOf(tabs.size + 1) }
    var preferences by remember { mutableStateOf(TerminalPreferences()) }
    Surface(Modifier.fillMaxSize()) { Box(Modifier.safeDrawingPadding()) {
        TerminalPane(tabs, selected, "remote_gpt", "/home/iaw/project/remote_gpt", ConnectionStatus.ONLINE, false, preferences,
            onBack = {}, onWorkspace = {}, onSelect = { selected = it },
            onNew = { val tab = terminal(next++); tabs = tabs + tab; selected = tab.id },
            onClose = { id -> tabs.firstOrNull { it.id == id }?.session?.finishIfRunning(); tabs = tabs.filterNot { it.id == id } }, onConnect = {},
            onFontSize = { preferences = preferences.copy(fontSize = it) }, onExtraKeys = { preferences = preferences.copy(extraKeys = it) })
    } }
}
