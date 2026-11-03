package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.Instant
import com.pr0gramm.app.Logger
import com.pr0gramm.app.model.user.LoginCookie
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Adds the age verification token as `av` cookie to media requests.
 * Without it, the media hosts answer nsfw/nsfl media with 403.
 */
class AgeVerificationInterceptor(
    private val cookieJar: LoginCookieJar,
    private val api: () -> Api,
) : Interceptor {
    private val logger = Logger("AgeVerification")

    private var token: String? = null

    // time after which we need to fetch a new token (or retry after a failure)
    private var refreshAfter: Long = 0

    // login the current token belongs to
    private var tokenLogin: LoginCookie? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // only media hosts, never the api itself
        if (!request.url.host.endsWith(".pr0gramm.com") || !cookieJar.hasCookie()) {
            return chain.proceed(request)
        }

        val token = currentToken() ?: return chain.proceed(request)

        return chain.proceed(request.newBuilder().header("Cookie", "av=$token").build())
    }

    private fun currentToken(): String? = synchronized(this) {
        val login = cookieJar.parsedCookie

        if (login !== tokenLogin || Instant.now().millis >= refreshAfter) {
            tokenLogin = login
            fetchToken()
        }

        token
    }

    private fun fetchToken() {
        val now = Instant.now().millis

        val result = try {
            api().avToken().execute().body()
        } catch (err: Exception) {
            logger.warn(err) { "Could not fetch age verification token" }
            null
        }

        val validUntil = result?.validUntil?.millis
        if (result?.token != null && validUntil != null) {
            logger.info { "Got age verification token, valid until ${result.validUntil}" }
            token = result.token
            refreshAfter = validUntil - TimeUnit.SECONDS.toMillis(60)
            return
        }

        token = null

        if (result?.error != null) {
            // probably notAgeVerified, do not ask again for every image
            logger.info { "No age verification token: ${result.error}" }
            refreshAfter = now + TimeUnit.HOURS.toMillis(1)
        } else {
            refreshAfter = now + TimeUnit.MINUTES.toMillis(1)
        }
    }
}
