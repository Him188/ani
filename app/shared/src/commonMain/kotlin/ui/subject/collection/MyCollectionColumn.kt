package me.him188.ani.app.ui.subject.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.debounce
import me.him188.ani.app.data.media.EpisodeCacheStatus
import me.him188.ani.app.data.subject.EpisodeCollection
import me.him188.ani.app.data.subject.SubjectCollection
import me.him188.ani.app.data.subject.episode
import me.him188.ani.app.tools.caching.LazyDataCache
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.indication.HorizontalIndicator
import me.him188.ani.app.ui.foundation.indication.IndicatedBox
import me.him188.ani.app.ui.subject.collection.progress.cacheStatusIndicationColor
import me.him188.ani.app.ui.subject.details.components.COVER_WIDTH_TO_HEIGHT_RATIO
import me.him188.ani.app.ui.subject.details.components.Tag
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import moe.tlaster.precompose.flow.collectAsStateWithLifecycle
import kotlin.time.Duration.Companion.seconds

private inline val spacedBy get() = 16.dp

/**
 * Lazy column of [item]s, designed for My Collections.
 *
 * @param item composes each item. See [SubjectCollection]
 * @param onEmpty content to be displayed when [LazyDataCache.cachedDataFlow] is empty.
 * @param contentPadding 要求该 column 的内容必须保持的 padding.
 * [SubjectCollectionsColumn] 将会允许元素渲染到这些区域, 但会在列表首尾添加 padding.
 * 这样可以让列表渲染到 bottom bar 的下面 (bottom bar 设置 alpha 0.97), 而用户又能正常地滑动到列表尾部并完整显示最后一个元素.
 */
@Composable
fun SubjectCollectionsColumn(
    cache: LazyDataCache<SubjectCollection>,
    onRequestMore: () -> Unit,
    item: @Composable (item: SubjectCollection) -> Unit,
    onEmpty: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    enableAnimation: () -> Boolean = { true },
) {
    // 当从其他页面回到这个页面时, cache.cachedDataFlow 会重新开始 collect
    val data by cache.cachedDataFlow.collectAsStateWithLifecycle(null) // 反正下面会立即用到, recompose 总是重组整个函数
    val dataLoaded by remember { derivedStateOf { data != null } }

    // 如果不 debounce, 会导致刚刚加载完成后会显示一小会 "空空如也"
    val isCompleted by remember(cache) { cache.isCompleted.debounce(1.seconds) }.collectAsStateWithLifecycle(false)
    val dataNullOrEmpty by remember { derivedStateOf { data.isNullOrEmpty() } }

    @Suppress("NAME_SHADOWING")
    val enableAnimation by remember(enableAnimation) { derivedStateOf(enableAnimation) }

    Composition {
        if (dataNullOrEmpty && isCompleted) {
            onEmpty()
        }
    }

    val gridState = rememberLazyGridState() // 要放在前面, 防止 return 后丢失

    if (!dataLoaded) return // 还没加载完, 不要去更新 grid 状态, 否则会恢复到顶部

    LazyVerticalGrid(
        GridCells.Adaptive(360.dp),
        modifier.padding(horizontal = 12.dp).padding(vertical = 0.dp),
        gridState,
        verticalArrangement = Arrangement.spacedBy(spacedBy),
        horizontalArrangement = Arrangement.spacedBy(spacedBy),
        contentPadding = PaddingValues(
            // 每两个 item 之间有 spacedBy dp, 这里再上补充 contentPadding 要求的高度, 这样顶部的总留空就是 contentPadding 要求的高度
            // 这个高度是可以滚到上面的, 所以它
            top = (contentPadding.calculateTopPadding() + spacedBy).coerceAtLeast(0.dp),
            bottom = (contentPadding.calculateBottomPadding() + spacedBy).coerceAtLeast(0.dp),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        items(data.orEmpty(), key = { it.subjectId }) { collection ->
            Box(Modifier.ifThen(enableAnimation) { animateItem() }) {
                item(collection)
            }
        }

        if (!isCompleted) {
            item("dummy loader", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }

                val onRequestMoreState by rememberUpdatedState(onRequestMore)
                LaunchedEffect(true) { onRequestMoreState() }
            }
        }
    }
}

@Composable
private fun Composition(content: @Composable () -> Unit) {
    content()
}

/**
 * 追番列表的一个条目卡片
 *
 * @param onClick on clicking this card (background)
 */
@Composable
fun SubjectCollectionItem(
    item: SubjectCollection,
    episodeCacheStatus: @Composable (subjectId: Int, episodeId: Int) -> EpisodeCacheStatus?,
    onClick: () -> Unit,
    onClickEpisode: (episode: EpisodeCollection) -> Unit,
    onClickSelectEpisode: () -> Unit,
    onSetAllEpisodesDone: () -> Unit,
    onSetCollectionType: (new: UnifiedCollectionType) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 148.dp,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    doneButton: @Composable (() -> Unit)? = null,
) {
    Card(
        onClick,
        modifier.clip(shape).fillMaxWidth().height(height),
        shape = shape,
    ) {
        Row(Modifier.weight(1f, fill = false)) {
            AsyncImage(
                item.image,
                contentDescription = null,
                modifier = Modifier
                    .height(height).width(height * COVER_WIDTH_TO_HEIGHT_RATIO),
            )

            Box(Modifier.weight(1f)) {
                SubjectCollectionItemContent(
                    item,
                    episodeCacheStatus,
                    onClickEpisode,
                    onClickSelectEpisode,
                    onSetAllEpisodesDone,
                    onSetCollectionType,
                    Modifier.padding(start = 12.dp).fillMaxSize(),
                    doneButton = doneButton,
                )
            }
        }
    }
}

/**
 * 追番列表的一个条目卡片的内容
 */
@Composable
private fun SubjectCollectionItemContent(
    item: SubjectCollection,
    cacheStatus: @Composable (subjectId: Int, episodeId: Int) -> EpisodeCacheStatus?,
    onClickEpisode: (episode: EpisodeCollection) -> Unit,
    onClickSelectEpisode: () -> Unit,
    onSetAllEpisodesDone: (() -> Unit)?,
    onSetCollectionType: (new: UnifiedCollectionType) -> Unit,
    modifier: Modifier = Modifier,
    doneButton: @Composable (() -> Unit)? = null,
) {
    Column(modifier) {
        // 标题和右上角菜单
        Row(
            Modifier.fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.displayName,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )

            Box {
                var showDropdown by rememberSaveable { mutableStateOf(false) }
                IconButton({ showDropdown = true }, Modifier.fillMaxHeight().padding()) {
                    Icon(Icons.Outlined.MoreVert, null, Modifier.size(24.dp))
                }

                EditCollectionTypeDropDown(
                    currentType = item.collectionType,
                    showDropdown, { showDropdown = false },
                    onSetAllEpisodesDone = onSetAllEpisodesDone,
                    onClick = { action ->
                        onSetCollectionType(action.type)
                    },
                )
            }
        }

        Row(
            Modifier.padding(top = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                // 2023 年 10 月
                item.date?.let {
                    Tag { Text(it) }
                }

                // 连载至第 28 话 · 全 34 话
                OnAirLabel(item, Modifier.padding(start = 8.dp))
            }
        }

        Spacer(Modifier.weight(1f))


        Row(
            Modifier
                .padding(vertical = 12.dp)
                .padding(horizontal = 12.dp)
                .align(Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClickSelectEpisode) {
                Text("选集")
            }

            val onClickEpisodeState by rememberUpdatedState(onClickEpisode)
            val onPlay: () -> Unit = { getEpisodeToPlay(item)?.let(onClickEpisodeState) }
            IndicatedBox(
                indicator = {
                    getEpisodeToPlay(item)?.episode?.id?.let { episodeId ->
                        HorizontalIndicator(
                            6.dp,
                            CircleShape,
                            cacheStatusIndicationColor(
                                cacheStatus(
                                    item.subjectId,
                                    episodeId,
                                ),
                                item.continueWatchingStatus is ContinueWatchingStatus.Watched,
                            ),
                            Modifier.offset(y = (-2).dp),
                        )
                    }
                },
            ) {
                when (val status = item.continueWatchingStatus) {
                    is ContinueWatchingStatus.Continue -> {
                        Button(onClick = onPlay) {
                            Text(remember(status.episodeSort) { "继续观看 ${status.episodeSort}" })
                        }
                    }

                    ContinueWatchingStatus.Done -> {
                        doneButton?.invoke()
                    }

                    ContinueWatchingStatus.NotOnAir -> {
                        androidx.compose.material3.FilledTonalButton(onClick = onPlay) {
                            Text("还未开播")
                        }
                    }

                    ContinueWatchingStatus.Start -> {
                        Button(onClick = onPlay) {
                            Text("开始观看")
                        }
                    }

                    is ContinueWatchingStatus.Watched -> {
                        androidx.compose.material3.FilledTonalButton(onClick = onPlay) {
                            Text("看到 ${status.episodeSort}")
                        }
                    }
                }
            }
        }
    }
}

private fun getEpisodeToPlay(
    item: SubjectCollection,
): EpisodeCollection? {
    if (item.continueWatchingStatus is ContinueWatchingStatus.Watched) {
        return item.episodes[item.continueWatchingStatus.episodeIndex]
    } else {
        item.lastWatchedEpIndex?.let {
            item.episodes.getOrNull(it + 1)
        }?.let {
            return it
        }

        item.lastWatchedEpIndex?.let {
            item.episodes.getOrNull(it)
        }?.let {
            return it
        }

        item.episodes.firstOrNull()?.let {
            return it
        }
    }

    return null
}


// The label "已完结 · 全 28 话"
@Composable
fun OnAirLabel(
    item: SubjectCollection,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    ProvideTextStyle(style) {
        Row(modifier.width(IntrinsicSize.Max).height(IntrinsicSize.Min)) {
            Text(
                item.onAirDescription,
                color = if (item.isOnAir) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                maxLines = 1,
            )
            Text(
                " · ",
                maxLines = 1,
            )
            Text(
                item.serialProgress,
                maxLines = 1,
            )
        }
    }
}