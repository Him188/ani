package me.him188.ani.app.data.media.resolver

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.him188.ani.app.tools.torrent.TorrentEngine
import me.him188.ani.app.torrent.api.FetchTorrentTimeoutException
import me.him188.ani.app.torrent.api.files.EncodedTorrentInfo
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.videoplayer.data.OpenFailures
import me.him188.ani.app.videoplayer.data.VideoSource
import me.him188.ani.app.videoplayer.data.VideoSourceOpenException
import me.him188.ani.app.videoplayer.torrent.TorrentVideoData
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.contains
import me.him188.ani.datasources.api.topic.titles.RawTitleParser
import me.him188.ani.datasources.api.topic.titles.parse
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.component.KoinComponent

class TorrentVideoSourceResolver(
    private val engine: TorrentEngine,
) : VideoSourceResolver {
    override suspend fun supports(media: Media): Boolean {
        if (!engine.isEnabled.first()) return false
        return media.download is ResourceLocation.HttpTorrentFile || media.download is ResourceLocation.MagnetLink
    }

    @Throws(VideoSourceResolutionException::class)
    override suspend fun resolve(media: Media, episode: EpisodeMetadata): VideoSource<*> {
        val downloader = try {
            engine.getDownloader()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw VideoSourceResolutionException(ResolutionFailures.ENGINE_ERROR, e)
        }

        downloader ?: throw UnsupportedMediaException(media)

        return when (val location = media.download) {
            is ResourceLocation.HttpTorrentFile,
            is ResourceLocation.MagnetLink
            -> {
                try {
                    TorrentVideoSource(
                        engine,
                        encodedTorrentInfo = downloader.fetchTorrent(location.uri),
                        episodeMetadata = episode,
                        extraFiles = media.extraFiles,
                    )
                } catch (e: FetchTorrentTimeoutException) {
                    throw VideoSourceResolutionException(ResolutionFailures.FETCH_TIMEOUT)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw VideoSourceResolutionException(ResolutionFailures.ENGINE_ERROR, e)
                }
            }

            else -> throw UnsupportedMediaException(media)
        }
    }

    companion object {
        private val DEFAULT_VIDEO_EXTENSIONS =
            setOf("mp4", "mkv", "avi", "mpeg", "mov", "flv", "wmv", "webm", "rm", "rmvb")

        /**
         * 黑名单词, 包含这些词的文件会被放到最后.
         *
         * 黑名单也有顺序, 在黑名单中的越靠前越容易被选择.
         */
        @Suppress("RegExpRedundantEscape")
        private val BLACKLIST_WORDS = // 性能还可以, regex 只是让 70ms 变成了 150ms (不过它可能在指数的复杂度)
            setOf(
                Regex("""\[SP[0-9]*\]"""),
                Regex("""\[OVA[0-9]*\]"""),
                // SP 和 OVA 要放到前面, 因为用户可能就是要看这个
                Regex("""PV[0-9]*"""),
                Regex("""NCOP[0-9]*"""),
                Regex("""NCED[0-9]*"""),
                Regex("""OP[0-9]+"""),
                Regex("""ED[0-9]+"""), // 必须匹配数字防止名字带有 OP ED 的情况
                Regex("""\[OP[0-9]*\]"""),
                Regex("""\[ED[0-9]*\]"""),
                Regex("""\[CM[0-9]*\]"""),
            )

        /**
         * @param episodeSort 在系列中的集数, 例如第二季的第一集为 26
         * @param episodeEp 在当前季度中的集数, 例如第二季的第一集为 01
         */
        fun <T> selectVideoFileEntry(
            entries: List<T>,
            getPath: T.() -> String,
            episodeTitles: List<String>,
            episodeSort: EpisodeSort,
            episodeEp: EpisodeSort?,
            videoExtensions: Set<String> = DEFAULT_VIDEO_EXTENSIONS,
        ): T? {
            // Filter by file extension
            val videos = entries
                .filterTo(ArrayList(entries.size)) {
                    videoExtensions.any { fileType -> it.getPath().endsWith(fileType, ignoreCase = true) }
                }

            videos.sortByDescending {
                BLACKLIST_WORDS.forEachIndexed { index, blacklistWord ->
                    if (it.getPath().contains(blacklistWord)) {
                        return@sortByDescending -index // 包含黑名单词的放到最后
                    }
                }
                1
            }

            // Find by name match
            for (episodeTitle in episodeTitles) {
                val entry = videos.singleOrNull {
                    it.getPath().contains(episodeTitle, ignoreCase = true)
                }
                if (entry != null) return entry
            }

            // 解析标题匹配集数
            val parsedTitles = buildMap { // similar to `associateWith`, but ignores nulls
                for (entry in videos) {
                    val title = RawTitleParser.getDefault().parse(
                        entry.getPath().substringBeforeLast("."),
                        null
                    ).episodeRange
                    if (title != null) { // difference between `associateWith`
                        put(entry, title)
                    }
                }
            }
            // 优先按系列集数 sort 匹配 (数字较大)
            if (parsedTitles.isNotEmpty()) {
                parsedTitles.entries.firstOrNull {
                    it.value.contains(episodeSort, allowSeason = false) // 季度全集在匹配文件时是无意义的
                }?.key?.let { return it }
            }
            // 然后按季度集数 ep 匹配
            if (episodeEp != null && parsedTitles.isNotEmpty()) {
                parsedTitles.entries.firstOrNull {
                    it.value.contains(episodeEp, allowSeason = false)
                }?.key?.let { return it }
            }

            // 解析失败, 尽可能匹配一个
            episodeSort.toString().let { number ->
                videos.firstOrNull { it.getPath().contains(number, ignoreCase = true) }
                    ?.let { return it }
            }

            return videos.firstOrNull()
        }
    }
}

private class TorrentVideoSource(
    private val engine: TorrentEngine,
    private val encodedTorrentInfo: EncodedTorrentInfo,
    private val episodeMetadata: EpisodeMetadata,
    override val extraFiles: MediaExtraFiles,
) : VideoSource<TorrentVideoData>, KoinComponent {
    @OptIn(ExperimentalStdlibApi::class)
    override val uri: String by lazy {
        "torrent://${encodedTorrentInfo.data.toHexString().take(32) + "..."}"
    }

    @Throws(VideoSourceOpenException::class)
    override suspend fun open(): TorrentVideoData {
        logger.info {
            "TorrentVideoSource opening a VideoData, metadata: $episodeMetadata"
        }
        val downloader = engine.getDownloader() ?: throw VideoSourceOpenException(OpenFailures.ENGINE_DISABLED)
        return TorrentVideoData(
            withContext(Dispatchers.IO) {
                val files = downloader.startDownload(encodedTorrentInfo)
                    .getFiles()

                logger.info {
                    "TorrentVideoSource started download, selecting file"
                }

                TorrentVideoSourceResolver.selectVideoFileEntry(
                    files,
                    { pathInTorrent },
                    listOf(episodeMetadata.title),
                    episodeSort = episodeMetadata.sort,
                    episodeEp = episodeMetadata.ep,
                )?.also {
                    logger.info {
                        "TorrentVideoSource selected file: ${it.pathInTorrent}"
                    }
                }?.createHandle()?.also {
                    it.resume(FilePriority.HIGH)
                } ?: throw VideoSourceOpenException(OpenFailures.NO_MATCHING_FILE)
            },
        )
    }

    override fun toString(): String = "TorrentVideoSource(uri=$uri, episodeMetadata=${episodeMetadata})"

    companion object {
        val logger = logger<TorrentVideoSource>()
    }
}
