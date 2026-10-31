@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.remotegpt.app.ui

import dev.remotegpt.app.tr
import dev.remotegpt.app.R

import android.graphics.Typeface
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.view.TerminalView
import dev.remotegpt.app.*
import dev.remotegpt.terminal.ViewCallbacks

@Composable fun TerminalsScreen(state: WorkbenchState, vm: WorkbenchViewModel, onWorkspace: () -> Unit, onBottomControls: (Int) -> Unit = {}) {
    val ws = state.workspace
    val terminals = ws.terminals.filter { it.projectPath == ws.selectedProject }
    val selected = terminals.firstOrNull { it.id == ws.selectedTerminal } ?: terminals.firstOrNull()
    var closing by remember { mutableStateOf<String?>(null) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }
    var fontSize by rememberSaveable { mutableFloatStateOf(14f) }
    val ctrlState by rememberUpdatedState(ctrl)
    val altState by rememberUpdatedState(alt)
    val shiftState by rememberUpdatedState(shift)
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = rememberHaptic()
    var view by remember { mutableStateOf<TerminalView?>(null) }
    Column(Modifier.fillMaxSize()) {
        if (selected == null) {
            Box(Modifier.weight(1f)) { EmptyPanel(WorkbenchIcons.Terminal, tr(R.string.ui_open_remote_terminal), tr(R.string.ui_supports_vim_tmux_and_common_terminal_programs)) }
            Box(Modifier.onSizeChanged { onBottomControls(it.height) }.imePadding()) {
                ReachableActions(onWorkspace, tr(R.string.ui_new_terminal), { vm.newTerminal() }, enabled = ws.connection == ConnectionStatus.ONLINE)
            }
        }
        else {
            key(selected.id) {
                AndroidView(factory = { ctx ->
                    TerminalView(ctx, null).apply {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        setTextSize((fontSize * density.density * density.fontScale).toInt())
                        setTypeface(Typeface.MONOSPACE)
                        setTerminalViewClient(object : ViewCallbacks() {
                            override fun readControlKey() = ctrlState
                            override fun readAltKey() = altState
                            override fun readShiftKey() = shiftState
                            override fun shouldEnforceCharBasedInput() = true
                            override fun onScale(scale: Float): Float { fontSize = (fontSize * scale).coerceIn(10f, 28f); return 1f }
                            override fun onSingleTapUp(e: MotionEvent?) {
                                requestFocus()
                                postDelayed({ (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(this@apply, InputMethodManager.SHOW_IMPLICIT) }, 100)
                            }
                        })
                        attachSession(selected.session)
                        contentDescription = tr(R.string.ui_ssh_terminal)
                        view = this
                    }
                }, modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black), update = { terminalView ->
                    terminalView.setTextSize((fontSize * density.density * density.fontScale).toInt())
                    terminalView.onScreenUpdated()
                })
                DisposableEffect(selected.session, view) {
                    selected.session.onChanged = { view?.onScreenUpdated() }
                    onDispose { selected.session.onChanged = null }
                }
            }
        Column(Modifier.onSizeChanged { onBottomControls(it.height) }.imePadding()) {
        if (terminals.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(terminals, key = { it.id }) { tab ->
                InputChip(selected = tab.id == selected?.id, onClick = { haptic(false); vm.selectTerminal(tab.id) }, label = { Text(tab.name) }, trailingIcon = {
                    IconButton(onClick = { haptic(true); closing = tab.id }, modifier = Modifier.size(48.dp)) { Icon(WorkbenchIcons.Close, tr(R.string.ui_close_330 , tab.name), Modifier.size(16.dp)) }
                })
            }
        }
            LazyRow(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item { FilterChip(selected = ctrl, onClick = { haptic(false); ctrl = !ctrl }, label = { Text("Ctrl") }, modifier = Modifier.heightIn(min = 48.dp)) }
                item { FilterChip(selected = alt, onClick = { haptic(false); alt = !alt }, label = { Text("Alt") }, modifier = Modifier.heightIn(min = 48.dp)) }
                item { FilterChip(selected = shift, onClick = { haptic(false); shift = !shift }, label = { Text("Shift") }, modifier = Modifier.heightIn(min = 48.dp)) }
                items(listOf("Esc" to "\u001b", "Tab" to "\t", "↑" to "\u001b[A", "↓" to "\u001b[B", "←" to "\u001b[D", "→" to "\u001b[C")) { (label, value) ->
                    FilledTonalButton(onClick = { haptic(false); selected.session.write(if (alt) "\u001b${value}" else value); ctrl = false; alt = false; shift = false }, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
                }
                item { OutlinedButton(onClick = { haptic(false); selected.session.onPasteTextFromClipboard() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr(R.string.ui_paste)) } }
            }
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { haptic(false); onWorkspace() }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(WorkbenchIcons.Menu, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(tr(R.string.ui_workspace)) }
                IconButton(onClick = {
                    haptic(false)
                    view?.let { terminal ->
                        terminal.requestFocus()
                        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(terminal, InputMethodManager.SHOW_IMPLICIT)
                    }
                }, modifier = Modifier.size(48.dp)) { Icon(WorkbenchIcons.Keyboard, tr(R.string.ui_show_keyboard), Modifier.size(20.dp)) }
                IconButton(onClick = { haptic(false); vm.newTerminal() }, enabled = ws.connection == ConnectionStatus.ONLINE, modifier = Modifier.size(48.dp)) { Icon(WorkbenchIcons.Add, tr(R.string.ui_new_terminal_session)) }
                IconButton(onClick = { haptic(false); fontSize = (fontSize - 1).coerceAtLeast(10f) }, modifier = Modifier.size(48.dp)) { Icon(WorkbenchIcons.TextDecrease, tr(R.string.ui_decrease_terminal_text_size)) }
                IconButton(onClick = { haptic(false); fontSize = (fontSize + 1).coerceAtMost(28f) }, modifier = Modifier.size(48.dp)) { Icon(WorkbenchIcons.TextIncrease, tr(R.string.ui_increase_terminal_text_size)) }
            }
            if (!selected.session.isRunning()) Text(tr(R.string.ui_terminal_disconnected_reconnect_and_use_tmux_to), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
        }
    }
    closing?.let { id -> AlertDialog(onDismissRequest = { closing = null }, title = { Text(tr(R.string.ui_close_terminal)) }, text = { Text(tr(R.string.ui_programs_in_a_regular_shell_may_stop)) },
        confirmButton = { TextButton(onClick = { vm.closeTerminal(id); closing = null }) { Text(tr(R.string.ui_close_terminal_339)) } }, dismissButton = { TextButton(onClick = { closing = null }) { Text(tr(R.string.ui_cancel)) } }) }
}
