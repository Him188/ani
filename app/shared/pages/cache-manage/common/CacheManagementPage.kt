package me.him188.ani.app.pages.cache.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.data.media.MediaCacheManager
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.TopAppBarGoBackButton
import me.him188.ani.app.ui.foundation.rememberViewModel
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.core.cache.MediaCache
import me.him188.ani.datasources.core.cache.MediaCacheStorage
import me.him188.ani.datasources.core.cache.MediaStats
import me.him188.ani.datasources.core.cache.emptyMediaStats
import me.him188.ani.datasources.core.cache.sum
import me.him188.ani.utils.coroutines.runningList
import moe.tlaster.precompose.flow.collectAsStateWithLifecycle
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
interface CacheManagementPageViewModel {
    val overallStats: MediaStats
    val storages: List<MediaCacheStorageState>?
    val accumulatedList: List<MediaCachePresentation>?
}

@Stable
class CacheManagementPageViewModelImpl : CacheManagementPageViewModel,
    AbstractViewModel(),
    KoinComponent {

    private val cacheManager: MediaCacheManager by inject()
    private val storagesFlow = cacheManager.enabledStorages.map { list ->
        list.map { storage ->
            MediaCacheStorageState(storage, backgroundScope)
        }
    }
    override val overallStats: MediaStats by cacheManager.enabledStorages.map { list ->
        list.map { it.stats }.sum()
    }.produceState(emptyMediaStats())

    override val storages by storagesFlow.produceState(emptyList())

    override val accumulatedList: List<MediaCachePresentation>? by kotlin.run {
        val mediaCacheListFromStorages = cacheManager.storages.map { storageFlow ->
            storageFlow.flatMapLatest { storage ->
                if (storage == null) {
                    return@flatMapLatest emptyFlow()
                }
                storage.listFlow
            }
        }

        combine(mediaCacheListFromStorages) { lists ->
            lists.asSequence()
                .flatten()
                .map {
                    MediaCachePresentation(it, null)
                }
                .toList()
        }.produceState(null)
    }
}

@Stable
class MediaCacheStorageState(
    private val storage: MediaCacheStorage,
    private val scope: CoroutineScope,
) : KoinComponent {
    private val items = mutableMapOf<MediaCache, MediaCachePresentation>()

    val mediaSourceId = storage.mediaSourceId

    val stats get() = storage.stats

    /**
     * A flow that subscribes on all the caches in the storage.
     */
    val list: Flow<List<MediaCachePresentation>> = storage.listFlow.flatMapLatest { list ->
        mapCacheToItem(list)
    }

    private fun mapCacheToItem(list: List<MediaCache>): Flow<List<MediaCachePresentation>> {
        return list.asFlow().map { cache ->
            items.getOrPut(cache) {
                MediaCachePresentation(cache, null)
            }
        }.also {
            items.keys.removeAll { key -> key !in list }
        }.runningList()
    }

    fun delete(item: MediaCachePresentation): Boolean {
        if (items.remove(item.cache) == null) {
            return false
        }
        scope.launch {
            storage.delete(item.cache)
        }
        return true
    }
}

@Composable
fun CacheManagementPage(
    modifier: Modifier = Modifier,
    vm: CacheManagementPageViewModel = rememberViewModel { CacheManagementPageViewModelImpl() },
) {
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text("缓存管理") },
                navigationIcon = {
                    TopAppBarGoBackButton()
                },
            )
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            val state = rememberLazyListState()

            StorageOverallStats(
                vm.overallStats,
                Modifier.fillMaxWidth()
                    .then(if (state.canScrollBackward) Modifier.shadow(2.dp, clip = false) else Modifier)
            )

            val storages = vm.storages
            if (storages?.isEmpty() == true) {
                Box(Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("未启用任何缓存服务, 请在设置中至少启用一个", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                val list = vm.accumulatedList
                if (list?.isEmpty() == true) {
                    Box(Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("还未缓存任何内容", style = MaterialTheme.typography.titleMedium)
                    }
                }
                StorageManagerView(
                    list ?: emptyList(),
                    onDelete = { item ->
                        vm.storages?.firstOrNull {
                            it.delete(item)
                        }
                    },
                    Modifier.padding(horizontal = 16.dp).padding(top = 2.dp).fillMaxWidth(),
                    state = state,
                )
            }
        }
    }
}

@Composable
fun StorageOverallStats(
    stats: MediaStats,
    modifier: Modifier = Modifier,
) {
    Surface(modifier) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Stat(
                title = {
                    Icon(Icons.Rounded.Upload, null)
                    Text("总上传", style = MaterialTheme.typography.titleMedium)
                },
                speedText = {
                    val speed by stats.uploadRate.collectAsStateWithLifecycle(FileSize.Unspecified)
                    Text(renderSpeed(speed))
                },
                totalText = {
                    val speed by stats.uploaded.collectAsStateWithLifecycle(FileSize.Unspecified)
                    Text(renderFileSize(speed))
                }
            )

            Stat(
                title = {
                    Icon(Icons.Rounded.Download, null)
                    Text("总下载", style = MaterialTheme.typography.titleMedium)
                },
                speedText = {
                    val speed by stats.downloadRate.collectAsStateWithLifecycle(FileSize.Unspecified)
                    Text(renderSpeed(speed))
                },
                totalText = {
                    val speed by stats.downloaded.collectAsStateWithLifecycle(FileSize.Unspecified)
                    Text(renderFileSize(speed))
                },
            )
        }
    }
}

@Composable
private fun Stat(
    title: @Composable () -> Unit,
    speedText: @Composable () -> Unit,
    totalText: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            title()
        }

        Row(
            Modifier.weight(1f).padding(start = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(textAlign = TextAlign.Center)) {
                Row(
                    Modifier.widthIn(min = 100.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Speed, null)
                    speedText()
                }
                Row(
                    Modifier.widthIn(min = 100.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.DownloadDone, null)
                    totalText()
                }
            }
        }
    }
}

// Management for a single storage
@Composable
fun StorageManagerView(
    list: List<MediaCachePresentation>,
    onDelete: (MediaCachePresentation) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        modifier,
        state = state,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { }

        items(list, key = { it.cache.cacheId }) { item ->
            CacheItemView(item, onDelete, { item.mediaSourceId })
        }

        item { }
    }
}
