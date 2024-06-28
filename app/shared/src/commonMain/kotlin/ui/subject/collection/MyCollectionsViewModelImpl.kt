package me.him188.ani.app.ui.subject.collection

import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.media.EpisodeCacheStatus
import me.him188.ani.app.data.media.MediaCacheManager
import me.him188.ani.app.data.models.MyCollectionsSettings
import me.him188.ani.app.data.repositories.SettingsRepository
import me.him188.ani.app.data.subject.SubjectCollection
import me.him188.ani.app.data.subject.SubjectManager
import me.him188.ani.app.session.ViewModelAuthSupport
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.tools.caching.LazyDataCache
import me.him188.ani.app.tools.caching.getCachedData
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class CollectionsByType(
    val type: UnifiedCollectionType,
    val cache: LazyDataCache<SubjectCollection>,
) {
    var isAutoRefreshing by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var pullToRefreshState: PullToRefreshState? by mutableStateOf(null)
}

@Stable
interface MyCollectionsViewModel : HasBackgroundScope, ViewModelAuthSupport {
    val subjectManager: SubjectManager

    val myCollectionsSettings: MyCollectionsSettings

    @Stable
    fun collectionsByType(type: UnifiedCollectionType): CollectionsByType

    fun requestMore(type: UnifiedCollectionType)

    @Stable
    fun cacheStatusForEpisode(subjectId: Int, episodeId: Int): Flow<EpisodeCacheStatus>

    suspend fun setCollectionType(subjectId: Int, type: UnifiedCollectionType)

    suspend fun setAllEpisodesWatched(subjectId: Int)
}

fun MyCollectionsViewModel(): MyCollectionsViewModel = MyCollectionsViewModelImpl()

@Stable
class MyCollectionsViewModelImpl : AbstractViewModel(), KoinComponent, MyCollectionsViewModel {
    override val subjectManager: SubjectManager by inject()
    private val cacheManager: MediaCacheManager by inject()
    private val settingsRepository: SettingsRepository by inject()

    val collectionsByType = subjectManager.collectionsByType.map {
        CollectionsByType(it.key, it.value)
    }
    override val myCollectionsSettings: MyCollectionsSettings by settingsRepository.uiSettings.flow
        .map { it.myCollections }
        .produceState(MyCollectionsSettings.Default)

    @Stable
    override fun collectionsByType(type: UnifiedCollectionType): CollectionsByType =
        collectionsByType.firstOrNull { it.type == type }
            ?: error("No such collection type: $type") // should not happen


    private val requestMoreTaskers = collectionsByType.map { it.type }.associateWith {
        MonoTasker(backgroundScope)
    }

    override fun requestMore(type: UnifiedCollectionType) {
        requestMoreTaskers[type]!!.run {
            if (isRunning) return
            launch {
                collectionsByType(type).cache.requestMore()
            }
        }
    }

    @Stable
    override fun cacheStatusForEpisode(subjectId: Int, episodeId: Int): Flow<EpisodeCacheStatus> =
        cacheManager.cacheStatusForEpisode(
            subjectId = subjectId,
            episodeId = episodeId,
        )

    override suspend fun setCollectionType(subjectId: Int, type: UnifiedCollectionType) =
        subjectManager.setSubjectCollectionType(subjectId, type)

    override suspend fun setAllEpisodesWatched(subjectId: Int) = subjectManager.setAllEpisodesWatched(subjectId)

    override fun init() {
        // 获取第一页, 得到数量
        // 不要太快, 测试到的如果全并行就会导致 "在看" 没有数据, 不清楚是哪边问题.
        launchInBackground {
            // 按实用顺序加载
            listOf(
                UnifiedCollectionType.DOING,
                UnifiedCollectionType.WISH,
                UnifiedCollectionType.ON_HOLD,
                UnifiedCollectionType.DONE,
                UnifiedCollectionType.DROPPED,
            ).forEach { type ->
                collectionsByType(type).cache.let { cache ->
                    if (cache.getCachedData().isEmpty()) {
                        cache.requestMore()
                    }
                }
            }
        }
    }
}

sealed class ContinueWatchingStatus {
    data object Start : ContinueWatchingStatus()

    /**
     * 还未开播
     */
    data object NotOnAir : ContinueWatchingStatus()

    /**
     * 继续看
     */
    class Continue(
        val episodeIndex: Int,
        val episodeSort: String, // "12.5"
    ) : ContinueWatchingStatus()

    /**
     * 看到了, 但是下一集还没更新
     */
    class Watched(
        val episodeIndex: Int,
        val episodeSort: String, // "12.5"
    ) : ContinueWatchingStatus()

    data object Done : ContinueWatchingStatus()
}

