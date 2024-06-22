package me.him188.ani.app.torrent.anitorrent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import me.him188.ani.app.torrent.anitorrent.binding.SWIGTYPE_p_libtorrent__session
import me.him188.ani.app.torrent.anitorrent.binding.anitorrent
import me.him188.ani.app.torrent.api.FetchTorrentTimeoutException
import me.him188.ani.app.torrent.api.HttpFileDownloader
import me.him188.ani.app.torrent.api.TorrentDownloadSession
import me.him188.ani.app.torrent.api.TorrentDownloader
import me.him188.ani.app.torrent.api.TorrentLibInfo
import me.him188.ani.app.torrent.api.files.EncodedTorrentInfo
import me.him188.ani.app.torrent.api.files.TorrentInfo
import me.him188.ani.app.torrent.libtorrent4j.DefaultTorrentDownloadSession
import me.him188.ani.app.torrent.libtorrent4j.files.Torrent4jTorrentInfo
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

class AnitorrentTorrentDownloader<Info : TorrentInfo>(
    cacheDirectory: File,
    val session: SWIGTYPE_p_libtorrent__session,
    private val httpFileDownloader: HttpFileDownloader,
    private val isDebug: Boolean,
    parentCoroutineContext: CoroutineContext,
) : TorrentDownloader {
    private val scope = CoroutineScope(parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job]))

    private val logger = logger(this::class)

    override val totalUploaded: MutableStateFlow<Long> = MutableStateFlow(0L)
    override val totalDownloaded: MutableStateFlow<Long> = MutableStateFlow(0L)
    override val totalUploadRate: MutableStateFlow<Long> = MutableStateFlow(0L)
    override val totalDownloadRate: MutableStateFlow<Long> = MutableStateFlow(0L)
    override val vendor: TorrentLibInfo = TorrentLibInfo(
        vendor = "Anitorrent",
        version = "1.0.0",
        supportsStreaming = true,
    )

    init {
        scope.launch {
            while (currentCoroutineContext().isActive) {
                // TODO: stats 
//                val stats = sessionManager.getStats()
//                totalUploaded.value = stats.totalUpload()
//                totalDownloaded.value = stats.totalDownload()
//                totalUploadRate.value = stats.uploadRate()
//                totalDownloadRate.value = stats.downloadRate()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun fetchTorrent(uri: String, timeoutSeconds: Int): EncodedTorrentInfo {
        if (uri.startsWith("http", ignoreCase = true)) {
            val cacheFile = getHttpTorrentFileCacheFile(uri)
            if (cacheFile.exists()) {
                val data = cacheFile.readText().hexToByteArray()
                logger.info { "HTTP torrent file '${uri}' found in cache: $cacheFile, length=${data.size}" }
                return Torrent4jTorrentInfo.encode(uri, data)
            }
            logger.info { "Fetching http url: $uri" }
            val data = httpFileDownloader.download(uri)
            logger.info { "Fetching http url success, file length = ${data.size}" }
            cacheFile.writeText(data.toHexString())
            logger.info { "Saved cache file: $cacheFile" }
            return Torrent4jTorrentInfo.encode(uri, data)
        }

        logger.info { "Fetching magnet: $uri" }
        val str = try {
            anitorrent.fetch_magnet(session, uri, timeoutSeconds, magnetCacheDir.absolutePath)
        } catch (e: InterruptedException) {
            throw FetchTorrentTimeoutException(cause = e)
        }
        val data = str.toByteArray()
        if (data.isEmpty()) {
            throw FetchTorrentTimeoutException()
        }
        logger.info { "Fetched magnet: size=${data.size}" }
        return Torrent4jTorrentInfo.encode(uri, data)
    }

    private val magnetCacheDir = cacheDirectory.resolve("magnet").apply {
        mkdirs()
    }

    private val httpTorrentFileCacheDir = cacheDirectory.resolve("torrentFiles").apply {
        mkdirs()
    }

    private fun getHttpTorrentFileCacheFile(uri: String): File {
        return httpTorrentFileCacheDir.resolve(uri.hashCode().toString() + ".txt")
    }

    private val downloadCacheDir = cacheDirectory.resolve("api/pieces").apply {
        mkdirs()
    }

    protected val dataToSession = ConcurrentHashMap<String, DefaultTorrentDownloadSession>()

    private val lock = Mutex()

    override suspend fun startDownload(
        data: EncodedTorrentInfo,
        parentCoroutineContext: CoroutineContext,
        overrideSaveDir: File?
    ): TorrentDownloadSession {
        TODO("Not yet implemented")
    }

    override fun getSaveDirForTorrent(data: EncodedTorrentInfo): File =
        downloadCacheDir.resolve(data.data.contentHashCode().toString())

    override fun listSaves(): List<File> {
        return downloadCacheDir.listFiles()?.toList() ?: emptyList()
    }

    override fun close() {
        scope.cancel()
        httpFileDownloader.close()
    }
}