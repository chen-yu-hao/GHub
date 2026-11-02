@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.remotegpt.app.ui

import dev.remotegpt.app.tr
import dev.remotegpt.app.R

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.remotegpt.app.*
import dev.remotegpt.client.HostProfile

@Composable fun ManagementPage(title: String, onBack: () -> Unit, bottom: @Composable () -> Unit = {}, snackbar: SnackbarHostState? = null, content: @Composable (PaddingValues) -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(WorkbenchIcons.ArrowBack, tr(R.string.ui_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
    }, bottomBar = bottom, snackbarHost = { snackbar?.let { SnackbarHost(it, Modifier.imePadding()) } }, content = content)
}

@Composable private fun SectionLabel(label: String) {
    Text(label, Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable fun PreferenceRow(title: String, icon: ImageVector, value: String? = null, description: String? = null, accent: WorkbenchAccent? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val haptic = rememberHaptic()
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = { haptic(false); onClick() }) else Modifier)
        .heightIn(min = 60.dp).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = accent?.let { accentColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        value?.let {
            Spacer(Modifier.width(12.dp))
            Text(it, Modifier.widthIn(max = 128.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.let { Spacer(Modifier.width(8.dp)); it() }
        if (onClick != null) { Spacer(Modifier.width(8.dp)); Icon(WorkbenchIcons.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable fun SettingsScreen(theme: String, hostCount: Int, backgroundKeepAlive: Boolean, onBack: () -> Unit, onTheme: (String) -> Unit,
    onHosts: () -> Unit, onKeepAlive: (Boolean) -> Unit, onClearCache: () -> Unit, onExport: () -> Unit, onLegal: () -> Unit, snackbar: SnackbarHostState? = null) {
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    when (section) {
        "appearance" -> AppearanceSettings(theme, onTheme, snackbar) { section = null }
        "language" -> LanguageSettings(snackbar) { section = null }
        "data" -> DataSettings(onClearCache, onExport, onLegal, snackbar) { section = null }
        else -> ManagementPage(tr(R.string.ui_settings), onBack, snackbar = snackbar) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { SectionLabel(tr(R.string.ui_connection)) }
                item {
                    WorkbenchSurface(Modifier.padding(horizontal = 20.dp)) {
                        PreferenceRow(tr(R.string.ui_servers), WorkbenchIcons.Dns, tr(R.string.ui_servers_189 , hostCount), description = tr(R.string.ui_ssh_connections_keys_and_jump_hosts), onClick = onHosts)
                        GroupDivider()
                        PreferenceRow(tr(R.string.ui_keep_connected_in_background), WorkbenchIcons.CloudDone,
                            description = if (backgroundKeepAlive) tr(R.string.ui_show_an_ongoing_notification_and_keep_ssh) else tr(R.string.ui_reconnect_when_needed_to_save_battery),
                            trailing = { Switch(checked = backgroundKeepAlive, onCheckedChange = onKeepAlive) })
                    }
                }
                item { SectionLabel(tr(R.string.ui_application)) }
                item {
                    WorkbenchSurface(Modifier.padding(horizontal = 20.dp)) {
                        PreferenceRow(tr(R.string.ui_appearance), WorkbenchIcons.Settings, when(theme) { "light" -> tr(R.string.ui_light); "dark" -> tr(R.string.ui_dark); else -> tr(R.string.ui_system_default) }, onClick = { section = "appearance" })
                        GroupDivider()
                        PreferenceRow(tr(R.string.ui_language), WorkbenchIcons.Language, languageLabel(), onClick = { section = "language" })
                        GroupDivider()
                        PreferenceRow(tr(R.string.ui_data_and_diagnostics), WorkbenchIcons.Archive, description = tr(R.string.ui_cache_diagnostic_logs_and_licenses), onClick = { section = "data" })
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${tr(R.string.app_name)} ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tr(R.string.ui_no_telemetry_by_default_tasks_run_on), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable private fun AppearanceSettings(theme: String, onTheme: (String) -> Unit, snackbar: SnackbarHostState?, onBack: () -> Unit) {
    ManagementPage(tr(R.string.ui_appearance), onBack, snackbar = snackbar) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionLabel(tr(R.string.ui_theme)) }
            item {
                WorkbenchSurface(Modifier.padding(horizontal = 20.dp)) {
                    listOf("system" to tr(R.string.ui_system_default), "light" to tr(R.string.ui_light), "dark" to tr(R.string.ui_dark)).forEachIndexed { index, (id, title) ->
                        if (index > 0) GroupDivider()
                        Row(Modifier.fillMaxWidth().clickable { onTheme(id) }.heightIn(min = 56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            RadioButton(selected = theme == id, onClick = { onTheme(id) })
                        }
                    }
                }
            }
        }
    }
}

private fun languageLabel(): String = when (AppCompatDelegate.getApplicationLocales().get(0)?.language) {
    "zh" -> "中文"
    "en" -> "English"
    else -> tr(R.string.ui_system_default)
}

@Composable private fun LanguageSettings(snackbar: SnackbarHostState?, onBack: () -> Unit) {
    val current = AppCompatDelegate.getApplicationLocales().get(0)?.language.orEmpty()
    ManagementPage(tr(R.string.ui_language), onBack, snackbar = snackbar) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp)) {
            item {
                WorkbenchSurface {
                    listOf("" to tr(R.string.ui_system_default), "zh" to "中文", "en" to "English").forEachIndexed { index, (tag, title) ->
                        if (index > 0) GroupDivider()
                        val select = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
                        Row(Modifier.fillMaxWidth().clickable(onClick = select).heightIn(min = 56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            RadioButton(selected = current == tag, onClick = select)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun DataSettings(onClearCache: () -> Unit, onExport: () -> Unit, onLegal: () -> Unit, snackbar: SnackbarHostState?, onBack: () -> Unit) {
    var clear by rememberSaveable { mutableStateOf(false) }
    ManagementPage(tr(R.string.ui_data_and_diagnostics), onBack, snackbar = snackbar) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionLabel(tr(R.string.ui_local_data)) }
            item {
                WorkbenchSurface(Modifier.padding(horizontal = 20.dp)) {
                    PreferenceRow(tr(R.string.ui_clear_session_cache), WorkbenchIcons.Archive, description = tr(R.string.ui_keep_drafts_server_connections_and_remote_sessions), onClick = { clear = true })
                }
            }
            item { SectionLabel(tr(R.string.ui_help_and_diagnostics)) }
            item {
                WorkbenchSurface(Modifier.padding(horizontal = 20.dp)) {
                    PreferenceRow(tr(R.string.ui_export_diagnostics), WorkbenchIcons.Download, description = tr(R.string.ui_excludes_keys_passwords_and_message_content), onClick = onExport)
                    GroupDivider()
                    PreferenceRow(tr(R.string.ui_open_source_licenses), WorkbenchIcons.FileCode, "GPL-3.0", onClick = onLegal)
                }
            }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text(tr(R.string.ui_clear_local_session_cache)) },
        text = { Text(tr(R.string.ui_drafts_connections_and_remote_sessions_are_kept)) },
        confirmButton = { TextButton(onClick = { clear = false; onClearCache() }) { Text(tr(R.string.ui_clear_cache)) } },
        dismissButton = { TextButton(onClick = { clear = false }) { Text(tr(R.string.ui_cancel)) } })
}

@Composable fun HostsScreen(profiles: List<HostProfile>, workspaces: Map<String, HostWorkspace>, onBack: () -> Unit,
    onOpen: (HostProfile) -> Unit, onAdd: () -> Unit, snackbar: SnackbarHostState? = null) {
    ManagementPage(tr(R.string.ui_servers), onBack, snackbar = snackbar, bottom = {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), horizontalArrangement = Arrangement.End) {
            Button(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Icon(WorkbenchIcons.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr(R.string.ui_add_server)) }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 12.dp)) {
            if (profiles.isEmpty()) item { Text(tr(R.string.ui_add_an_ssh_connection_to_access_projects), Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(profiles, key = { it.id }) { profile ->
                WorkbenchSurface(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), onClick = { onOpen(profile) }) {
                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(WorkbenchIcons.Dns, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(profile.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        StatusPill(workspaces[profile.id]?.connection ?: ConnectionStatus.OFFLINE)
                    }
                }
            }
        }
    }
}

@Composable fun HostSwitcherSheet(
    profiles: List<HostProfile>, activeId: String?, workspaces: Map<String, HostWorkspace>,
    onSelect: (HostProfile) -> Unit, onDismiss: () -> Unit,
) {
    SelectionSheet(tr(R.string.ui_switch_server), profiles.map { profile ->
        SelectionOption(profile.id, profile.name, status = connectionLabel(workspaces[profile.id]?.connection ?: ConnectionStatus.OFFLINE))
    }, activeId, onSelect = { id -> profiles.firstOrNull { it.id == id }?.let(onSelect) }, onDismiss = onDismiss,
        emptyText = if (profiles.isEmpty()) tr(R.string.ui_no_server_connections_yet) else null)
}

@Composable fun HostDetailsScreen(profile: HostProfile, workspace: HostWorkspace, onBack: () -> Unit, onEdit: () -> Unit,
    onConnect: () -> Unit, onDisconnect: () -> Unit, snackbar: SnackbarHostState? = null) {
    val connected = workspace.connection == ConnectionStatus.ONLINE
    val canStop = connected || workspace.connection.busy || workspace.sshConnected
    val actionLabel = tr(if (workspace.sshConnected && !workspace.connection.busy) R.string.ui_disconnect else when (workspace.connection) {
        ConnectionStatus.ONLINE, ConnectionStatus.CHECKING, ConnectionStatus.SYNCING -> R.string.ui_disconnect
        ConnectionStatus.CONNECTING -> R.string.ui_cancel_connection
        ConnectionStatus.RECONNECTING, ConnectionStatus.WAITING_NETWORK -> R.string.ui_stop_reconnecting
        else -> if (workspace.needsBootstrap) R.string.ui_set_up_and_connect
            else if (workspace.connection == ConnectionStatus.ERROR) R.string.ui_retry_now else R.string.ui_connect
    })
    ManagementPage(profile.name, onBack, snackbar = snackbar) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionLabel(tr(R.string.ui_connection)) }
            item {
                WorkbenchSurface(Modifier.padding(horizontal = 20.dp)) {
                    PreferenceRow(tr(R.string.ui_ssh_connection), WorkbenchIcons.Lan,
                        tr(if (workspace.sshConnected) R.string.ui_ssh_connected else R.string.ui_ssh_disconnected),
                        accent = if (workspace.sshConnected) WorkbenchAccent.TEAL else null)
                    PreferenceRow(tr(R.string.ui_task_service), WorkbenchIcons.CloudDone, connectionLabel(workspace.connection),
                        accent = if (connected) WorkbenchAccent.TEAL else null)
                    if (workspace.sshConnected && !connected) Text(tr(R.string.ui_ssh_without_task_service),
                        Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    workspace.error?.let { error ->
                        Text(error, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                        if (canStop) OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(actionLabel) }
                        else Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(actionLabel) }
                    }
                    if (workspace.sshConnected && !connected && !workspace.connection.busy) TextButton(
                        onClick = onConnect, modifier = Modifier.padding(horizontal = 20.dp).heightIn(min = 48.dp),
                    ) { Text(tr(if (workspace.needsBootstrap) R.string.ui_set_up_and_connect else R.string.ui_retry_task_service)) }
                }
            }
            item { SectionLabel(tr(R.string.ui_server_configuration)) }
            item {
                WorkbenchSurface(Modifier.padding(horizontal = 20.dp)) {
                    PreferenceRow(tr(R.string.ui_address), WorkbenchIcons.Dns, description = "${profile.endpoint.username}@${profile.endpoint.host}:${profile.endpoint.port}")
                    GroupDivider()
                    PreferenceRow(tr(R.string.ui_jump_hosts), WorkbenchIcons.FolderCopy, if (profile.jumps.isEmpty()) tr(R.string.ui_direct_connection) else tr(R.string.ui_hops , profile.jumps.size))
                    GroupDivider()
                    PreferenceRow(tr(R.string.ui_edit_connection), WorkbenchIcons.Settings, onClick = onEdit)
                }
            }
            if (connected) {
                item { SectionLabel(tr(R.string.ui_remote_environment)) }
                item {
                    WorkbenchSurface(Modifier.padding(horizontal = 20.dp)) {
                        PreferenceRow(tr(R.string.ui_codex_account), WorkbenchIcons.Key, description = workspace.account.ifBlank { tr(R.string.ui_available_after_connecting) })
                        GroupDivider()
                        PreferenceRow(tr(R.string.ui_app_server), WorkbenchIcons.Terminal, workspace.serverVersion.ifBlank { tr(R.string.ui_not_loaded) })
                    }
                }
            }
        }
    }
}
