package com.pr0gramm.app.services

import android.content.Context
import com.pr0gramm.app.Logger
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.unsigned
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicReference
import kotlin.experimental.or


/**
 * Very simple service to check if an item was already visited or not.
 */

class SeenService(context: Context) {
    private val lock = Any()
    private val buffer = AtomicReference<ByteBuffer>()

    private val logger = Logger("SeenService")

    val currentGeneration = MutableStateFlow(0)

    init {
        doInBackground {
            try {
                val file = File(context.filesDir, "seen-posts.bits")
                buffer.set(mapByteBuffer(file))
            } catch (error: Exception) {
                logger.warn("Could not load the seen-Cache", error)
            }
        }
    }

    fun isSeen(id: Long): Boolean {
        val buffer = this.buffer.get() ?: return false

        val idx = id.toInt() / 8
        if (idx < 0 || idx >= buffer.limit()) {
            logger.warn { "Id is too large" }
            return false
        }

        val mask = 1 shl (7 - id % 8).toInt()
        return (buffer.get(idx).unsigned and mask) != 0
    }

    fun markAsSeen(id: Long) {
        val buffer = buffer.get() ?: return

        val idx = id.toInt() / 8
        if (idx < 0 || idx >= buffer.limit()) {
            logger.warn { "Id is too large" }
            return
        }

        // only one thread can write the buffer at a time.
        val changed = synchronized(lock) {
            val value = buffer.get(idx).unsigned
            val updatedValue = value or (1 shl (7 - id.toInt() % 8))

            buffer.put(idx, updatedValue.toByte())
            value != updatedValue
        }

        if (changed) {
            currentGeneration.value++
        }
    }

    /**
     * Removes the "marked as seen" status from all items.
     */
    fun clear() {
        val buffer = this.buffer.get() ?: return

        synchronized(lock) {
            logger.info { "Removing all the items" }
            for (idx in 0 until buffer.limit()) {
                buffer.put(idx, 0.toByte())
            }
        }

        currentGeneration.value++
    }

    /**
     * Merges the raw (uncompressed) seen bits of the server into the local bits.
     */
    fun merge(other: ByteArray) {
        val buffer = this.buffer.get() ?: return

        val changed = synchronized(lock) { orInto(buffer, other) }
        if (changed > 0) {
            currentGeneration.value++
        }

        logger.info { "Merged ${other.size} bytes of seen bits, $changed bytes changed" }
    }

    /**
     * Bits that are set locally but not in [server], or null if there are none.
     */
    fun bitsMissingIn(server: ByteArray): ByteArray? {
        val buffer = this.buffer.get() ?: return null
        // no lock: bits are only ever set, one missed during the scan is uploaded next time.
        return missingBits(buffer, server)
    }

    /**
     * Maps the cache into a byte buffer. The buffer is backed by the file, so
     * all changes to the buffer are written back to the file.

     * @param file The file to map into memory
     */
    private fun mapByteBuffer(file: File): ByteBuffer {
        // one bit per post, 64 million posts like the web app. Growing keeps the existing bits.
        val size = 8_000_000L

        logger.info { "Mapping cache: $file" }
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(size)
            return raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, size)
        }
    }
}

/**
 * Sets all bits of [other] in [target]. Returns the number of bytes that changed.
 */
internal fun orInto(target: ByteBuffer, other: ByteArray): Int {
    var changed = 0

    for (idx in 0 until minOf(other.size, target.limit())) {
        val value = target.get(idx)
        val merged = value or other[idx]
        if (merged != value) {
            target.put(idx, merged)
            changed++
        }
    }

    return changed
}

/**
 * Bits set in [local] but not in [server], cut after the last set byte. Null if there are none.
 */
internal fun missingBits(local: ByteBuffer, server: ByteArray): ByteArray? {
    fun missingAt(idx: Int): Int = local.get(idx).toInt() and server.getOrElse(idx) { 0 }.toInt().inv() and 0xff

    var last = local.limit() - 1
    while (last >= 0 && missingAt(last) == 0) {
        last--
    }

    if (last < 0) {
        return null
    }

    return ByteArray(last + 1) { idx -> missingAt(idx).toByte() }
}
