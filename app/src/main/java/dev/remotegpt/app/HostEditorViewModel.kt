package dev.remotegpt.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dev.remotegpt.client.Credential
import dev.remotegpt.client.HostProfile
import dev.remotegpt.client.wireJson
import kotlinx.serialization.encodeToString

/** Only connection metadata enters saved state. Unsaved credentials stay in memory. */
class HostEditorViewModel(private val saved: SavedStateHandle) : ViewModel() {
    var profile by mutableStateOf(saved.get<String>(PROFILE)?.let {
        runCatching { wireJson.decodeFromString<HostProfile>(it) }.getOrNull()
    })
        private set
    var credentials by mutableStateOf<Map<String, Credential>>(emptyMap())
        private set
    var loading by mutableStateOf(true)
        private set
    private var loaded = false
    var importTarget: String
        get() = saved[IMPORT_TARGET] ?: ""
        set(value) { saved[IMPORT_TARGET] = value }

    fun open(value: HostProfile) {
        credentials = emptyMap()
        loaded = false
        loading = true
        importTarget = ""
        updateProfile(value)
    }

    fun updateProfile(value: HostProfile) {
        profile = value
        saved[PROFILE] = wireJson.encodeToString(value)
    }

    suspend fun loadCredentials(read: suspend (String) -> Credential) {
        if (loaded) return
        val current = profile ?: return
        try {
            val restored = (current.jumps + current.endpoint).associate { it.credentialId to read(it.credentialId) }
            if (profile?.id == current.id) { credentials = restored + credentials; loaded = true }
        } finally { if (profile?.id == current.id) loading = false }
    }

    fun updateCredential(id: String, value: Credential) { credentials = credentials + (id to value) }

    fun close() {
        profile = null
        credentials = emptyMap()
        loaded = false
        loading = true
        saved.remove<String>(PROFILE)
        saved.remove<String>(IMPORT_TARGET)
    }

    companion object {
        private const val PROFILE = "host_editor_profile"
        private const val IMPORT_TARGET = "host_editor_import_target"
    }
}
