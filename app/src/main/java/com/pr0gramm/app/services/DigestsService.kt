package com.pr0gramm.app.services

import com.pr0gramm.app.api.pr0gramm.Api

class DigestsService(private val api: Api, private val inboxService: InboxService) {
    suspend fun digests(): List<Api.DigestsInbox.Digest> {
        val digests = api.inboxDigests(older = null, markAsRead = true).digests

        // the server marked them as read, update the badge right away
        inboxService.clearUnreadDigestsCount()

        return digests
    }
}
