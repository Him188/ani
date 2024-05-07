package me.him188.ani.app.ui.preference.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.media.MediaSourceManager
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.rememberViewModel
import me.him188.ani.app.ui.preference.PreferencePage
import me.him188.ani.app.ui.preference.PreferenceScope
import me.him188.ani.app.ui.preference.PreferenceTab
import me.him188.ani.app.ui.preference.SwitchItem
import me.him188.ani.app.ui.preference.TextFieldDialog
import me.him188.ani.app.ui.preference.framework.ConnectionTestResult
import me.him188.ani.datasources.acgrip.AcgRipMediaSource
import me.him188.ani.datasources.api.paging.PageBasedPagedSource
import me.him188.ani.datasources.api.paging.Paged
import me.him188.ani.datasources.api.paging.PagedSource
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.DownloadSearchQuery
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.TopicMediaSource
import me.him188.ani.datasources.api.topic.Topic
import me.him188.ani.datasources.dmhy.DmhyMediaSource
import me.him188.ani.datasources.mikan.MikanMediaSource
import kotlin.random.Random

@Composable
private fun PreviewTab(
    content: @Composable PreferenceScope.() -> Unit,
) {
    ProvideCompositionLocalsForPreview {
        PreferenceTab {
            content()
        }
    }
}

@Preview
@Composable
private fun PreviewPreferencePage() {
    ProvideCompositionLocalsForPreview {
        PreferencePage()
    }
}

private class TestMediaSource(
    override val mediaSourceId: String,
) : TopicMediaSource() {
    override suspend fun checkConnection(): ConnectionStatus {
        return Random.nextBoolean().let {
            if (it) ConnectionStatus.SUCCESS else ConnectionStatus.FAILED
        }
    }

    override suspend fun startSearch(query: DownloadSearchQuery): PagedSource<Topic> {
        return PageBasedPagedSource {
            Paged.empty()
        }
    }
}

@Preview
@Composable
private fun PreviewNetworkPreferenceTab() {
    ProvideCompositionLocalsForPreview(
        module = {
            single<MediaSourceManager> {
                object : MediaSourceManager {
                    override val enabledSources: MutableStateFlow<List<MediaSource>> = MutableStateFlow(
                        listOf(
                            TestMediaSource(AcgRipMediaSource.ID),
                            TestMediaSource(DmhyMediaSource.ID),
                            TestMediaSource(MikanMediaSource.ID),
                            TestMediaSource("local"),
                        )
                    )
                    override val allIds: List<String> = enabledSources.value.map { it.mediaSourceId }
                    override val allIdsExceptLocal: List<String>
                        get() = allIds.filter { !isLocal(it) }
                }
            }
        }
    ) {
        val vm = rememberViewModel { NetworkPreferenceViewModel() }
        SideEffect {
            val testers = vm.allMediaTesters.testers
            testers.first().result = ConnectionTestResult.SUCCESS
            testers.drop(1).first().result = ConnectionTestResult.FAILED
            testers.drop(2).first().result = ConnectionTestResult.NOT_ENABLED
        }
        NetworkPreferenceTab()
    }
}

@Preview
@Composable
private fun PreviewPreferenceScope() {
    ProvideCompositionLocalsForPreview {
        PreferenceTab {
            SwitchItem(
                checked = true,
                onCheckedChange = {},
                title = {
                    Text("Test")
                },
                description = {
                    Text(text = "Test description")
                },
            )
        }
    }
}

@Preview
@Composable
private fun PreviewTextFieldDialog() {
    PreviewTab {
        TextFieldDialog(
            onDismissRequest = {},
            onConfirm = {},
            title = { Text(text = "编辑") },
            description = { Text(LoremIpsum(20).values.first()) }
        ) {
            OutlinedTextField(
                value = "test",
                onValueChange = {},
                shape = MaterialTheme.shapes.medium
            )
        }
    }
}