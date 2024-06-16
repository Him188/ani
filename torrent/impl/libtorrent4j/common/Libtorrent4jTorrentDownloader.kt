package me.him188.ani.app.torrent.libtorrent4j

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.app.torrent.api.FetchTorrentTimeoutException
import me.him188.ani.app.torrent.api.HttpFileDownloader
import me.him188.ani.app.torrent.api.TorrentDownloadSession
import me.him188.ani.app.torrent.api.TorrentDownloader
import me.him188.ani.app.torrent.api.TorrentDownloaderConfig
import me.him188.ani.app.torrent.api.TorrentLibInfo
import me.him188.ani.app.torrent.api.files.EncodedTorrentInfo
import me.him188.ani.app.torrent.api.files.TorrentInfo
import me.him188.ani.app.torrent.api.handle.TorrentThread
import me.him188.ani.app.torrent.libtorrent4j.DefaultTorrentDownloadSession.Companion.FAST_RESUME_FILENAME
import me.him188.ani.app.torrent.libtorrent4j.files.Torrent4jTorrentInfo
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.libtorrent4j.AlertListener
import org.libtorrent4j.LibTorrent
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.swig.settings_pack
import org.libtorrent4j.swig.settings_pack.string_types
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

abstract class AbstractLockedTorrentDownloader<Info : TorrentInfo>(
    cacheDirectory: File,
    val sessionManager: LockedSessionManager,
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

    init {
        scope.launch {
            while (currentCoroutineContext().isActive) {
                val stats = sessionManager.use { stats() }
                totalUploaded.value = stats.totalUpload()
                totalDownloaded.value = stats.totalDownload()
                totalUploadRate.value = stats.uploadRate()
                totalDownloadRate.value = stats.downloadRate()
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
        val data: ByteArray? = try {
            sessionManager.use {
                fetchMagnet(uri, timeoutSeconds, magnetCacheDir)
            }
        } catch (e: InterruptedException) {
            throw FetchTorrentTimeoutException(cause = e)
        }
        if (data == null) {
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

    @TorrentThread
    protected abstract fun decodeTorrentInfo(data: EncodedTorrentInfo): Info

    protected abstract fun SessionManager.startDownload(
        ti: Info,
        saveDirectory: File
    )

    protected abstract suspend fun closeSession(info: Info)

    override suspend fun startDownload(
        data: EncodedTorrentInfo,
        parentCoroutineContext: CoroutineContext,
        overrideSaveDir: File?,
    ): TorrentDownloadSession = lock.withLock {
        withContext(Dispatchers.IO) {
            val torrentInfo = sessionManager.use {
                @OptIn(TorrentThread::class)
                decodeTorrentInfo(data)
            }
            val torrentName = torrentInfo.name

            logger.info { "[$torrentName] TorrentDownloader.startDownload called" }
            val saveDirectory = (overrideSaveDir ?: getSaveDirForTorrent(data)).apply { mkdirs() }
            val hash = torrentInfo.infoHashHex

            val reopened = dataToSession[hash]?.let {
                logger.info { "[$torrentName] Found existing session" }
                true
            } ?: kotlin.run {
                logger.info { "[$torrentName] This is a new session" }
                false
            }

            val session =
                dataToSession[hash] ?: DefaultTorrentDownloadSession(
                    torrentName = torrentName,
                    saveDirectory = saveDirectory,
                    onClose = { session ->
                        runBlocking(LockedSessionManager.dispatcher) {
                            logger.debug { "[$torrentName] Close: removeListener" }
                            sessionManager.removeListener(session.listener)
                            dataToSession.remove(hash)
                            logger.debug { "[$torrentName] Close: close handle" }
                            closeSession(torrentInfo)
                        }
                    },
                    onDelete = {
                        logger.debug { "[$torrentName] Delete: remove http torrent file cache" }
                        val uri = torrentInfo.originalUri
                        if (uri == null) {
                            logger.error { "[$torrentName] Delete: originalUri is null" }
                        } else {
                            getHttpTorrentFileCacheFile(uri)
                            logger.debug { "[$torrentName] Delete: removed" }
                        }
                    },
                    isDebug = isDebug,
                    parentCoroutineContext = parentCoroutineContext,
                )
            dataToSession[hash] = session
            sessionManager.use {
                settings().run {
                    //  https://libtorrent.org/reference-Settings.html#settings_pack
//            setInteger(settings_pack.int_types.piece_timeout.swigValue(), 3)
                    setInteger(settings_pack.int_types.request_timeout.swigValue(), 3)
                    setInteger(settings_pack.int_types.peer_timeout.swigValue(), 3)
                }

                if (reopened) {
                    logger.info { "[$torrentName] Torrent has already opened" }
                } else {
                    sessionManager.addListener(session.listener)

                    // 第一次打开, 设置忽略所有文件, 除了我们需要的那些
                    try {
                        logger.info { "[$torrentName] Starting torrent download" }
                        startDownload(torrentInfo, saveDirectory)
                        logger.info { "[$torrentName] Torrent download started" }
                    } catch (e: Throwable) {
                        sessionManager.removeListener(session.listener)
                        logger.error(e) { "[$torrentName] Failed to start torrent download" }
                        throw e
                    }
                }
            }
            return@withContext session
        }
    }


    override fun getSaveDirForTorrent(data: EncodedTorrentInfo): File =
        downloadCacheDir.resolve(data.data.contentHashCode().toString())

    override fun listSaves(): List<File> {
        return downloadCacheDir.listFiles()?.toList() ?: emptyList()
    }

    override fun close() {
        scope.cancel()
        httpFileDownloader.close()
        LockedSessionManager.launch { sessionManager.use { stop() } }
    }
}

/**
 * 使用 [libtorrent4j](https://github.com/aldenml/libtorrent4j) 的下载管理器.
 *
 * 内嵌运行一个 libtorrent4j 的 [SessionManager] 实例管理下载任务. 支持边下边播 [TorrentLibInfo.supportsStreaming].
 *
 * 该实现在安卓跑得不错, 但是在 PC 非常容易 crash VM, 很难调试. 在 PC 使用 `QBittorrentTorrentDownloader` 代替.
 */
class Libtorrent4jTorrentDownloader(
    cacheDirectory: File,
    sessionManager: LockedSessionManager,
    downloadFile: HttpFileDownloader,
    isDebug: Boolean,
    parentCoroutineContext: CoroutineContext,
) : AbstractLockedTorrentDownloader<Torrent4jTorrentInfo>(
    cacheDirectory, sessionManager, downloadFile, isDebug,
    parentCoroutineContext
) {
    companion object {
        const val VENDOR_NAME = "libtorrent"
    }

    override val vendor: TorrentLibInfo = TorrentLibInfo(
        vendor = VENDOR_NAME,
        version = LibTorrent.version(),
        supportsStreaming = true,
    )

    override fun SessionManager.startDownload(ti: Torrent4jTorrentInfo, saveDirectory: File) {
        val priorities = Priority.array(Priority.IGNORE, ti.fileCount)
        val resumeFile = saveDirectory.resolve(FAST_RESUME_FILENAME)
        download(
            ti.info,
            saveDirectory,
            resumeFile.takeIf { it.exists() },
            priorities,
            null,
            TorrentFlags.AUTO_MANAGED
                .or_(TorrentFlags.NEED_SAVE_RESUME),
            //                TorrentFlags.SEQUENTIAL_DOWNLOAD,//.or_(TorrentFlags.NEED_SAVE_RESUME)
        )
    }

    @TorrentThread
    override fun decodeTorrentInfo(data: EncodedTorrentInfo): Torrent4jTorrentInfo =
        Torrent4jTorrentInfo.decodeFrom(data)

    override suspend fun closeSession(info: Torrent4jTorrentInfo) {
        val hash = info.infoHashHex
        dataToSession.remove(hash)?.close()
        sessionManager.use {
            val torrentName = info.name
            find(Sha1Hash.parseHex(hash))?.let { handle ->
                logger.debug { "[$torrentName] Close: remove from libtorrent SessionManager" }
                remove(handle)
                logger.debug { "[$torrentName] Close: removed" }
            } ?: run {
                logger.debug { "[$torrentName] Close: handle not found, ignoring" }
            }
        }
    }
}

private val logger = logger(TorrentDownloader::class)

/**
 * Creates a new [Libtorrent4jTorrentDownloader] instance.
 *
 * The returned instance must be closed when it is no longer needed.
 *
 * @param downloadFile automatically closed when the returned instance is closed.
 *
 * @see Libtorrent4jTorrentDownloader
 */
fun Libtorrent4jTorrentDownloader(
    cacheDirectory: File,
    downloadFile: HttpFileDownloader,
    config: TorrentDownloaderConfig = TorrentDownloaderConfig.Default,
    parentCoroutineContext: CoroutineContext,
): Libtorrent4jTorrentDownloader {
    val sessionManager = SessionManager()

    val listener = object : AlertListener {
        override fun types(): IntArray {
            return intArrayOf(AlertType.SESSION_STATS.swig(), AlertType.DHT_STATS.swig())
        }

        override fun alert(alert: Alert<*>) {
            if (alert.type() == AlertType.SESSION_STATS) {
                sessionManager.postDhtStats()
            }

//            if (alert.type() == AlertType.DHT_STATS) {
//                val nodes: Long = sessionManager.stats().dhtNodes()
//                // wait for at least 10 nodes in the DHT.
//                if (nodes >= 10) {
//                    dht.complete(Unit)
//                }
//            }
        }
    }
    sessionManager.addListener(listener)
    logger.info { "Starting SessionManager" }
    sessionManager.start()
    sessionManager.applySettings(sessionManager.settings().apply {
        isEnableDht = true
        isEnableLsd = true
        activeDhtLimit(300)
        activeTrackerLimit(50)
        activeSeeds(8)
        activeDownloads(8)
        connectionsLimit(500) // default was 200
        seedingOutgoingConnections(true) // default was true, just to make sure
        uploadRateLimit(0)
        downloadRateLimit(0)
        maxPeerlistSize(1000)
        dhtBootstrapNodes = setOf(
            dhtBootstrapNodes.split(",") + listOf(
                "router.utorrent.com:6881",
                "router.bittorrent.com:6881",
                "dht.transmissionbt.com:6881",
                "router.bitcomet.com:6881",
            )
        ).joinToString(",")

        logger.info { "peerFingerprint was: $peerFingerprintString" }
        logger.info { "user_agent was: $userAgentString" }
        logger.info { "handshake_client_version was: $handshakeClientVersionString" }

        peerFingerprintString = config.peerFingerprint
        userAgentString = config.userAgent
        config.clientHandshakeVersion?.let {
            handshakeClientVersionString = it
        }
        logger.info { "peerFingerprint set: $peerFingerprintString" }
        logger.info { "user_agent set: $userAgentString" }
        logger.info { "handshake_client_version set: $handshakeClientVersionString" }
    })
    logger.info { "postDhtStats" }
    sessionManager.postDhtStats()
    // No need to wait for DHT, some devices may not have access to the DHT network.

//    logger.info { "Waiting for nodes in DHT" }
//    val dhtResult = withTimeoutOrNull(30.seconds) {
//        dht.await()
//    }
//    if (dhtResult == null) {
//        logger.info { "DHT bootstrap failed" }
//        error("DHT bootstrap failed")
//    }
    sessionManager.removeListener(listener)

    return Libtorrent4jTorrentDownloader(
        cacheDirectory,
        LockedSessionManager(sessionManager),
        downloadFile,
        isDebug = config.isDebug,
        parentCoroutineContext = parentCoroutineContext
    )
}

var SettingsPack.peerFingerprintString: String
    get() = getBytes(string_types.peer_fingerprint.swigValue()).toString(Charset.forName("UTF-8"))
    set(value) {
        setBytes(string_types.peer_fingerprint.swigValue(), value.toByteArray(Charset.forName("UTF-8")))
    }

var SettingsPack.userAgentString: String
    get() = getBytes(string_types.user_agent.swigValue()).toString(Charset.forName("UTF-8"))
    set(value) {
        setBytes(string_types.user_agent.swigValue(), value.toByteArray(Charset.forName("UTF-8")))
    }

var SettingsPack.handshakeClientVersionString: String
    get() = getBytes(string_types.handshake_client_version.swigValue()).toString(Charset.forName("UTF-8"))
    set(value) {
        setBytes(string_types.handshake_client_version.swigValue(), value.toByteArray(Charset.forName("UTF-8")))
    }

