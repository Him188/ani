package me.him188.ani.danmaku.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import me.him188.ani.app.platform.isInLandscapeMode
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.subject.episode.video.settings.EpisodeVideoSettings
import me.him188.ani.app.ui.subject.episode.video.settings.EpisodeVideoSettingsViewModel
import me.him188.ani.danmaku.api.Danmaku
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.api.DanmakuPresentation
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Note, use "Run Preview" to preview on your phone or the emulator if you only see the initial danmaku-s.
 */
@Composable
@Preview(showBackground = true, device = Devices.TABLET)
@Preview(showBackground = true)
internal actual fun PreviewDanmakuHost() = ProvideCompositionLocalsForPreview {
    var emitted by remember {
        mutableIntStateOf(0)
    }
    var config by remember {
        mutableStateOf(DanmakuConfig())
    }
    var filterConfig by remember {
        mutableStateOf(DanmakuRegexFilterConfig())
    }
    val data = remember {
        flow {
            var counter = 0
            fun danmaku() =
                Danmaku(
                    counter++.toString(),
                    "dummy",
                    0L, "1",
                    DanmakuLocation.entries.random(),
                    text = LoremIpsum(Random.nextInt(1..5)).values.first(),
                    0,
                )

            emit(danmaku())
            emit(danmaku())
            emit(danmaku())
            while (true) {
                emit(danmaku())
                emitted++
                delay(Random.nextLong(20, 500).milliseconds)
            }
        }
    }
    val state = remember {
        DanmakuHostState()
    }
    LaunchedEffect(true) {
        data.collect {
            state.trySend(
                DanmakuPresentation(
                    it,
                    isSelf = Random.nextBoolean(),
                ),
            )
        }
    }

    if (isInLandscapeMode()) {
        Row {
            Box(Modifier.weight(1f)) {
                DanmakuHost(
                    state,
                    Modifier.fillMaxHeight(),
                ) { config }
                Column {
                    Text("Emitted: $emitted")
                    state.topTracks.forEachIndexed { index, danmakuTrackState ->
                        Text(
                            "track top$index: visible=${danmakuTrackState.visibleDanmaku}",
                        )
                    }
                    HorizontalDivider()
                    state.floatingTracks.forEachIndexed { index, danmakuTrackState ->
                        Text(
                            "track floating$index: offset=${danmakuTrackState.trackOffset.toInt()}, " +
                                    "visible=${danmakuTrackState.visibleDanmaku.size}, " +
                                    "starting=${danmakuTrackState.startingDanmaku.size}",
                        )
                    }
                    HorizontalDivider()
                    state.bottomTracks.forEachIndexed { index, danmakuTrackState ->
                        Text(
                            "track bottom$index: visible=${danmakuTrackState.visibleDanmaku}",
                        )
                    }
                }
            }
            VerticalDivider()
            EpisodeVideoSettings(
                remember {
                    object : EpisodeVideoSettingsViewModel {
                        override val danmakuConfig: DanmakuConfig = DanmakuConfig.Default
                        override val danmakuRegexFilterConfig: DanmakuRegexFilterConfig = DanmakuRegexFilterConfig.Default
                        override val isLoading: Boolean = false

                        override fun setDanmakuConfig(config: DanmakuConfig) {
                            // Do nothing in preview
                        }

                        override fun addDanmakuRegexFilter(filter: DanmakuRegexFilter) {
                            //Do nothing in preview
                        }

                        override fun editDanmakuRegexFilter(filter: DanmakuRegexFilter) {
                            //Do nothing in preview
                        }

                        override fun removeDanmakuRegexFilter(filter: DanmakuRegexFilter) {
                            //Do nothing in preview
                        }

                        override fun switchDanmakuRegexFilterCompletely() {
                            //Do nothing in preview

                        }

                        override fun switchDanmakuRegexFilter(filter: DanmakuRegexFilter) {
                            // Do nothing in preview
                        }
                    }
                }
            )
        }
    } else {
        Column {
            Box(
                Modifier
                    .weight(1f),
            ) {
                DanmakuHost(
                    state,
                    Modifier.fillMaxWidth(),
                ) { config }
                Column {
                    Text("Emitted: $emitted")
                    state.floatingTracks.forEachIndexed { index, danmakuTrackState ->
                        Text(
                            "track$index: offset=${danmakuTrackState.trackOffset.toInt()}, " +
                                    "visible=${danmakuTrackState.visibleDanmaku.size}, " +
                                    "starting=${danmakuTrackState.startingDanmaku.size}",
                        )
                    }
                }

            }
            HorizontalDivider()
            EpisodeVideoSettings(
                remember {
                    object : EpisodeVideoSettingsViewModel {
                        override val danmakuConfig: DanmakuConfig = DanmakuConfig.Default
                        override val danmakuRegexFilterConfig: DanmakuRegexFilterConfig = DanmakuRegexFilterConfig.Default
                        override val isLoading: Boolean = false

                        override fun setDanmakuConfig(config: DanmakuConfig) {
                            // Do nothing in preview
                        }

                        override fun addDanmakuRegexFilter(filter: DanmakuRegexFilter) {
                            //Do nothing in preview
                        }

                        override fun editDanmakuRegexFilter(filter: DanmakuRegexFilter) {
                            //Do nothing in preview
                        }

                        override fun removeDanmakuRegexFilter(filter: DanmakuRegexFilter) {
                            //Do nothing in preview
                        }

                        override fun switchDanmakuRegexFilterCompletely() {
                            //Do nothing in preview

                        }

                        override fun switchDanmakuRegexFilter(filter: DanmakuRegexFilter) {
                            TODO("Not yet implemented")
                        }
                    }
                }
            )
        }

    }
}

@Composable
@Preview("Light", showBackground = true)
@Preview("Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
private fun PreviewDanmakuText() {
    ProvideCompositionLocalsForPreview {
        Surface(color = Color.White) {
            DanmakuText(
                DummyDanmakuState,
                style = DanmakuStyle(),
            )
        }
    }
}