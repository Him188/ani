package me.him188.ani.app.data.media.cache.requester

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.media.cache.TestMediaCache
import me.him188.ani.app.data.media.cache.TestMediaCacheStorage
import me.him188.ani.app.data.media.fetch.MediaFetcherConfig
import me.him188.ani.app.data.media.fetch.MediaSourceMediaFetcher
import me.him188.ani.app.data.media.fetch.awaitCompletion
import me.him188.ani.app.data.media.framework.SOURCE_DMHY
import me.him188.ani.app.data.media.framework.TestMediaList
import me.him188.ani.app.data.media.instance.createTestMediaSourceInstance
import me.him188.ani.app.data.media.selector.DefaultMediaSelector
import me.him188.ani.app.data.media.selector.MediaSelector
import me.him188.ani.app.data.media.selector.MediaSelectorContext
import me.him188.ani.app.data.media.selector.MediaSelectorFactory
import me.him188.ani.app.data.models.MediaSelectorSettings
import me.him188.ani.app.data.subject.EpisodeInfo
import me.him188.ani.app.data.subject.SubjectInfo
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaPreference
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.source.TestHttpMediaSource
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.SubtitleLanguage.ChineseSimplified
import me.him188.ani.datasources.api.topic.SubtitleLanguage.ChineseTraditional
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpisodeCacheRequesterTest {
    companion object {
        private val DEFAULT_PREFERENCE = MediaPreference.Empty.copy(
            fallbackResolutions = listOf(
                Resolution.R2160P,
                Resolution.R1440P,
                Resolution.R1080P,
                Resolution.R720P,
            ).map { it.id },
            fallbackSubtitleLanguageIds = listOf(
                ChineseSimplified,
                ChineseTraditional,
            ).map { it.id }
        )
    }

    private val mediaList: MutableStateFlow<MutableList<DefaultMedia>> = MutableStateFlow(TestMediaList.toMutableList())
    private fun addMedia(vararg media: DefaultMedia) {
        mediaList.value.addAll(media)
    }

    private val savedUserPreference = MutableStateFlow(DEFAULT_PREFERENCE)
    private val savedDefaultPreference = MutableStateFlow(DEFAULT_PREFERENCE)
    private val mediaSelectorSettings = MutableStateFlow(MediaSelectorSettings.Default)
    private val mediaSelectorContext = MutableStateFlow(
        MediaSelectorContext(
            subjectFinished = false,
            mediaSourcePrecedence = emptyList(),
        )
    )
    private val storage = TestMediaCacheStorage()
    private val storageFlow = MutableStateFlow(listOf(storage))

    private val requester =
        EpisodeCacheRequester(
            flowOf(
                MediaSourceMediaFetcher(
                    { MediaFetcherConfig.Default },
                    listOf(createTestMediaSourceInstance(TestHttpMediaSource(fetch = {
                        SinglePagePagedSource {
                            mediaList.value.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    })))
                )
            ),
            mediaSelectorFactory = object : MediaSelectorFactory {
                override fun create(
                    subjectId: Int,
                    mediaList: Flow<List<Media>>,
                    flowCoroutineContext: CoroutineContext
                ): MediaSelector {
                    return DefaultMediaSelector(
                        mediaSelectorContextNotCached = mediaSelectorContext,
                        mediaListNotCached = mediaList,
                        savedUserPreference = savedUserPreference,
                        savedDefaultPreference = savedDefaultPreference,
                        enableCaching = false,
                        mediaSelectorSettings = mediaSelectorSettings
                    )
                }
            },
            storagesLazy = storageFlow
        )

    private fun createRequest(
        sort: EpisodeSort = EpisodeSort(0)
    ): EpisodeCacheRequest {
        return EpisodeCacheRequest(
            subjectInfo = SubjectInfo.Empty,
            episodeInfo = EpisodeInfo(0, sort = sort),
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // Basic states
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `initial stage idle`() = runTest {
        assertIs<CacheRequestStage.Idle>(requester.stage.value)
    }

    @Test
    fun `request changes state to SelectMedia`() = runTest {
        val request = createRequest()
        requester.request(request)
        assertIs<CacheRequestStage.SelectMedia>(requester.stage.value)
    }

    @Test
    fun `select unknown media`() = runTest {
        val request = createRequest()
        requester.request(request)
            .select(mediaList.value.first().copy()) // 注意, 这个时候实际上 MediaFetchSession 并未开始, 因为它是 lazy 的
        assertIs<CacheRequestStage.SelectStorage>(requester.stage.value)
    }

    @Test
    fun `select known media`() = runTest {
        val request = createRequest()
        requester.request(request).run {
            fetchSession.awaitCompletion()
            select(mediaList.value.first())
        }
        assertIs<CacheRequestStage.SelectStorage>(requester.stage.value)
    }

    @Test
    fun `select known storage`() = runTest {
        val request = createRequest()
        requester.request(request)
            .select(mediaList.value.first())
            .select(storage)
        assertIs<CacheRequestStage.Done>(requester.stage.value)
    }

    @Test
    fun `select unknown storage`() = runTest {
        val request = createRequest()
        requester.request(request)
            .select(mediaList.value.first())
            .select(TestMediaCacheStorage())
        assertIs<CacheRequestStage.Done>(requester.stage.value)
    }

    ///////////////////////////////////////////////////////////////////////////
    // SelectMedia trySelect
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `SelectMedia tryAutoSelectByPreference`() = runTest {
        val request = createRequest()
        requester.request(request)
            .tryAutoSelectByPreference()
        assertIs<CacheRequestStage.SelectStorage>(requester.stage.value)
    }

    @Test
    fun `SelectMedia tryAutoSelectByCachedSeason selects none for empty input`() = runTest {
        val request = createRequest()
        assertNull(
            requester.request(request)
                .tryAutoSelectByCachedSeason(emptyList())
        )
    }

    @Test
    fun `SelectMedia tryAutoSelectByCachedSeason selects none for inputs matching single ep`() = runTest {
        val originalMedia = DefaultMedia(
            mediaId = "$SOURCE_DMHY.1",
            mediaSourceId = SOURCE_DMHY,
            originalTitle = "[桜都字幕组] 孤独摇滚 ABC ABC ABC ABC ABC ABC ABC ABC ABC ABC",
            download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
            originalUrl = "https://example.com/1",
            publishedTime = System.currentTimeMillis(),
            episodeRange = EpisodeRange.single(EpisodeSort(1)), // note here: single
            properties = MediaProperties(
                subtitleLanguageIds = listOf(ChineseSimplified, ChineseTraditional).map { it.id },
                resolution = "1080P",
                alliance = "桜都字幕组",
                size = 122.megaBytes,
            ),
            kind = MediaSourceKind.BitTorrent,
            location = MediaSourceLocation.Online,
        )
        val mediaCache = TestMediaCache(
            CachedMedia(
                originalMedia, "local",
                ResourceLocation.LocalFile("/dev/null"),
                MediaSourceLocation.Local,
                MediaSourceKind.LocalCache,
            ),
            MediaCacheMetadata(
                subjectNames = setOf("孤独摇滚"),
                episodeSort = EpisodeSort(1),
                episodeEp = EpisodeSort(1),
                episodeName = "test",
            )
        )

        val request = createRequest(sort = EpisodeSort(1))
        assertNull(
            requester.request(request)
                .tryAutoSelectByCachedSeason(listOf(mediaCache))
        )
    }

    @Test
    fun `SelectMedia tryAutoSelectByCachedSeason selects none for inputs without matching ep`() = runTest {
        val originalMedia = DefaultMedia(
            mediaId = "$SOURCE_DMHY.1",
            mediaSourceId = SOURCE_DMHY,
            originalTitle = "[桜都字幕组] 孤独摇滚 ABC ABC ABC ABC ABC ABC ABC ABC ABC ABC",
            download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
            originalUrl = "https://example.com/1",
            publishedTime = System.currentTimeMillis(),
            episodeRange = EpisodeRange.range(EpisodeSort(1), EpisodeSort(12)),
            properties = MediaProperties(
                subtitleLanguageIds = listOf(ChineseSimplified, ChineseTraditional).map { it.id },
                resolution = "1080P",
                alliance = "桜都字幕组",
                size = 122.megaBytes,
            ),
            kind = MediaSourceKind.BitTorrent,
            location = MediaSourceLocation.Online,
        )
        val mediaCache = TestMediaCache(
            CachedMedia(
                originalMedia, "local",
                ResourceLocation.LocalFile("/dev/null"),
                MediaSourceLocation.Local,
                MediaSourceKind.LocalCache,
            ),
            MediaCacheMetadata(
                subjectNames = setOf("孤独摇滚"),
                episodeSort = EpisodeSort(1),
                episodeEp = EpisodeSort(1),
                episodeName = "test",
            )
        )

        val request = createRequest(sort = EpisodeSort(0)) // 0 !in 12
        assertNull(
            requester.request(request)
                .tryAutoSelectByCachedSeason(listOf(mediaCache))
        )
    }

    @Test
    fun `SelectMedia tryAutoSelectByCachedSeason selects one for inputs with matching ep`() = runTest {
        val originalMedia = DefaultMedia(
            mediaId = "$SOURCE_DMHY.1",
            mediaSourceId = SOURCE_DMHY,
            originalTitle = "[桜都字幕组] 孤独摇滚 ABC ABC ABC ABC ABC ABC ABC ABC ABC ABC",
            download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
            originalUrl = "https://example.com/1",
            publishedTime = System.currentTimeMillis(),
            episodeRange = EpisodeRange.range(EpisodeSort(1), EpisodeSort(12)),
            properties = MediaProperties(
                subtitleLanguageIds = listOf(ChineseSimplified, ChineseTraditional).map { it.id },
                resolution = "1080P",
                alliance = "桜都字幕组",
                size = 122.megaBytes,
            ),
            kind = MediaSourceKind.BitTorrent,
            location = MediaSourceLocation.Online,
        )
        val mediaCache = TestMediaCache(
            CachedMedia(
                originalMedia, "local",
                ResourceLocation.LocalFile("/dev/null"),
                MediaSourceLocation.Local,
                MediaSourceKind.LocalCache,
            ),
            MediaCacheMetadata(
                subjectNames = setOf("孤独摇滚"),
                episodeSort = EpisodeSort(1),
                episodeEp = EpisodeSort(1),
                episodeName = "test",
            )
        )

        val request = createRequest().run {
            copy(
                subjectInfo.copy(id = 12, name = "ひ", nameCn = "孤独摇滚"),
                episodeInfo.copy(sort = EpisodeSort(2), name = "第二集") // 2 in 12
            )
        }
        val done = requester.request(request)
            .tryAutoSelectByCachedSeason(listOf(mediaCache))!!
            .trySelectSingle()!!

        assertEquals(originalMedia, done.media)
        assertEquals(
            // note: compare with the MediaCacheMetadata above
            // We expect all info updated
            MediaCacheMetadata(
                subjectId = "12",
                episodeId = "0",
                subjectNames = setOf("ひ", "孤独摇滚"),
                episodeSort = EpisodeSort(2), // using new
                episodeEp = null,
                episodeName = "第二集",
            ),
            done.metadata
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // StaleStageException
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `select media two times`() = runTest {
        val request = createRequest()
        val selectMedia = requester.request(request)
        selectMedia.select(mediaList.value.first())
        assertFailsWith<StaleStageException> {
            selectMedia.select(mediaList.value.first())
        }
    }

    @Test
    fun `select storage two times`() = runTest {
        val request = createRequest()
        val state = requester.request(request).select(mediaList.value.first())
        state.select(storage)
        assertFailsWith<StaleStageException> {
            state.select(storage)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // SelectStorage trySelect
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `SelectStorage trySelectByCache`() = runTest {
        val request = createRequest()
        // TODO:  
    }

    @Test
    fun `SelectStorage trySelectSingle selects one when storageFlow is not empty`() = runTest {
        assertTrue { storageFlow.value.isNotEmpty() }
        val request = createRequest()
        val state = requester.request(request).select(mediaList.value.first())
        assertNotNull(state.trySelectSingle())
    }

    @Test
    fun `SelectStorage trySelectSingle selects none when storageFlow is empty`() = runTest {
        storageFlow.value = listOf()
        assertTrue { storageFlow.value.isEmpty() }
        val request = createRequest()
        val state = requester.request(request).select(mediaList.value.first())
        assertNull(state.trySelectSingle())
    }

    @Test
    fun `SelectStorage trySelectSingle selects none when storageFlow is not single`() = runTest {
        storageFlow.value = listOf(TestMediaCacheStorage(), TestMediaCacheStorage())
        assertTrue { storageFlow.value.size > 1 }
        val request = createRequest()
        val state = requester.request(request).select(mediaList.value.first())
        assertNull(state.trySelectSingle())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Cancel
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `cancel SelectMedia`() = runTest {
        val request = createRequest()
        requester.request(request)
            .cancel()
        assertIs<CacheRequestStage.Idle>(requester.stage.value)
    }

    @Test
    fun `cancel SelectStorage`() = runTest {
        val request = createRequest()
        requester.request(request)
            .select(mediaList.value.first())
            .cancel()
        assertIs<CacheRequestStage.SelectMedia>(requester.stage.value)
    }
}
