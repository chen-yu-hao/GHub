package dev.remotegpt.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import dev.remotegpt.app.ProjectProfile
import dev.remotegpt.app.ResultReceipt
import dev.remotegpt.client.wireJson
import kotlinx.serialization.encodeToString

private val Context.settingsStore by preferencesDataStore("settings")
class Settings(private val context: Context) {
    suspend fun results(host: String): Map<String, ResultReceipt> = context.settingsStore.data.first()[stringPreferencesKey("results:${host}")]
        ?.let { runCatching { wireJson.decodeFromString<Map<String, ResultReceipt>>(it) }.getOrNull() }.orEmpty()
    suspend fun results(host: String, value: Map<String, ResultReceipt>) {
        context.settingsStore.edit { it[stringPreferencesKey("results:${host}")] = wireJson.encodeToString(value) }
    }
    val theme = context.settingsStore.data.map { it[stringPreferencesKey("theme")] ?: "system" }
    val activeHost = context.settingsStore.data.map { it[stringPreferencesKey("active_host")] }
    suspend fun theme(value: String) { context.settingsStore.edit { it[stringPreferencesKey("theme")] = value } }
    suspend fun activeHost(value: String) { context.settingsStore.edit { it[stringPreferencesKey("active_host")] = value } }
    suspend fun lastThread(host: String): String? = context.settingsStore.data.first()[stringPreferencesKey("thread:${host}")]
    suspend fun lastThread(host: String, thread: String?) { context.settingsStore.edit { if (thread == null) it.remove(stringPreferencesKey("thread:${host}")) else it[stringPreferencesKey("thread:${host}")] = thread } }
    suspend fun clearLastThreadIf(host: String, thread: String) { context.settingsStore.edit {
        val key = stringPreferencesKey("thread:${host}")
        if (it[key] == thread) it.remove(key)
    } }
    suspend fun projects(host: String): List<ProjectProfile> = context.settingsStore.data.first()[stringPreferencesKey("projects:${host}")]
        ?.let { runCatching { wireJson.decodeFromString<List<ProjectProfile>>(it) }.getOrNull() }.orEmpty()
    suspend fun projects(host: String, projects: List<ProjectProfile>) { context.settingsStore.edit { it[stringPreferencesKey("projects:${host}")] = wireJson.encodeToString(projects) } }
    suspend fun hiddenProjects(host: String): Set<String> =
        context.settingsStore.data.first()[stringSetPreferencesKey("hidden_projects:${host}")] ?: emptySet()
    suspend fun projectVisibility(host: String, path: String, hidden: Boolean) { context.settingsStore.edit {
        val key = stringSetPreferencesKey("hidden_projects:${host}")
        val paths = it[key] ?: emptySet()
        it[key] = if (hidden) paths + path else paths - path
    } }
    suspend fun lastProject(host: String): String? = context.settingsStore.data.first()[stringPreferencesKey("project:${host}")]
    suspend fun lastProject(host: String, path: String?) { context.settingsStore.edit {
        val key = stringPreferencesKey("project:${host}")
        if (path == null) it.remove(key) else it[key] = path
    } }
    suspend fun location(host: String, project: String?, thread: String?) { context.settingsStore.edit {
        val projectKey = stringPreferencesKey("project:${host}")
        val threadKey = stringPreferencesKey("thread:${host}")
        if (project == null) it.remove(projectKey) else it[projectKey] = project
        if (thread == null) it.remove(threadKey) else it[threadKey] = thread
    } }
    suspend fun reconnectHosts(): Set<String> = context.settingsStore.data.first()[stringSetPreferencesKey("reconnect_hosts")] ?: emptySet()
    suspend fun reconnect(host: String, enabled: Boolean) { context.settingsStore.edit {
        val key = stringSetPreferencesKey("reconnect_hosts")
        val current = it[key] ?: emptySet()
        it[key] = if (enabled) current + host else current - host
    } }
    private fun hostKey(host: String, port: Int) = stringPreferencesKey("host_key:${host}:${port}")
    suspend fun fingerprint(host: String, port: Int): String? = context.settingsStore.data.first()[hostKey(host, port)]
    suspend fun trust(host: String, port: Int, fingerprint: String) { context.settingsStore.edit { it[hostKey(host, port)] = fingerprint } }
}
