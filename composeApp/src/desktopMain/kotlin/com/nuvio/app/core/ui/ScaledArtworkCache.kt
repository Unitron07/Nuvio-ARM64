package com.nuvio.app.core.ui

import java.lang.ref.WeakReference

/** Retains scaled results across painter disposal, not decoded sources or URLs. */
internal class ScaledArtworkCache<T : Any>(private val budgetBytes: Long) {
    private class Key(source: Any, val revision: Int, val width: Int, val height: Int) {
        val source = WeakReference(source)
        private val identityHash = System.identityHashCode(source)
        override fun hashCode() = ((identityHash * 31 + revision) * 31 + width) * 31 + height
        override fun equals(other: Any?): Boolean = this === other ||
            (other is Key && revision == other.revision && width == other.width && height == other.height &&
                source.get()?.let { it === other.source.get() } == true)
    }
    private data class Entry<T>(val value: T, val bytes: Long)
    private val entries = LinkedHashMap<Key, Entry<T>>(16, 0.75f, true)
    var retainedBytes = 0L; private set
    val size: Int @Synchronized get() = entries.size

    @Synchronized
    fun getOrCreate(source: Any, revision: Int, width: Int, height: Int, create: () -> T): T {
        require(width > 0 && height > 0)
        val key = Key(source, revision, width, height)
        entries[key]?.let { return it.value }
        val bytes = width.toLong() * height * 4
        // Remove dead sources and superseded source generations without retaining
        // decoded bitmaps. Never dispose a bitmap still referenced by a painter.
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val existingSource = entry.key.source.get()
            if (existingSource == null || (existingSource === source && entry.key.revision != revision)) {
                retainedBytes -= entry.value.bytes; iterator.remove()
            }
        }
        val value = create()
        if (bytes > budgetBytes) return value
        while (retainedBytes + bytes > budgetBytes && entries.isNotEmpty()) {
            val eldest = entries.iterator()
            retainedBytes -= eldest.next().value.bytes; eldest.remove()
        }
        entries[key] = Entry(value, bytes)
        retainedBytes += bytes
        return value
    }

    @Synchronized fun clear() { entries.clear(); retainedBytes = 0 }
}
