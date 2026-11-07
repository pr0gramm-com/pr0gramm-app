package com.pr0gramm.app.services

import com.pr0gramm.app.api.pr0gramm.Api
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.experimental.or

class SeenApiService(private val api: Api) {
    /**
     * Raw seen bits of the server with their version and etag.
     */
    class State(val bits: ByteArray, val version: Int, val etag: String?)

    /**
     * Gets the seen bits from the server. Returns [previous] if they did not change since then.
     */
    suspend fun fetch(previous: State?): State {
        val response = api.seenData(previous?.etag)

        if (response.code() == 304 && previous != null) {
            response.errorBody()?.close()
            return previous
        }

        if (response.code() == 404) {
            // no data on the server yet, the web app starts with version 1 too.
            response.errorBody()?.close()
            return State(byteArrayOf(), version = 1, etag = null)
        }

        val body = response.body()
        if (!response.isSuccessful || body == null) {
            throw HttpException(response)
        }

        val version = response.headers()["X-pr0gramm-Bits-Version"]?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        return State(body.use { it.bytes() }, version, response.headers()["ETag"])
    }

    /**
     * Merges the raw [bits] into the seen bits on the server. Returns the new server state.
     */
    suspend fun uploadPartial(state: State, bits: ByteArray): State {
        val body = deflate(bits).toRequestBody("application/octet-stream".toMediaType())

        val result = try {
            api.seenBitsUpdatePartial(null, state.version, body)
        } catch (err: HttpException) {
            if (err.code() == 400 || err.code() == 413) {
                throw RejectedException(err)
            }

            if (err.code() == 409) {
                throw VersionConflictException()
            }

            throw err
        }

        if (!result.success) {
            throw VersionConflictException()
        }

        // the etag only belongs to our merged copy if nobody else wrote in between.
        val etag = result.etag.takeIf { result.version == state.version + 1 }
        return State(or(state.bits, bits), result.version, etag)
    }

    class VersionConflictException : RuntimeException("version conflict during update")

    class RejectedException(cause: Throwable) : RuntimeException("server rejected seen bits", cause)
}

private fun deflate(bits: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.BEST_COMPRESSION)
    try {
        val output = ByteArrayOutputStream()
        DeflaterOutputStream(output, deflater).use { it.write(bits) }
        return output.toByteArray()
    } finally {
        deflater.end()
    }
}

internal fun or(a: ByteArray, b: ByteArray): ByteArray {
    return ByteArray(maxOf(a.size, b.size)) { idx ->
        a.getOrElse(idx) { 0 } or b.getOrElse(idx) { 0 }
    }
}
