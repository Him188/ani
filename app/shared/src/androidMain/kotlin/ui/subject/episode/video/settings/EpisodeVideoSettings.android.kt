package me.him188.ani.app.ui.subject.episode.video.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.rememberViewModel

@Preview
@Composable
private fun PreviewEpisodeVideoSettings() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoSettings(
            remember {
                EpisodeVideoSettingsViewModel()
            },
        )
    }
}

@Preview(heightDp = 200)
@Composable
private fun PreviewEpisodeVideoSettingsSmall() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoSettings(
            remember {
                EpisodeVideoSettingsViewModel()
            },
        )
    }
}

@Preview(device = Devices.TABLET)
@Preview
@Composable
private fun PreviewEpisodeVideoSettingsSideSheet() = ProvideCompositionLocalsForPreview {
    var showSettings by remember { mutableStateOf(true) }
    if (showSettings) {
        EpisodeVideoSettingsSideSheet(
            onDismissRequest = { showSettings = false },
        ) {
            EpisodeVideoSettings(
                rememberViewModel { EpisodeVideoSettingsViewModel() },
                Modifier.padding(8.dp),
            )
        }
    }
}