package me.him188.ani.app.ui.subject.episode

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.notification.VideoNotificationState
import me.him188.ani.app.platform.setRequestFullScreen
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.LocalImageLoader
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.effects.OnLifecycleEvent
import me.him188.ani.app.ui.foundation.effects.ScreenOnEffect
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.foundation.layout.LocalLayoutMode
import me.him188.ani.app.ui.foundation.rememberViewModel
import me.him188.ani.app.ui.subject.episode.details.EpisodeActionRow
import me.him188.ani.app.ui.subject.episode.details.EpisodeDetails
import me.him188.ani.app.ui.subject.episode.details.EpisodePlayerTitle
import me.him188.ani.app.ui.theme.aniDarkColorTheme
import me.him188.ani.app.videoplayer.ui.VideoControllerState
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults.randomDanmakuPlaceholder
import me.him188.ani.danmaku.protocol.DanmakuInfo
import me.him188.ani.danmaku.protocol.DanmakuLocation
import me.him188.ani.danmaku.ui.DanmakuConfig
import moe.tlaster.precompose.flow.collectAsStateWithLifecycle
import moe.tlaster.precompose.lifecycle.Lifecycle
import moe.tlaster.precompose.navigation.BackHandler
import kotlin.time.Duration.Companion.milliseconds


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

@OptIn(ExperimentalCoilApi::class)
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

    val scope = rememberCoroutineScope()
    val imageLoader = LocalImageLoader.current
    val coilContext = LocalPlatformContext.current
    DisposableEffect(vm) {
        val state = VideoNotificationState()
        state.setPlayer(vm.playerState)
        scope.launch {
            val properties = vm.playerState.videoProperties.filterNotNull().first()
            state.setDescription(
                title = vm.subjectPresentation.title,
                text = "${vm.episodePresentation.sort} ${vm.episodePresentation.title}",
                length = properties.durationMillis.milliseconds,
            )
            scope.launch {
                kotlin.runCatching {
                    val request = ImageRequest.Builder(coilContext)
                        .data(vm.subjectPresentation.info.imageCommon)
                        .build()

                    (imageLoader.execute(request) as? SuccessResult)
                        ?.image
                        ?.let {
                            state.setAlbumArt(it)
                        }
                }.onFailure {
                    it.printStackTrace()
                }
            }
        }

        onDispose {
            state.release()
            scope.cancel()
        }
    }

    BoxWithConstraints(modifier) {
        val layoutMode by rememberUpdatedState(LocalLayoutMode.current)
        val isVeryWide by remember {
            derivedStateOf { layoutMode.deviceSize.width / layoutMode.deviceSize.height >= 1200f / 770 }
        }
        when {
            isVeryWide -> EpisodePageContentTabletVeryWide(vm, Modifier.fillMaxSize())
            layoutMode.showLandscapeUI -> EpisodePageContentTablet(vm, Modifier.fillMaxSize())
            else -> EpisodePageContentPhone(vm, Modifier.fillMaxSize())
        }
    }

    vm.videoSourceResolver.ComposeContent()
}

@Composable
private fun EpisodePageContentTabletVeryWide(
    vm: EpisodeViewModel,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints {
        val maxWidth = maxWidth
        Row(
            modifier
                .then(
                    if (vm.isFullscreen) Modifier.fillMaxSize()
                    else Modifier.navigationBarsPadding()
                ),
        ) {
            EpisodeVideo(
                vm,
                expanded = true,
                maintainAspectRatio = false,
                initialControllerVisible = true,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )

            if (vm.isFullscreen) {
                return@Row
            }

            Column(Modifier.width(width = (maxWidth * 0.18f).coerceAtLeast(300.dp))) {
                EpisodeDetails(
                    vm,
                    LocalSnackbar.current,
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    actionRow = {
                        EpisodeActionRow(
                            vm,
                            snackbar = LocalSnackbar.current,
                        )
                    }
                )
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
    val danmakuConfig by vm.danmaku.config.collectAsStateWithLifecycle(DanmakuConfig.Default)

    val videoLoadingState by vm.videoLoadingState.collectAsStateWithLifecycle(VideoLoadingState.Initial)

    // Don't rememberSavable. 刻意让每次切换都是隐藏的
    val videoControllerState = remember { VideoControllerState(initialControllerVisible) }
    var danmakuEditorText by rememberSaveable { mutableStateOf("") }


    // Refresh every time on configuration change (i.e. switching theme, entering fullscreen)
    val danmakuTextPlaceholder = remember { randomDanmakuPlaceholder() }


    EpisodeVideoImpl(
        vm.playerState,
        expanded = expanded,
        videoControllerState = videoControllerState,
        title = {
            val episode = vm.episodePresentation
            val subject = vm.subjectPresentation
            EpisodePlayerTitle(
                episode.ep,
                episode.title,
                subject.title,
                Modifier.placeholder(episode.isPlaceholder || subject.isPlaceholder)
            )
        },
        danmakuHostState = vm.danmaku.danmakuHostState,
        videoLoadingState = { videoLoadingState },
        danmakuConfig = remember { { danmakuConfig } },
        onClickFullScreen = {
            if (vm.isFullscreen) {
                context.setRequestFullScreen(false)
                vm.isFullscreen = false
            } else {
                vm.isFullscreen = true
                context.setRequestFullScreen(true)
            }
        },
        onExitFullscreen = {
            context.setRequestFullScreen(false)
            vm.isFullscreen = false
        },
        danmakuEditor = {
            val danmakuEditorRequester = remember { Any() }

            /**
             * 是否设置了暂停
             */
            var didSetPaused by rememberSaveable { mutableStateOf(false) }

            DanmakuEditor(
                vm = vm,
                text = danmakuEditorText,
                onTextChange = { danmakuEditorText = it },
                setControllerVisible = {
                    videoControllerState.isVisible = false
                },
                placeholderText = danmakuTextPlaceholder,
                modifier = Modifier.onFocusChanged {
                    if (it.isFocused) {
                        if (vm.videoScaffoldConfig.pauseVideoOnEditDanmaku && vm.playerState.state.value.isPlaying) {
                            didSetPaused = true
                            vm.playerState.pause()
                        }
                        videoControllerState.setRequestAlwaysOn(danmakuEditorRequester, true)
                    } else {
                        if (didSetPaused) {
                            didSetPaused = false
                            vm.playerState.resume()
                        }
                        videoControllerState.setRequestAlwaysOn(danmakuEditorRequester, false)
                    }
                }.weight(1f)
            )
        },
        modifier = modifier.fillMaxWidth().background(Color.Black)
            .then(if (expanded) Modifier.fillMaxSize() else Modifier.statusBarsPadding()),
        maintainAspectRatio = maintainAspectRatio,
        configProvider = remember(vm) { { vm.videoScaffoldConfig } },
    )
}

@Composable
internal fun DanmakuEditor(
    vm: EpisodeViewModel,
    text: String,
    onTextChange: (String) -> Unit,
    placeholderText: String,
    setControllerVisible: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textUpdated by rememberUpdatedState(text)
    val onTextChangeUpdated by rememberUpdatedState(onTextChange)
    MaterialTheme(aniDarkColorTheme()) {
        PlayerControllerDefaults.DanmakuTextField(
            text,
            onValueChange = onTextChange,
            modifier = modifier,
            onSend = remember(vm) {
                onSend@{
                    if (textUpdated.isEmpty()) return@onSend
                    val textSnapshot = textUpdated
                    onTextChangeUpdated("")
                    val exactPosition = vm.playerState.getExactCurrentPositionMillis()
                    vm.launchInBackground {
                        try {
                            danmaku.send(
                                episodeId = vm.episodeId,
                                DanmakuInfo(
                                    exactPosition,
                                    text = textSnapshot,
                                    color = Color.White.toArgb(),
                                    location = DanmakuLocation.NORMAL
                                )
                            )
                            withContext(Dispatchers.Main) { setControllerVisible(false) }
                        } catch (e: Throwable) {
                            withContext(Dispatchers.Main) { onTextChangeUpdated(textSnapshot) }
                            throw e
                        }
                    }
                }
            },
            isSending = vm.danmaku.isSending,
            placeholder = {
                Text(
                    placeholderText,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            },
        )
    }
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