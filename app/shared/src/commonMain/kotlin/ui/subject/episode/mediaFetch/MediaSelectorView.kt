package me.him188.ani.app.ui.subject.episode.mediaFetch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.widgets.FastLinearProgressIndicator
import me.him188.ani.app.ui.icons.MediaSourceIcons
import me.him188.ani.app.ui.icons.renderMediaSource
import me.him188.ani.app.ui.subject.episode.details.renderSubtitleLanguage
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.FileSize


private inline val WINDOW_VERTICAL_PADDING get() = 8.dp

// For search: "数据源"
/**
 * 通用的数据源选择器. See preview
 *
 * @param bottomActions shown at the bottom
 */
@Composable
fun MediaSelectorView(
    state: MediaSelectorPresentation,
    sourceResults: @Composable LazyItemScope.() -> Unit,
    modifier: Modifier = Modifier,
    itemProgressBar: @Composable RowScope.(Media) -> Unit = {
        FastLinearProgressIndicator(
            state.selected == it,
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            delayMillis = 300,
        )
    },
    onClickItem: ((Media) -> Unit) = { state.select(it) },
    bottomActions: (@Composable RowScope.() -> Unit)? = null,
    singleLineFilter: Boolean = false,
) = Surface {
    Column(modifier) {
        val lazyListState = rememberLazyListState()
        LazyColumn(
            Modifier.padding(bottom = WINDOW_VERTICAL_PADDING).weight(1f, fill = false),
            lazyListState,
        ) {
            item {
                Row(Modifier.padding(bottom = 12.dp)) {
                    sourceResults()
                }
            }

            stickyHeader {
                val isStuck by remember(lazyListState) {
                    derivedStateOf {
                        lazyListState.firstVisibleItemIndex == 2
                    }
                }
                Surface(
//                    tonalElevation = if (isStuck) 3.dp else 0.dp,
                    Modifier.animateItem(),
                ) {
                    Column {
                        Column(
                            Modifier.padding(bottom = 12.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                remember(state.filteredCandidates.size, state.mediaList.size) {
                                    "筛选到 ${state.filteredCandidates.size}/${state.mediaList.size} 条资源"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )

                            MediaSelectorFilters(
                                resolution = state.resolution,
                                subtitleLanguageId = state.subtitleLanguageId,
                                alliance = state.alliance,
                                singleLine = singleLineFilter,
                            )
                        }
                        if (isStuck) {
                            HorizontalDivider(Modifier.fillMaxWidth(), thickness = 2.dp)
                        }
                    }
                }
            }

            items(state.filteredCandidates, key = { it.mediaId }) { item ->
                Column {
                    MediaItem(
                        item,
                        state.selected == item,
                        state,
                        onClick = { onClickItem(item) },
                        Modifier
                            .animateItem()
                            .fillMaxWidth(),
                    )
                    Row(Modifier.height(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        itemProgressBar(item)
                    }
                }
            }
            item { } // dummy spacer
        }

        if (bottomActions != null) {
            HorizontalDivider(Modifier.padding(bottom = 8.dp))

            Row(
                Modifier.align(Alignment.End).padding(bottom = 8.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                    bottomActions()
                }
            }
        }
    }
}

/**
 * 一个资源的卡片
 */
@Composable
private fun MediaItem(
    media: Media,
    selected: Boolean,
    state: MediaSelectorPresentation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick,
        modifier.width(IntrinsicSize.Min),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else CardDefaults.elevatedCardColors().containerColor,
        ),
    ) {
        Column(Modifier.padding(all = 16.dp)) {
            ProvideTextStyle(MaterialTheme.typography.titleSmall) {
                Text(media.originalTitle)
            }

            // Labels
            FlowRow(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (media.properties.size != FileSize.Zero && media.properties.size != FileSize.Unspecified) {
                    InputChip(
                        false,
                        onClick = {},
                        label = { Text(media.properties.size.toString()) },
                    )
                }
                InputChip(
                    false,
                    onClick = { state.resolution.preferOrRemove(media.properties.resolution) },
                    label = { Text(media.properties.resolution) },
                    enabled = state.resolution.finalSelected != media.properties.resolution,
                )
                media.properties.subtitleLanguageIds.map {
                    InputChip(
                        false,
                        onClick = { state.subtitleLanguageId.preferOrRemove(it) },
                        label = { Text(renderSubtitleLanguage(it)) },
                        enabled = state.subtitleLanguageId.finalSelected != it,
                    )
                }
            }

            // Bottom row: source, alliance, published time
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                Row(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Layout note:
                    // On overflow, only the alliance will be ellipsized.

                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(MediaSourceIcons.location(media.location, media.kind), null)

                            Text(
                                remember(media.mediaSourceId) { renderMediaSource(media.mediaSourceId) },
                                maxLines = 1,
                                softWrap = false,
                            )
                        }

                        Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
                            Text(
                                media.properties.alliance,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Text(
                        formatDateTime(media.publishedTime, showTime = false),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
