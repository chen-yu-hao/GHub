package dev.remotegpt.app.data

import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Separate from FileProvider shares/camera captures. All disk operations run on IO. */
internal class RemoteImageCache(
    private val directory: File,
    private val maxBytes: Long = 100L * 1024 * 1024,
    private val maxFiles: Int = 64,
) {
    private val mutex = Mutex()
    data class Entry(val file: File, val retained: Set<String>)
    suspend fun get(key: String, protected: () -> String?, download: suspend (OutputStream) -> Unit): Entry = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!directory.isDirectory && !directory.mkdirs()) throw java.io.IOException("Cannot create image cache")
            val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
            val file = File(directory, hash)
            if (!file.exists()) {
                val temporary = File(directory, "$hash.part")
                try {
                    temporary.outputStream().use { download(it) }
                    ensureActive()
                    if (!temporary.renameTo(file)) throw java.io.IOException("Cannot store image preview")
                } finally { temporary.delete() }
            }
            file.setLastModified(System.currentTimeMillis())
            Entry(file, prune(setOfNotNull(protected(), file.absolutePath)))
        }
    }
    suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock {
        directory.listFiles()?.forEach { if (!it.delete() && it.exists()) throw java.io.IOException("Cannot clear image cache") }
        Unit
    } }
    private fun prune(protected: Set<String>): Set<String> {
        val files = directory.listFiles().orEmpty().sortedBy { it.lastModified() }.toMutableList()
        var bytes = files.sumOf { it.length() }
        val iterator = files.iterator()
        while (iterator.hasNext()) {
            val file = iterator.next()
            if (file.absolutePath in protected) continue
            if (file.name.endsWith(".part") || files.size > maxFiles || bytes > maxBytes) {
                val length = file.length()
                if (file.delete()) { iterator.remove(); bytes -= length }
            }
        }
        return files.map { it.absolutePath }.toSet()
    }
}
