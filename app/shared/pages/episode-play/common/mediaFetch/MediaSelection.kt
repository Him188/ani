package me.him188.ani.app.ui.subject.episode.mediaFetch

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyHorizontalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.subject.episode.details.renderSubtitleLanguage
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.SubtitleLanguage


@Stable
private val verticalPadding = 8.dp

// For search: "数据源"
/**
 * 通用的数据源选择器. See preview
 *
 * @param progressProvider `1f` to hide the progress bar. `null` to show a endless progress bar.
 * @param actions shown at the bottom
 */
@Composable
fun MediaSelector(
    state: MediaSelectorState,
    modifier: Modifier = Modifier,
    progressProvider: () -> Float? = { 1f },
    onClickItem: ((Media) -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier,
    ) {
        Row(Modifier.fillMaxWidth()) {
            val progress = progressProvider() // recompose this row only as the progress might update frequently
            if (progress == 1f) return@Row

            if (progress == null) {
                LinearProgressIndicator(
                    Modifier.padding(bottom = verticalPadding)
                        .fillMaxWidth()
                )
            } else {
                val progressAnimated by animateFloatAsState(
                    targetValue = progress,
                    spring(Spring.StiffnessHigh)
                )
                LinearProgressIndicator(
                    { progressAnimated },
                    Modifier.padding(bottom = verticalPadding)
                        .fillMaxWidth()
                )
            }
        }

        LazyColumn(
            Modifier.padding(bottom = verticalPadding).weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth(),
                ) {
                    MediaFilterFlowRow(
                        state.mediaSources,
                        label = { Text("来源", overflow = TextOverflow.Visible) },
                        eachItem = { item ->
                            FilterChip(
                                item == state.selectedMediaSource,
                                onClick = { state.preferMediaSource(item, removeOnExist = true) },
                                label = { Text(remember(item) { renderMediaSource(item) }) },
                            )
                        },
                        maxItemsInEachColumn = 1,
                        Modifier.heightIn(min = 32.dp),
                    )

                    MediaFilterRow(
                        state.resolutions,
                        label = { Text("分辨率", overflow = TextOverflow.Visible) },
                        key = { it },
                        eachItem = { item ->
                            FilterChip(
                                item == state.selectedResolution,
                                onClick = { state.preferResolution(item, removeOnExist = true) },
                                label = { Text(remember(item) { item }) },
                            )
                        },
                        Modifier.heightIn(min = 32.dp)
                    )

                    MediaFilterRow(
                        state.subtitleLanguageIds,
                        label = { Text("字幕语言", overflow = TextOverflow.Visible) },
                        key = { it },
                        eachItem = { item ->
                            FilterChip(
                                item == state.selectedSubtitleLanguageId,
                                onClick = { state.preferSubtitleLanguage(item, removeOnExist = true) },
                                label = {
                                    Text(
                                        remember(item) { // TODO: Subtitle Language i18n 
                                            SubtitleLanguage.tryParse(item)?.toString() ?: item
                                        }
                                    )
                                },
                            )
                        },
                        Modifier.heightIn(min = 32.dp)
                    )

                    MediaFilterFlowRow(
                        state.alliances,
                        label = { Text("字幕组", overflow = TextOverflow.Visible) },
                        eachItem = { item ->
                            FilterChip(
                                item == state.selectedAlliance,
                                onClick = { state.preferAlliance(item, removeOnExist = true) },
                                label = {
                                    Text(
                                        item,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        },
                        maxItemsInEachColumn = 3,
                        Modifier.heightIn(min = 32.dp)
                    )

                    Text(
                        remember(state.candidates.size) { "匹配到 ${state.candidates.size} 条资源" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            items(state.candidates, key = { it.mediaId }) { item ->
                MediaItem(
                    item,
                    state.selected == item,
                    state,
                    onClick = {
                        state.select(item)
                        onClickItem?.invoke(item)
                    },
                    Modifier
                        .animateItemPlacement()
                        .fillMaxWidth(),
                )
            }
            item { } // dummy spacer
        }

        if (actions != null) {
            HorizontalDivider(Modifier.padding(bottom = 8.dp))

            Row(
                Modifier.align(Alignment.End).padding(bottom = 8.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                    actions()
                }
            }
        }
    }
}

@Composable
private fun MediaItem(
    media: Media,
    selected: Boolean,
    state: MediaSelectorState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick,
        modifier.width(IntrinsicSize.Min),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else CardDefaults.elevatedCardColors().containerColor
        ),
    ) {
        Box {
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
                    if (media.properties.size != FileSize.Zero) {
                        InputChip(
                            false,
                            onClick = {},
                            label = { Text(media.properties.size.toString()) },
                        )
                    }
                    InputChip(
                        false,
                        onClick = { state.preferResolution(media.properties.resolution) },
                        label = { Text(media.properties.resolution) },
                        enabled = state.selectedResolution != media.properties.resolution,
                    )
                    media.properties.subtitleLanguageIds.map {
                        InputChip(
                            false,
                            onClick = { state.preferSubtitleLanguage(it) },
                            label = { Text(renderSubtitleLanguage(it)) },
                            enabled = state.selectedSubtitleLanguageId != it,
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
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (media.location) {
                                    MediaSourceLocation.Local -> Icon(Icons.Rounded.DownloadDone, null)
                                    MediaSourceLocation.Lan -> Icon(Icons.Rounded.Radar, null)
                                    MediaSourceLocation.Online -> Icon(Icons.Rounded.Public, null)
                                }

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
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Text(
                            formatDateTime(media.publishedTime),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

//            if (selected) {
//                ProvideTextStyleContentColor(MaterialTheme.typography.labelLarge) {
//                    Row(
//                        Modifier.align(Alignment.BottomEnd).padding(16.dp),
//                        verticalAlignment = Alignment.CenterVertically,
//                    ) {
//                        Icon(Icons.Rounded.Check, null, tint = LocalContentColor.current)
//                        Text("当前选择", Modifier.padding(start = 4.dp))
//                    }
//                }
//            }
        }
    }
}

private val PLAY_SOURCE_LABEL_WIDTH = 62.dp

@Composable
private fun <T> MediaFilterFlowRow(
    items: List<T>,
    label: @Composable () -> Unit,
    eachItem: @Composable (item: T) -> Unit,
    maxItemsInEachColumn: Int,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        ProvideTextStyle(labelStyle) {
            Box(Modifier.padding(top = 12.dp).widthIn(min = PLAY_SOURCE_LABEL_WIDTH)) {
                label()
            }
        }

        Box(
            Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
//            FlowColumn(
//                maxItemsInEachColumn = maxItemsInEachColumn,
//                horizontalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//                for (item in items) {
//                    Box(Modifier.height(40.dp)) {
//                        eachItem(item)
//                    }
//                }
//            }
            LazyHorizontalStaggeredGrid(
                StaggeredGridCells.FixedSize(32.dp),
                Modifier.padding(vertical = 8.dp)
                    .fillMaxWidth()
                    .heightIn(max = 32.dp * maxItemsInEachColumn + 8.dp * (maxItemsInEachColumn - 1)),
                horizontalItemSpacing = 8.dp,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items) { item ->
                    eachItem(item)
                }
            }
        }
    }
}

@Composable
private fun <T> MediaFilterRow(
    items: List<T>,
    label: @Composable () -> Unit,
    key: (item: T) -> Any,
    eachItem: @Composable (item: T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ProvideTextStyle(MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)) {
            Box(Modifier.widthIn(min = PLAY_SOURCE_LABEL_WIDTH)) {
                label()
            }
        }

        Box(
            Modifier.padding(start = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key) { item ->
                    eachItem(item)
                }
            }
        }
    }
}
