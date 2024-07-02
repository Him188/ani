package me.him188.ani.app.ui.subject.episode

import androidx.annotation.UiThread
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import me.him188.ani.app.data.danmaku.DanmakuManager
import me.him188.ani.app.data.media.MediaSourceManager
import me.him188.ani.app.data.media.createFetchFetchSessionFlow
import me.him188.ani.app.data.media.fetch.FilteredMediaSourceResults
import me.him188.ani.app.data.media.fetch.MediaFetchSession
import me.him188.ani.app.data.media.fetch.create
import me.him188.ani.app.data.media.resolver.VideoSourceResolver
import me.him188.ani.app.data.media.selector.MediaSelector
import me.him188.ani.app.data.media.selector.MediaSelectorAutoSelect
import me.him188.ani.app.data.media.selector.MediaSelectorFactory
import me.him188.ani.app.data.media.selector.autoSelect
import me.him188.ani.app.data.media.selector.eventHandling
import me.him188.ani.app.data.models.VideoScaffoldConfig
import me.him188.ani.app.data.repositories.EpisodePreferencesRepository
import me.him188.ani.app.data.repositories.SettingsRepository
import me.him188.ani.app.data.subject.SubjectManager
import me.him188.ani.app.data.subject.displayName
import me.him188.ani.app.data.subject.episodeInfoFlow
import me.him188.ani.app.data.subject.subjectInfoFlow
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.platform.Context
import me.him188.ani.app.tools.caching.ContentPolicy
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.foundation.launchInMain
import me.him188.ani.app.ui.subject.episode.danmaku.PlayerDanmakuViewModel
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSelectorPresentation
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceResultsPresentation
import me.him188.ani.app.ui.subject.episode.statistics.DanmakuLoadingState
import me.him188.ani.app.ui.subject.episode.statistics.PlayerStatisticsState
import me.him188.ani.app.ui.subject.episode.video.PlayerLauncher
import me.him188.ani.app.ui.subject.episode.video.sidesheet.EpisodeSelectorState
import me.him188.ani.app.videoplayer.ui.state.PlayerState
import me.him188.ani.app.videoplayer.ui.state.PlayerStateFactory
import me.him188.ani.danmaku.api.Danmaku
import me.him188.ani.danmaku.api.DanmakuCollection
import me.him188.ani.danmaku.api.DanmakuEvent
import me.him188.ani.danmaku.api.DanmakuPresentation
import me.him188.ani.danmaku.api.DanmakuSearchRequest
import me.him188.ani.danmaku.api.DanmakuSession
import me.him188.ani.danmaku.ui.DanmakuRegexFilterConfig
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.danmaku.api.emptyDanmakuCollection
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.coroutines.cancellableCoroutineScope
import me.him188.ani.utils.coroutines.runUntilSuccess
import me.him188.ani.utils.coroutines.sampleWithInitial
import me.him188.ani.utils.logging.info
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.milliseconds


@Stable
interface EpisodeViewModel : HasBackgroundScope {
    val videoSourceResolver: VideoSourceResolver

    val subjectId: Int
    val episodeId: StateFlow<Int>

    val subjectPresentation: SubjectPresentation // by state
    val episodePresentation: EpisodePresentation // by state

    var isFullscreen: Boolean

    suspend fun setEpisodeCollectionType(type: UnifiedCollectionType)

    /**
     * 播放器内切换剧集
     */
    val episodeSelectorState: EpisodeSelectorState

    // Media Fetching

    /**
     * "数据源" bottom sheet 内容
     */
    val mediaSelectorPresentation: MediaSelectorPresentation

    /**
     * "数据源" bottom sheet 中的每个数据源的结果
     */
    val mediaSourceResultsPresentation: MediaSourceResultsPresentation
    val DanmakuRegexFilterConfig: Flow<DanmakuRegexFilterConfig>

    /**
     * "视频统计" bottom sheet 显示内容
     */
    val playerStatistics: PlayerStatisticsState

    // Media Selection

    /**
     * 是否显示数据源选择器
     */
    var mediaSelectorVisible: Boolean


    // Video
    val videoScaffoldConfig: VideoScaffoldConfig

    val videoLoadingState: StateFlow<VideoLoadingState> get() = playerStatistics.videoLoadingState

    /**
     * Play controller for video view. This can be saved even when window configuration changes (i.e. everything recomposes).
     */
    val playerState: PlayerState

    @UiThread
    suspend fun copyDownloadLink(clipboardManager: ClipboardManager, snackbar: SnackbarHostState)

    @UiThread
    suspend fun browseMedia(context: Context, snackbar: SnackbarHostState)

    @UiThread
    suspend fun browseDownload(context: Context, snackbar: SnackbarHostState)

    // Danmaku

    val danmaku: PlayerDanmakuViewModel
}

fun EpisodeViewModel(
    initialSubjectId: Int,
    initialEpisodeId: Int,
    initialIsFullscreen: Boolean = false,
    context: Context,
): EpisodeViewModel = EpisodeViewModelImpl(initialSubjectId, initialEpisodeId, initialIsFullscreen, context)


@Stable
private class EpisodeViewModelImpl(
    override val subjectId: Int,
    initialDanmakuId: Int,
    initialIsFullscreen: Boolean = false,
    context: Context,
) : AbstractViewModel(), KoinComponent, EpisodeViewModel {
    override val episodeId: MutableStateFlow<Int> = MutableStateFlow(initialDanmakuId)
    private val browserNavigator: BrowserNavigator by inject()
    private val playerStateFactory: PlayerStateFactory by inject()
    private val subjectManager: SubjectManager by inject()
    private val danmakuManager: DanmakuManager by inject()
    override val videoSourceResolver: VideoSourceResolver by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val mediaSourceManager: MediaSourceManager by inject()
    private val episodePreferencesRepository: EpisodePreferencesRepository by inject()

    private val subjectInfo = subjectManager.subjectInfoFlow(subjectId).shareInBackground()
    private val episodeInfo =
        episodeId.transformLatest { episodeId ->
            emit(null) // 清空前端
            emitAll(subjectManager.episodeInfoFlow(episodeId))
        }.stateInBackground(null)

    // Media Selection

    // 会在更换 ep 时更换
    private val mediaFetchSession = episodeInfo.flatMapLatest { episodeInfo ->
        mediaSourceManager.createFetchFetchSessionFlow(
            if (episodeInfo == null) {
                emptyFlow()
            } else {
                subjectInfo.map { subjectInfo ->
                    MediaFetchRequest.create(subjectInfo, episodeInfo)
                }
            },
        )
    }.shareInBackground(started = SharingStarted.Lazily)


    /**
     * 更换 EP 是否已经完成了.
     *
     * 之所以需要这个状态, 是因为当切换 EP 时, [mediaFetchSession] 会变更,
     * 随后 [MediaFetchSession.cumulativeResults] 会传递给 [mediaSelector],
     * 但是 [MediaSelector.mediaList] 是 share 在后台的, 也就是说它可能会在任意时间之后才会发现 [mediaFetchSession] 有更新.
     *
     * 这就导致当切换 EP 后, [MediaSelector.mediaList] 会有一段时间仍然是旧的值.
     *
     * 问题在于, [mediaFetchSession] 的变更会触发 [MediaSelectorAutoSelect.selectCached].
     * 自动选择可能比 [MediaSelector.mediaList] 更新更早, 所以自动选择就会用久的 `mediaList` 选择缓存, 导致将会播放旧的视频.
     *
     *
     * 因此我们增加 [switchEpisodeCompleted], 在操作 EP 时, 先将其设置为 `false`, 然后再修改 [episodeId],
     * 并在 [mediaSelector] 更新时设置为 true.
     *
     * 这样也并不是一定安全的, 有可能在我们修改 [episodeId] 后, 正好旧的查询触发了 [MediaSelector.mediaList] 更新,
     * 就导致 [switchEpisodeCompleted] 被设置为 `true`, [MediaSelectorAutoSelect.selectCached] 仍然会参考旧的 `mediaList` 选择缓存.
     *
     * 但是这种情况发生的概率比较小, 仅限于后台还有一个查询正在进行的时候用户切换了 EP, 并且旧的 EP 要至少有一个缓存, 而且恰好在一个比较短的时间内旧的查询完成了.
     *
     *
     * 一个更恰当的解决方法可能是把 [mediaSelector] 变成 flow. 当切换 EP 时直接把 [mediaSelector] 重新创建, 就不可能访问到旧的状态了.
     * 但是这会导致所有依赖 [mediaSelector] 的客户都更换为 flow 方式, 这很可能会导致更多问题. 因为我们先使用这个临时解决方案.
     */
    private val switchEpisodeCompleted = MutableStateFlow(false)

    private suspend inline fun awaitSwitchEpisodeCompleted() {
        switchEpisodeCompleted.first { it }
    }

    private val mediaSelector = MediaSelectorFactory.withKoin(getKoin())
        .create(
            subjectId,
            mediaFetchSession.flatMapLatest { it.cumulativeResults },
        )
        .apply {
            autoSelect.run {
                launchInBackground {
                    mediaFetchSession.collectLatest {
                        awaitSwitchEpisodeCompleted()
                        awaitCompletedAndSelectDefault(it)
                    }
                }
                launchInBackground {
                    mediaFetchSession.collectLatest {
                        awaitSwitchEpisodeCompleted()
                        selectCached(it)
                    }
                }

                launchInBackground {
                    mediaFetchSession.collectLatest {
                        awaitSwitchEpisodeCompleted()
                        if (settingsRepository.mediaSelectorSettings.flow.first().autoEnableLastSelected) {
                            autoEnableLastSelected(it)
                        }
                    }
                }
            }
            eventHandling.run {
                launchInBackground {
                    savePreferenceOnSelect {
                        episodePreferencesRepository.setMediaPreference(subjectId, it)
                    }
                }
            }
            launchInBackground {
                mediaList.collect {
                    switchEpisodeCompleted.value = true
                }
            }
        }

    override val DanmakuRegexFilterConfig: Flow<DanmakuRegexFilterConfig> =
        settingsRepository.danmakuRegexFilterConfig.flow

    override val mediaSelectorPresentation: MediaSelectorPresentation =
        MediaSelectorPresentation(mediaSelector, backgroundScope.coroutineContext)

    override val mediaSourceResultsPresentation: MediaSourceResultsPresentation =
        MediaSourceResultsPresentation(
            FilteredMediaSourceResults(
                results = mediaFetchSession.mapLatest { it.mediaSourceResults },
                settings = settingsRepository.mediaSelectorSettings.flow,
            ),
            backgroundScope.coroutineContext,
        )

    override val playerState: PlayerState =
        playerStateFactory.create(context, backgroundScope.coroutineContext)

    private val playerLauncher: PlayerLauncher = PlayerLauncher(
        mediaSelector, videoSourceResolver, playerState,
        episodeInfo, backgroundScope.coroutineContext,
    )

    override val playerStatistics: PlayerStatisticsState get() = playerLauncher.playerStatistics

    override var mediaSelectorVisible: Boolean by mutableStateOf(false)
    override val videoScaffoldConfig: VideoScaffoldConfig by settingsRepository.videoScaffoldConfig
        .flow.produceState(VideoScaffoldConfig.Default)

    override val videoLoadingState get() = playerStatistics.videoLoadingState

    override val subjectPresentation: SubjectPresentation by subjectInfo
        .map {
            SubjectPresentation(title = it.displayName, info = it)
        }
        .produceState(SubjectPresentation.Placeholder)

    private val episodePresentationFlow =
        episodeId
            .flatMapLatest { episodeId ->
                subjectManager.episodeCollectionFlow(subjectId, episodeId, ContentPolicy.CACHE_ONLY)
            }.map {
                it.toPresentation()
            }
            .stateInBackground(SharingStarted.Eagerly)

    override val episodePresentation: EpisodePresentation by episodePresentationFlow.filterNotNull()
        .produceState(EpisodePresentation.Placeholder)

    override var isFullscreen: Boolean by mutableStateOf(initialIsFullscreen)

    override suspend fun setEpisodeCollectionType(type: UnifiedCollectionType) {
        subjectManager.setEpisodeCollectionType(subjectId, episodeId.value, type)
    }

    override val episodeSelectorState: EpisodeSelectorState = EpisodeSelectorState(
        itemsFlow = subjectManager.episodeCollectionsFlow(subjectId).map { list -> list.map { it.toPresentation() } },
        onSelect = {
            mediaSelector.unselect() // 否则不会自动选择
            playerState.stop()

            switchEpisodeCompleted.value = false // 要在修改 episodeId 之前才安全, 但会有极小的概率在 fetchSession 更新前有 mediaList 更新
            episodeId.value = it.episodeId // ep 要在取消选择 media 之后才能变, 否则会导致使用旧的 media
        },
        currentEpisodeId = episodeId,
        parentCoroutineContext = backgroundScope.coroutineContext,
    )

    override suspend fun copyDownloadLink(clipboardManager: ClipboardManager, snackbar: SnackbarHostState) {
        requestMediaOrNull()?.let {
            clipboardManager.setText(AnnotatedString(it.download.uri))
            snackbar.showSnackbar("已复制下载链接")
        }
    }

    override suspend fun browseMedia(context: Context, snackbar: SnackbarHostState) {
        requestMediaOrNull()?.let {
            browserNavigator.openBrowser(context, it.originalUrl)
        }
    }

    override suspend fun browseDownload(context: Context, snackbar: SnackbarHostState) {
        requestMediaOrNull()?.let {
            browserNavigator.openMagnetLink(context, it.download.uri)
        }
    }

    override val danmaku: PlayerDanmakuViewModel = PlayerDanmakuViewModel().also {
        addCloseable(it)
    }

    private val danmakuCollectionFlow: Flow<DanmakuCollection> =
        mediaFetchSession.transformLatest {
            emit(emptyDanmakuCollection()) // 每次更换 mediaFetchSession 时 (ep 变更), 首先清空历史弹幕
            emitAll(
                playerState.videoData.mapLatest { data ->
                    if (data == null) {
                        return@mapLatest emptyDanmakuCollection()
                    }
                    playerStatistics.danmakuLoadingState.value = DanmakuLoadingState.Loading
                    val filename = data.filename
                    try {
                        val subject = subjectInfo.first()
                        val episode = episodeInfo.filterNotNull().first()
                        val result = danmakuManager.fetch(
                            request = DanmakuSearchRequest(
                                subjectId = subjectId,
                                subjectPrimaryName = subject.displayName,
                                subjectNames = subject.allNames,
                                subjectPublishDate = subject.airDate,
                                episodeId = episodeId.value,
                                episodeSort = episode.sort,
                                episodeEp = episode.ep,
                                episodeName = episode.displayName,
                                filename = filename,
                                fileHash = "aa".repeat(16),
                                fileSize = data.fileLength,
                                videoDuration = 0.milliseconds,
                            ),
                        )
                        playerStatistics.danmakuLoadingState.value = DanmakuLoadingState.Success(result.matchInfos)
                        result.danmakuCollection
                    } catch (e: Throwable) {
                        playerStatistics.danmakuLoadingState.value = DanmakuLoadingState.Failed(e)
                        throw e
                    }
                },
            )
        }.shareInBackground(started = SharingStarted.Lazily)

    private val danmakuSessionFlow: Flow<DanmakuSession> = danmakuCollectionFlow.mapLatest { session ->
        session.at(
            progress = playerState.currentPositionMillis.map { it.milliseconds },
            danmakuRegexFilterConfig = DanmakuRegexFilterConfig,
        )
    }.shareInBackground(started = SharingStarted.Lazily)

    private val danmakuEventFlow: Flow<DanmakuEvent> = danmakuSessionFlow.flatMapLatest { it.events }

    private val selfUserId = danmakuManager.selfId

    init {
        launchInMain { // state changes must be in main thread
            playerState.state.collect {
                if (it.isPlaying) {
                    danmaku.danmakuHostState.resume()
                } else {
                    danmaku.danmakuHostState.pause()
                }
            }
        }
        launchInBackground {
            videoLoadingState.collect {
                playerStatistics.videoLoadingState.value = it
            }
        }
        launchInBackground {
            settingsRepository.danmakuConfig.flow.drop(1).collectLatest {
                logger.info { "Danmaku config changed, repopulating" }
                danmakuSessionFlow.first().requestRepopulate()
            }
        }

        launchInBackground {
            cancellableCoroutineScope {
                val selfId = selfUserId.stateIn(this)
                val danmakuConfig = settingsRepository.danmakuConfig.flow.stateIn(this)
                danmakuEventFlow.collect { event ->
                    when (event) {
                        is DanmakuEvent.Add -> {
                            val data = event.danmaku
                            danmaku.danmakuHostState.trySend(
                                createDanmakuPresentation(data, selfId.value),
                            )
                        }

                        is DanmakuEvent.Repopulate -> {
                            danmaku.danmakuHostState.repopulate(
                                event.list.map {
                                    createDanmakuPresentation(it, selfId.value)
                                },
                                danmakuConfig.value.style,
                            )

                        }
                    }
                }
                cancelScope()
            }
        }

        // 自动标记看完
        launchInBackground {
            settingsRepository.videoScaffoldConfig.flow
                .map { it.autoMarkDone }
                .distinctUntilChanged()
                .debounce(1000)
                .collectLatest { enabled ->
                    if (!enabled) return@collectLatest

                    // 设置启用

                    mediaFetchSession.collectLatest {
                        cancellableCoroutineScope {
                            combine(
                                playerState.currentPositionMillis.sampleWithInitial(5000),
                                playerState.videoProperties.map { it?.durationMillis }.debounce(5000),
                            ) { pos, max ->
                                if (max == null) return@combine
                                if (episodePresentationFlow.value?.collectionType == UnifiedCollectionType.DONE) {
                                    cancelScope() // 已经看过了
                                }
                                if (pos > max.toFloat() * 0.9) {
                                    logger.info { "观看到 90%, 标记看过" }
                                    runUntilSuccess(maxAttempts = 5) {
                                        setEpisodeCollectionType(UnifiedCollectionType.DONE)
                                    }
                                    cancelScope() // 标记成功一次后就不要再检查了
                                }
                            }.collect()
                        }
                    }
                }
        }
    }

    private fun createDanmakuPresentation(
        data: Danmaku,
        selfId: String?,
    ) = DanmakuPresentation(
        data,
        isSelf = selfId == data.senderId,
    )

    /**
     * Requests the user to select a media if not already.
     * Returns null if the user cancels the selection.
     */
    @UiThread
    private suspend fun requestMediaOrNull(): Media? {
        mediaSelectorPresentation.selected?.let {
            return it // already selected
        }

        mediaSelectorVisible = true
        snapshotFlow { mediaSelectorVisible }.first { !it } // await closed
        return mediaSelectorPresentation.selected
    }
}

sealed interface VideoLoadingState {
    sealed interface Progressing : VideoLoadingState

    /**
     * 等待选择 [Media]
     */
    data object Initial : VideoLoadingState

    /**
     * 在解析磁力链/寻找文件
     */
    data object ResolvingSource : VideoLoadingState, Progressing

    /**
     * 在寻找种子资源中的正确的文件, 并打开文件
     */
    data object DecodingData : VideoLoadingState, Progressing

    /**
     * 文件成功找到
     */
    data object Succeed : VideoLoadingState, Progressing

    sealed class Failed : VideoLoadingState
    data object ResolutionTimedOut : Failed()

    /**
     * 不支持的媒体, 或者说是未启用支持该媒体的 [VideoSourceResolver]
     */
    data object UnsupportedMedia : Failed()
    data object NoMatchingFile : Failed()
    data class UnknownError(
        val cause: Throwable,
    ) : Failed()
}