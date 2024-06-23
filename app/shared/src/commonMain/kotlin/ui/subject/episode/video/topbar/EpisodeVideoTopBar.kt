package me.him188.ani.app.ui.subject.episode.video.topbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.subject.episode.details.EpisodePlayerTitle
import me.him188.ani.app.ui.subject.episode.video.settings.EpisodeVideoSettings
import me.him188.ani.app.videoplayer.ui.top.PlayerTopBar

/**
 * @param title [EpisodePlayerTitle]
 * @param settings [EpisodeVideoSettings]
 */
@Composable
fun EpisodeVideoTopBar(
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    settings: @Composable (() -> Unit)? = null,
) {
    PlayerTopBar(
        title = title,
        actions = {
            // Danmaku Settings
            settings?.invoke()
        },
        modifier,
    )

}