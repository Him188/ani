package me.him188.ani.app.ui.subject.episode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.videoplayer.ExoPlayerControllerFactory

@Composable
@Preview(widthDp = 1080 / 3, heightDp = 2400 / 3, showBackground = true)
internal actual fun PreviewEpisodePage() {
    ProvideCompositionLocalsForPreview(playerControllerFactory = ExoPlayerControllerFactory()) {
        val context = LocalContext.current
        EpisodePageContent(
            remember {
                EpisodeViewModel(
                    424663,
                    1277147,
                    context = context
                )
            }
        )
    }
}