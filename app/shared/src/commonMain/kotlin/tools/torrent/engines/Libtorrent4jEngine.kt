package me.him188.ani.app.tools.torrent.engines

import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.platform.getAniUserAgent
import me.him188.ani.app.platform.versionCode
import me.him188.ani.app.tools.torrent.AbstractTorrentEngine
import me.him188.ani.app.tools.torrent.TorrentEngineConfig
import me.him188.ani.app.tools.torrent.TorrentEngineType
import me.him188.ani.app.torrent.api.HttpFileDownloader
import me.him188.ani.app.torrent.api.TorrentDownloaderConfig
import me.him188.ani.app.torrent.libtorrent4j.Libtorrent4jTorrentDownloader
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.utils.ktor.createDefaultHttpClient
import java.io.File


@Serializable
class Libtorrent4jConfig(
    override val enabled: Boolean = true,
    @Transient val placeholder: Int = 0,
) : TorrentEngineConfig {
    companion object {
        val Default = Libtorrent4jConfig()
    }
}


class Libtorrent4jEngine(
    scope: CoroutineScope,
    config: Flow<Libtorrent4jConfig>,
    private val saveDir: File,
) : AbstractTorrentEngine<Libtorrent4jTorrentDownloader, Libtorrent4jConfig>(
    scope,
    TorrentEngineType.Libtorrent4j,
    config
) {
    override val location: MediaSourceLocation get() = MediaSourceLocation.Local
    override val isSupported: Flow<Boolean>
        get() = flowOf(true)
    override val isEnabled: Flow<Boolean> get() = config.map { it.enabled }
    override suspend fun testConnection(): Boolean = true

    override suspend fun newInstance(config: Libtorrent4jConfig): Libtorrent4jTorrentDownloader {
        val client = createDefaultHttpClient {
            install(UserAgent) {
                agent = getAniUserAgent()
            }
            expectSuccess = true
        }

        return Libtorrent4jTorrentDownloader(
            parentCoroutineContext = scope.coroutineContext,
            cacheDirectory = saveDir,
            downloadFile = client.asHttpFileDownloader(),
            config = TorrentDownloaderConfig(
                peerFingerprint = computeTorrentFingerprint(),
                userAgent = computeTorrentUserAgent(),
                isDebug = currentAniBuildConfig.isDebug,
            ),
        )
    }
}

private fun computeTorrentFingerprint(
    versionCode: String = currentAniBuildConfig.versionCode,
): String = "-aniLT${versionCode}-"

private fun computeTorrentUserAgent(
    versionCode: String = currentAniBuildConfig.versionCode,
): String = "ani_libtorrent/${versionCode}"

internal fun HttpClient.asHttpFileDownloader(): HttpFileDownloader = object : HttpFileDownloader {
    override suspend fun download(url: String): ByteArray = get(url).readBytes()
    override fun close() {
        this@asHttpFileDownloader.close()
    }

    override fun toString(): String {
        return "HttpClientAsHttpFileDownloader(client=$this@asHttpFileDownloader)"
    }
}