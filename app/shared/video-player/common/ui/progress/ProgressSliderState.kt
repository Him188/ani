package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import me.him188.ani.app.ui.foundation.theme.aniDarkColorTheme
import me.him188.ani.app.ui.foundation.theme.looming
import me.him188.ani.app.ui.foundation.theme.weaken
import me.him188.ani.app.videoplayer.ui.state.PlayerState
import me.him188.ani.datasources.bangumi.processing.fixToString
import moe.tlaster.precompose.flow.collectAsStateWithLifecycle
import kotlin.math.roundToLong

/**
 * @see ProgressSlider
 */
@Stable
class ProgressSliderState(
    currentPositionMillis: State<Long>,
    totalDurationMillis: State<Long>,
    private val onPreview: (positionMillis: Long) -> Unit,
    private val onPreviewFinished: (positionMillis: Long) -> Unit,
) {
    val currentPositionMillis: Long by currentPositionMillis
    val totalDurationMillis: Long by totalDurationMillis

    private var previewPositionMillis: Long by mutableStateOf(-1L)

    private fun previewPosition(positionMillis: Long) {
        previewPositionMillis = positionMillis
        onPreview(positionMillis)
    }

    /**
     * Sets the slider to move to the given position.
     * [onPreview] will be triggered.
     */
    fun previewPositionRatio(ratio: Float) {
        previewPosition((totalDurationMillis * ratio).roundToLong())
    }

    /**
     * The position to display in the progress slider. If a preview is active, this will be the preview position.
     */
    private val displayPositionMillis: Long by derivedStateOf {
        val previewPositionMillis = previewPositionMillis
        if (previewPositionMillis != -1L) {
            previewPositionMillis
        } else {
            currentPositionMillis.value
        }
    }

    /**
     * The ratio of the current display position to the total duration. Range is `0..1`
     */
    val displayPositionRatio by derivedStateOf {
        val total = this.totalDurationMillis
        if (total == 0L) {
            return@derivedStateOf 0f
        }
        displayPositionMillis.toFloat() / total
    }

    fun finishPreview() {
        onPreviewFinished(previewPositionMillis)
        previewPositionMillis = -1L
    }
}

@Composable
fun ProgressIndicator(
    state: ProgressSliderState,
    modifier: Modifier = Modifier
) {
    val text by remember {
        derivedStateOf {
            renderSeconds(state.currentPositionMillis / 1000, state.totalDurationMillis / 1000)
        }
    }
    val reserve by remember {
        derivedStateOf {
            renderSecondsReserve(state.totalDurationMillis / 1000)
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(reserve, Modifier.alpha(0f)) // fix width
        Text(
            text = text,
            style = LocalTextStyle.current.copy(
                color = Color.DarkGray,
                drawStyle = Stroke(
                    miter = 3f,
                    width = 2f,
                    join = StrokeJoin.Round,
                ),
            ),
        ) // border
        Text(text = text)
    }
}

/**
 * The slider to control the progress of a video, with a preview feature.
 */
// Preview: PreviewVideoScaffoldFullscreen
@Composable
fun ProgressSlider(
    state: ProgressSliderState,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val interactionSource = remember { MutableInteractionSource() }
        MaterialTheme(aniDarkColorTheme()) {
            Slider(
                value = state.displayPositionRatio,
                valueRange = 0f..1f,
                onValueChange = { state.previewPositionRatio(it) },
                interactionSource = interactionSource,
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = interactionSource,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                        ),
                        enabled = true,
                        thumbSize = DpSize(4.dp, 22.dp), // was 44.dp
                    )
                },
                track = {
                    SliderDefaults.Track(
                        it,
                        Modifier.height(8.dp),
                        colors = SliderDefaults.colors(
                            inactiveTrackColor = MaterialTheme.colorScheme.background.weaken(),
                            activeTrackColor = MaterialTheme.colorScheme.onBackground.looming(),
                        ),
                        thumbTrackGapSize = 3.dp,
                    )
                },
                onValueChangeFinished = {
                    state.finishPreview()
                },
                modifier = Modifier.height(24.dp),
            )
        }
    }
}

@Composable
fun rememberProgressSliderState(
    playerState: PlayerState,
    onPreview: (positionMillis: Long) -> Unit,
    onPreviewFinished: (positionMillis: Long) -> Unit,
): ProgressSliderState {
    val currentPosition = playerState.currentPositionMillis.collectAsStateWithLifecycle()
    val totalDuration = remember(playerState) {
        playerState.videoProperties.filterNotNull().map { it.durationMillis }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(0L)

    val onPreviewUpdated by rememberUpdatedState(onPreview)
    val onPreviewFinishedUpdated by rememberUpdatedState(onPreviewFinished)
    return remember(currentPosition, totalDuration) {
        ProgressSliderState(
            currentPosition,
            totalDuration,
            onPreviewUpdated,
            onPreviewFinishedUpdated,
        )
    }
}

/**
 * Returns the most wide text that [renderSeconds] may return for that [totalSecs]. This can be used to reserve space for the text.
 */
@Stable
private fun renderSecondsReserve(
    totalSecs: Long?
): String = when (totalSecs) { // 8 is usually the visually widest character
    null -> "88:88 / 88:88"
    in 0..59 -> "88:88 / 88:88"
    in 60..3599 -> "88:88 / 88:88"
    in 3600..Int.MAX_VALUE -> "88:88:88 / 88:88:88"
    else -> "88:88 / 88:88"
}

/**
 * Renders position into format like "888:88:88 / 888:88:88" (hours:minutes:seconds)
 * @see renderSecondsReserve
 */
@Stable
private fun renderSeconds(current: Long, total: Long?): String {
    if (total == null) {
        return "00:${current.fixToString(2)} / 00:00"
    }
    return if (current < 60 && total < 60) {
        "00:${current.fixToString(2)} / 00:${total.fixToString(2)}"
    } else if (current < 3600 && total < 3600) {
        val startM = (current / 60).fixToString(2)
        val startS = (current % 60).fixToString(2)
        val endM = (total / 60).fixToString(2)
        val endS = (total % 60).fixToString(2)
        """$startM:$startS / $endM:$endS"""
    } else {
        val startH = (current / 3600).fixToString(2)
        val startM = (current % 3600 / 60).fixToString(2)
        val startS = (current % 60).fixToString(2)
        val endH = (total / 3600).fixToString(2)
        val endM = (total % 3600 / 60).fixToString(2)
        val endS = (total % 60).fixToString(2)
        """$startH:$startM:$startS / $endH:$endM:$endS"""
    }
}
