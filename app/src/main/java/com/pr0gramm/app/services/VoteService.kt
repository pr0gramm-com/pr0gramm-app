package com.pr0gramm.app.services

import android.annotation.TargetApi
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import com.google.common.base.Preconditions.checkArgument
import com.google.common.base.Stopwatch
import com.google.common.base.Stopwatch.createStarted
import com.google.common.base.Throwables
import com.google.common.collect.ImmutableMap
import com.google.common.io.BaseEncoding
import com.google.common.io.LittleEndianDataInputStream
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.orm.CachedVote
import com.pr0gramm.app.orm.CachedVote.Type.ITEM
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.util.AndroidUtility.checkNotMainThread
import com.pr0gramm.app.util.AndroidUtility.doInBackground
import com.pr0gramm.app.util.Databases.withTransaction
import com.pr0gramm.app.util.Holder
import com.pr0gramm.app.util.map
import com.pr0gramm.app.util.subscribeOnBackground
import gnu.trove.TCollections
import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import org.slf4j.LoggerFactory
import rx.Completable
import rx.Observable
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 */
@Singleton
class VoteService @Inject constructor(private val api: Api,
                                      private val seenService: SeenService,
                                      private val database: Holder<SQLiteDatabase>) {

    /**
     * Votes a post. This sends a request to the server, so you need to be signed in
     * to vote posts.

     * @param item The item that is to be voted
     * @param vote The vote to send to the server
     */
    fun vote(item: FeedItem, vote: Vote): Completable {
        logger.info("Voting feed item {} {}", item.id(), vote)
        Track.votePost(vote)

        doInBackground { storeVoteValueInTx(CachedVote.Type.ITEM, item.id(), vote) }
        return api.vote(null, item.id(), vote.voteValue).toCompletable()
    }

    fun vote(comment: Api.Comment, vote: Vote): Completable {
        logger.info("Voting comment {} {}", comment.id, vote)
        Track.voteComment(vote)

        doInBackground { storeVoteValueInTx(CachedVote.Type.COMMENT, comment.id, vote) }
        return api.voteComment(null, comment.id, vote.voteValue).toCompletable()
    }

    fun vote(tag: Api.Tag, vote: Vote): Completable {
        logger.info("Voting tag {} {}", tag.id, vote)
        Track.voteTag(vote)

        doInBackground { storeVoteValueInTx(CachedVote.Type.TAG, tag.id, vote) }
        return api.voteTag(null, tag.id, vote.voteValue).toCompletable()
    }

    /**
     * Gets the vote for an item.

     * @param item The item to get the vote for.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    fun getVote(item: FeedItem): Observable<Vote> {
        return Observable
                .fromCallable { CachedVote.find(database.value(), ITEM, item.id()) }
                .map<Vote> { vote -> vote.map({ it.vote }).or(Vote.NEUTRAL) }
                .subscribeOnBackground()
    }

    /**
     * Stores the vote value. This creates a transaction to prevent lost updates.

     * @param type   The type of vote to store in the vote cache.
     * @param itemId The id of the item to vote
     * @param vote   The vote to store for that item
     */
    private fun storeVoteValueInTx(type: CachedVote.Type, itemId: Long, vote: Vote) {
        checkNotMainThread()
        withTransaction(database.value()) {
            storeVoteValue(type, itemId, vote)
        }
    }

    /**
     * Stores a vote value for an item with the given id.
     * This method must be called inside of an transaction to guarantee
     * consistency.

     * @param type   The type of vote to store in the vote cache.
     * *
     * @param itemId The id of the item to vote
     * *
     * @param vote   The vote to store for that item
     */
    private fun storeVoteValue(type: CachedVote.Type, itemId: Long, vote: Vote) {
        checkNotMainThread()
        CachedVote.quickSave(database.value(), type, itemId, vote)
    }

    /**
     * Applies the given voting actions from the log.

     * @param actions The actions from the log to apply.
     */
    fun applyVoteActions(actions: String) {
        if (actions.isEmpty())
            return

        val decoded = BaseEncoding.base64().decode(actions)
        checkArgument(decoded.size % 5 == 0, "Length of vote log must be a multiple of 5")

        val actionCount = decoded.size / 5
        val actionStream = LittleEndianDataInputStream(ByteArrayInputStream(decoded))

        try {
            val watch = Stopwatch.createStarted()
            withTransaction(database.value()) {
                logger.info("Applying {} vote actions", actionCount)
                for (idx in 0..actionCount - 1) {
                    val id = actionStream.readInt().toLong()
                    val action = VOTE_ACTIONS[actionStream.readUnsignedByte()] ?: continue

                    storeVoteValue(action.type, id, action.vote)
                    if (action.type == ITEM) {
                        seenService.markAsSeen(id.toInt())
                    }
                }
            }

            logger.info("Applying vote actions took {}", watch)

        } catch (err: Exception) {
            throw Throwables.propagate(err)
        }
    }

    /**
     * Tags the given post. This methods adds the tags to the given post
     * and returns a list of tags.
     */
    fun tag(feedItem: FeedItem, tags: List<String>): Observable<List<Api.Tag>> {
        val tagString = tags.map { tag -> tag.replace(',', ' ') }.joinToString(",")
        return api.addTags(null, feedItem.id(), tagString).map { response ->
            withTransaction(database.value()) {
                // auto-apply up-vote to newly created tags
                for (tagId in response.tagIds) {
                    storeVoteValue(CachedVote.Type.TAG, tagId, Vote.UP)
                }
            }

            response.tags
        }
    }

    /**
     * Writes a comment to the given post.
     */
    fun postComment(itemId: Long, parentId: Long, comment: String): Observable<Api.NewComment> {
        return api.postComment(null, itemId, parentId, comment)
                .filter { response -> response.comments.size >= 1 }
                .doOnNext { response ->
                    // store the implicit upvote for the comment.
                    storeVoteValueInTx(CachedVote.Type.COMMENT, response.commentId, Vote.UP)
                }
    }

    fun postComment(item: FeedItem, parentId: Long, comment: String): Observable<Api.NewComment> {
        return postComment(item.id(), parentId, comment)
    }

    /**
     * Removes all votes from the vote cache.
     */
    fun clear() {
        logger.info("Removing all items from vote cache")
        CachedVote.clear(database.value())
    }

    /**
     * Gets the votes for the given comments

     * @param comments A list of comments to get the votes for.
     * *
     * @return A map containing the vote from commentId to vote
     */
    fun getCommentVotes(comments: List<Api.Comment>): Observable<TLongObjectMap<Vote>> {
        val ids = comments.map { it.id }
        return findCachedVotes(CachedVote.Type.COMMENT, ids)
    }

    fun getTagVotes(tags: List<Api.Tag>): Observable<TLongObjectMap<Vote>> {
        val ids = tags.map { it.id }
        return findCachedVotes(CachedVote.Type.TAG, ids)
    }

    private fun findCachedVotes(type: CachedVote.Type, ids: List<Long>): Observable<TLongObjectMap<Vote>> {
        if (ids.isEmpty())
            return Observable.just(NO_VOTES)

        return Observable.fromCallable {
            val watch = createStarted()
            val cachedVotes = CachedVote.find(database.value(), type, ids)

            val result = TLongObjectHashMap<Vote>()
            for (cachedVote in cachedVotes)
                result.put(cachedVote.itemId, cachedVote.vote)

            logger.info("Loading votes for {} {}s took {}", ids.size, type.name.toLowerCase(), watch)
            result
        }
    }

    private class VoteAction internal constructor(internal val type: CachedVote.Type, internal val vote: Vote)

    companion object {
        val NO_VOTES: TLongObjectMap<Vote> = TCollections.unmodifiableMap(TLongObjectHashMap<Vote>())

        private val logger = LoggerFactory.getLogger("VoteService")

        private val VOTE_ACTIONS = ImmutableMap.Builder<Int, VoteAction>()
                .put(1, VoteAction(CachedVote.Type.ITEM, Vote.DOWN))
                .put(2, VoteAction(CachedVote.Type.ITEM, Vote.NEUTRAL))
                .put(3, VoteAction(CachedVote.Type.ITEM, Vote.UP))
                .put(4, VoteAction(CachedVote.Type.COMMENT, Vote.DOWN))
                .put(5, VoteAction(CachedVote.Type.COMMENT, Vote.NEUTRAL))
                .put(6, VoteAction(CachedVote.Type.COMMENT, Vote.UP))
                .put(7, VoteAction(CachedVote.Type.TAG, Vote.DOWN))
                .put(8, VoteAction(CachedVote.Type.TAG, Vote.NEUTRAL))
                .put(9, VoteAction(CachedVote.Type.TAG, Vote.UP))
                .put(10, VoteAction(CachedVote.Type.ITEM, Vote.FAVORITE))
                .build()
    }
}
