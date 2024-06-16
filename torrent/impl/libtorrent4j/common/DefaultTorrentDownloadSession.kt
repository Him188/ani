package me.him188.ani.app.torrent.libtorrent4j

import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.torrent.api.TorrentDownloadSession
import me.him188.ani.app.torrent.api.TorrentDownloadState
import me.him188.ani.app.torrent.api.files.AbstractTorrentFileEntry
import me.him188.ani.app.torrent.api.files.DownloadStats
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.api.files.PieceState
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.app.torrent.api.files.TorrentFileHandle
import me.him188.ani.app.torrent.api.files.TorrentFilePieceMatcher.matchPiecesForFile
import me.him188.ani.app.torrent.api.files.findPieceByPieceIndex
import me.him188.ani.app.torrent.api.handle.AniTorrentHandle
import me.him188.ani.app.torrent.api.handle.EventListener
import me.him188.ani.app.torrent.api.handle.StatsUpdateEvent
import me.him188.ani.app.torrent.api.handle.TaskQueue
import me.him188.ani.app.torrent.api.handle.TorrentAddEvent
import me.him188.ani.app.torrent.api.handle.TorrentContents
import me.him188.ani.app.torrent.api.handle.TorrentEvent
import me.him188.ani.app.torrent.api.handle.TorrentFile
import me.him188.ani.app.torrent.api.handle.TorrentFinishedEvent
import me.him188.ani.app.torrent.api.handle.TorrentResumeEvent
import me.him188.ani.app.torrent.api.handle.TorrentSaveResumeDataEvent
import me.him188.ani.app.torrent.api.handle.TorrentThread
import me.him188.ani.app.torrent.api.pieces.Piece
import me.him188.ani.app.torrent.api.pieces.PiecePriorities
import me.him188.ani.app.torrent.api.pieces.TorrentDownloadController
import me.him188.ani.app.torrent.api.pieces.lastIndex
import me.him188.ani.app.torrent.api.pieces.startIndex
import me.him188.ani.app.torrent.io.TorrentInput
import me.him188.ani.utils.coroutines.flows.resetStale
import me.him188.ani.utils.io.SeekableInput
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.libtorrent4j.AddTorrentParams
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

open class DefaultTorrentDownloadSession(
    private val torrentName: String,
    /**
     * The directory where the torrent is saved.
     *
     * The directory may contain multiple files, or a single file.
     * The files are not guaranteed to be present at the moment when this function returns.
     */
    final override val saveDirectory: File,
    private val onClose: (DefaultTorrentDownloadSession) -> Unit,
    private val onDelete: (DefaultTorrentDownloadSession) -> Unit,
    private val isDebug: Boolean,
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
) : TorrentDownloadSession {
    companion object {
        const val FAST_RESUME_FILENAME = "fastresume"
    }

    private val sessionScope = CoroutineScope(parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job]))
    private val coroutineCloseHandle =
        parentCoroutineContext[Job]?.invokeOnCompletion {
            close()
        }

    private val logger = logger(this::class.simpleName + "@${this.hashCode()}")

    /**
     * 在 BT 线程执行
     */
    private val torrentThreadTasks = TaskQueue<AniTorrentHandle>(
        enableTimeoutWatchdog = isDebug,
    )

    final override val state: MutableStateFlow<TorrentDownloadState> = MutableStateFlow(TorrentDownloadState.Starting)

    inner class OverallStatsImpl : DownloadStats() {
        override val totalBytes: MutableStateFlow<Long> = MutableStateFlow(0L)
        override val downloadedBytes = MutableStateFlow(0L)

        val downloadRate0 = MutableStateFlow<Long?>(null)
        override val downloadRate: Flow<Long?>
            get() = downloadRate0
                .resetStale(1000) {
                    emit(0L)
                }
                .distinctUntilChanged()

        val uploadRate0 = MutableStateFlow<Long?>(null)
        override val uploadRate
            get() = uploadRate0
                .resetStale(1000) {
                    emit(0L)
                }
                .distinctUntilChanged()

        override val progress = combine(downloadedBytes, totalBytes) { downloaded, total ->
            if (total == 0L) {
                0f
            } else {
                downloaded.toFloat() / total.toFloat()
            }
        }.distinctUntilChanged()
        override val isFinished: Flow<Boolean> = flow {
            emitAll(combine(getFiles().map { it.stats.isFinished }) { list ->
                list.all { it }
            })
        }

        override suspend fun awaitFinished() {
            isFinished.filter { it }.first()
        }
    }

    override val overallStats = OverallStatsImpl()
    override suspend fun getFiles(): List<TorrentFileEntry> = actualInfo.await().entries

    private inner class ActualTorrentInfo(
        val allPiecesInTorrent: List<Piece>,
        files: List<TorrentFile>,
    ) {
        @Deprecated(
            "Recursive call",
            level = DeprecationLevel.ERROR,
            replaceWith = ReplaceWith("this")
        )
        private fun actualInfo(): ActualTorrentInfo = this

        val entries: List<TorrentFileEntryImpl> = kotlin.run {
            val numFiles = files.size

            var currentOffset = 0L
            val list = List(numFiles) { index ->
                val file = files[index]
                val size = file.size
                val path = file.path

                val list = matchPiecesForFile(allPiecesInTorrent, currentOffset, size).also { pieces ->
                    logPieces(pieces, path)
                }
                val filePieces = if (list is RandomAccess) {
                    list
                } else {
                    ArrayList(list)
                }
                TorrentFileEntryImpl(
                    index = index,
                    offset = currentOffset,
                    length = size,
                    relativePath = path,
                    saveDirectory = saveDirectory,
                    pieces = filePieces,
                    initialDownloadedBytes = calculateTotalFinishedSize(filePieces)
                ).also {
                    currentOffset += size
                }
            }
            list
        }

        val controller: TorrentDownloadController = TorrentDownloadController(
            allPiecesInTorrent,
            createPiecePriorities(),
            windowSize = 32
        )

        @Synchronized
        fun onPieceDownloaded(index: Int) {
            allPiecesInTorrent[index].state.value = PieceState.FINISHED
            for (openHandle in openHandles) {
                if (openHandle.entry.findPieceByPieceIndex(index) != null) {
                    openHandle.entry.downloadedBytes.value += allPiecesInTorrent[index].size
                }
            }
            // 不要在这里打印 log, 否则会导致启动时遍历已下载资源慢
//            logger.debug { "[TorrentDownloadControl] Piece downloaded: $index. " } // Was downloading ${controller.getDebugInfo().downloadingPieces}
            controller.onPieceDownloaded(index)
        }

        @Synchronized
        fun onBlockDownloading(pieceIndex: Int) {
            allPiecesInTorrent[pieceIndex].state.compareAndSet(PieceState.READY, PieceState.DOWNLOADING)
        }

        @TorrentThread
        @Synchronized
        fun onFinished(contents: TorrentContents) {
            val entries = entries
            for ((file, downloaded) in contents.getFileProgresses()) {
                if (file.size == downloaded) {
                    val entry = entries.fastFirstOrNull { it.pathInTorrent == file.path } ?: continue
                    logger.info { "[TorrentDownloadControl] Set file finished because torrent finished: ${file.path}" }
                    entry.finishedOverride.value = true
                    entry.pieces.fastForEach {
                        it.state.value = PieceState.FINISHED
                    }
                    entry.downloadedBytes.value = entry.length
                }
            }

            for (entry in entries) {
                // 重新扫描一遍 piece, 如果全都下载完了, 则设置为 finished. #418
                val finished = entry.pieces.fastAll { it.state.value == PieceState.FINISHED }
                if (finished) {
                    entry.finishedOverride.value = true
                    entry.downloadedBytes.value = entry.length
                }
            }
        }

        @Synchronized // 必须在这个锁里计算, 因为 [onPieceDownloaded] 会 +updatedDownloadedBytes
        fun calculateTotalFinishedSize(pieces: List<Piece>): Long =
            pieces.sumOf { if (it.state.value == PieceState.FINISHED) it.size else 0 }
    }

    internal val openHandles = ConcurrentLinkedQueue<TorrentFileEntryImpl.TorrentFileHandleImpl>()

    fun logPieces(pieces: List<Piece>, pathInTorrent: String) {
        logger.info {
            val start = pieces.minByOrNull { it.startIndex }
            val end = pieces.maxByOrNull { it.lastIndex }
            "[$torrentName] File '$pathInTorrent' piece initialized, ${pieces.size} pieces, offset range: $start..$end"
        }
    }

    // 构造时, actualInfo must be available
    internal inner class TorrentFileEntryImpl(
        index: Int,
        val offset: Long,
        length: Long,
        relativePath: String,
        saveDirectory: File,
        /**
         * 与这个文件有关的 pieces, sorted naturally by offset
         *
         * must support [RandomAccess]
         */
        override val pieces: List<Piece>,
        initialDownloadedBytes: Long,
    ) : AbstractTorrentFileEntry(
        index,
        length,
        saveDirectory,
        relativePath,
        torrentName,
        isDebug,
        sessionScope.coroutineContext
    ) {
        inner class TorrentFileHandleImpl : AbstractTorrentFileHandle() {
            override val entry get() = this@TorrentFileEntryImpl

            override fun closeImpl() {
                openHandles.remove(this)
                this@DefaultTorrentDownloadSession.closeIfNotInUse()
            }

            override fun resumeImpl(priority: FilePriority) {
                val pieces = pieces
                torrentThreadTasks.submit { handle ->
                    if (pieces.isNotEmpty()) {
                        val firstIndex = pieces.first().pieceIndex
                        val lastIndex = pieces.last().pieceIndex
                        handle.setPieceDeadline(firstIndex, 0)
                        handle.setPieceDeadline(lastIndex, 1)

                        if (firstIndex + 1 <= lastIndex) {
                            handle.setPieceDeadline(firstIndex + 1, 2)
                        }
                        if (firstIndex + 2 <= lastIndex) {
                            handle.setPieceDeadline(firstIndex + 2, 3)
                        }
                        println("setPieceDeadline ok")
                    }
                    handle.resume()
                }
            }

            override fun toString(): String = "TorrentFileHandleImpl(index=$index, filePath='$pathInTorrent')"
            override fun closeAndDelete() {
                close()
                deleteEntireTorrentIfNotInUse()
            }
        }

        override val supportsStreaming: Boolean get() = true

        val finishedOverride = MutableStateFlow(false)

        val downloadedBytes = MutableStateFlow(initialDownloadedBytes)
        override val stats: DownloadStats = object : DownloadStats() {
            override val totalBytes: Flow<Long> = flowOf(length)
            override val downloadedBytes get() = this@TorrentFileEntryImpl.downloadedBytes
            override val downloadRate: Flow<Long?> get() = overallStats.downloadRate // TODO: separate download/upload rate for torrent file 
            override val uploadRate: Flow<Long?> get() = overallStats.uploadRate
            override val progress: Flow<Float> =
                combine(finishedOverride, downloadedBytes) { finished, downloadBytes ->
                    when {
                        finished -> 1f
                        length == 0L -> 0f
                        else -> (downloadBytes.toFloat() / length.toFloat()).coerceAtMost(1f)
                    }
                }
            override val isFinished: Flow<Boolean> = combine(downloadedBytes, totalBytes) { downloaded, total ->
                downloaded >= total
            }

            override suspend fun awaitFinished() {
                isFinished.filter { it }.first()
            }
        }

        override fun updatePriority() {
            @OptIn(TorrentThread::class)
            torrentThreadTasks.submit { handle ->
                val highestPriority = priorityRequests.values.maxWithOrNull(nullsFirst(naturalOrder()))
                    ?: FilePriority.IGNORE
                handle.contents.files[index].priority = highestPriority
                logger.info { "[$torrentName] Set file $pathInTorrent priority to $highestPriority" }
                handle.resume()
            }
        }

        override fun createHandle(): TorrentFileHandle = TorrentFileHandleImpl().also {
            priorityRequests[it] = null
            openHandles.add(it)
        }

        override fun createInput(): SeekableInput {
            val input = (resolveFileOrNull() ?: runBlocking { resolveDownloadingFile() })
            val pieces = pieces
            return TorrentInput(
                RandomAccessFile(input, "r"),
                pieces,
                logicalStartOffset = offset,
                onWait = { piece ->
                    logger.info { "[TorrentDownloadControl] $torrentName: Set piece ${piece.pieceIndex} deadline to 0 because it was requested " }
                    torrentThreadTasks.submit { handle ->
                        handle.setPieceDeadline(piece.pieceIndex, 0) // 最高优先级
                        for (i in (piece.pieceIndex + 1..piece.pieceIndex + 3)) {
                            if (i < pieces.size - 1) {
                                handle.setPieceDeadline( // 按请求时间的优先
                                    i,
                                    calculatePieceDeadlineByTime(i)
                                )
                            }
                        }
                    }
                },
                size = length
            )
        }

        override fun toString(): String {
            return "TorrentFileEntryImpl(index=$index, offset=$offset, length=$length, relativePath='$relativePath')"
        }
    }

    /**
     * 通过磁力链解析的初始的信息可能是不准确的
     */
    private val actualInfo: CompletableDeferred<ActualTorrentInfo> = CompletableDeferred()

    private fun actualInfo(): ActualTorrentInfo = actualInfo.getCompleted()

    val listener = object : EventListener {
        @TorrentThread
        override fun onUpdate(handle: AniTorrentHandle) {
            torrentThreadTasks.invokeAll(handle)
        }

        override val torrentName: String
            get() = this@DefaultTorrentDownloadSession.torrentName

        @TorrentThread
        @Synchronized
        override fun onEvent(event: TorrentEvent) {
            when (event) {
                is TorrentAddEvent -> {
                    logger.info { "[$torrentName] Received alert: Torrent added" }
                    val torrentHandle = event.handle

                    // Add trackers
                    trackers.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                        torrentHandle.addTracker(it)
                    }

                    // Initialize [pieces]
                    // 注意, 必须在这里初始化获取 pieces, 通过磁力链解析的可能是不准确的
                    val contents = torrentHandle.contents
                    logger.info { "[$torrentName] Torrent contents: ${contents.files.size} files" }
                    actualInfo.complete(ActualTorrentInfo(contents.createPieces(), contents.files))
                    logger.info { "[$torrentName] ActualTorrentInfo computed" }
                }

                is TorrentResumeEvent -> {
                    if (actualInfo.isCompleted) return
                    state.value = TorrentDownloadState.FetchingMetadata
                }

                is TorrentSaveResumeDataEvent -> {
                    val data = event.platformData
                    check(data is AddTorrentParams)
                    logger.info { "[$torrentName] Save resume data, encoding..." }
                    val buf = AddTorrentParams.writeResumeDataBuf(data)
                    logger.info { "[$torrentName] Save resume data, buf length = ${buf.size}" }
                    val file = saveDirectory.resolve(FAST_RESUME_FILENAME)
                    file.writeBytes(buf)
                    logger.info { "[$torrentName] Resume data saved" }
                }

                // TODO: torrent peer stats 
//                is PeerConnectAlert -> {
//                    overallStats.peerCount.getAndUpdate { it + 1 }
//                }
//
//                is PeerDisconnectedAlert -> {
//                    overallStats.peerCount.getAndUpdate { it - 1 }
//                }
                is TorrentFinishedEvent -> {
                    // https://libtorrent.org/reference-Alerts.html#:~:text=report%20issue%5D-,torrent_finished_alert,-Declared%20in%20%22
                    logger.info { "[$torrentName] Torrent finished" }
                    event.handle.saveResumeData()
                    actualInfo().onFinished(event.handle.contents)
                }

//                is FileErrorAlert -> {
//                    logger.warn { "[libtorrent] $torrentName: File error: ${alert.operation()} ${alert.error()}" }
//                }
//
//                is MetadataFailedAlert -> {
//                    logger.warn { "[libtorrent] $torrentName: Metadata failed: ${alert.error.message}" }
//                }
                is StatsUpdateEvent -> {
                    overallStats.totalBytes.value = event.totalBytes
                    overallStats.downloadedBytes.value = event.downloadedBytes
                    overallStats.downloadRate0.value = event.downloadRate
                    overallStats.uploadRate0.value = event.uploadRate
                }
            }
        }

        @TorrentThread
        override fun onPieceFinished(pieceIndex: Int) {
            actualInfo().onPieceDownloaded(pieceIndex)
        }

        @TorrentThread
        override fun onBlockDownloading(pieceIndex: Int) {
            actualInfo().onBlockDownloading(pieceIndex)
        }
    }


    private var closed = false
    override fun close() {
        if (closed) {
            return
        }
        synchronized(this) {
            if (closed) {
                return
            }
            state.value = TorrentDownloadState.Closed
            closed = true
        }

        for (openHandle in openHandles) {
            logger.info { "Closing torrent: close handle $openHandle" }
            openHandle.close()
        }

        logger.info { "Closing torrent" }

        torrentThreadTasks.submit {
            onClose(this@DefaultTorrentDownloadSession)
            logger.info { "Close torrent $torrentName: dispose handle" }
            sessionScope.cancel()
            coroutineCloseHandle?.dispose()
        }
    }

    /*
     * 注意, 目前其实种子的 saveDirectory 不会被删除. close TorrentHandle 时只会删除它们自己对应的文件.
     */
    override fun closeIfNotInUse() {
        if (openHandles.isEmpty()) {
            close()
        }
    }

    fun deleteEntireTorrentIfNotInUse() {
        if (openHandles.isEmpty() && closed) {
            saveDirectory.deleteRecursively()
            onDelete(this)
        }
    }

    private fun createPiecePriorities(): PiecePriorities {
        return object : PiecePriorities {
            //            private val priorities = Array(torrentFile().numPieces()) { Priority.IGNORE }
            private var lastPrioritizedIndexes: Collection<Int>? = null

            override fun downloadOnly(pieceIndexes: Collection<Int>) {
                if (pieceIndexes.isEmpty()) {
                    return
                }
                if (lastPrioritizedIndexes == pieceIndexes) {
                    return
                }
                logger.debug { "[TorrentDownloadControl] Prioritizing pieces: $pieceIndexes" }
                torrentThreadTasks.submit { handle ->
                    pieceIndexes.forEachIndexed { index, it ->
                        handle.setPieceDeadline(it, calculatePieceDeadlineByTime(index))
                    }
                }
                lastPrioritizedIndexes = pieceIndexes.toList()
            }
        }
    }
}

private fun calculatePieceDeadlineByTime(
    shift: Int
): Int {
    return (System.currentTimeMillis().and(0x0FFF_FFFFL).toInt() % 1000_000_000) * 100 + shift
}

private val trackers = """
udp://tracker1.itzmx.com:8080/announce
udp://moonburrow.club:6969/announce
udp://new-line.net:6969/announce
udp://opentracker.io:6969/announce
udp://tamas3.ynh.fr:6969/announce
udp://tracker.bittor.pw:1337/announce
udp://tracker.dump.cl:6969/announce
udp://tracker1.myporn.club:9337/announce
udp://tracker2.dler.org:80/announce
https://tracker.tamersunion.org:443/announce
udp://open.demonii.com:1337/announce
udp://open.stealth.si:80/announce
udp://tracker.torrent.eu.org:451/announce
udp://exodus.desync.com:6969/announce
udp://tracker.moeking.me:6969/announce
udp://explodie.org:6969/announce
udp://tracker1.bt.moack.co.kr:80/announce
udp://tracker.tiny-vps.com:6969/announce
udp://retracker01-msk-virt.corbina.net:80/announce
udp://bt1.archive.org:6969/announce

udp://tracker2.itzmx.com:6961/announce

udp://tracker3.itzmx.com:6961/announce

udp://tracker4.itzmx.com:2710/announce

http://tracker1.itzmx.com:8080/announce

http://tracker2.itzmx.com:6961/announce

http://tracker3.itzmx.com:6961/announce

http://tracker4.itzmx.com:2710/announce

udp://tracker.opentrackr.org:1337/announce

http://tracker.opentrackr.org:1337/announce
                        """.trimIndent().lineSequence().filter { it.isNotBlank() }.joinToString()