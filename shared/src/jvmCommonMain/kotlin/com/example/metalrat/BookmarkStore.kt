package com.example.metalrat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** All bookmarks, newest first, persisted as one JSON file (same format as the export). */
class BookmarkStore(private val file: File) {
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Bookmark>> get() = _items

    fun get(id: String): Bookmark? = _items.value.firstOrNull { it.id == id }

    /** Adds or replaces (same id) a bookmark. */
    @Synchronized
    fun add(bookmark: Bookmark) {
        set(_items.value.filterNot { it.id == bookmark.id } + bookmark)
    }

    @Synchronized
    fun update(id: String, transform: (Bookmark) -> Bookmark) {
        val current = _items.value
        if (current.none { it.id == id }) return
        set(current.map { if (it.id == id) transform(it) else it })
    }

    @Synchronized
    fun delete(id: String): Bookmark? {
        val removed = get(id) ?: return null
        set(_items.value.filterNot { it.id == id })
        return removed
    }

    /** Merges an export file in; bookmarks already present (same id) stay as they are. Returns how many were new. */
    @Synchronized
    fun importJson(text: String): Int {
        val known = _items.value.mapTo(HashSet()) { it.id }
        val fresh = BookmarkJson.parse(text).filter { known.add(it.id) }
        if (fresh.isNotEmpty()) set(_items.value + fresh)
        return fresh.size
    }

    fun exportJson(): String = BookmarkJson.encode(_items.value)

    private fun set(list: List<Bookmark>) {
        val sorted = list.sortedByDescending { it.createdAt }
        _items.value = sorted
        save(sorted)
    }

    private fun load(): List<Bookmark> {
        if (!file.exists()) return emptyList()
        return try {
            BookmarkJson.parse(file.readText()).sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            // Never overwrite a file we couldn't read: keep it next to the new one.
            file.renameTo(File(file.parentFile, "${file.name}.unlesbar-${System.currentTimeMillis()}"))
            emptyList()
        }
    }

    private fun save(list: List<Bookmark>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(BookmarkJson.encode(list))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
