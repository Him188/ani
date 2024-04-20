package me.him188.ani.app.videoplayer

import androidx.annotation.OptIn
import androidx.annotation.UiThread
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.platform.Context
import me.him188.ani.app.videoplayer.data.VideoData
import me.him188.ani.app.videoplayer.data.VideoProperties
import me.him188.ani.app.videoplayer.data.VideoSource
import me.him188.ani.app.videoplayer.media.VideoDataDataSource
import me.him188.ani.app.videoplayer.ui.state.AbstractPlayerState
import me.him188.ani.app.videoplayer.ui.state.PlaybackState
import me.him188.ani.app.videoplayer.ui.state.PlayerState
import me.him188.ani.app.videoplayer.ui.state.PlayerStateFactory
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds


class ExoPlayerStateFactory : PlayerStateFactory {
    @OptIn(UnstableApi::class)
    override fun create(context: Context, parentCoroutineContext: CoroutineContext): PlayerState =
        ExoPlayerState(context, parentCoroutineContext)
}


@OptIn(UnstableApi::class)
internal class ExoPlayerState @UiThread constructor(
    context: Context,
    parentCoroutineContext: CoroutineContext
) : AbstractPlayerState(),
    AutoCloseable {
    private val backgroundScope = CoroutineScope(
        parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job])
    ).apply {
        coroutineContext.job.invokeOnCompletion {
            close()
        }
    }

    override val videoSource: MutableStateFlow<VideoSource<*>?> = MutableStateFlow(null)

    private class OpenedVideoSource(
        val videoSource: VideoSource<*>,
        val data: VideoData,
        val releaseResource: () -> Unit,
        val mediaSourceFactory: ProgressiveMediaSource.Factory,
    )

    /**
     * Currently playing resource that should be closed when the controller is closed.
     * @see setVideoSource
     */
    private val openResource = MutableStateFlow<OpenedVideoSource?>(null)

    override val videoData: Flow<VideoData?> = openResource.map {
        it?.data
    }

    override suspend fun setVideoSource(source: VideoSource<*>?) {
        if (source == null) {
            logger.info { "setVideoSource: Cleaning up player since source is null" }
            withContext(Dispatchers.Main.immediate) {
                player.stop()
                player.clearMediaItems()
            }
            this.videoSource.value = null
            this.openResource.value = null
            return
        }

        val previousResource = openResource.value
        if (source == previousResource?.videoSource) {
            return
        }

        openResource.value = null
        previousResource?.releaseResource?.invoke()

        val opened = openSource(source)

        try {
            logger.info { "Initializing player with VideoSource: $source" }
            val item = opened.mediaSourceFactory.createMediaSource(MediaItem.fromUri(source.uri))
            withContext(Dispatchers.Main.immediate) {
                player.setMediaSource(item)
                player.prepare()
                player.play()
            }
            logger.info { "ExoPlayer is now initialized with media and will play when ready" }
        } catch (e: Throwable) {
            logger.error(e) { "ExoPlayer failed to initialize" }
            opened.releaseResource()
            throw e
        }

        this.openResource.value = opened
    }

    private suspend fun openSource(source: VideoSource<*>): OpenedVideoSource {
        val data = source.open()
        return OpenedVideoSource(
            source,
            data,
            releaseResource = {
                data.close()
            },
            mediaSourceFactory = ProgressiveMediaSource.Factory { VideoDataDataSource(data) }
        )
    }

    val player = kotlin.run {
        ExoPlayer.Builder(context).apply {}.build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    logger.warn("ExoPlayer error: ${error.errorCodeName}")
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    super.onVideoSizeChanged(videoSize)
                    updateVideoProperties()
                }

                private fun updateVideoProperties(): Boolean {
                    val video = videoFormat ?: return false
                    val audio = audioFormat ?: return false
                    val data = openResource.value?.data ?: return false
                    videoProperties.value = VideoProperties(
                        title = mediaMetadata.title?.toString(),
                        heightPx = video.height,
                        widthPx = video.width,
                        videoBitrate = video.bitrate,
                        audioBitrate = audio.bitrate,
                        frameRate = video.frameRate,
                        durationMillis = duration,
                        fileLengthBytes = data.fileLength,
                        fileHash = data.hash,
                        filename = data.filename,
                    )
                    return true
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            state.value = PlaybackState.PAUSED_BUFFERING
                            isBuffering.value = true
                        }

                        Player.STATE_ENDED -> {
                            state.value = PlaybackState.FINISHED
                            isBuffering.value = false
                        }

                        Player.STATE_IDLE -> {
                            state.value = PlaybackState.READY
                            isBuffering.value = false
                        }

                        Player.STATE_READY -> {
                            state.value = PlaybackState.READY
                            isBuffering.value = false
                        }
                    }
                    updateVideoProperties()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        state.value = PlaybackState.PLAYING
                        isBuffering.value = false
                    } else {
                        state.value = PlaybackState.PAUSED
                    }
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    super.onPlaybackParametersChanged(playbackParameters)
                    playbackSpeed.value = playbackParameters.speed
                }
            })
        }
    }

    override val state: MutableStateFlow<PlaybackState> = MutableStateFlow(PlaybackState.PAUSED_BUFFERING)
    override val isBuffering: MutableStateFlow<Boolean> = MutableStateFlow(false) // 需要单独状态, 因为要用户可能会覆盖 [state] 

    override val videoProperties = MutableStateFlow<VideoProperties?>(null)
    override val bufferedPercentage = MutableStateFlow(0)

    override fun seekTo(positionMillis: Long) {
        player.seekTo(positionMillis)
    }

    override val currentPositionMillis: MutableStateFlow<Long> = MutableStateFlow(0)
    override val playProgress: Flow<Float> =
        combine(videoProperties.filterNotNull(), currentPositionMillis) { properties, duration ->
            if (properties.durationMillis == 0L) {
                return@combine 0f
            }
            (duration / properties.durationMillis).toFloat().coerceIn(0f, 1f)
        }

    init {
        backgroundScope.launch(Dispatchers.Main) {
            while (currentCoroutineContext().isActive) {
                currentPositionMillis.value = player.currentPosition
                bufferedPercentage.value = player.bufferedPercentage
                delay(0.1.seconds) // 20 fps
            }
        }
    }

    override fun pause() {
        player.playWhenReady = false
        player.pause()
    }

    override fun resume() {
        player.playWhenReady = true
        player.play()
    }

    override val playbackSpeed: MutableStateFlow<Float> = MutableStateFlow(1f)

    @Volatile
    private var closed = false

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        player.stop()
        player.release()
        openResource.value?.releaseResource?.invoke()
        backgroundScope.cancel()
    }

    override fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    private companion object {
        val logger = logger(ExoPlayerState::class)
    }
}
