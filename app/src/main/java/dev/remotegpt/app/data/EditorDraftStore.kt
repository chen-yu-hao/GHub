package dev.remotegpt.app.data

import android.util.AtomicFile
import dev.remotegpt.client.wireJson
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable internal data class EditorDraft(val path: String, val original: String, val sha256: String, val text: String)

/** Private, backup-excluded storage; never put potentially large file contents in a Bundle. */
internal class EditorDraftStore(private val directory: File) {
    private val mutex = Mutex()
    private fun file(host: String) = AtomicFile(File(directory, host.replace(Regex("[^a-zA-Z0-9-]"), "_") + ".json"))
    suspend fun read(host: String): EditorDraft? = withContext(Dispatchers.IO) { mutex.withLock {
        val target = file(host)
        if (!target.baseFile.exists()) null else wireJson.decodeFromString<EditorDraft>(target.readFully().decodeToString())
    } }
    suspend fun write(host: String, draft: EditorDraft?) = withContext(Dispatchers.IO) { mutex.withLock {
        directory.mkdirs()
        val target = file(host)
        if (draft == null) target.delete() else {
            val output = target.startWrite()
            try { output.write(wireJson.encodeToString(draft).toByteArray()); target.finishWrite(output) }
            catch (e: Throwable) { target.failWrite(output); throw e }
        }
    } }
}
