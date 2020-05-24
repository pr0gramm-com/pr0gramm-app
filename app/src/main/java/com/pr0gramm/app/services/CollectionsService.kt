package com.pr0gramm.app.services

import androidx.lifecycle.MutableLiveData
import com.pr0gramm.app.Logger
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.db.CollectionItemQueries
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.ui.base.launchIgnoreErrors
import com.pr0gramm.app.ui.base.withBackgroundContext
import com.pr0gramm.app.util.asFlow
import com.pr0gramm.app.util.readOnly
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.*

data class PostCollection(
        val id: Long,
        val key: String,
        val title: String,
        val uniqueTitle: String,
        val isPublic: Boolean,
        val isDefault: Boolean) {

    companion object {
        fun fromApi(input: List<Api.Collection>): List<PostCollection> {
            return input.map { apiCollection ->
                val titleIsUnique = input.count { it.name == apiCollection.name } == 1
                PostCollection(
                        id = apiCollection.id,
                        key = apiCollection.keyword,
                        title = apiCollection.name,
                        uniqueTitle = if (titleIsUnique) apiCollection.name else "${apiCollection.name} (${apiCollection.keyword})",
                        isPublic = apiCollection.isPublic,
                        isDefault = apiCollection.isDefault
                )
            }
        }
    }
}


class CollectionsService(private val api: Api, private val userService: UserService) {
    private val logger = Logger("CollectionsService")
    private val _collections: MutableLiveData<List<PostCollection>> = MutableLiveData()

    val collections = _collections.readOnly()

    val defaultCollection: PostCollection?
        get() = collections.value?.firstOrNull(PostCollection::isDefault)

    init {
        AsyncScope.launchIgnoreErrors {
            userService.loginStates.asFlow().collect { loginState ->
                logger.debug { "Update list of collections after loginState changed: user=${loginState.name}" }

                if (loginState.authorized) {
                    launchIgnoreErrors { refresh() }
                } else {
                    logger.info { "Reset all collections." }
                    _collections.postValue(listOf())
                }
            }
        }
    }

    fun isValidNameForNewCollection(name: String): Boolean {
        val existing = _collections.value.orEmpty().map { it.title.toLowerCase(Locale.getDefault()) }
        return name.length > 3 && name !in existing
    }

    suspend fun create(name: String): Long {
        val response = api.collectionsCreate(null, name)
        _collections.postValue(PostCollection.fromApi(response.collections))
        return response.collectionId
    }

    suspend fun refresh() {
        val collections = PostCollection.fromApi(api.collectionsGet().collections)
        logger.info { "Found ${collections.size} collections" }

        // take default collection first, sort the rest by name
        val (defaultCollections, otherCollections) = collections.partition { it.isDefault }
        val sortedCollections = defaultCollections + otherCollections.sortedBy { it.uniqueTitle }

        _collections.postValue(sortedCollections)
    }

    fun byId(collectionId: Long): PostCollection? {
        return _collections.value?.firstOrNull { it.id == collectionId }
    }
}

class CollectionItemsService(private val api: Api, private val db: CollectionItemQueries) {
    private val logger = Logger("CollectionItemsService")

    private val lock = Any()
    private var cache: MutableMap<Long, MutableSet<Long>> = hashMapOf()

    val updateTime = MutableStateFlow<Long>(0L)

    init {
        AsyncScope.launch {
            observeDatabaseUpdates()
        }
    }

    fun clear() {
        logger.time("Remove all entries from database") {
            db.clear()
        }
    }

    suspend fun removeFromCollection(collectionId: Long, itemId: Long) {
        logger.info { "Removing item $itemId from collection $collectionId" }

        val response = api.collectionsRemove(null, collectionId, itemId)

        if (response.error == null) {
            // delete on server side was okay, remove locally
            withBackgroundContext {
                db.remove(itemId, collectionId)
            }
        }
    }

    suspend fun addToCollection(itemId: Long, collectionId: Long?): Result {
        logger.info { "Adding item $itemId to collection $collectionId" }

        val response = api.collectionsAdd(null, collectionId, itemId)

        val result = when (val error = response.error) {
            null -> Result.ItemAdded(response.collectionId)
            "collectionNotFound" -> Result.CollectionNotFound
            else -> Result.UnknownError(error)
        }

        if (result is Result.ItemAdded) {
            // mimic adding to collection locally
            withBackgroundContext {
                db.add(itemId, result.collectionId)
            }
        }

        return result
    }

    fun isItemInAnyCollection(itemId: Long): Boolean {
        synchronized(lock) {
            return cache.values.any { collection -> itemId in collection }
        }
    }

    fun collectionsContaining(itemId: Long): List<Long> {
        synchronized(lock) {
            return cache.mapNotNull { (collectionId, items) -> collectionId.takeIf { itemId in items } }
        }
    }

    private suspend fun observeDatabaseUpdates() {
        db.all().asFlow().mapToList().collect { collectionItems ->
            synchronized(lock) {
                logger.time("Updating memory cache with ${collectionItems.size} entries") {
                    cache = collectionItems
                            .groupBy(keySelector = { it.collectionId }, valueTransform = { it.itemId })
                            .mapValuesTo(mutableMapOf()) { (_, itemIds) -> itemIds.toHashSet() }
                }
            }

            // publish update to listeners
            updateTime.value = System.currentTimeMillis()
        }
    }

    sealed class Result {
        class ItemAdded(val collectionId: Long) : Result()
        object CollectionNotFound : Result()
        class UnknownError(errorCode: String) : Result()
    }
}