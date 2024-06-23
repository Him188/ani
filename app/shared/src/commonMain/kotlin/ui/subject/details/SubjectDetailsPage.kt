package me.him188.ani.app.ui.subject.details

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.backgroundWithGradient
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.foundation.theme.slightlyWeaken
import me.him188.ani.app.ui.foundation.theme.weaken
import me.him188.ani.app.ui.foundation.widgets.TopAppBarGoBackButton
import me.him188.ani.app.ui.subject.collection.CollectionActionButton
import me.him188.ani.app.ui.subject.collection.EditCollectionTypeDropDown
import me.him188.ani.app.ui.subject.collection.progress.EpisodeProgressDialog
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.client.BangumiEpisode
import me.him188.ani.datasources.bangumi.processing.fixToString
import moe.tlaster.precompose.flow.collectAsStateWithLifecycle


/**
 * 一部番的详情页
 */
@Composable
fun SubjectDetailsPage(
    viewModel: SubjectDetailsViewModel,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
//                Modifier.statusBarsPadding(),
//                padding = PaddingValues(start = 12.dp, top = 6.dp, end = 16.dp, bottom = 6.dp),
                navigationIcon = { TopAppBarGoBackButton() },
                actions = {
                    IconButton({ viewModel.browseSubjectBangumi(context) }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                    }
                },
//                containerColor = Color.Transparent,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { scaffoldPadding ->
        val coverImageUrl by viewModel.coverImage.collectAsStateWithLifecycle(null)

        val density = LocalDensity.current
        // 虚化渐变背景
        val backgroundColor by rememberUpdatedState(MaterialTheme.colorScheme.background)
        val surfaceColor by rememberUpdatedState(MaterialTheme.colorScheme.surface)
        Box(
            Modifier
                .height(270.dp + density.run { WindowInsets.systemBars.getTop(density).toDp() })
                .fillMaxWidth()
                .blur(12.dp)
                .backgroundWithGradient(
                    coverImageUrl, backgroundColor,
                    brush = if (isSystemInDarkTheme()) {
                        Brush.verticalGradient(
                            0f to surfaceColor.copy(alpha = 0xA2.toFloat() / 0xFF),
                            0.4f to surfaceColor.copy(alpha = 0xA2.toFloat() / 0xFF),
                            1.00f to backgroundColor,
                        )
                    } else {
                        Brush.verticalGradient(
                            0f to Color(0xA2FAFAFA),
                            0.4f to Color(0xA2FAFAFA),
                            1.00f to backgroundColor,
                        )
                    },
                ),
        ) {
        }

        SubjectDetailsContent(
            viewModel,
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(scaffoldPadding) // pad top bar
                .padding(bottom = 16.dp) // pad bottom
                .fillMaxSize(),
        )
    }
}

// 详情页内容 (不包含背景)
@Composable
private fun SubjectDetailsContent(
    vm: SubjectDetailsViewModel,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
) {
    Column(modifier) {
        // 封面, 标题, 标签 
        SubjectDetailsHeader(
            vm,
            Modifier.padding(top = 8.dp, bottom = 4.dp).padding(start = horizontalPadding),
        )

        // 收藏数据和收藏按钮
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).padding(horizontal = horizontalPadding)) {
            val collection by vm.collection.collectAsStateWithLifecycle(null)
            // 数据
            Row(Modifier.placeholder(visible = collection == null).fillMaxWidth()) {
                if (collection == null) {
                    Text(" ", style = MaterialTheme.typography.bodySmall) // spacer
                }
                collection?.let {
                    Text(
                        "${it.collect} 收藏 / ${it.wish} 想看 / ${it.doing} 在看",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        " / ${it.onHold} 搁置 / ${it.dropped} 抛弃",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.slightlyWeaken(),
                    )
                }
            }

            val selfCollectionType by vm.selfCollectionAction.collectAsStateWithLifecycle(null)
            val selfCollected by vm.selfCollected.collectAsStateWithLifecycle(null)

            Row(
                Modifier.padding(start = 8.dp, top = 8.dp).align(Alignment.End),
            ) {
                // 收藏按钮
                if (selfCollected == true) {
                    var showEpisodeProgressDialog by rememberSaveable { mutableStateOf(false) }
                    if (showEpisodeProgressDialog) {
                        EpisodeProgressDialog(
                            vm.episodeProgressState,
                            onDismissRequest = { showEpisodeProgressDialog = false },
                        )
                    }
                    TextButton({ showEpisodeProgressDialog = true }) {
                        Text("选集播放")
                    }

                    var showDropdown by remember { mutableStateOf(false) }
                    EditCollectionTypeDropDown(
                        currentType = selfCollectionType,
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false },
                        onSetAllEpisodesDone = { vm.launchInBackground { setAllEpisodesWatched() } },
                        onClick = {
                            showDropdown = false
                            vm.launchInBackground { setSelfCollectionType(it.type) }
                        },
                    )
                    CollectionActionButton(
                        collected = selfCollected,
                        type = selfCollectionType,
                        onCollect = { vm.launchInBackground { setSelfCollectionType(UnifiedCollectionType.DOING) } },
                        onEdit = { vm.launchInBackground { setSelfCollectionType(it) } },
                        onSetAllEpisodesDone = { vm.launchInBackground { setAllEpisodesWatched() } },
                    )
                } else {
                    CollectionActionButton(
                        collected = selfCollected,
                        type = selfCollectionType,
                        onCollect = { vm.launchInBackground { setSelfCollectionType(UnifiedCollectionType.DOING) } },
                        onEdit = { vm.launchInBackground { setSelfCollectionType(it) } },
                        onSetAllEpisodesDone = { vm.launchInBackground { setAllEpisodesWatched() } },
                    )
                }
            }
        }

        val characters by vm.characters.collectAsStateWithLifecycle(null)
        SectionTitle(Modifier.padding(horizontal = horizontalPadding)) {
            Text("角色")
        }
        PersonList(characters, { it.id }, horizontalPadding, Modifier) {
            PersonView(
                avatar = {
                    AvatarImage(
                        it.images?.medium ?: "",
                        alignment = Alignment.TopStart,
                        contentScale = ContentScale.Crop,
                    )
                },
                text = { Text(it.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                role = { Text(it.actors?.firstOrNull()?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }

        val staff by vm.relatedPersons.collectAsStateWithLifecycle(null)
        SectionTitle(Modifier.padding(horizontal = horizontalPadding)) {
            Text("Staff")
        }
        PersonList(staff, { it.id }, horizontalPadding, Modifier) {
            PersonView(
                avatar = { AvatarImage(it.images?.medium ?: "", contentScale = ContentScale.Crop) },
                text = { Text(it.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                role = { Text(it.relation, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }

        val navigator = LocalNavigator.current
        val episodesMain by vm.episodesMain.collectAsStateWithLifecycle(listOf())
        if (episodesMain.isNotEmpty()) {
            SectionTitle(Modifier.padding(horizontal = horizontalPadding)) { Text("正片") }
            EpisodeList(
                episodesMain,
                horizontalPadding,
                { navigator.navigateEpisodeDetails(vm.subjectId, it.id.toInt()) },
                Modifier.padding(top = 8.dp),
            )
        }

//        val episodesSP by viewModel.episodesSP.collectAsStateWithLifecycle(listOf())
//        if (episodesSP.isNotEmpty()) {
//            SectionTitle(Modifier.padding(horizontal = horizontalPadding)) { Text("SP") }
//            EpisodeList(
//                episodesSP,
//                horizontalPadding,
//                { navigator.navigateEpisodeDetails(viewModel.subjectId.value, it.id.toInt()) },
//                Modifier.padding(top = 8.dp)
//            )
//        }
//
//        val episodesPV by viewModel.episodesPV.collectAsStateWithLifecycle(listOf())
//        if (episodesPV.isNotEmpty()) {
//            SectionTitle(Modifier.padding(horizontal = horizontalPadding)) { Text("PV") }
//            EpisodeList(
//                episodesPV,
//                horizontalPadding,
//                { navigator.navigateEpisodeDetails(viewModel.subjectId.value, it.id.toInt()) },
//                Modifier.padding(top = 8.dp)
//            )
//        }
    }
}

@Composable
private fun <T> PersonList(
    list: List<T>?,
    key: (T) -> Any,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
    each: @Composable (T) -> Unit,
) {
    val spacedBy = 16.dp
    LazyRow(
        modifier = modifier.placeholder(visible = list == null).fillMaxWidth().heightIn(min = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(spacedBy),
    ) {
        item(key = "spacer header") { Spacer(Modifier.width(horizontalPadding - spacedBy)) }
        items(list.orEmpty(), key = key) { item ->
            each(item)
        }
        item(key = "spacer footer") { Spacer(Modifier.width(horizontalPadding - spacedBy)) }
    }
}

@Composable
private fun PersonView(
    avatar: @Composable () -> Unit,
    text: @Composable () -> Unit,
    role: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Box(Modifier.clip(CircleShape).size(64.dp)) {
                avatar()
            }
            Box(Modifier.padding(top = 4.dp)) {
                ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                    text()
                }
            }
            Box(Modifier.padding(top = 4.dp)) {
                ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.weaken()) {
                        role()
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeList(
    episodes: List<BangumiEpisode>,
    horizontalPadding: Dp,
    onClickItem: (BangumiEpisode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontalSpacedBy = 8.dp
    val onClickItemState by rememberUpdatedState(onClickItem)
    LazyHorizontalGrid(
        GridCells.Fixed(1),
        modifier = modifier.height(60.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacedBy),
    ) {
        item(key = "spacer header") { Spacer(Modifier.width(horizontalPadding - horizontalSpacedBy)) }
        items(episodes, key = { it.id }) { episode ->
            EpisodeItem(episode, { onClickItemState(episode) }, Modifier.widthIn(min = 60.dp, max = 160.dp))
        }
        item(key = "spacer footer") { Spacer(Modifier.width(horizontalPadding - horizontalSpacedBy)) }
    }
}

/**
 * 一个剧集:
 * ```
 * |------------|
 * | 01 冒险结束 |
 * |       评论 |
 * |------------|
 * ```
 */
@Composable
fun EpisodeItem(
    episode: BangumiEpisode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Card(
        onClick,
        modifier.clip(shape),
        shape = shape,
//        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // "01"
                Text(episode.sort.fixToString(2), style = MaterialTheme.typography.bodyMedium)

                Spacer(Modifier.weight(1f, fill = false))

                // "冒险结束"
                Text(
                    episode.chineseName,
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.ChatBubbleOutline,
                    null,
                    Modifier.size(16.dp),
                )
                Text(
                    remember { "${episode.comment}" },
                    Modifier.offset(y = (-1).dp).padding(start = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(modifier: Modifier = Modifier, text: @Composable () -> Unit) {
    Row(modifier.padding(top = 8.dp, bottom = 8.dp)) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                text()
            }
        }
    }
}
