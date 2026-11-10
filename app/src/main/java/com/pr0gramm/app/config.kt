package com.pr0gramm.app


const val ServiceBaseURL = "https://app.pr0gramm.com"

/**
 * Domain of the website, the api and (as subdomains) the media hosts.
 */
const val Pr0Domain = "pr0.app"

/**
 * True for the main host of the current or the old domain. Links and
 * data from before the move still point to pr0gramm.com.
 */
fun isPr0Host(host: String?): Boolean {
    return host.equals(Pr0Domain, ignoreCase = true) || host.equals("pr0gramm.com", ignoreCase = true)
}

/**
 * True for subdomains (media hosts) of the current or the old domain.
 */
fun isPr0MediaHost(host: String): Boolean {
    return host.endsWith(".$Pr0Domain", ignoreCase = true) || host.endsWith(".pr0gramm.com", ignoreCase = true)
}
