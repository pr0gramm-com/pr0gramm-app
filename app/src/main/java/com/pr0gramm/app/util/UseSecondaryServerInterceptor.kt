package com.pr0gramm.app.util

import com.pr0gramm.app.Pr0Domain
import com.pr0gramm.app.Settings
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class UseSecondaryServerInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var req = chain.request()

        if (Settings.useSecondaryServers) {
            req = req.newBuilder()
                .url(injectSecondaryServer(req.url))
                .build()
        }

        return chain.proceed(req)
    }

    private fun injectSecondaryServer(url: HttpUrl): HttpUrl {
        val (subdomain, domain) = url.host.split('.', limit = 2).takeIf { it.size == 2 } ?: return url
        if (domain != Pr0Domain && domain != "pr0gramm.com") {
            return url
        }

        val secondary = when (subdomain) {
            "img" -> "images"
            "vid" -> "videos"
            "full" -> "fullsize"
            else -> return url
        }

        return url.newBuilder().host("$secondary.$domain").build()
    }
}
