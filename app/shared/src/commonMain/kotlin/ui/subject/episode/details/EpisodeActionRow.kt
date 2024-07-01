package me.him188.ani.app.ui.subject.episode.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InsertChartOutlined
import androidx.compose.material.icons.rounded.Outbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.launchInMain
import me.him188.ani.app.ui.subject.episode.EpisodeViewModel
import me.him188.ani.app.ui.subject.episode.statistics.PlayerStatistics
import moe.tlaster.precompose.flow.collectAsStateWithLifecycle


@Composable
fun EpisodeActionRow(
    viewModel: EpisodeViewModel, // TODO: remove viewModel dependency from EpisodeActionRow
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val clipboard by rememberUpdatedState(LocalClipboardManager.current)
    val context by rememberUpdatedState(LocalContext.current)

    val navigator by rememberUpdatedState(LocalNavigator.current)

    var showPlayerStatistics by remember { mutableStateOf(false) }
    if (showPlayerStatistics) {
        ModalBottomSheet({ showPlayerStatistics = false }) {
            PlayerStatistics(viewModel.playerStatistics, Modifier.padding(16.dp))
        }
    }

    val isDanmakuLoading by viewModel.playerStatistics.isDanmakuLoading.collectAsStateWithLifecycle(false)

    EpisodeActionRow(
        isMediaFetcherWorking = { viewModel.mediaSourceResultsPresentation.anyLoading },
        isDanmakuLoading = { isDanmakuLoading },
        onClickMediaSelection = { viewModel.mediaSelectorVisible = true },
        onClickCopyLink = {
            viewModel.launchInMain {
                copyDownloadLink(clipboard, snackbar)
            }
        },
        onClickCache = {
            navigator.navigateSubjectCaches(viewModel.subjectId)
        },
        onClickStatistics = {
            showPlayerStatistics = true
        },
        onClickDownload = {
            viewModel.launchInMain {
                browseDownload(context, snackbar)
            }
        },
        onClickOriginalPage = {
            viewModel.launchInMain {
                browseMedia(context, snackbar)
            }
        },
        modifier = modifier,
    )
}

/**
 * 一行功能按钮.
 *
 * 选择播放源, 复制磁力, 下载, 原始页面.
 */
@Composable
fun EpisodeActionRow(
    isMediaFetcherWorking: () -> Boolean,
    isDanmakuLoading: () -> Boolean,
    onClickMediaSelection: () -> Unit,
    onClickCopyLink: () -> Unit,
    onClickCache: () -> Unit,
    onClickStatistics: () -> Unit,
    onClickDownload: () -> Unit,
    onClickOriginalPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        // 选择播放源
        MediaSelectionAction(
            isMediaFetcherWorking,
            onClick = onClickMediaSelection,
            Modifier.weight(1f),
        )

        ActionButton(
            onClick = onClickStatistics,
            icon = { Icon(Icons.Rounded.InsertChartOutlined, null) },
            text = { Text("视频统计", maxLines = 1, softWrap = false) },
            Modifier.weight(1f),
            isLoadingProvider = isDanmakuLoading,
        )

        ActionButton(
            onClick = onClickCache,
            icon = { Icon(Icons.Rounded.Download, null) },
            text = { Text("缓存", maxLines = 1, softWrap = false) },
            Modifier.weight(1f),
        )

        var showMore by remember { mutableStateOf(false) }
        Box(Modifier.weight(1f)) { // to provide placement
            ActionButton(
                onClick = { showMore = true },
                icon = { Icon(Icons.Rounded.Outbox, null) },
                text = { Text("更多", maxLines = 1, softWrap = false) },
                Modifier.fillMaxWidth(),
            )

            DropdownMenu(showMore, { showMore = false }) {
                DropdownMenuItem(
                    text = { Text("复制磁力链接") },
                    onClick = onClickCopyLink,
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                )
                DropdownMenuItem(
                    text = { Text("使用其他应用打开") },
                    onClick = onClickDownload,
                    leadingIcon = { Icon(Icons.Rounded.Outbox, null) },
                )
                DropdownMenuItem(
                    text = { Text("访问原始页面") },
                    onClick = onClickOriginalPage,
                    leadingIcon = { Icon(Icons.Rounded.ArrowOutward, null) },
                )
            }
        }
    }
}


/**
 * 选择播放源
 */
@Composable
private fun MediaSelectionAction(
    isLoading: () -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionButton(
        onClick = { onClick() },
        icon = { Icon(Icons.Rounded.DisplaySettings, null) },
        text = { Text("数据源", maxLines = 1, softWrap = false) },
        modifier,
        isLoading,
    )
}

/**
 * 数据源; 下载; 分享
 */
@Composable
private fun ActionButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    isLoadingProvider: () -> Boolean = { false },
) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick,
            )
            .height(64.dp)
            .padding(all = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        icon()
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                text()
            }

            val isLoading by remember {
                derivedStateOf(isLoadingProvider)
            }
            AnimatedVisibility(isLoading) {
                Box(Modifier.padding(start = 8.dp).height(12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}
