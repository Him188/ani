package me.him188.ani.app.ui.subject.episode

import androidx.compose.animation.Crossfade
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.setRequestFullScreen
import me.him188.ani.app.platform.showTabletUI
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.effects.OnLifecycleEvent
import me.him188.ani.app.ui.foundation.effects.ScreenOnEffect
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.foundation.rememberViewModel
import me.him188.ani.app.ui.subject.episode.details.EpisodeActionRow
import me.him188.ani.app.ui.subject.episode.details.EpisodeDetails
import me.him188.ani.app.ui.subject.episode.details.EpisodePlayerTitle
import me.him188.ani.danmaku.ui.DanmakuConfig
import moe.tlaster.precompose.flow.collectAsStateWithLifecycle
import moe.tlaster.precompose.lifecycle.Lifecycle
import moe.tlaster.precompose.navigation.BackHandler


private val LocalSnackbar = compositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

/**
 * 番剧详情 (播放) 页面
 */
@Composable
fun EpisodePage(
    viewModel: EpisodeViewModel,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState, Modifier.navigationBarsPadding())
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) {
        CompositionLocalProvider(LocalSnackbar provides snackbarHostState) {
            EpisodePageContent(
                viewModel,
                modifier
            )
        }
    }
}

@Composable
fun EpisodePageContent(
    vm: EpisodeViewModel,
    modifier: Modifier = Modifier,
) {
    // 处理当用户点击返回键时, 如果是全屏, 则退出全屏
    val navigator = LocalNavigator.current
    BackHandler {
        vm.playerState.pause()
        navigator.navigator.goBack()
    }

    // 按返回退出全屏
    val context by rememberUpdatedState(LocalContext.current)
    BackHandler(enabled = vm.isFullscreen) {
        context.setRequestFullScreen(false)
        vm.isFullscreen = false
    }

    ScreenOnEffect()

    AutoPauseEffect(vm)

    BoxWithConstraints(modifier) {
        Crossfade(
            targetState = showTabletUI(),
            modifier = Modifier.fillMaxSize()
        ) { isTablet ->
            if (isTablet) {
                EpisodePageContentTablet(vm, Modifier.fillMaxSize())
            } else {
                EpisodePageContentPhone(vm, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun EpisodePageContentTablet(
    vm: EpisodeViewModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .then(
                if (vm.isFullscreen) Modifier.fillMaxSize()
                else Modifier.navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
            ),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(Modifier.weight(1f)) {
            EpisodeVideo(
                vm,
                expanded = true,
                maintainAspectRatio = true,
                initialControllerVisible = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (vm.isFullscreen) {
                return@Row
            }

            EpisodeDetails(
                vm,
                LocalSnackbar.current,
                Modifier.fillMaxWidth(),
                actionRow = {
                    EpisodeActionRow(
                        vm,
                        snackbar = LocalSnackbar.current,
                        Modifier.width(400.dp),
                    )
                }
            )
        }

        if (vm.isFullscreen) {
            return@Row
        }

        Column(Modifier.width(240.dp)) {
            // 选集
            Text(
                "这里是选集, 快有了",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.labelLarge,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
private fun EpisodePageContentPhone(
    vm: EpisodeViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier.then(if (vm.isFullscreen) Modifier.fillMaxSize() else Modifier.navigationBarsPadding())) {
        EpisodeVideo(vm, vm.isFullscreen, Modifier)

        if (vm.isFullscreen) {
            return@Column
        }

        val pagerState = rememberPagerState(initialPage = 0) { 2 }

        Column(Modifier.fillMaxSize()) {
//            TabRow(
//                selectedTabIndex = pagerState.currentPage,
//                indicator = @Composable { tabPositions ->
//                    TabRowDefaults.PrimaryIndicator(
//                        Modifier.pagerTabIndicatorOffset(pagerState, tabPositions),
//                    )
//                },
//            ) {
//                Tab(
//                    selected = pagerState.currentPage == 0,
//                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
//                    text = { Text("详情") },
//                )
//                Tab(
//                    selected = pagerState.currentPage == 1,
//                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
//                    text = { Text("评论") },
//                )
//            }
//
//            val episodeId by viewModel.episodeId.collectAsStateWithLifecycle()
//            val commentViewModel = remember(episodeId) {
//                CommentViewModel(episodeId)
//            }

            HorizontalPager(state = pagerState, Modifier.fillMaxSize()) { index ->

                when (index) {
                    0 -> EpisodeDetails(vm, LocalSnackbar.current, Modifier.fillMaxSize())
//                    1 -> {
//                        CommentColumn(commentViewModel, Modifier.fillMaxSize())
//                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeVideo(
    vm: EpisodeViewModel,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    maintainAspectRatio: Boolean = !expanded,
    initialControllerVisible: Boolean = false,
) {
    val context by rememberUpdatedState(LocalContext.current)

    // 视频
    val selected by vm.mediaSelected.collectAsStateWithLifecycle(false)
    val danmakuConfig by vm.danmaku.config.collectAsStateWithLifecycle(DanmakuConfig.Default)

    val danmakuEnabled by vm.danmaku.enabled.collectAsStateWithLifecycle(false)
    val videoSourceState by vm.videoSourceState.collectAsStateWithLifecycle(VideoSourceState.Initial)
    EpisodeVideo(
        vm.playerState,
        expanded = expanded,
        title = {
            val episode = vm.episodePresentation
            val subject = vm.subjectPresentation
            EpisodePlayerTitle(
                episode.sort,
                episode.title,
                subject.title,
                modifier.placeholder(episode.isPlaceholder || subject.isPlaceholder)
            )
        },
        videoSourceState = { videoSourceState },
        danmakuHostState = vm.danmaku.danmakuHostState,
        videoSourceSelected = { selected },
        danmakuConfig = { danmakuConfig },
        onClickFullScreen = {
            if (vm.isFullscreen) {
                context.setRequestFullScreen(false)
                vm.isFullscreen = false
            } else {
                vm.isFullscreen = true
                context.setRequestFullScreen(true)
            }
        },
        danmakuEnabled = { danmakuEnabled },
        setDanmakuEnabled = { vm.launchInBackground { danmaku.setEnabled(it) } },
        onSendDanmaku = {},
        modifier = Modifier.fillMaxWidth().background(Color.Black)
            .then(if (expanded) Modifier.fillMaxSize() else Modifier.statusBarsPadding()),
        maintainAspectRatio = maintainAspectRatio,
        initialControllerVisible = initialControllerVisible
    )
}

/**
 * 切后台自动暂停
 */
@Composable
private fun AutoPauseEffect(viewModel: EpisodeViewModel) {
    var pausedVideo by rememberSaveable { mutableStateOf(true) } // live after configuration change
    val isPreviewing by rememberUpdatedState(LocalIsPreviewing.current)

    val autoPauseTasker = rememberUiMonoTasker()
    OnLifecycleEvent {
        if (isPreviewing) return@OnLifecycleEvent
        if (it == Lifecycle.State.InActive || it == Lifecycle.State.Destroyed) {
            if (viewModel.playerState.state.value.isPlaying) {
                pausedVideo = true
                autoPauseTasker.launch {
                    // #160, 切换全屏时视频会暂停半秒
                    // > 这其实是之前写切后台自动暂停导致的，检测了 lifecycle 事件，切全屏和切后台是一样的事件。延迟一下就可以了
                    viewModel.playerState.pause() // 正在播放时, 切到后台自动暂停
                }
            } else {
                // 如果不是正在播放, 则不操作暂停, 当下次切回前台时, 也不要恢复播放
                pausedVideo = false
            }
        } else if (it == Lifecycle.State.Active && pausedVideo) {
            autoPauseTasker.launch {
                viewModel.playerState.resume() // 切回前台自动恢复, 当且仅当之前是自动暂停的
            }
            pausedVideo = false
        } else {
            pausedVideo = false
        }
    }
}

@Preview
@Composable
private fun PreviewEpisodePageDesktop() {
    ProvideCompositionLocalsForPreview {
        val context = LocalContext.current
        val vm = rememberViewModel<EpisodeViewModel> {
            EpisodeViewModel(
                initialSubjectId = 0,
                initialEpisodeId = 0,
                initialIsFullscreen = false,
                context,
            )
        }
        EpisodePage(vm)
    }
}

@Composable
internal expect fun PreviewEpisodePage()