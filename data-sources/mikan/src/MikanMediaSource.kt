/*
 * Ani
 * Copyright (C) 2022-2024 Him188
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.him188.ani.datasources.mikan

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.charsets.decode
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.streams.asInput
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.paging.PageBasedPagedSource
import me.him188.ani.datasources.api.paging.Paged
import me.him188.ani.datasources.api.paging.PagedSource
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.DownloadSearchQuery
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import me.him188.ani.datasources.api.source.TopicMediaSource
import me.him188.ani.datasources.api.source.applyMediaSourceConfig
import me.him188.ani.datasources.api.topic.FileSize.Companion.Zero
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.Topic
import me.him188.ani.datasources.api.topic.TopicCategory
import me.him188.ani.datasources.api.topic.matches
import me.him188.ani.datasources.api.topic.titles.RawTitleParser
import me.him188.ani.datasources.api.topic.titles.parse
import me.him188.ani.datasources.api.topic.titles.toTopicDetails
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class MikanCNMediaSource(
    config: MediaSourceConfig,
) : AbstractMikanMediaSource(ID, config, "https://mikanime.tv") {
    class Factory : MediaSourceFactory {
        override val mediaSourceId: String get() = ID
        override fun create(config: MediaSourceConfig): MediaSource =
            MikanCNMediaSource(config)
    }

    companion object {
        const val ID = "mikan-mikanime-tv"
    }
}

class MikanMediaSource(
    config: MediaSourceConfig,
) : AbstractMikanMediaSource(ID, config, "https://mikanani.me") {
    class Factory : MediaSourceFactory {
        override val mediaSourceId: String get() = ID
        override fun create(config: MediaSourceConfig): MediaSource = MikanMediaSource(config)
    }

    companion object {
        const val ID = "mikan"
    }
}

abstract class AbstractMikanMediaSource(
    override val mediaSourceId: String,
    private val config: MediaSourceConfig,
    baseUrl: String,
) : TopicMediaSource() {
    private val logger = logger(this::class)

    private val baseUrl = baseUrl.removeSuffix("/")

    override suspend fun checkConnection(): ConnectionStatus {
        return try {
            client.get(baseUrl).run {
                check(status.isSuccess()) { "Request failed: $this" }
            }
            ConnectionStatus.SUCCESS
        } catch (e: Exception) {
            logger.error(e) { "Failed to connect to $baseUrl" }
            ConnectionStatus.FAILED
        }
    }

    private val client = createHttpClient {
        applyMediaSourceConfig(config)
        Logging {
            logger = object : io.ktor.client.plugins.logging.Logger {
                override fun log(message: String) {
                    this@AbstractMikanMediaSource.logger.info { message }
                }
            }
            level = LogLevel.INFO
        }
    }

    override suspend fun startSearch(query: DownloadSearchQuery): PagedSource<Topic> {
        return PageBasedPagedSource(initialPage = 1) {
            val resp = client.get("$baseUrl/RSS/Search") {
                parameter("searchstr", query.keywords?.take(10))
            }
            val document: Document = Jsoup.parse(resp.bodyAsChannel().toInputStream(), "UTF-8", baseUrl)
            parseDocument(document)
                .filter { query.matches(it) }
                .run {
                    Paged(size, false, this) // mikan 直接返回全部
                }
        }
    }

    // 2024-03-31T10:27:49.932
    private val linkRegex = Regex("$baseUrl/Home/Episode/(.+)")

    private fun parseDocument(document: Document): List<Topic> {
        val items = document.getElementsByTag("item")

        return items.map { element ->
            val title = element.getElementsByTag("title").text()

            val details = RawTitleParser.getDefault().parse(title, null)

            Topic(
                topicId = element.getElementsByTag("guid").text().substringAfterLast("/"),
                publishedTimeMillis = element.getElementsByTag("pubDate").text().let {
                    runCatching {
                        ZonedDateTime.of(
                            LocalDateTime.parse(it),
                            ZoneId.of("UTC+8"),
                        ).toEpochSecond() * 1000
                    }.getOrNull()
                },
                category = TopicCategory.ANIME,
                rawTitle = title,
                commentsCount = 0,
                downloadLink = ResourceLocation.HttpTorrentFile(element.getElementsByTag("enclosure").attr("url")),
                size = element.getElementsByTag("contentLength").text().toLongOrNull()?.bytes ?: Zero,
                alliance = title.trim().split("]", "】").getOrNull(0).orEmpty().removePrefix("[").removePrefix("【")
                    .trim(),
                author = null,
                details = details.toTopicDetails(),
                originalLink = run {
                    element.getElementsByTag("link").text().takeIf { it.isNotBlank() }?.let { return@run it }
                    // Note: It looks like Jsoup failed to parse the xml. Debug and print `element` to see details.
                    linkRegex.find(element.toString())?.value // This should work well
                } ?: "",
            )
        }
    }
}

private fun createHttpClient(
    clientConfig: HttpClientConfig<*>.() -> Unit = {},
) = HttpClient {
    install(HttpRequestRetry) {
        maxRetries = 1
        delayMillis { 1000 }
    }
    install(HttpCookies)
    install(HttpTimeout) {
        requestTimeoutMillis = 5000
    }
    install(UserAgent) {
        agent = "him188/ani (https://github.com/Him188/ani)"
    }
    clientConfig()
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
        register(
            ContentType.Text.Xml,
            object : ContentConverter {
                override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
                    if (typeInfo.type.qualifiedName != Document::class.qualifiedName) return null
                    content.awaitContent()
                    val decoder = Charsets.UTF_8.newDecoder()
                    val string = decoder.decode(content.toInputStream().asInput())
                    return Jsoup.parse(string, charset.name())
                }

                override suspend fun serializeNullable(
                    contentType: ContentType,
                    charset: Charset,
                    typeInfo: TypeInfo,
                    value: Any?
                ): OutgoingContent? {
                    return null
                }
            },
        ) {}
    }
}