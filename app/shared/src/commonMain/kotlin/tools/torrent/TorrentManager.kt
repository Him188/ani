package me.him188.ani.app.tools.torrent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import me.him188.ani.app.data.repositories.SettingsRepository
import me.him188.ani.app.platform.Platform
import me.him188.ani.app.tools.torrent.engines.Libtorrent4jEngine
import me.him188.ani.app.tools.torrent.engines.QBittorrentEngine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import kotlin.coroutines.CoroutineContext

/**
 * 管理本地 BT 下载器的实现. 根据配置选择不同的下载器.
 *
 * 目前支持的下载实现:
 * - libtorrent4j (内嵌)
 * - qBittorrent (本机局域网).
 */
interface TorrentManager {
    val libtorrent4j: Libtorrent4jEngine
    val qBittorrent: QBittorrentEngine

    val engines: List<TorrentEngine>
}

enum class TorrentEngineType(
    val id: String,
) {
    Libtorrent4j("libtorrent4j"),
    QBittorrent("qbittorrent"),
}

class TorrentDownloaderInitializationException(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

class TorrentDownloaderManagerError(
    val exception: Throwable,
)

class DefaultTorrentManager(
    parentCoroutineContext: CoroutineContext,
    private val saveDir: (type: TorrentEngineType) -> File,
) : TorrentManager, KoinComponent {
    private val settingsRepository: SettingsRepository by inject()

    private val libtorrent4jConfig get() = settingsRepository.libtorrent4jConfig.flow
    private val qbittorrentConfig get() = settingsRepository.qBittorrentConfig.flow

    private val scope = CoroutineScope(parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job]))

    override val libtorrent4j: Libtorrent4jEngine by lazy {
        Libtorrent4jEngine(scope, libtorrent4jConfig, saveDir(TorrentEngineType.Libtorrent4j))
    }

    override val qBittorrent: QBittorrentEngine by lazy {
        QBittorrentEngine(scope, qbittorrentConfig, saveDir(TorrentEngineType.QBittorrent))
    }

    override val engines: List<TorrentEngine> by lazy(LazyThreadSafetyMode.NONE) {
        // 注意, 是故意只启用一个下载器的, 因为每个下载器都会创建一个 DirectoryMediaCacheStorage
        // 并且使用相同的 mediaSourceId: MediaCacheManager.LOCAL_FS_MEDIA_SOURCE_ID.
        // 搜索数据源时会使用 mediaSourceId 作为 map key, 导致总是只会用一个 storage.
        // 
        // 如果要支持多个, 需要考虑将所有 storage 合并成一个 MediaSource.

        when (Platform.currentPlatform) {
            is Platform.Desktop -> listOf(libtorrent4j)
            Platform.Android -> listOf(libtorrent4j)
        }
    }
}
