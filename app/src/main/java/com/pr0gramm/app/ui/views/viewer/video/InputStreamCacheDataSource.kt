package com.pr0gramm.app.ui.views.viewer.video

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.pr0gramm.app.Logger
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.util.BoundedInputStream
import com.pr0gramm.app.util.closeQuietly
import java.io.BufferedInputStream
import java.io.InputStream

/**
 * A data source that uses a Cache as a data source.
 */
@OptIn(UnstableApi::class)
internal class InputStreamCacheDataSource(private val cache: Cache) : BaseDataSource(true) {
    private val logger = Logger("InputStreamCacheDataSource(${this.hashCode()})")

    private var opened: Boolean = false
    private var _uri: Uri? = null

    private var inputStream: InputStream? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

    private var bytesTransferred: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        assert(_uri == null && inputStream == null) { "datasource already initialized" }

        logger.info {
            "Open dataSource for ${dataSpec.length} at ${dataSpec.position} of ${dataSpec.uri}"
        }

        _uri = dataSpec.uri

        transferInitializing(dataSpec)

        cache.get(dataSpec.uri).use { entry ->
            // get the input stream from the entry.
            val inputStream = entry.inputStreamAt(dataSpec.position.toInt())
            this.inputStream = inputStream

            // gets the size of the file. This also initializes the cache entry.
            val totalSize = entry.totalSize.toLong()

            if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                this.bytesRemaining = totalSize - dataSpec.position
            } else {
                // limit amount to the requested length.
                this.inputStream = BoundedInputStream(inputStream, dataSpec.length)
                this.bytesRemaining = dataSpec.length
            }

            // reduce read calls to the actual input
            this.inputStream = BufferedInputStream(this.inputStream, 1024 * 64)

            opened = true

            // everything looks fine, inform listeners about data transfer
            transferStarted(dataSpec)

            return bytesRemaining
        }
    }

    override fun close() {
        logger.info {
            "Closing dataSource for uri $_uri after reading $bytesTransferred with $bytesRemaining bytes remaining"
        }

        _uri = null

        inputStream?.closeQuietly()
        inputStream = null

        bytesRemaining = C.LENGTH_UNSET.toLong()
        bytesTransferred = 0

        if (opened) {
            opened = false
            transferEnded()
        }
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) {
            return 0
        }

        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            readLength.coerceAtMost(64 * 1024)
        } else {
            bytesRemaining.coerceAtMost(readLength.toLong()).toInt()
        }

        // read from input stream
        val stream = inputStream ?: throw IllegalStateException("DataSource is not open.")
        val bytesTransferred = stream.read(buffer, offset, bytesToRead)

        if (bytesTransferred == -1) {
            // we did not get any data but we expected to get some.
            // indicate end of input to the caller.
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            // reduce by number of bytes read
            bytesRemaining -= bytesTransferred.toLong()
        }

        this.bytesTransferred += bytesTransferred.toLong()

        bytesTransferred(bytesTransferred)

        return bytesTransferred
    }

    override fun getUri(): Uri? = _uri
}
