package me.him188.ani.app.ui.subject.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.Platform
import me.him188.ani.app.platform.isMobile
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.layout.ConnectedScrollState
import me.him188.ani.app.ui.foundation.layout.connectedScrollContainer
import me.him188.ani.app.ui.foundation.layout.connectedScrollTarget
import me.him188.ani.app.ui.foundation.layout.rememberConnectedScrollState
import me.him188.ani.app.ui.foundation.pagerTabIndicatorOffset
import me.him188.ani.app.ui.foundation.theme.weaken
import me.him188.ani.app.ui.foundation.widgets.FastLinearProgressIndicator
import me.him188.ani.app.ui.foundation.widgets.FastLinearProgressState
import me.him188.ani.app.ui.foundation.widgets.TopAppBarGoBackButton
import me.him188.ani.app.ui.subject.collection.progress.EpisodeProgressDialog
import me.him188.ani.app.ui.subject.details.components.DetailsTab
import me.him188.ani.app.ui.subject.details.components.SubjectBlurredBackground
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsCollectionData
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsDefaults
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsHeader
import me.him188.ani.datasources.bangumi.client.BangumiEpisode
import me.him188.ani.datasources.bangumi.processing.fixToString

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
    SubjectDetailsPage(
        vm.subjectDetailsState,
        onClickOpenExternal = { vm.browseSubjectBangumi(context) },
        collectionData = {
            SubjectDetailsCollectionData(
                vm.subjectDetailsState.info,
                vm.subjectDetailsState.selfCollectionType,
                onClickSelectEpisode = { showSelectEpisode = true },
                onSetAllEpisodesDone = { vm.setAllEpisodesWatched() },
                onSetCollectionType = { vm.setSelfCollectionType(it) },
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
    collectionData: @Composable () -> Unit,
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

        Box {
            AnimatedVisibility(
                isContentReady,
                Modifier.fillMaxSize(),
                // 从中间往上滑
                enter = fadeIn(tween(500)) + slideInVertically(
                    tween(600),
                    initialOffsetY = { 150.coerceAtMost(it) },
                ),
            ) {
                Column(Modifier.fillMaxSize()) {
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
                                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                            )

                            Row(Modifier.padding(top = 16.dp)) {
                                collectionData()
                            }
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
                            .fillMaxSize(),
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
                            modifier = Modifier.fillMaxWidth(),
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
                            Column(Modifier.fillMaxSize().padding()) {
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


@Composable
private fun <T> PersonList(
    list: List<T>?,
    key: (T) -> Any,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
    each: @Composable (T) -> Unit,
) {
    val spacedBy = 16.dp
    LazyRow(
        modifier = modifier.placeholder(visible = list == null).fillMaxWidth().heightIn(min = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(spacedBy),
    ) {
        item(key = "spacer header") { Spacer(Modifier.width(horizontalPadding - spacedBy)) }
        items(list.orEmpty(), key = key) { item ->
            each(item)
        }
        item(key = "spacer footer") { Spacer(Modifier.width(horizontalPadding - spacedBy)) }
    }
}

@Composable
private fun PersonView(
    avatar: @Composable () -> Unit,
    text: @Composable () -> Unit,
    role: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Box(Modifier.clip(CircleShape).size(64.dp)) {
                avatar()
            }
            Box(Modifier.padding(top = 4.dp)) {
                ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                    text()
                }
            }
            Box(Modifier.padding(top = 4.dp)) {
                ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.weaken()) {
                        role()
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeList(
    episodes: List<BangumiEpisode>,
    horizontalPadding: Dp,
    onClickItem: (BangumiEpisode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontalSpacedBy = 8.dp
    val onClickItemState by rememberUpdatedState(onClickItem)
    LazyHorizontalGrid(
        GridCells.Fixed(1),
        modifier = modifier.height(60.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacedBy),
    ) {
        item(key = "spacer header") { Spacer(Modifier.width(horizontalPadding - horizontalSpacedBy)) }
        items(episodes, key = { it.id }) { episode ->
            EpisodeItem(episode, { onClickItemState(episode) }, Modifier.widthIn(min = 60.dp, max = 160.dp))
        }
        item(key = "spacer footer") { Spacer(Modifier.width(horizontalPadding - horizontalSpacedBy)) }
    }
}

/**
 * 一个剧集:
 * ```
 * |------------|
 * | 01 冒险结束 |
 * |       评论 |
 * |------------|
 * ```
 */
@Composable
fun EpisodeItem(
    episode: BangumiEpisode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Card(
        onClick,
        modifier.clip(shape),
        shape = shape,
//        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // "01"
                Text(episode.sort.fixToString(2), style = MaterialTheme.typography.bodyMedium)

                Spacer(Modifier.weight(1f, fill = false))

                // "冒险结束"
                Text(
                    episode.chineseName,
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.ChatBubbleOutline,
                    null,
                    Modifier.size(16.dp),
                )
                Text(
                    remember { "${episode.comment}" },
                    Modifier.offset(y = (-1).dp).padding(start = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(modifier: Modifier = Modifier, text: @Composable () -> Unit) {
    Row(modifier.padding(top = 8.dp, bottom = 8.dp)) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                text()
            }
        }
    }
}
