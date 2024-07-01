package me.him188.ani.app.ui.subject.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.Platform
import me.him188.ani.app.platform.isMobile
import me.him188.ani.app.ui.foundation.layout.ConnectedScrollState
import me.him188.ani.app.ui.foundation.layout.connectedScrollContainer
import me.him188.ani.app.ui.foundation.layout.connectedScrollTarget
import me.him188.ani.app.ui.foundation.layout.rememberConnectedScrollState
import me.him188.ani.app.ui.foundation.pagerTabIndicatorOffset
import me.him188.ani.app.ui.foundation.widgets.FastLinearProgressIndicator
import me.him188.ani.app.ui.foundation.widgets.FastLinearProgressState
import me.him188.ani.app.ui.foundation.widgets.TopAppBarGoBackButton
import me.him188.ani.app.ui.subject.collection.SetAllEpisodeDoneDialog
import me.him188.ani.app.ui.subject.collection.progress.EpisodeProgressDialog
import me.him188.ani.app.ui.subject.details.components.CollectionAction
import me.him188.ani.app.ui.subject.details.components.CollectionData
import me.him188.ani.app.ui.subject.details.components.DetailsTab
import me.him188.ani.app.ui.subject.details.components.SelectEpisodeButton
import me.him188.ani.app.ui.subject.details.components.SubjectBlurredBackground
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsDefaults
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsHeader
import me.him188.ani.app.ui.subject.rating.EditRatingDialog
import me.him188.ani.app.ui.subject.rating.EditRatingState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

@Composable
fun SubjectDetailsScene(
    vm: SubjectDetailsViewModel,
) {
    var showSelectEpisode by rememberSaveable { mutableStateOf(false) }
    if (showSelectEpisode) {
        EpisodeProgressDialog(
            vm.episodeProgressState,
            onDismissRequest = { showSelectEpisode = false },
        )
    }

    val context = LocalContext.current
    val connectedScrollState = rememberConnectedScrollState()

    // 同时设置所有剧集为看过
    var showSetAllEpisodesDialog by rememberSaveable { mutableStateOf(false) }
    if (showSetAllEpisodesDialog) {
        SetAllEpisodeDoneDialog(
            onDismissRequest = { showSetAllEpisodesDialog = false },
            onConfirm = {
                vm.setAllEpisodesWatched()
                showSetAllEpisodesDialog = false
            },
        )
    }

    if (vm.showRatingDialog) {
        EditRatingDialog(
            remember(vm) {
                val ratingInfo = vm.subjectDetailsState.selfRatingInfo
                EditRatingState(
                    initialScore = ratingInfo.score,
                    initialComment = ratingInfo.comment ?: "",
                    initialIsPrivate = ratingInfo.isPrivate,
                )
            },
            onDismissRequest = {
                vm.cancelUpdateRating()
                vm.showRatingDialog = false
            },
            onRate = { vm.updateRating(it) },
            isLoading = vm.isRatingUpdating,
        )
    }

    var showRatingRequiresCollectionDialog by rememberSaveable { mutableStateOf(false) }
    if (showRatingRequiresCollectionDialog) {
        AlertDialog(
            { showRatingRequiresCollectionDialog = false },
            text = { Text("请先收藏再评分") },
            confirmButton = { TextButton({ showRatingRequiresCollectionDialog = false }) { Text("关闭") } },
        )
    }

    SubjectDetailsPage(
        vm.subjectDetailsState,
        onClickOpenExternal = { vm.browseSubjectBangumi(context) },
        onClickRating = {
            if (!vm.subjectDetailsState.selfCollected) {
                showRatingRequiresCollectionDialog = true
            } else {
                vm.showRatingDialog = true
            }
        },
        collectionData = {
            SubjectDetailsDefaults.CollectionData(
                collectionStats = vm.subjectDetailsState.info.collection,
            )
        },
        collectionActions = {
            SubjectDetailsDefaults.CollectionAction(
                vm.subjectDetailsState.selfCollectionType,
                onSetCollectionType = {
                    vm.setSelfCollectionType(it)
                    if (it == UnifiedCollectionType.DONE && vm.episodeProgressState.hasAnyUnwatched) {
                        showSetAllEpisodesDialog = true
                    }
                },
                enabled = !vm.isSetSelfCollectionTypeWorking,
            )
        },
        selectEpisodeButton = {
            SubjectDetailsDefaults.SelectEpisodeButton(
                onClick = { showSelectEpisode = true },
            )
        },
        connectedScrollState = connectedScrollState,
        detailsTab = {
            SubjectDetailsDefaults.DetailsTab(
                info = vm.subjectDetailsState.info,
                staff = vm.subjectDetailsState.persons,
                characters = vm.subjectDetailsState.characters,
                Modifier.nestedScroll(connectedScrollState.nestedScrollConnection),
            )
        },
        commentsTab = {},
        discussionsTab = {},
    )
}

@Immutable
enum class SubjectDetailsTab {
    DETAILS,
    COMMENTS,
    DISCUSSIONS,
}

/**
 * 一部番的详情页
 */
@Composable
fun SubjectDetailsPage(
    state: SubjectDetailsState,
    onClickOpenExternal: () -> Unit,
    onClickRating: () -> Unit,
    collectionData: @Composable () -> Unit,
    collectionActions: @Composable () -> Unit,
    selectEpisodeButton: @Composable () -> Unit,
    connectedScrollState: ConnectedScrollState,
    detailsTab: @Composable () -> Unit,
    commentsTab: @Composable () -> Unit,
    discussionsTab: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val indicatorState = remember(scope) { FastLinearProgressState(scope) }

    indicatorState.setVisible(state.isLoading, 0, 300)

    // 出场动画
    var isContentReady by remember { mutableStateOf(!state.isLoading) }
    LaunchedEffect(indicatorState) {
        indicatorState.awaitCompletion()
        isContentReady = true
    }

    val density by rememberUpdatedState(LocalDensity.current)

    Scaffold(
        topBar = {
            Box {
                // 透明背景的, 总是显示
                TopAppBar(
                    title = {},
                    navigationIcon = { TopAppBarGoBackButton() },
                    actions = {
                        IconButton(onClickOpenExternal) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )

                // 有背景, 仅在滚动一段距离后使用
                AnimatedVisibility(connectedScrollState.isScrolledTop, enter = fadeIn(), exit = fadeOut()) {
                    TopAppBar(
                        title = { Text(state.info.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = { TopAppBarGoBackButton() },
                        actions = {
                            IconButton(onClickOpenExternal) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(),
                    )
                }
            }
        },
        modifier = modifier,
        contentWindowInsets = WindowInsets(0.dp),
    ) { scaffoldPadding ->
        FastLinearProgressIndicator(
            indicatorState,
            Modifier.zIndex(2f).padding(scaffoldPadding).padding(horizontal = 4.dp).fillMaxWidth(),
        )

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AnimatedVisibility(
                isContentReady,
                Modifier.wrapContentSize(),
                // 从中间往上滑
                enter = fadeIn(tween(500)) + slideInVertically(
                    tween(600),
                    initialOffsetY = { 150.coerceAtMost(it) },
                ),
            ) {
                Column(Modifier.widthIn(max = 1300.dp).fillMaxHeight()) {
                    Box(Modifier.connectedScrollContainer(connectedScrollState)) {
                        // 虚化渐变背景
                        SubjectBlurredBackground(
                            coverImageUrl = if (isContentReady) state.coverImageUrl else null,
                            backgroundColor = MaterialTheme.colorScheme.background,
                            surfaceColor = MaterialTheme.colorScheme.surface,
                            Modifier.matchParentSize(),
                        )

                        // 标题和封面, 以及收藏数据, 可向上滑动
                        Column(Modifier.padding(scaffoldPadding).connectedScrollTarget(connectedScrollState)) {
                            SubjectDetailsHeader(
                                state.info,
                                state.coverImageUrl,
                                selfRatingScore = state.selfRatingInfo.score,
                                airingInfo = state.airingInfo,
                                onClickRating,
                                collectionData = collectionData,
                                collectionAction = collectionActions,
                                selectEpisodeButton = {
                                    if (state.selfCollected) {
                                        selectEpisodeButton()
                                    }
                                },
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                            )
                        }
                    }
                    val pagerState = rememberPagerState(
                        initialPage = SubjectDetailsTab.DETAILS.ordinal,
                        pageCount = { 3 },
                    )

                    // Pager with TabRow
                    Column(
                        Modifier
                            .padding(bottom = 16.dp)
                            .fillMaxHeight(),
                    ) {
                        val tabContainerColor by animateColorAsState(
                            if (connectedScrollState.isScrolledTop) TabRowDefaults.secondaryContainerColor else MaterialTheme.colorScheme.background,
                            tween(),
                        )
                        ScrollableTabRow(
                            selectedTabIndex = pagerState.currentPage,
                            indicator = @Composable { tabPositions ->
                                TabRowDefaults.PrimaryIndicator(
                                    Modifier.pagerTabIndicatorOffset(pagerState, tabPositions),
                                )
                            },
                            containerColor = tabContainerColor,
                            contentColor = TabRowDefaults.secondaryContentColor,
                            divider = {},
                            modifier = Modifier,
                        ) {
                            SubjectDetailsTab.entries.forEachIndexed { index, tabId ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = {
                                        println("click")
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                    text = {
                                        Text(text = renderSubjectDetailsTab(tabId))
                                    },
                                )
                            }
                        }

                        HorizontalPager(
                            state = pagerState,
                            Modifier.fillMaxHeight(),
                            userScrollEnabled = Platform.currentPlatform.isMobile(),
                        ) { index ->
                            val type = SubjectDetailsTab.entries[index]
                            Column(Modifier.padding()) {
                                when (type) {
                                    SubjectDetailsTab.DETAILS -> detailsTab()
                                    SubjectDetailsTab.COMMENTS -> commentsTab()
                                    SubjectDetailsTab.DISCUSSIONS -> discussionsTab()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Stable
private fun renderSubjectDetailsTab(tab: SubjectDetailsTab): String {
    return when (tab) {
        SubjectDetailsTab.DETAILS -> "详情"
        SubjectDetailsTab.COMMENTS -> "短评"
        SubjectDetailsTab.DISCUSSIONS -> "讨论"
    }
}
