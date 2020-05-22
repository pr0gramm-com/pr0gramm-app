package com.pr0gramm.app.feed

import com.google.android.gms.common.util.Strings.emptyToNull
import com.pr0gramm.app.parcel.Freezable
import com.pr0gramm.app.parcel.Unfreezable
import com.pr0gramm.app.parcel.parcelableCreator
import java.util.*

/**
 */
class FeedFilter : Freezable {
    var feedType: FeedType = FeedType.PROMOTED
        private set

    var tags: String? = null
        private set

    var collection: String? = null
        private set

    var username: String? = null
        private set

    /**
     * Checks if this filter is a basic filter. A filter is basic, if
     * it has no tag/likes or username-filter.
     */
    val isBasic: Boolean
        get() = equals(basic())

    /**
     * Returns a copy of this filter with all optional constraints removed.
     * This removes tags, username-filter and the likes.
     */
    fun basic(): FeedFilter {
        return copy {
            tags = null
            username = null
        }
    }

    /**
     * Returns a copy of this filter that filters by the given feed type.
     */
    fun withFeedType(type: FeedType): FeedFilter {
        return copy {
            feedType = type
        }
    }

    /**
     * Returns a copy of this filter that will filter by the given tag
     */
    fun withTags(tags: String): FeedFilter {
        val copy = basic()
        copy.tags = normalizeString(tags)
        return normalize(copy)
    }

    /**
     * Returns a copy of this filter that filters by the given username
     */
    fun withUser(username: String): FeedFilter {
        val copy = basic()
        copy.username = normalizeString(username)
        return normalize(copy)
    }

    fun withCollection(owner: String, collectionKey: String): FeedFilter {
        val copy = basic()
        copy.username = normalizeString(owner)
        copy.collection = normalizeString(collectionKey)
        return normalize(copy)
    }

    fun withTagsNoReset(tags: String): FeedFilter {
        val copy = basic()

        if (collection != null) {
            copy.username = username
            copy.collection = collection
        }

        copy.tags = normalizeString(tags)
        return normalize(copy)
    }

    /**
     * Normalizes the given string by trimming it and setting empty strings to null.
     */
    private fun normalizeString(value: String): String? = emptyToNull(value.trim())

    private fun copy(fn: FeedFilter.() -> Unit): FeedFilter {
        val copy = FeedFilter()
        copy.feedType = feedType
        copy.tags = tags
        copy.collection = collection
        copy.username = username
        copy.fn()
        return normalize(copy)
    }

    override fun hashCode(): Int {
        return Objects.hash(feedType, tags, collection, username)
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is FeedFilter
                && feedType === other.feedType
                && tags == other.tags
                && username == other.username
                && collection == other.collection)
    }

    override fun freeze(sink: Freezable.Sink) = with(sink) {
        writeInt(feedType.ordinal)
        writeString(tags ?: "")
        writeString(username ?: "")
        writeString(collection ?: "")
    }

    companion object : Unfreezable<FeedFilter> {
        private val values: Array<FeedType> = FeedType.values()

        @JvmField
        val CREATOR = parcelableCreator()

        override fun unfreeze(source: Freezable.Source): FeedFilter {
            return FeedFilter().apply {
                this.feedType = values[source.readInt()]
                this.tags = source.readString().ifEmpty { null }
                this.username = source.readString().ifEmpty { null }
                this.collection = source.readString().ifEmpty { null }
            }
        }

        private fun normalize(filter: FeedFilter): FeedFilter {
            // if it is a non searchable filter, we need to switch to some searchable category.
            if (!filter.feedType.searchable && !filter.isBasic) {
                return filter.withFeedType(FeedType.NEW)
            }

            if (filter.collection != null && filter.username == null) {
                return filter.copy { collection = null }
            }

            return filter
        }
    }
}

object Tags {
    fun join(lhs: String, rhs: String?): String {
        if (rhs.isNullOrBlank()) {
            return lhs
        }

        val lhsTrimmed = lhs.trimStart { ch -> ch.isWhitespace() || ch == '!' || ch == '?' }
        val rhsTrimmed = rhs.trimStart { ch -> ch.isWhitespace() || ch == '!' || ch == '?' }

        val extendedQuery = isExtendedQuery(lhs) || isExtendedQuery(rhs)
        if (extendedQuery) {
            return "! ($rhsTrimmed) ($lhsTrimmed)"
        } else {
            return "$lhsTrimmed $rhsTrimmed"
        }
    }

    private fun isExtendedQuery(query: String): Boolean {
        val trimmed = query.trimStart()
        return trimmed.startsWith('?') || trimmed.startsWith('!')
    }
}
