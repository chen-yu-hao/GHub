package dev.remotegpt.client

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Bounded reads also work on Android 10, where InputStream.readNBytes is unavailable. */
fun readLimited(input: InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val bytes = ByteArray(8192)
    while (true) {
        val count = input.read(bytes, 0, minOf(bytes.size, limit - output.size() + 1))
        if (count < 0) return output.toByteArray()
        require(output.size() + count <= limit) { "文件或输出超过大小限制" }
        output.write(bytes, 0, count)
    }
}
