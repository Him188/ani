package me.him188.ani.app.data.media.resolver

import me.him188.ani.app.videoplayer.data.VideoSource
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media

/**
 * Resolves the video description [Media] into a [VideoSource] which can be opened to play.
 */
interface VideoSourceResolver {
    /**
     * Checks if this resolver supports the given media's [Media.download].
     */
    fun supports(media: Media): Boolean

    /**
     * Resolves the given media into a [VideoSource] which can be opened to play.
     *
     * @param episode Target episode to resolve, because a media can have multiple episodes.
     *
     * @throws UnsupportedOperationException if the media cannot be resolved.
     * Use [supports] to check if the media can be resolved.
     */
    suspend fun resolve(media: Media, episode: EpisodeMetadata): VideoSource<*>

    companion object {
        fun from(vararg resolvers: VideoSourceResolver): VideoSourceResolver {
            return ChainedVideoSourceResolver(resolvers.toList())
        }

        fun from(resolvers: Iterable<VideoSourceResolver>): VideoSourceResolver {
            return ChainedVideoSourceResolver(resolvers.toList())
        }
    }
}

data class EpisodeMetadata(
    val title: String,
    val sort: EpisodeSort,
)

class UnsupportedMediaException(
    val media: Media,
) : UnsupportedOperationException("Media is not supported: $media")

private class ChainedVideoSourceResolver(
    private val resolvers: List<VideoSourceResolver>
) : VideoSourceResolver {
    override fun supports(media: Media): Boolean {
        return resolvers.any { it.supports(media) }
    }

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): VideoSource<*> {
        return resolvers.firstOrNull { it.supports(media) }?.resolve(media, episode)
            ?: throw UnsupportedMediaException(media)
    }
}
