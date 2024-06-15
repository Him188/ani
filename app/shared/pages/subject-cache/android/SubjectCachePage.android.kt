package me.him188.ani.app.ui.subject.cache

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import me.him188.ani.app.data.media.EpisodeCacheStatus
import me.him188.ani.app.pages.cache.manage.testMediaCache1
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

@PreviewLightDark
@Composable
private fun PreviewSubjectCachePage() {
    ProvideCompositionLocalsForPreview {
        SubjectCachePage(
            state = SubjectCacheState(
                listOf(
                    EpisodeCacheState(
                        1,
                        sort = EpisodeSort(1),
                        ep = EpisodeSort(1),
                        title = "第一集的标题",
                        watchStatus = UnifiedCollectionType.DONE,
                        cacheStatus = EpisodeCacheStatus.Cached(
                            300.megaBytes,
                            testMediaCache1,
                        ),
                        hasPublished = true,
                        originMedia = null,
                    ),
                    EpisodeCacheState(
                        2,
                        sort = EpisodeSort(2),
                        ep = EpisodeSort(2),
                        title = "第二集的标题第二集的标题第二集的标题第二集的标题第二集的标题第二集的标题第二集的标题第二集的标题",
                        watchStatus = UnifiedCollectionType.DONE,
                        EpisodeCacheStatus.Caching(
                            progress = 0.3f, totalSize = 300.megaBytes,
                            testMediaCache1,
                        ),
                        hasPublished = true,
                        originMedia = null,
                    ),
                    EpisodeCacheState(
                        3,
                        sort = EpisodeSort(3),
                        ep = EpisodeSort(3),
                        title = "第三集的标题第三集的标题第三集的标题第三集的标题第三集的标题第三集的标题第三集的标题第三集的标题",
                        watchStatus = UnifiedCollectionType.DOING,
                        cacheStatus = EpisodeCacheStatus.NotCached,
                        hasPublished = true,
                        originMedia = null,
                    ),
                    EpisodeCacheState(
                        4,
                        sort = EpisodeSort(4),
                        ep = EpisodeSort(4),
                        title = "第四集的标题",
                        watchStatus = UnifiedCollectionType.DOING,
                        cacheStatus = EpisodeCacheStatus.NotCached,
                        hasPublished = false,
                        originMedia = null,
                    ),
                )
            ),
            subjectTitle = {
                Text(text = "葬送的芙莉莲")
            },
            onClickGlobalCacheSettings = {},
            onClickGlobalCacheManage = {},
            onDeleteCache = {},
        )
    }
}