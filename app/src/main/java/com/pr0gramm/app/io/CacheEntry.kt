package com.pr0gramm.app.io

import android.annotation.SuppressLint
import android.net.Uri
import androidx.concurrent.futures.ResolvableFuture
import com.google.common.io.Closer
import com.pr0gramm.app.Instant
import com.pr0gramm.app.Logger
import com.pr0gramm.app.util.AndroidUtility.logToCrashlytics
import com.pr0gramm.app.util.CountingInputStream
import com.pr0gramm.app.util.closeQuietly
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.readStream
import com.pr0gramm.app.util.updateTimestamp
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.max
import kotlin.math.min

private class LockState(val writtenUpdated: Condition) {
    // number of bytes currently written to disk.
    var written: Int = 0

    var totalSizeValue: Int = -1

    var fp: RandomAccessFile? = null

    // delegate all calls to this delegate if set
    var delegate: FileEntry? = null
}

private class Lock {
    private val lock = ReentrantLock()
    private val state = LockState(
        writtenUpdated = lock.newCondition(),
    )

    inline fun <T> withLock(action: (state: LockState) -> T): T {
        contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }

        return this.lock.withLock {
            action(state)
        }
    }


    inline fun <T> withTryLock(block: (state: LockState) -> T): T? {
        if (!lock.tryLock()) {
            return null
        }

        try {
            return block(state)
        } finally {
            lock.unlock()
        }
    }
}


/**
 * A entry that is hold by the [Cache].
 */
internal class CacheEntry(
    private val httpClient: OkHttpClient,
    private val partialCached: File,
    private val fullyCached: File,
    val uri: Uri
) : Cache.Entry {
    private val logger = Logger("CacheEntry(${uri.lastPathSegment})")

    // lock to guard all io operations
    private val lock = Lock()

    private val refCount = AtomicInteger()

    // reference to the currently running file worker.
    // A Worker will use this reference to see if it is still the
    // active worker or if it should shutdown.
    private val activeWorker = AtomicReference<Worker?>(null)

    fun read(pos: Int, data: ByteArray, offset: Int, amount: Int): Int {
        // Always succeed when reading 0 bytes.
        if (amount == 0) {
            return 0
        }

        lock.withLock { state ->
            val fp = ensureOpen(state)

            // if we are at the end of the file, we need to signal that
            if (pos >= state.totalSizeValue) {
                return -1
            }

            // check how much we can actually read at most!
            val amountToRead = min(pos + amount, state.totalSizeValue) - pos

            // wait for the data to be there
            expectCached(state, pos + amountToRead)

            // now try to read the bytes we requested
            fp.seek(pos.toLong())
            val byteCount = read(fp, data, offset, amountToRead)

            // check if we got as many bytes as we wanted to.
            if (byteCount != amountToRead) {
                logToCrashlytics(
                    EOFException(
                        "Expected to read $amountToRead bytes at $pos, but got only $byteCount. Cache entry: $this"
                    )
                )
            }

            return byteCount
        }
    }

    /**
     * Waits until at least the given amount of data is written.
     */
    private fun expectCached(state: LockState, requiredCount: Int) {
        try {
            while (state.written < requiredCount) {
                ensureCaching(state)
                state.writtenUpdated.await(250, TimeUnit.MILLISECONDS)
            }
        } catch (_: InterruptedException) {
            throw InterruptedIOException("Waiting for bytes was interrupted.")
        }
    }

    private fun ensureOpen(state: LockState): RandomAccessFile {
        // we are initialized if we already have a opened file.
        // just return that one.
        val fp = state.fp
        if (fp != null) {
            return fp
        }

        logger.debug { "Entry needs to be initialized: $this" }
        return open(state)
    }

    /**
     * Returns true, if the entry is fully cached.
     * You need to hold the lock to call this method.
     */
    private fun fullyCached(state: LockState): Boolean {
        return state.delegate != null && state.written >= state.totalSizeValue
    }

    /**
     * Will be called if we need to initialize the file.
     * If this is called, we can expect the entry to hold its own lock.
     */
    private fun open(state: LockState, retryCount: Int = 4): RandomAccessFile {
        if (retryCount <= 0) {
            throw IOException("Retries are up, failed to open: $uri")
        }

        // ensure that the parent directory exists.
        partialCached.parentFile?.let { parentFile ->
            if (!parentFile.exists()) {
                if (!parentFile.mkdirs()) {
                    logger.warn { "Could not create parent directory." }
                }
            }
        }

        if (fullyCached.exists()) {
            logger.debug { "open() called even if file was already fully cached" }
            // try to open fullyCached file in read-only mode first.
            // the file was already cached but someone still wants to use our
            // old entry.
            //
            // also we can now set the delegate if not already done.
            val fp = RandomAccessFile(fullyCached, "r")

            state.fp = fp
            state.written = fp.length().toInt()
            state.totalSizeValue = fp.length().toInt()

            if (state.delegate == null) {
                logger.debug { "delegate not yet set, setting it now" }
                state.delegate = FileEntry(fullyCached)
            }

            return fp
        }

        // open the file in read/write mode, creating it if it did not exist.
        val fp = RandomAccessFile(partialCached, "rwd")

        state.fp = fp

        try {
            logger.debug { "partialCached was opened by initialize()" }

            // consider the already cached bytes
            state.written = fp.length().toInt()

            // start caching in background
            state.totalSizeValue = resumeCaching(state)

            if (state.written > state.totalSizeValue) {
                // okay, someone fucked up! :/
                logToCrashlytics(IOException("written=${state.written} greater than totalSize=${state.totalSizeValue}"))

                // invalidate the file and try again.
                fp.setLength(0)
                fp.close()

                state.fp = null

                logger.debug { "Opening file again." }
                return open(state, retryCount = retryCount - 1)
            }

            return fp

        } catch (err: Exception) {
            // resetting fp on error.
            fp.closeQuietly()

            // cleanup
            reset(state)

            throw err
        }
    }

    private fun reset(state: LockState) {
        logger.debug { "Resetting entry $this" }
        state.fp?.closeQuietly()
        state.fp = null
        state.written = 0
        state.totalSizeValue = -1

        activeWorker.set(null)
    }

    /**
     * Ensure that the entry is caching data. Caching is needed, if it is not fully
     * cached yet and currently not caching.
     */
    private fun ensureCaching(state: LockState) {
        if (activeWorker.get() == null && !fullyCached(state)) {
            logger.debug { "Caching will start on entry $this" }
            resumeCaching(state)
        }
    }

    private fun resumeCaching(state: LockState): Int {
        // start a new cache writer if required.
        val worker = activeWorker.get() ?: Worker().also { newWorker ->
            // set this as the active worker. No need for compareAndSet, as we're the
            // only one setting this to a non-null value, and we are holding the lock.
            this.activeWorker.set(newWorker)

            logger.debug { "Resume caching in a background thread" }

            // resume caching in the background.
            val offset = state.written
            doInBackground { newWorker.resumeCaching(offset) }
        }

        return try {
            logger.debug { "Wait for totalSize future to be available" }

            @SuppressLint("RestrictedApi")
            worker.totalSize.get()
        } catch (err: ExecutionException) {
            // throw the real error, not the wrapped one.
            throw err.cause ?: err
        }
    }

    /**
     * Increment the refCount
     */
    fun incrementRefCount(): CacheEntry {
        refCount.incrementAndGet()
        return this
    }

    override val totalSize: Int
        get() {
            file?.let { fullyCached ->
                return fullyCached.length().toInt()
            }

            lock.withLock { state ->
                // ensure open just for the side effects
                ensureOpen(state)

                return state.totalSizeValue
            }
        }

    /**
     * Mark this entry as "closed" - as far as the caller is concerned. The entry
     * itself does not need to close immediately if it is used somewhere else.
     */
    override fun close() {
        lock.withLock { state ->
            if (this.refCount.decrementAndGet() == 0 && state.fp != null) {
                logger.debug { "Closing cache file for entry $this now." }
                reset(state)
            }
        }
    }

    override val file: File?
        get() = lock.withLock { state ->
            state.delegate?.file
        }

    override fun inputStreamAt(offset: Int): InputStream {
        lock.withLock { state ->
            // serve directly from file if possible
            state.delegate?.let { file -> return file.inputStreamAt(offset) }

            // update the time stamp if the cache file already exists.
            if (partialCached.exists()) {
                if (!partialCached.updateTimestamp()) {
                    logger.warn { "Could not update timestamp on $partialCached" }
                }
            }

            return EntryInputStream(incrementRefCount(), offset)
        }
    }

    override val fractionCached: Float
        get() {
            return lock.withLock { state ->
                state.delegate?.let { delegate ->
                    return delegate.fractionCached
                }

                if (state.totalSizeValue > 0) {
                    state.written / state.totalSizeValue.toFloat()
                } else {
                    -1f
                }
            }
        }

    /**
     * Returns the number of bytes that are available too read without caching
     * from the given position.
     */
    private fun availableStartingAt(position: Int): Int {
        lock.withLock { state ->
            return max(0, state.written - position)
        }
    }

    override fun toString(): String {
        class Values(
            val written: String,
            val totalSizeValue: String?,
            val fullyCached: String,
        )

        val values = lock.withTryLock { state ->
            Values(
                written = state.written.toString(),
                totalSizeValue = state.totalSizeValue.takeIf { it > 0 }?.toString(),
                fullyCached = fullyCached(state).toString(),
            )
        } ?: Values(
            written = "locked",
            totalSizeValue = "locked",
            fullyCached = "locked",
        )

        return "Entry(written=${values.written}, totalSize=${values.totalSizeValue}, " +
                "caching=${activeWorker.get() != null}, refCount=${refCount.get()}, " +
                "fullyCached=${values.fullyCached}, uri=$uri)"
    }

    private class EntryInputStream(private val entry: CacheEntry, private var position: Int) :
        InputStream() {
        private val closed = AtomicBoolean()
        private var mark: Int = 0

        override fun read(): Int {
            throw UnsupportedOperationException("read() not implemented due to performance.")
        }

        override fun read(bytes: ByteArray, off: Int, len: Int): Int {
            val byteCount = entry.read(position, bytes, off, len)
            if (byteCount > 0) {
                position += byteCount
            }

            return byteCount
        }

        override fun skip(amount: Long): Long {
            if (amount < 0) {
                return 0
            }

            val skipped = min(entry.totalSize.toLong(), position + amount) - position
            position += skipped.toInt()
            return skipped
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                entry.close()
            }
        }

        override fun available(): Int {
            return entry.availableStartingAt(position)
        }

        override fun markSupported(): Boolean {
            return true
        }

        override fun mark(readlimit: Int) {
            mark = position
        }

        override fun reset() {
            position = mark
        }
    }

    /**
     * Reads the given number of bytes from the current position of the stream
     * if possible. The method returns the numbers of bytes actually read.
     */
    private fun read(fp: RandomAccessFile, data: ByteArray, offset: Int, amount: Int): Int {
        var totalCount = 0

        do {
            val count = fp.read(data, offset + totalCount, amount - totalCount)
            if (count < 0) {
                break
            }

            totalCount += count
        } while (totalCount < amount)

        return totalCount
    }


    @SuppressLint("RestrictedApi")
    private inner class Worker {
        private val logger = Logger("${this@CacheEntry.logger}.Worker")

        val isActiveWorker get() = activeWorker.get() == this

        // The total size that this worker determined for the file.
        val totalSize: ResolvableFuture<Int> = ResolvableFuture.create()

        /**
         * This method is called from the caching thread once caching stops.
         */
        private fun cachingStopped() {
            // logger.debug { "Caching stopped on entry $this" }

            lock.withLock { state ->
                if (isActiveWorker) {
                    // unset us as the active worker
                    activeWorker.compareAndSet(this, null)

                    // and close our reference to the entry
                    close()
                }

                // If there are any readers, we need to notify them, so caching will be
                // re-started if needed
                state.writtenUpdated.signalAll()
            }
        }

        fun resumeCaching(offset: Int) {
            incrementRefCount()

            try {
                logger.debug { "Resume caching for $this starting at $offset" }

                val request = Request.Builder()
                    .url(uri.toString())
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .apply {
                        if (offset > 0) {
                            header("Range", "bytes=$offset-")
                        }
                    }
                    .build()

                // do the call synchronously
                val response = httpClient.newCall(request).execute()

                var (totalSize, body, readAll) = try {
                    logger.debug { "Response is $response" }
                    val body = response.body
                        ?: throw IllegalStateException("no body in media response")

                    val (totalSize, readAll) = when (response.code) {
                        200 -> {
                            lock.withLock { state ->
                                logger.info { "Resetting cached file due to http 200 response" }
                                state.written = 0

                                // also reset the file itself
                                state.fp?.setLength(0)
                            }

                            Pair(body.contentLength().toInt(), true)
                        }

                        206 -> {
                            val range = response.header("Content-Range")
                                ?: throw IOException("Expected Content-Range header")

                            logger.debug { "Got Content-Range header with $range" }
                            Pair(parseRangeHeaderTotalSize(range), false)
                        }

                        403 -> {
                            throw IOException("Not allowed to read file, are you on a public wifi?")
                        }

                        404 -> {
                            throw FileNotFoundException("File not found at " + response.request.url)
                        }

                        416 -> {
                            val range = response.header("Content-Range")
                                ?: throw IOException("Request for ${request.url} failed with status ${response.code}")

                            logger.debug { "Got Content-Range header with $range" }
                            val totalSize = parseRangeHeaderTotalSize(range)
                            if (offset < totalSize) {
                                throw IOException("Request for ${request.url} failed with status ${response.code}")
                            } else {
                                Pair(totalSize, false)
                            }
                        }

                        else -> throw IOException("Request for ${request.url} failed with status ${response.code}")
                    }

                    // we now know the size of the full file
                    Triple(totalSize.takeIf { totalSize > 0 }, body, readAll)

                } catch (err: Exception) {
                    logger.debug { "Closing the response because of an error." }
                    response.closeQuietly()

                    throw err
                }

                if (totalSize == null) {
                    logger.warn { "No total size for uri: $uri" }
                }

                // closer to handle all close operations in the end.
                Closer.create().use { closer ->
                    closer.register(response)

                    var bodyStream = body.byteStream()
                    closer.register(bodyStream)

                    if (totalSize == null) {
                        if (!readAll) {
                            throw IOException("We don't have the full size of the response")
                        }

                        // we do not know the size of the response, so we will just copy the full response
                        // to an unnamed temporary file and count the bytes we put into that file
                        val buf = File(partialCached.path + ".buf")
                        try {
                            logger.debug { "Writing response of unknown length to temp file" }

                            // copy everything to temp file
                            val countStream = CountingInputStream(bodyStream)
                            FileOutputStream(buf).use { out -> countStream.copyTo(out) }

                            // replace the original stream with the new stream to the saved body
                            bodyStream = FileInputStream(buf)
                            closer.register(bodyStream)

                            // update the total size to the amount of data we've written to the
                            // temporary file
                            totalSize = countStream.count.toInt()
                            logger.debug { "File was written to temp, we now know totalSize=$totalSize" }

                        } finally {
                            // unlink file from the disk so it gets deleted automatically once closed
                            buf.delete()
                        }
                    }

                    var fullyWritten = false
                    if (offset < totalSize) {
                        logger.debug { "Writing response to cache file" }
                        fullyWritten =
                            bodyStream.use { writeResponseToEntry(bodyStream, totalSize) }
                    }

                    logger.debug { "Body was fully written to cache file: $fullyWritten" }

                    if (fullyWritten) {
                        // check if we need can now promote the cached file to the target file
                        promoteFullyCached(totalSize)
                    }
                }

            } catch (err: Exception) {
                logger.error { "Error in caching thread ($err)" }
                this.totalSize.setException(err)

            } finally {
                cachingStopped()
            }
        }

        private fun parseRangeHeaderTotalSize(header: String): Int {
            return header.takeLastWhile { it != '/' }.toInt()
        }

        /**
         * Writes the response to the file. If fp disappears, we log a warning
         * and then we just return.
         */
        private fun writeResponseToEntry(stream: InputStream, totalSize: Int): Boolean {
            // Work at least for a second before giving up, if we're the only
            // one holding a reference to the entry. This way we do not fill
            // the cache of a file no one is reading from
            val minWorkThreshold = Instant.now().plus(1, TimeUnit.SECONDS)

            var hasSetTotalSize = false
            readStream(stream, bufferSize = 64 * 1024) { buffer, byteCount ->
                lock.withLock { state ->
                    val fp = state.fp
                    if (fp == null) {
                        logger.warn { "Error during caching, the file-handle went away: $this" }
                        return false
                    }

                    write(state, fp, buffer, byteCount)

                    // publish the size to waiting consumers
                    // if the first read & write was successful. If an error would have occurred,
                    // it would be caught and set via totalSize.setException to propagate
                    // it to the caller
                    if (!hasSetTotalSize) {
                        this.totalSize.set(totalSize)
                        hasSetTotalSize = true
                    }

                    if (!isActiveWorker) {
                        logger.debug { "Caching canceled, stopping now." }
                        return false
                    }

                    // if this worker is the only one holding a reference to the
                    // cache entry, and the timeout has expired, we stop caching
                    if (refCount.toInt() == 1 && minWorkThreshold.isBefore(Instant.now())) {
                        logger.debug { "Worker was abandoned, stopping cache now" }
                        return false
                    }
                }
            }

            return true
        }

        private fun write(state: LockState, fp: RandomAccessFile, data: ByteArray, byteCount: Int) {
            if (byteCount > 0) {
                // logger.debug { "Writing $byteCount of data at byte index $written" }

                // write the data to the right place
                fp.seek(state.written.toLong())
                fp.write(data, 0, byteCount)

                // and increase the write pointer
                state.written += byteCount
            }

            // tell any readers about the new data.
            state.writtenUpdated.signalAll()
        }

        private fun promoteFullyCached(expectedSize: Int) {
            lock.withLock { state ->
                // sync to disk if needed
                state.fp?.fd?.sync()

                fullyCached.length().let { fullyCachedSize ->
                    if (fullyCachedSize > 0 && fullyCachedSize != expectedSize.toLong()) {
                        logger.debug { "Deleting fullyCached file with wrong size." }
                        fullyCached.delete()
                    }
                }

                if (partialCached.length() != expectedSize.toLong()) {
                    throw IllegalStateException("partialCached file has unexpected size, expected $expectedSize")
                }

                // rename to fully cached file. All read operations can now work directly
                // from that file.
                partialCached.renameTo(fullyCached)

                // delegate to new entry from now on.
                state.delegate = FileEntry(fullyCached)
            }
        }

        override fun toString(): String {
            return "Worker()"
        }
    }
}
