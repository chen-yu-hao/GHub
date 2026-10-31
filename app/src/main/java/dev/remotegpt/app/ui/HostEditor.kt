@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.remotegpt.app.ui

import dev.remotegpt.app.tr
import dev.remotegpt.app.R

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.remotegpt.app.*
import dev.remotegpt.client.*
import kotlinx.coroutines.*

@Composable fun HostEditor(initial: HostProfile, vm: WorkbenchViewModel, snackbar: SnackbarHostState? = null, onClose: () -> Unit) {
    var profile by remember { mutableStateOf(initial) }
    var credentials by remember { mutableStateOf<Map<String, Credential>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var importTarget by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(initial.jumps.isNotEmpty() || initial.codexPath != "codex" || initial.socketPath.isNotBlank()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(initial.id) {
        try { credentials = (initial.jumps + initial.endpoint).associate { it.credentialId to vm.credential(it.credentialId) } }
        catch (_: Exception) { error = tr(R.string.ui_saved_credentials_cannot_be_decrypted_import_them) }
        finally { loading = false }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { stream ->
                    readLimited(stream, 65536).decodeToString()
                } ?: error(tr(R.string.ui_cannot_read_private_key)) }
                require(text.contains("PRIVATE KEY")) { tr(R.string.ui_select_a_private_key_not_a_pub) }
                credentials = credentials + (importTarget to (credentials[importTarget] ?: Credential()).copy(privateKey = text))
                error = null
            } catch (e: Exception) { error = e.message }
        }
    }
    fun changeSecret(id: String, secret: Credential) { credentials = credentials + (id to secret) }
    val valid = !loading && profile.name.isNotBlank() && profile.codexPath.isNotBlank() && (profile.jumps + profile.endpoint).all {
        it.host.isNotBlank() && it.username.isNotBlank() && it.port in 1..65535 &&
            (it.auth != AuthKind.PRIVATE_KEY || !credentials[it.credentialId]?.privateKey.isNullOrBlank())
    }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize()) {
        Scaffold(modifier = Modifier.imePadding(), snackbarHost = { snackbar?.let { SnackbarHost(it) } }, topBar = { TopAppBar(title = { Text(tr(R.string.ui_server_connection), style = MaterialTheme.typography.titleLarge) }, navigationIcon = { IconButton(onClick = onClose) { Icon(WorkbenchIcons.ArrowBack, tr(R.string.ui_back_to_servers)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
            bottomBar = {
                Surface {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Button(onClick = { saving = true; vm.saveProfile(profile, credentials) { success -> saving = false; if (success) onClose() else error = tr(R.string.ui_could_not_save_check_local_storage_and) } },
                            enabled = valid && !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (saving) tr(R.string.ui_saving) else tr(R.string.ui_save_connection)) }
                    }
                }
            }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item { OutlinedTextField(profile.name, { profile = profile.copy(name = it) }, label = { Text(tr(R.string.ui_connection_name)) }, placeholder = { Text(tr(R.string.ui_for_example_development_server)) }, modifier = Modifier.fillMaxWidth().then(imeAwareFieldModifier()), singleLine = true) }
                item {
                    WorkbenchSurface() {
                    Column(Modifier.padding(16.dp)) {
                    EndpointFields(tr(R.string.ui_target_host), profile.endpoint, credentials[profile.endpoint.credentialId] ?: Credential(),
                        onEndpoint = { profile = profile.copy(endpoint = it) }, onCredential = { changeSecret(profile.endpoint.credentialId, it) },
                        onImport = { importTarget = profile.endpoint.credentialId; picker.launch(arrayOf("*/*")) })
                    }
                    }
                }
                item {
                    SheetAction(tr(R.string.ui_advanced_settings), WorkbenchIcons.Settings, supporting = if (advanced) tr(R.string.ui_hide_jump_hosts_and_codex_settings) else tr(R.string.ui_jump_hosts_codex_path_and_working_directory)) { advanced = !advanced }
                }
                if (advanced) {
                item { HorizontalDivider(); Text(tr(R.string.ui_jump_hosts), style = MaterialTheme.typography.titleMedium); Text(tr(R.string.ui_add_hosts_in_connection_order_each_host), style = MaterialTheme.typography.bodySmall) }
                itemsIndexed(profile.jumps, key = { _, endpoint -> endpoint.credentialId }) { index, endpoint ->
                    OutlinedCard {
                        Column(Modifier.padding(14.dp)) {
                            EndpointFields(tr(R.string.ui_hop , index + 1), endpoint, credentials[endpoint.credentialId] ?: Credential(),
                                onEndpoint = { updated -> profile = profile.copy(jumps = profile.jumps.toMutableList().also { it[index] = updated }) },
                                onCredential = { changeSecret(endpoint.credentialId, it) }, onImport = { importTarget = endpoint.credentialId; picker.launch(arrayOf("*/*")) })
                            TextButton(onClick = { profile = profile.copy(jumps = profile.jumps.filterNot { it.credentialId == endpoint.credentialId }) }) { Text(tr(R.string.ui_remove_jump_host)) }
                        }
                    }
                }
                item { OutlinedButton(onClick = { profile = profile.copy(jumps = profile.jumps + SshEndpoint()) }) { Icon(WorkbenchIcons.Add, null); Text(tr(R.string.ui_add_jump_host)) } }
                item {
                    HorizontalDivider(); Spacer(Modifier.height(16.dp)); Text(tr(R.string.ui_remote_codex), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(profile.codexPath, { profile = profile.copy(codexPath = it) }, label = { Text(tr(R.string.ui_executable_path)) }, supportingText = { Text(tr(R.string.ui_for_example_codex_or_home_user_local)) }, modifier = Modifier.fillMaxWidth().then(imeAwareFieldModifier()), singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(profile.defaultCwd, { profile = profile.copy(defaultCwd = it) }, label = { Text(tr(R.string.ui_default_working_directory)) }, placeholder = { Text("/home/user/project") }, modifier = Modifier.fillMaxWidth().then(imeAwareFieldModifier()), singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(profile.socketPath, { profile = profile.copy(socketPath = it) }, label = { Text(tr(R.string.ui_service_socket_path_optional)) }, supportingText = { Text(tr(R.string.ui_leave_empty_to_use_the_default_shared)) }, modifier = Modifier.fillMaxWidth().then(imeAwareFieldModifier()), singleLine = true)
                }
                }
                error?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.error) } }
                item { Text(tr(R.string.ui_credentials_are_encrypted_on_this_device_host), style = MaterialTheme.typography.bodySmall) }
                if (vm.state.value.profiles.any { it.id == initial.id }) item { TextButton(onClick = { deleting = true }) { Text(tr(R.string.ui_delete_server_connection), color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(tr(R.string.ui_delete_server_connection_160)) }, text = { Text(tr(R.string.ui_this_removes_the_connection_and_credentials_saved)) },
        confirmButton = { TextButton(onClick = { vm.deleteProfile(initial); onClose() }) { Text(tr(R.string.ui_delete)) } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text(tr(R.string.ui_cancel)) } })
}

@Composable private fun EndpointFields(title: String, endpoint: SshEndpoint, secret: Credential, onEndpoint: (SshEndpoint) -> Unit, onCredential: (Credential) -> Unit, onImport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(endpoint.host, { onEndpoint(endpoint.copy(host = it.trim())) }, label = { Text(tr(R.string.ui_host_address)) }, singleLine = true, modifier = Modifier.fillMaxWidth().then(imeAwareFieldModifier()), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(endpoint.username, { onEndpoint(endpoint.copy(username = it.trim())) }, label = { Text(tr(R.string.ui_username)) }, singleLine = true, modifier = Modifier.weight(2f).then(imeAwareFieldModifier()))
            OutlinedTextField(if (endpoint.port == 0) "" else endpoint.port.toString(), { onEndpoint(endpoint.copy(port = it.toIntOrNull() ?: 0)) }, label = { Text(tr(R.string.ui_port)) }, singleLine = true, modifier = Modifier.weight(1f).then(imeAwareFieldModifier()), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(AuthKind.PRIVATE_KEY to tr(R.string.ui_private_key), AuthKind.PASSWORD to tr(R.string.ui_password), AuthKind.INTERACTIVE to tr(R.string.ui_interactive_authentication)).forEach { (kind, label) ->
                FilterChip(selected = endpoint.auth == kind, onClick = { onEndpoint(endpoint.copy(auth = kind)) }, label = { Text(label) })
            }
        }
        when (endpoint.auth) {
            AuthKind.PRIVATE_KEY -> {
                OutlinedButton(onClick = onImport) { Icon(WorkbenchIcons.Key, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (secret.privateKey.isBlank()) tr(R.string.ui_import_private_key) else tr(R.string.ui_key_imported_replace)) }
                OutlinedTextField(secret.passphrase, { onCredential(secret.copy(passphrase = it)) }, label = { Text(tr(R.string.ui_key_passphrase_optional)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().then(imeAwareFieldModifier()))
            }
            AuthKind.PASSWORD -> OutlinedTextField(secret.password, { onCredential(secret.copy(password = it)) }, label = { Text(tr(R.string.ui_ssh_password)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().then(imeAwareFieldModifier()))
            AuthKind.INTERACTIVE -> Text(tr(R.string.ui_enter_the_password_or_verification_code_requested), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun imeAwareFieldModifier(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) {
        if (focused) { withFrameNanos { }; requester.bringIntoView() }
    }
    return Modifier.bringIntoViewRequester(requester).onFocusChanged { focused = it.isFocused }
}

@Composable fun ConnectionPromptDialog(prompt: ConnectionPrompt?) {
    when (prompt) {
        is ConnectionPrompt.HostKey -> {
            val c = prompt.challenge
            AlertDialog(onDismissRequest = { prompt.answer.complete(false) }, title = { Text(if (c.previous == null) tr(R.string.ui_verify_host_identity) else tr(R.string.ui_host_fingerprint_changed)) },
                text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${c.host}:${c.port}\n${c.algorithm}")
                    androidx.compose.foundation.text.selection.SelectionContainer { Text(c.fingerprint) }
                    if (c.previous != null) Text(tr(R.string.ui_previous_nverify_this_change_through_a_trusted , c.previous))
                    else Text(tr(R.string.ui_compare_this_fingerprint_with_one_provided_by))
                } }, confirmButton = { TextButton(onClick = { prompt.answer.complete(true) }) { Text(if (c.previous == null) tr(R.string.ui_trust_and_connect) else tr(R.string.ui_verified_update_trust)) } },
                dismissButton = { TextButton(onClick = { prompt.answer.complete(false) }) { Text(tr(R.string.ui_cancel)) } })
        }
        is ConnectionPrompt.Keyboard -> {
            var answers by remember(prompt) { mutableStateOf(List(prompt.challenge.prompts.size) { "" }) }
            AlertDialog(onDismissRequest = { prompt.answer.cancel() }, title = { Text(prompt.challenge.name.ifBlank { tr(R.string.ui_ssh_authentication) }) },
                text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (prompt.challenge.instruction.isNotBlank()) Text(prompt.challenge.instruction)
                    prompt.challenge.prompts.forEachIndexed { index, (label, echo) ->
                        OutlinedTextField(answers[index], { text -> answers = answers.toMutableList().also { it[index] = text } }, label = { Text(label) }, modifier = Modifier.fillMaxWidth().then(imeAwareFieldModifier()),
                            visualTransformation = if (echo) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation())
                    }
                } }, confirmButton = { TextButton(onClick = { prompt.answer.complete(answers) }) { Text(tr(R.string.ui_continue)) } }, dismissButton = { TextButton(onClick = { prompt.answer.cancel() }) { Text(tr(R.string.ui_cancel)) } })
        }
        null -> Unit
    }
}
