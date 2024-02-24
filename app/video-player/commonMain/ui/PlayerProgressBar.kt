package me.him188.ani.app.videoplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.theme.aniDarkColorTheme
import me.him188.ani.app.videoplayer.PlayerController
import me.him188.ani.datasources.bangumi.processing.fixToString
import moe.tlaster.precompose.flow.collectAsStateWithLifecycle
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PlayerProgressController(
    controller: PlayerController,
    onClickFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier, verticalAlignment = Alignment.CenterVertically
    ) {
        // 播放 / 暂停按钮
        val state by controller.state.collectAsStateWithLifecycle(null)
        Box(Modifier.padding(horizontal = 8.dp).size(32.dp)) {
            IconButton(
                onClick = {
                    if (state?.isPlaying == true) {
                        controller.pause()
                    } else {
                        controller.resume()
                    }
                },
            ) {
                if (state?.isPlaying == true) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                }
            }
        }

        val bufferProgress by controller.bufferProgress.collectAsStateWithLifecycle()
        val videoProperties by controller.videoProperties.collectAsStateWithLifecycle(null)
        val playedDuration by controller.playedDuration.collectAsStateWithLifecycle()
        val sliderPosition by controller.previewingOrPlayingProgress.collectAsStateWithLifecycle(0f)

        val playedDurationSeconds = remember(playedDuration) { playedDuration.inWholeSeconds }
        val totalDurationSeconds = remember(videoProperties) { videoProperties?.duration?.inWholeSeconds ?: 0L }
        val totalDurationMillis = remember(videoProperties) { videoProperties?.duration?.inWholeMilliseconds ?: 0L }

        Text(
            text = renderSeconds(playedDurationSeconds, totalDurationSeconds),
            Modifier.padding(end = 8.dp),
            style = MaterialTheme.typography.labelSmall,
        )

        Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
//            LinearProgressIndicator(
//                modifier = Modifier.padding(horizontal = 8.dp).matchParentSize(),
//                progress = bufferProgress,
//                color = aniDarkColorTheme().onSurface,
//                trackColor = aniDarkColorTheme().surface,
//                strokeCap = StrokeCap.Round,
//            )
//            LinearProgressIndicator(
//                modifier = Modifier.matchParentSize().alpha(0.8f),
//                progress = playProgress,
//                color = aniDarkColorTheme().primary,
//                trackColor = aniDarkColorTheme().surface,
//                strokeCap = StrokeCap.Round,
//            )
            Slider(
                value = sliderPosition,
                valueRange = 0f..1f,
                onValueChange = {
                    controller.setPreviewingProgress(it)
                    controller.seekTo((it * totalDurationMillis).toLong().milliseconds)
                },
                track = {
                    SliderDefaults.Track(
                        it,
                        colors = SliderDefaults.colors(
                            activeTrackColor = aniDarkColorTheme().secondary,
                            inactiveTrackColor = aniDarkColorTheme().surface,
                        )
                    )
                },
                modifier = Modifier.alpha(0.8f).matchParentSize(),
            )
        }

        Box(Modifier.padding(horizontal = 8.dp).size(32.dp)) {
            IconButton(
                onClick = onClickFullScreen,
            ) {
                Icon(Icons.Default.Fullscreen, contentDescription = null)
            }
        }
    }
}

@Stable
private fun renderSeconds(played: Long, length: Long?): String {
    if (length == null) {
        return "00:${played.fixToString(2)} / 00:00"
    }
    return if (played < 60 && length < 60) {
        "00:${played.fixToString(2)} / 00:${length.fixToString(2)}"
    } else if (played < 3600 && length < 3600) {
        val startM = (played / 60).fixToString(2)
        val startS = (played % 60).fixToString(2)
        val endM = (length / 60).fixToString(2)
        val endS = (length % 60).fixToString(2)
        """$startM:$startS / $endM:$endS"""
    } else {
        val startH = (played / 3600).fixToString(2)
        val startM = (played % 3600 / 60).fixToString(2)
        val startS = (played % 60).fixToString(2)
        val endH = (length / 3600).fixToString(2)
        val endM = (length % 3600 / 60).fixToString(2)
        val endS = (length % 60).fixToString(2)
        """$startH:$startM:$startS / $endH:$endM:$endS"""
    }
}
