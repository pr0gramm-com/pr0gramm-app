package com.pr0gramm.app.services

import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.core.content.edit
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.Settings
import com.pr0gramm.app.adapter
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.orm.Bookmark
import com.pr0gramm.app.util.*
import rx.Observable
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

/**
 */
class BookmarkService(
        private val database: Holder<SQLiteDatabase>,
        private val preferences: SharedPreferences,
        singleShotService: SingleShotService) {

    private val logger = Logger("BookmarkService")
    private val bookmarks = BehaviorSubject.create<List<Bookmark>>()

    init {
        // restore previous json
        restoreFromSerialized(preferences.getStringOrNull("Bookmarks.json") ?: "[]")

        // serialize updates back to the preferences
        bookmarks.skip(1)
                .debounce(100, TimeUnit.MILLISECONDS, Schedulers.computation())
                .subscribe { persist(it) }

        if (singleShotService.isFirstTime("BookmarkService.migrate")) {
            // migrate "old" data
            doInBackground { migrate() }
        }
    }

    private fun restoreFromSerialized(json: String) {
        val bookmarks = MoshiInstance.adapter<List<Bookmark>>().fromJson(json) ?: listOf()

        logger.debug { "Restored ${bookmarks.size} bookmarks" }
        this.bookmarks.onNext(bookmarks)
    }

    private fun persist(bookmarks: List<Bookmark>) {
        logger.debug { "Persisting ${bookmarks.size} bookmarks to storage" }

        val json = MoshiInstance.adapter<List<Bookmark>>().toJson(bookmarks)
        preferences.edit { putString("Bookmarks.json", json) }
    }

    /**
     * Creates a bookmark for the filter.

     * @param filter The filter to create a bookmark for.
     */
    fun create(filter: FeedFilter, title: String) {
        save(Bookmark(title, filter))
    }

    /**
     * Returns an observable producing "true", if the item is bookmarkable.
     * The observable produces "false" otherwise.

     * @param filter The filter that the user wants to bookmark.
     */
    fun isBookmarkable(filter: FeedFilter): Boolean {
        if (filter.isBasic)
            return false

        if (filter.likes != null)
            return false

        return byFilter(filter) == null
    }

    /**
     * Observes change to the bookmarks
     */
    fun observe(): Observable<List<Bookmark>> {
        return bookmarks
    }

    /**
     * Deletes the given bookmark if it exists.
     */
    fun delete(bookmark: Bookmark) {
        synchronized(bookmarks) {
            // remove all matching bookmarks
            val newValues = bookmarks.value.filter { it != bookmark }
            bookmarks.onNext(newValues)
        }
    }

    /**
     * Returns a bookmark that has a filter equal to the queried one.
     */
    fun byFilter(filter: FeedFilter): Bookmark? {
        return bookmarks.value.firstOrNull { bookmark -> filter == bookmark.asFeedFilter() }
    }

    fun byTitle(title: String): Bookmark? {
        return bookmarks.value.firstOrNull { it.title == title }
    }

    /**
     * Save a bookmark to the database.
     */
    fun save(bookmark: Bookmark) {
        synchronized(bookmarks) {
            val values = bookmarks.value.toMutableList()

            // replace the first bookmark that has the same title
            val idx = values.indexOfFirst { it.title == bookmark.title }
            if (idx >= 0) {
                values[idx] = bookmark
            } else {
                values.add(0, bookmark)
            }

            // dispatch changes
            bookmarks.onNext(values)
        }
    }

    /**
     * Query a list of all bookmarks directly from the database.
     */
    private suspend fun migrate() {
        if (Settings.get().legacyShowCategoryText) {
            // disable this for the next time
            Settings.get().edit {
                putBoolean("pref_show_category_text", false)
            }

            val filter = FeedFilter().withFeedType(FeedType.PROMOTED).withTags("!'text'")
            save(Bookmark("Text in Top", filter))
        }

        try {
            // get all values
            val query = "SELECT title, filter_tags, filter_username, filter_feed_type FROM bookmark ORDER BY title DESC"
            val bookmarks = database.get().rawQuery(query, null).use { cursor ->
                cursor.mapToList {
                    Bookmark(title = getString(0),
                            filterTags = getString(1),
                            filterUsername = getString(2),
                            filterFeedType = getString(3))
                }
            }

            // and delete all values from the table
            database.get().execSQL("DELETE FROM bookmark")

            // save all those bookmarks
            logger.info { "Migrating ${bookmarks.size} bookmarks to new storage" }
            bookmarks.distinctBy { it.title }.forEach { save(it) }

        } catch (err: SQLiteException) {
            logger.warn { "Could not migrate: $err" }
        }

        // check what else we need to change...
        synchronized(bookmarks) {
            val fixed = bookmarks.value.map { bookmark ->
                // migrate some values...
                val tags = bookmark.filterTags
                        ?.replace("webm", "video")
                        ?.replace("hat text", "text")
                        ?.replace("^'original content'", "! 'original content'")

                bookmark.copy(filterTags = tags)
            }

            logger.debug { "Fixing ${fixed.size} bookmarks" }
            bookmarks.onNext(fixed)
        }
    }
}