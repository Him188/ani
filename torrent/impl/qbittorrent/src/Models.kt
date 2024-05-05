package me.him188.ani.app.torrent.qbittorrent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.him188.ani.app.torrent.api.files.FilePriority


// Generated by ChatGPT

@Serializable
data class QBTorrent(
    /**
     * Time (Unix Epoch) when the torrent was added to the client
     */
    @SerialName("added_on") val addedOn: Long,
    /**
     * Amount of data left to download (bytes)
     */
    @SerialName("amount_left") val amountLeft: Int,
    /**
     * Whether this torrent is managed by Automatic Torrent Management
     */
    @SerialName("auto_tmm") val autoTmm: Boolean,
    /**
     * Percentage of file pieces currently available
     */
    @SerialName("availability") val availability: Float,
    /**
     * Category of the torrent
     */
    @SerialName("category") val category: String,
    /**
     * Amount of transfer data completed (bytes)
     */
    @SerialName("completed") val completed: Int,
    /**
     * Time (Unix Epoch) when the torrent completed
     */
    @SerialName("completion_on") val completionOn: Long,
    /**
     * Absolute path of torrent content (root path for multifile torrents, absolute file path for singlefile torrents)
     */
    @SerialName("content_path") val contentPath: String,
    /**
     * Torrent download speed limit (bytes/s). -1 if unlimited.
     */
    @SerialName("dl_limit") val dlLimit: Int,
    /**
     * Torrent download speed (bytes/s)
     */
    @SerialName("dlspeed") val dlSpeed: Long,
    /**
     * Amount of data downloaded
     */
    @SerialName("downloaded") val downloaded: Long,
    /**
     * Amount of data downloaded this session
     */
    @SerialName("downloaded_session") val downloadedSession: Int,
    /**
     * Torrent ETA (seconds)
     */
    @SerialName("eta") val eta: Int,
    /**
     * True if first last piece are prioritized
     */
    @SerialName("f_l_piece_prio") val firstLastPiecePrio: Boolean,
    /**
     * True if force start is enabled for this torrent
     */
    @SerialName("force_start") val forceStart: Boolean,
    /**
     * Torrent hash
     */
    @SerialName("hash") val hash: String,
    /**
     * Last time (Unix Epoch) when a chunk was downloaded/uploaded
     */
    @SerialName("last_activity") val lastActivity: Long,
    /**
     * Magnet URI corresponding to this torrent
     */
    @SerialName("magnet_uri") val magnetUri: String,
    /**
     * Maximum share ratio until torrent is stopped from seeding/uploading
     */
    @SerialName("max_ratio") val maxRatio: Float,
    /**
     * Maximum seeding time (seconds) until torrent is stopped from seeding
     */
    @SerialName("max_seeding_time") val maxSeedingTime: Int,
    /**
     * Torrent name
     */
    @SerialName("name") val name: String,
    /**
     * Number of seeds in the swarm
     */
    @SerialName("num_complete") val numComplete: Int,
    /**
     * Number of leechers in the swarm
     */
    @SerialName("num_incomplete") val numIncomplete: Int,
    /**
     * Number of leechers connected to
     */
    @SerialName("num_leechs") val numLeechs: Int,
    /**
     * Number of seeds connected to
     */
    @SerialName("num_seeds") val numSeeds: Int,
    /**
     * Torrent priority. Returns -1 if queuing is disabled or torrent is in seed mode
     */
    @SerialName("priority") val priority: Int,
    /**
     * Torrent progress (percentage/100)
     */
    @SerialName("progress") val progress: Float,
    /**
     * Torrent share ratio. Max ratio value: 9999.
     */
    @SerialName("ratio") val ratio: Float,
    /**
     * (what is different from max_ratio?)
     */
    @SerialName("ratio_limit") val ratioLimit: Float,
    /**
     * Path where this torrent's data is stored
     */
    @SerialName("save_path") val savePath: String,
    /**
     * Torrent elapsed time while complete (seconds)
     */
    @SerialName("seeding_time") val seedingTime: Int,
    /**
     * (what is different from max_seeding_time?) seeding_time_limit is a per torrent setting, when Automatic Torrent Management is disabled, furthermore then max_seeding_time is set to seeding_time_limit for this torrent. If Automatic Torrent Management is enabled, the value is -2. And if max_seeding_time is unset it have a default value -1.
     */
    @SerialName("seeding_time_limit") val seedingTimeLimit: Int,
    /**
     * Time (Unix Epoch) when this torrent was last seen complete
     */
    @SerialName("seen_complete") val seenComplete: Long,
    /**
     * True if sequential download is enabled
     */
    @SerialName("seq_dl") val seqDl: Boolean,
    /**
     * Total size (bytes) of files selected for download
     */
    @SerialName("size") val size: Long,
    /**
     * Torrent state. See table here below for the possible values
     */
    @SerialName("state") val state: TorrentState,
    /**
     * True if super seeding is enabled
     */
    @SerialName("super_seeding") val superSeeding: Boolean,
    /**
     * Comma-concatenated tag list of the torrent
     */
    @SerialName("tags") val tags: String,
    /**
     * Total active time (seconds)
     */
    @SerialName("time_active") val timeActive: Long,
    /**
     * Total size (bytes) of all file in this torrent (including unselected ones)
     */
    @SerialName("total_size") val totalSize: Long,
    /**
     * The first tracker with working status. Returns empty string if no tracker is working.
     */
    @SerialName("tracker") val tracker: String,
    /**
     * Torrent upload speed limit (bytes/s). -1 if unlimited.
     */
    @SerialName("up_limit") val upLimit: Long,
    /**
     * Amount of data uploaded
     */
    @SerialName("uploaded") val uploaded: Long,
    /**
     * Amount of data uploaded this session
     */
    @SerialName("uploaded_session") val uploadedSession: Int,
    /**
     * Torrent upload speed (bytes/s)
     */
    @SerialName("upspeed") val upSpeed: Long
)

@Serializable
enum class TorrentState {
    @SerialName("error")
    ERROR,

    @SerialName("missingFiles")
    MISSING_FILES,

    @SerialName("uploading")
    UPLOADING,

    @SerialName("pausedUP")
    PAUSED_UP,

    @SerialName("queuedUP")
    QUEUED_UP,

    @SerialName("stalledUP")
    STALLED_UP,

    @SerialName("checkingUP")
    CHECKING_UP,

    @SerialName("forcedUP")
    FORCED_UP,

    @SerialName("allocating")
    ALLOCATING,

    @SerialName("downloading")
    DOWNLOADING,

    @SerialName("metaDL")
    META_DL,

    @SerialName("pausedDL")
    PAUSED_DL,

    @SerialName("queuedDL")
    QUEUED_DL,

    @SerialName("stalledDL")
    STALLED_DL,

    @SerialName("checkingDL")
    CHECKING_DL,

    @SerialName("forcedDL")
    FORCED_DL,

    @SerialName("checkingResumeData")
    CHECKING_RESUME_DATA,

    @SerialName("moving")
    MOVING,

    @SerialName("unknown")
    UNKNOWN
}

enum class QBPieceState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED
}

@Serializable
data class QBFile(
    /**
     * File index
     */
    @SerialName("index") val index: Int,
    /**
     * File name (including relative path)
     */
    @SerialName("name") val name: String,
    /**
     * File size (bytes)
     */
    @SerialName("size") val size: Long,
    /**
     * File progress (percentage/100)
     */
    @SerialName("progress") val progress: Float,
    /**
     * File priority. See possible values here below
     */
    @SerialName("priority") private val priorityRaw: Int, // 0-7
    /**
     * True if file is seeding/complete
     */
    @SerialName("is_seed") val isSeed: Boolean,
    /**
     * The first number is the starting piece index and the second number is the ending piece index (inclusive)
     */
    @SerialName("piece_range") private val pieceRangeRaw: List<Int>,
    /**
     * Percentage of file pieces currently available (percentage/100)
     */
    @SerialName("availability") val availability: Float
) {
    val priority: QBFilePriority
        get() = QBFilePriority.entries.first {
            it.value == priorityRaw
        }

    @Transient
    val pieceRange: IntRange = pieceRangeRaw[0]..pieceRangeRaw[1]
}

enum class QBFilePriority(
    val value: Int
) {
    DO_NOT_DOWNLOAD(0),
    NORMAL_PRIORITY(1),
    HIGH_PRIORITY(6),
    MAXIMAL_PRIORITY(7)
}

fun FilePriority.toQBFilePriority(): QBFilePriority = when (this) {
    FilePriority.IGNORE -> QBFilePriority.DO_NOT_DOWNLOAD
    FilePriority.NORMAL -> QBFilePriority.NORMAL_PRIORITY
    FilePriority.HIGH -> QBFilePriority.HIGH_PRIORITY
    FilePriority.LOW -> QBFilePriority.NORMAL_PRIORITY
}

// https://github.com/qbittorrent/qBittorrent/wiki/WebUI-API-(qBittorrent-4.1)#get-global-transfer-info
@Serializable
data class GlobalTransferInfo(
    /**
     * Global download rate (bytes/s)
     */
    @SerialName("dl_info_speed") val dlInfoSpeed: Long,
    /**
     * Data downloaded this session (bytes)
     */
    @SerialName("dl_info_data") val dlInfoData: Long,
    /**
     * Global upload rate (bytes/s)
     */
    @SerialName("up_info_speed") val upInfoSpeed: Long,
    /**
     * Data uploaded this session (bytes)
     */
    @SerialName("up_info_data") val upInfoData: Long,
    /**
     * Download rate limit (bytes/s)
     */
    @SerialName("dl_rate_limit") val dlRateLimit: Long,
    /**
     * Upload rate limit (bytes/s)
     */
    @SerialName("up_rate_limit") val upRateLimit: Long,
    /**
     * DHT nodes connected to
     */
    @SerialName("dht_nodes") val dhtNodes: Long,
    /**
     * Connection status
     */
    @SerialName("connection_status") val connectionStatus: ConnectionStatus,
)

@Serializable
enum class ConnectionStatus {
    @SerialName("connected")
    CONNECTED,

    @SerialName("firewalled")
    FIREWALLED,

    @SerialName("disconnected")
    DISCONNECTED
}
