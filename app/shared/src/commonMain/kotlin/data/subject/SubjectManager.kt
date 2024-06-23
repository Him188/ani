package me.him188.ani.app.data.subject

import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import me.him188.ani.app.data.media.EpisodeCacheStatus
import me.him188.ani.app.data.media.MediaCacheManager
import me.him188.ani.app.data.repositories.BangumiEpisodeRepository
import me.him188.ani.app.data.repositories.BangumiSubjectRepository
import me.him188.ani.app.data.repositories.setSubjectCollectionTypeOrDelete
import me.him188.ani.app.data.repositories.toEpisodeCollection
import me.him188.ani.app.data.repositories.toEpisodeInfo
import me.him188.ani.app.data.repositories.toSubjectCollectionItem
import me.him188.ani.app.persistent.asDataStoreSerializer
import me.him188.ani.app.persistent.dataStores
import me.him188.ani.app.platform.Context
import me.him188.ani.app.session.SessionManager
import me.him188.ani.app.tools.caching.ContentPolicy
import me.him188.ani.app.tools.caching.LazyDataCache
import me.him188.ani.app.tools.caching.LazyDataCacheSave
import me.him188.ani.app.tools.caching.MutationContext.replaceAll
import me.him188.ani.app.tools.caching.addFirst
import me.him188.ani.app.tools.caching.data
import me.him188.ani.app.tools.caching.dataTransaction
import me.him188.ani.app.tools.caching.getCachedData
import me.him188.ani.app.tools.caching.mutate
import me.him188.ani.app.tools.caching.removeFirstOrNull
import me.him188.ani.app.tools.caching.setEach
import me.him188.ani.app.ui.subject.collection.progress.EpisodeProgressItem
import me.him188.ani.datasources.api.paging.map
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.processing.toEpisodeCollectionType
import me.him188.ani.datasources.bangumi.processing.toSubjectCollectionType
import me.him188.ani.utils.coroutines.runUntilSuccess
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openapitools.client.models.EpType
import org.openapitools.client.models.EpisodeCollectionType
import org.openapitools.client.models.SubjectType
import org.openapitools.client.models.UserSubjectCollection

/**
 * 管理收藏条目以及它们的缓存.
 */
abstract class SubjectManager {
    ///////////////////////////////////////////////////////////////////////////
    // Subject collections
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 本地 subject 缓存
     */
    abstract val collectionsByType: Map<UnifiedCollectionType, LazyDataCache<SubjectCollection>>

    /**
     * 获取所有收藏的条目列表
     */
    fun subjectCollectionsFlow(contentPolicy: ContentPolicy): Flow<List<SubjectCollection>> {
        return combine(collectionsByType.values.map { it.data(contentPolicy) }) { collections ->
            collections.asSequence().flatten().toList()
        }
    }

    /**
     * 获取某一个收藏条目 flow.
     * @see subjectProgressFlow
     */
    // TODO: 如果 subjectId 没收藏, 这个函数的 flow 就会为空. 需要 (根据 policy) 实现为当未收藏时, 就向服务器请求单个 subjectId 的状态.
    //  这目前不是问题, 但在修改番剧详情页时可能会有问题.
    fun subjectCollectionFlow(
        subjectId: Int,
        contentPolicy: ContentPolicy
    ): Flow<SubjectCollection?> =
        combine(collectionsByType.values.map { it.data(contentPolicy) }) { collections ->
            collections.asSequence().flatten().firstOrNull { it.subjectId == subjectId }
        }

    /**
     * 获取缓存的收藏条目. 注意, 这不会请求网络. 若缓存中不包含则返回 `null`.
     */
    suspend fun findCachedSubjectCollection(subjectId: Int): SubjectCollection? {
        return collectionsByType.values.map { it.cachedDataFlow.first() }.asSequence().flatten()
            .firstOrNull { it.subjectId == subjectId }
    }

    /**
     * 从缓存中获取条目, 若没有则从网络获取.
     */
    abstract suspend fun getSubjectInfo(subjectId: Int): SubjectInfo // TODO: replace with  subjectInfoFlow


    fun subjectInfoFlow(subjectId: Flow<Int>): Flow<SubjectInfo> {
        return subjectId.mapLatest { getSubjectInfo(it) }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Subject progress
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 获取指定条目的观看进度 flow. 进度还会包含该剧集的缓存状态 [EpisodeProgressItem.cacheStatus].
     */
    @Stable
    abstract fun subjectProgressFlow(
        subjectId: Int,
        contentPolicy: ContentPolicy
    ): Flow<List<EpisodeProgressItem>>

    /**
     * 获取用户该条目的收藏情况, 以及该条目的信息.
     *
     * 如果用户未收藏该条目, 则返回空列表.
     */
    abstract fun episodeCollectionsFlow(subjectId: Int): Flow<List<EpisodeCollection>>

    /**
     * 获取指定条目下指定剧集的收藏情况 flow.
     */
    abstract fun episodeCollectionFlow(
        subjectId: Int,
        episodeId: Int,
        contentPolicy: ContentPolicy
    ): Flow<EpisodeCollection>

    abstract suspend fun setSubjectCollectionType(subjectId: Int, type: UnifiedCollectionType)

    abstract suspend fun setAllEpisodesWatched(subjectId: Int)

    abstract suspend fun setEpisodeCollectionType(subjectId: Int, episodeId: Int, collectionType: UnifiedCollectionType)

    ///////////////////////////////////////////////////////////////////////////
    // Get info
    ///////////////////////////////////////////////////////////////////////////
    // TODO: extract EpisodeRepository  (remote/mixed(?))

    /**
     * 从缓存中获取剧集, 若没有则从网络获取. 在获取失败时将会抛出异常.
     */
    abstract suspend fun getEpisodeInfo(episodeId: Int): EpisodeInfo // TODO: replace with  episodeInfoFlow

    /**
     * 获取一个 [EpisodeInfo] flow. 将优先从缓存中获取, 若没有则从网络获取.
     *
     * 返回的 flow 只会 emit 唯一一个元素, 或者抛出异常.
     */
    fun episodeInfoFlow(episodeId: Flow<Int>): Flow<EpisodeInfo> = episodeId.mapLatest { getEpisodeInfo(it) }
}

/**
 * 获取一个 [SubjectInfo] flow. 将优先从缓存中获取, 若没有则从网络获取.
 *
 * 返回的 flow 只会 emit 唯一一个元素, 或者抛出异常.
 */
fun SubjectManager.subjectInfoFlow(subjectId: Int): Flow<SubjectInfo> = subjectInfoFlow(flowOf(subjectId))

/**
 * 获取一个 [EpisodeInfo] flow. 将优先从缓存中获取, 若没有则从网络获取.
 *
 * 返回的 flow 只会 emit 唯一一个元素, 或者抛出异常.
 */
fun SubjectManager.episodeInfoFlow(episodeId: Int): Flow<EpisodeInfo> = episodeInfoFlow(flowOf(episodeId))

/**
 * 获取指定条目是否已经完结. 不是用户是否看完, 只要条目本身完结了就算.
 */
fun SubjectManager.subjectCompletedFlow(subjectId: Int): Flow<Boolean> {
    return episodeCollectionsFlow(subjectId).map { epCollection ->
        EpisodeCollections.isSubjectCompleted(epCollection.map { it.episode })
    }
}

suspend inline fun SubjectManager.setEpisodeWatched(subjectId: Int, episodeId: Int, watched: Boolean) =
    setEpisodeCollectionType(
        subjectId,
        episodeId,
        if (watched) UnifiedCollectionType.DONE else UnifiedCollectionType.WISH,
    )

class SubjectManagerImpl(
    context: Context
) : KoinComponent, SubjectManager() {
    private val bangumiSubjectRepository: BangumiSubjectRepository by inject()
    private val bangumiEpisodeRepository: BangumiEpisodeRepository by inject()

    private val sessionManager: SessionManager by inject()
    private val cacheManager: MediaCacheManager by inject()

    override val collectionsByType: Map<UnifiedCollectionType, LazyDataCache<SubjectCollection>> =
        UnifiedCollectionType.entries.associateWith { type ->
            LazyDataCache(
                createSource = {
                    val username = sessionManager.username.filterNotNull().first()
                    bangumiSubjectRepository.getSubjectCollections(
                        username,
                        subjectType = SubjectType.Anime,
                        subjectCollectionType = type.toSubjectCollectionType(),
                    ).map {
                        it.convertToItem()
                    }
                },
                getKey = { it.subjectId },
                debugName = "collectionsByType-${type.name}",
                persistentStore = DataStoreFactory.create(
                    LazyDataCacheSave.serializer(SubjectCollection.serializer())
                        .asDataStoreSerializer(LazyDataCacheSave.empty()),
                    ReplaceFileCorruptionHandler { LazyDataCacheSave.empty() },
                    migrations = listOf(),
                    produceFile = {
                        context.dataStores.resolveDataStoreFile("collectionsByType-${type.name}")
                    },
                ),
            )
        }

    @Stable
    override fun subjectProgressFlow(
        subjectId: Int,
        contentPolicy: ContentPolicy
    ): Flow<List<EpisodeProgressItem>> = subjectCollectionFlow(subjectId, contentPolicy)
        .map { it?.episodes ?: emptyList() }
        .distinctUntilChanged()
        .flatMapLatest { episodes ->
            combine(
                episodes.map { episode ->
                    cacheManager.cacheStatusForEpisode(
                        subjectId = subjectId,
                        episodeId = episode.episode.id,
                    ).onStart {
                        emit(EpisodeCacheStatus.NotCached)
                    }.map { cacheStatus ->
                        EpisodeProgressItem(
                            episodeId = episode.episode.id,
                            episodeSort = episode.episode.sort.toString(),
                            watchStatus = episode.type,
                            isOnAir = episode.episode.isKnownOnAir,
                            cacheStatus = cacheStatus,
                        )
                    }
                },
            ) {
                it.toList()
            }
        }
        .flowOn(Dispatchers.Default)

    override fun episodeCollectionsFlow(subjectId: Int): Flow<List<EpisodeCollection>> {
        return flow {
            // 对于已经收藏的条目, 使用缓存
            findCachedSubjectCollection(subjectId)?.episodes?.let {
                emit(it)
            } ?: run {
                // 这是网络请求, 无网情况下会一直失败
                emit(
                    bangumiEpisodeRepository.getSubjectEpisodeCollection(subjectId, EpType.MainStory)
                        .map {
                            it.toEpisodeCollection()
                        }
                        .toList(),
                )
            }
        }
    }

    override suspend fun getSubjectInfo(subjectId: Int): SubjectInfo {
        findCachedSubjectCollection(subjectId)?.info?.let { return it }
        return runUntilSuccess {
            // TODO: we should unify how to compute display name from subject 
            bangumiSubjectRepository.getSubject(subjectId)?.createSubjectInfo() ?: error("Failed to get subject")
        }
    }

    override suspend fun getEpisodeInfo(episodeId: Int): EpisodeInfo {
        collectionsByType.values.map { it.getCachedData() }.asSequence().flatten()
            .flatMap { it.episodes }
            .map { it.episode }
            .firstOrNull { it.id == episodeId }
            ?.let { return it }

        return runUntilSuccess {
            bangumiEpisodeRepository.getEpisodeById(episodeId)?.toEpisodeInfo() ?: error("Failed to get episode")
        }
    }

    override fun episodeCollectionFlow(
        subjectId: Int,
        episodeId: Int,
        contentPolicy: ContentPolicy
    ): Flow<EpisodeCollection> {
        return subjectCollectionFlow(subjectId, contentPolicy)
            .transform { subject ->
                if (subject == null) {
                    emit(
                        me.him188.ani.utils.coroutines.runUntilSuccess {
                            bangumiEpisodeRepository.getEpisodeCollection(
                                episodeId,
                            )?.toEpisodeCollection() ?: error("Failed to get episode collection")
                        },
                    )
                } else {
                    emitAll(
                        subject.episodes.filter { it.episode.id == episodeId }.asFlow(),
                    )
                }
            }
            .flowOn(Dispatchers.Default)
    }

    override suspend fun setSubjectCollectionType(subjectId: Int, type: UnifiedCollectionType) {
        val from = findSubjectCacheById(subjectId) ?: return // not found
        val target = collectionsByType[type] ?: return

        dataTransaction(from, target) { (f, t) ->
            val old = f.removeFirstOrNull { it.subjectId == subjectId } ?: return@dataTransaction
            t.addFirst(old)
        }

        bangumiSubjectRepository.setSubjectCollectionTypeOrDelete(subjectId, type.toSubjectCollectionType())
    }

    /**
     * Finds the cache that contains the subject.
     */
    private suspend fun findSubjectCacheById(subjectId: Int) =
        collectionsByType.values.firstOrNull { list -> list.getCachedData().any { it.subjectId == subjectId } }

    override suspend fun setAllEpisodesWatched(subjectId: Int) {
        val cache = findSubjectCacheById(subjectId) ?: return
        cache.mutate {
            setEach({ it.subjectId == subjectId }) {
                copy(
                    episodes = episodes.map { episode ->
                        episode.copy(collectionType = UnifiedCollectionType.DONE)
                    },
                )
            }
        }

        val ids = bangumiEpisodeRepository.getEpisodesBySubjectId(subjectId, EpType.MainStory).map { it.id }.toList()
        bangumiEpisodeRepository.setEpisodeCollection(
            subjectId,
            ids,
            EpisodeCollectionType.WATCHED,
        )
    }

    override suspend fun setEpisodeCollectionType(
        subjectId: Int,
        episodeId: Int,
        collectionType: UnifiedCollectionType
    ) {
        bangumiEpisodeRepository.setEpisodeCollection(
            subjectId,
            listOf(episodeId),
            collectionType.toEpisodeCollectionType(),
        )

        val cache = findSubjectCacheById(subjectId) ?: return

        cache.mutate {
            setEach({ it.subjectId == subjectId }) {
                copy(
                    episodes = episodes.replaceAll({ it.episode.id == episodeId }) {
                        copy(collectionType = collectionType)
                    },
                )
            }
        }
    }

    private suspend fun UserSubjectCollection.convertToItem() = coroutineScope {
        val subject = async {
            runUntilSuccess { bangumiSubjectRepository.getSubject(subjectId) ?: error("Failed to get subject") }
        }
        val eps = runUntilSuccess {
            bangumiEpisodeRepository.getSubjectEpisodeCollection(subjectId, EpType.MainStory)
        }.toList()

        toSubjectCollectionItem(subject.await(), eps)
    }
}


