package com.pr0gramm.app.sync

import com.pr0gramm.app.*
import com.pr0gramm.app.services.*
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.util.catchAll
import com.pr0gramm.app.util.unless
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class SyncService(private val userService: UserService,
                  private val notificationService: NotificationService,
                  private val singleShotService: SingleShotService,
                  private val seenService: SeenService,
                  private val seenApiService: SeenApiService) {

    private val logger = Logger("SyncService")

    private val seenSyncLock = AtomicBoolean()

    // last known seen bits of the server, only valid for seenServerUserId
    @Volatile
    private var seenServer: SeenApiService.State? = null
    private var seenServerUserId: Int? = null

    // the server rejected our upload, do not retry until the next app start
    @Volatile
    private var seenUploadRejected = false


    init {
        AsyncScope.launch {
            userService.loginStates
                    .map { state -> state.id }
                    .distinctUntilChanged()
                    .onStart { delay(1.seconds) }
                    .collect { launch { performSyncSeenService() } }
        }
    }

    suspend fun dailySync() {
        catchAll {
            UpdateChecker().queryAll().let { response ->
                if (response is UpdateChecker.Response.UpdateAvailable) {
                    notificationService.showUpdateNotification(response.update)
                }
            }
        }
    }

    suspend fun sync() {
        if (!userService.isAuthorized) {
            logger.info { "Will not sync now - user is not signed in." }
            return
        }

        logger.info { "Performing a sync operation now" }

        logger.time("Sync operation") {
            catchAll {
                syncCachedUserInfo()
            }

            catchAll {
                syncUserState()
            }

            catchAll {
                // cheap: the server answers 304 if nothing changed.
                performSyncSeenService()
            }
        }
    }

    private suspend fun syncCachedUserInfo() {
        if (singleShotService.firstTimeToday("update-userInfo")) {
            logger.info { "Update current user info" }

            catchAll {
                userService.updateCachedUserInfo()
            }
        }
    }

    private suspend fun syncUserState() {
        logger.info { "Sync with pr0gramm api" }

        val sync = userService.sync() ?: return

        if (sync.inbox.total > 0) {
            notificationService.showUnreadMessagesNotification()
        } else {
            // remove if no messages are found
            notificationService.cancelForAllUnread()
        }
    }

    private suspend fun performSyncSeenService() {
        unless(Settings.backup && seenSyncLock.compareAndSet(false, true)) {
            logger.info { "Not starting sync of seen bits." }
            return
        }

        logger.info { "Syncing of seen bits" }

        try {
            if (!userService.isAuthorized) {
                return
            }

            // never mix the seen bits of different users
            val userId = userService.loginState.id
            if (seenServerUserId != userId) {
                seenServer = null
                seenServerUserId = userId
            }

            val previous = seenServer
            val server = seenApiService.fetch(previous)

            if (userService.loginState.id != userId) {
                logger.info { "User changed during seen sync, dropping result." }
                return
            }

            if (server === previous) {
                logger.info { "Seen bits unchanged on server, version ${server.version}" }
            } else {
                logger.info { "Fetched ${server.bits.size} bytes of seen bits, version ${server.version}" }
                seenService.merge(server.bits)
            }

            seenServer = server

            val missing = seenService.bitsMissingIn(server.bits)
            if (missing != null && !seenUploadRejected) {
                logger.info { "Uploading ${missing.sumOf { Integer.bitCount(it.toInt() and 0xff) }} new seen posts" }
                seenServer = seenApiService.uploadPartial(server, missing)
                logger.info { "Uploaded seen bits, now version ${seenServer?.version}, etag=${seenServer?.etag != null}" }
            }

        } catch (err: SeenApiService.VersionConflictException) {
            // the next sync fetches the new state and retries.
            logger.warn(err) { "Version conflict during update." }

        } catch (err: SeenApiService.RejectedException) {
            logger.warn(err) { "Server rejected our seen bits, stop uploading." }
            seenUploadRejected = true

        } catch (err: Exception) {
            throw err

        } finally {
            seenSyncLock.set(false)
        }
    }
}
