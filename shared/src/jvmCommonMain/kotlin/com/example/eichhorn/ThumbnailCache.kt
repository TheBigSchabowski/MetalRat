package com.example.eichhorn

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.security.MessageDigest

/** Loads preview images (http or file://) with a memory and disk cache. Failures are remembered per run. */
class ThumbnailCache(private val dir: File, private val decode: (ByteArray) -> ImageBitmap?) {
    private val memory = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 120
    }
    private val failed = HashSet<String>()

    @Synchronized fun cached(url: String): ImageBitmap? = memory[url]

    @Synchronized fun hasFailed(url: String): Boolean = url in failed

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        cached(url)?.let { return@withContext it }
        if (hasFailed(url)) return@withContext null
        val image = runCatching { bytesFor(url)?.let(decode) }.getOrNull()
        synchronized(this@ThumbnailCache) { if (image != null) memory[url] = image else failed += url }
        image
    }

    private fun bytesFor(url: String): ByteArray? {
        if (url.startsWith("file:")) return File(URI(url)).takeIf { it.isFile }?.readBytes()
        val file = File(dir, sha1(url))
        if (file.isFile && file.length() > 0) return file.readBytes()
        val bytes = Http.get(url) ?: return null
        dir.mkdirs()
        file.writeBytes(bytes)
        return bytes
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
