/*
 * Ani
 * Copyright (C) 2022-2024 Him188
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.him188.ani.app.ui.subject.collection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.data.subject.SubjectCollection
import me.him188.ani.app.data.subject.episode
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.platform.Platform
import me.him188.ani.app.platform.currentPlatform
import me.him188.ani.app.platform.isDesktop
import me.him188.ani.app.platform.isMobile
import me.him188.ani.app.session.isLoggedIn
import me.him188.ani.app.tools.caching.LazyDataCache
import me.him188.ani.app.tools.caching.RefreshOrderPolicy
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.ui.foundation.effects.OnLifecycleEvent
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.foundation.layout.isShowLandscapeUI
import me.him188.ani.app.ui.foundation.pagerTabIndicatorOffset
import me.him188.ani.app.ui.foundation.rememberViewModel
import me.him188.ani.app.ui.profile.UnauthorizedTips
import me.him188.ani.app.ui.subject.collection.progress.EpisodeProgressDialog
import me.him188.ani.app.ui.subject.collection.progress.rememberEpisodeProgressState
import me.him188.ani.app.ui.update.TextButtonUpdateLogo
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import moe.tlaster.precompose.flow.collectAsStateWithLifecycle
import moe.tlaster.precompose.lifecycle.Lifecycle
import kotlin.time.Duration.Companion.minutes


// 有顺序, https://github.com/Him188/ani/issues/73
@Stable
val COLLECTION_TABS_SORTED = listOf(
    UnifiedCollectionType.DROPPED,
    UnifiedCollectionType.WISH,
    UnifiedCollectionType.DOING,
    UnifiedCollectionType.ON_HOLD,
    UnifiedCollectionType.DONE,
)


/**
 * My collections
 */
@Composable
fun CollectionPage(
    onClickCaches: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val vm = rememberViewModel { MyCollectionsViewModel() }

    val pagerState =
        rememberPagerState(initialPage = COLLECTION_TABS_SORTED.size / 2) { COLLECTION_TABS_SORTED.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = { Text("我的追番") },
                    modifier = Modifier.alpha(0.97f),
                    actions = {
                        if (!isShowLandscapeUI()) {
                            TextButtonUpdateLogo()

                            IconButton(onClickCaches) {
                                Icon(Icons.Rounded.Download, "缓存管理")
                            }
                        }

                        if (currentPlatform.isDesktop()) {
                            // PC 无法下拉刷新
                            val refreshTasker = rememberUiMonoTasker()
                            IconButton(
                                {
                                    val type = COLLECTION_TABS_SORTED[pagerState.currentPage]
                                    val collection = vm.collectionsByType(type)
                                    collection.isAutoRefreshing = false
                                    collection.isRefreshing = true
//                                val cache = collection.cache
//                                
//                                refreshTasker.launch {
//                                    cache.refresh(RefreshOrderPolicy.REPLACE)
//                                }
                                },
                            ) {
                                Icon(Icons.Rounded.Refresh, null)
                            }
                        }
                    },
                )

                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    indicator = @Composable { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.pagerTabIndicatorOffset(pagerState, tabPositions),
                        )
                    },
                    contentColor = TabRowDefaults.secondaryContentColor,
                    containerColor = TabRowDefaults.secondaryContainerColor,
                    modifier = Modifier.fillMaxWidth().alpha(0.97f),
                ) {
                    COLLECTION_TABS_SORTED.forEachIndexed { index, collectionType ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                val type = COLLECTION_TABS_SORTED[index]
                                val cache = vm.collectionsByType(type).cache
                                val size by cache.totalSize.collectAsStateWithLifecycle(null)
                                if (size == null) {
                                    Text(text = collectionType.displayText())
                                } else {
                                    Text(
                                        text = remember(collectionType, size) {
                                            collectionType.displayText() + " " + size
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        },

        ) { topBarPaddings ->
        val isLoggedIn by isLoggedIn()

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = Platform.currentPlatform.isMobile(),
        ) { index ->
            val type = COLLECTION_TABS_SORTED[index]
            val collection = vm.collectionsByType(type)

            val pullToRefreshState = rememberPullToRefreshState()
            LaunchedEffect(true) {
                snapshotFlow { collection.isRefreshing }.collectLatest {
                    if (!it) return@collectLatest

                    try {
                        val policy = if (collection.isAutoRefreshing) {
                            RefreshOrderPolicy.KEEP_ORDER_APPEND_LAST
                        } else {
                            RefreshOrderPolicy.REPLACE
                        }
                        collection.isAutoRefreshing = false
                        collection.cache.refresh(policy)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                    } finally {
                        collection.isRefreshing = false
                    }
                }
            }

            val autoUpdateScope = rememberUiMonoTasker()
            OnLifecycleEvent {
                if (it == Lifecycle.State.Active) {
                    autoUpdateScope.launch {
                        val lastUpdated = collection.cache.lastUpdated.first()
                        if (System.currentTimeMillis() - lastUpdated > 60.minutes.inWholeMilliseconds) {
                            collection.isAutoRefreshing = true
                            collection.isRefreshing = true
                        }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = collection.isRefreshing,
                onRefresh = {
                    collection.isAutoRefreshing = false
                    collection.isRefreshing = true
                },
                indicator = {
                    Indicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(topBarPaddings.calculateTopPadding()),
                        isRefreshing = collection.isRefreshing,
                        state = pullToRefreshState,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                TabContent(
                    cache = collection.cache,
                    onRequestMore = { vm.requestMore(type) },
                    vm = vm,
                    type = type,
                    isLoggedIn = isLoggedIn,
                    contentPadding = PaddingValues(
                        top = topBarPaddings.calculateTopPadding() + contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding(),
                        start = 0.dp,
                        end = 0.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                    enableAnimation = { vm.myCollectionsSettings.enableListAnimation },
                )
            }
        }
    }
}

/**
 * @param contentPadding overall content padding
 */
@Composable
private fun TabContent(
    cache: LazyDataCache<SubjectCollection>,
    onRequestMore: () -> Unit,
    vm: MyCollectionsViewModel,
    type: UnifiedCollectionType,
    isLoggedIn: Boolean?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    enableAnimation: () -> Boolean = { true },
) {
    // 同时设置所有剧集为看过
    var currentSetAllEpisodesDialogSubjectId by rememberSaveable { mutableStateOf<Int?>(null) }
    if (currentSetAllEpisodesDialogSubjectId != null) {
        SetAllEpisodeDoneDialog(
            onDismissRequest = { currentSetAllEpisodesDialogSubjectId = null },
            onConfirm = {
                currentSetAllEpisodesDialogSubjectId?.let {
                    vm.launchInBackground {
                        setAllEpisodesWatched(it)
                    }
                }
                currentSetAllEpisodesDialogSubjectId = null
            },
        )
    }

    SubjectCollectionsColumn(
        cache,
        onRequestMore = onRequestMore,
        item = { subjectCollection ->
            var showEpisodeProgressDialog by rememberSaveable { mutableStateOf(false) }

            // 即使对话框不显示也加载, 避免打开对话框要等待一秒才能看到进度
            val episodeProgressState = rememberEpisodeProgressState(subjectCollection.subjectId)

            val navigator = LocalNavigator.current
            if (showEpisodeProgressDialog) {
                EpisodeProgressDialog(
                    episodeProgressState,
                    onDismissRequest = { showEpisodeProgressDialog = false },
                    actions = {
                        OutlinedButton({ navigator.navigateSubjectDetails(episodeProgressState.subjectId) }) {
                            Text("条目详情")
                        }
                    },
                )
            }

            SubjectCollectionItem(
                subjectCollection,
                episodeCacheStatus = { subjectId, episodeId ->
                    remember(vm, subjectId, episodeId) {
                        vm.cacheStatusForEpisode(subjectId, episodeId)
                    }.collectAsStateWithLifecycle(null).value
                },
                onClick = {
                    navigator.navigateSubjectDetails(subjectCollection.subjectId)
                },
                onClickEpisode = {
                    navigator.navigateEpisodeDetails(subjectCollection.subjectId, it.episode.id)
                },
                onClickSelectEpisode = {
                    showEpisodeProgressDialog = true
                },
                onSetCollectionType = {
                    vm.launchInBackground { setCollectionType(subjectCollection.subjectId, it) }
                    if (it == UnifiedCollectionType.DONE) {
                        currentSetAllEpisodesDialogSubjectId = subjectCollection.subjectId
                    }
                },
                doneButton = if (type == UnifiedCollectionType.DONE) {
                    null
                } else {
                    {
                        FilledTonalButton(
                            {
                                vm.launchInBackground {
                                    setCollectionType(subjectCollection.subjectId, UnifiedCollectionType.DONE)
                                }
                            },
                        ) {
                            Text("移至\"看过\"")
                        }
                    }
                },
            )
        },
        onEmpty = {
            Column(Modifier.padding(contentPadding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(32.dp))
                if (isLoggedIn == false) {
                    UnauthorizedTips(Modifier.fillMaxSize())
                } else {
                    Text("~ 空空如也 ~\n请点击 \"找番\" 收藏条目", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        modifier,
        contentPadding = contentPadding,
        enableAnimation = enableAnimation,
    )
}

@Stable
private fun UnifiedCollectionType.displayText(): String {
    return when (this) {
        UnifiedCollectionType.WISH -> "想看"
        UnifiedCollectionType.DOING -> "在看"
        UnifiedCollectionType.DONE -> "看过"
        UnifiedCollectionType.ON_HOLD -> "搁置"
        UnifiedCollectionType.DROPPED -> "抛弃"
        UnifiedCollectionType.NOT_COLLECTED -> "未收藏"
    }
}

@Composable
internal expect fun PreviewCollectionPage()
