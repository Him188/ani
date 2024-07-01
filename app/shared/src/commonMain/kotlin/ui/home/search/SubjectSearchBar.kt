package me.him188.ani.app.ui.home.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NewLabel
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.constraintlayout.compose.layoutId
import me.him188.ani.app.ui.foundation.interaction.clearFocusOnKeyboardDismiss
import me.him188.ani.app.ui.foundation.widgets.OneshotTip
import kotlin.math.max
import androidx.compose.ui.unit.max as maxDp

@Composable
fun SubjectSearchBar(
    editingTagMode: Boolean,
    searchTag: List<SearchTag>,
    showDeleteTagTip: Boolean,
    searchHistory: List<SearchHistory>,
    contentPadding: PaddingValues,
    onActiveChange: (Boolean) -> Unit,
    onSearchFilterEvent: (SearchFilterEvent) -> Unit,
    onDeleteHistory: (Int) -> Unit,
    onStartEditingTagMode: () -> Unit,
    onSearch: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    initialActive: Boolean = false,
    initialSearchText: String = "",
) {
    var isActive by remember { mutableStateOf(initialActive) }
    var searchText by remember { mutableStateOf(initialSearchText) }
    var savedSearchText by remember { mutableStateOf(initialSearchText) }
    var searchMode by remember { mutableStateOf(SearchMode.KEYWORD) }

    val keyboard by rememberUpdatedState(LocalSoftwareKeyboardController.current)
    val imePadding = WindowInsets.ime.asPaddingValues()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current

    val horizontalPadding by animateDpAsState(
        targetValue = if (isActive) 0.dp else 16.dp,
        animationSpec = tween(easing = FastOutSlowInEasing),
    )
    val shapeSize by animateDpAsState(
        targetValue = if (isActive) 0.dp else 28.0.dp,
        animationSpec = tween(easing = FastOutSlowInEasing),
    )

    fun toggleActive(value: Boolean? = null) {
        isActive = value ?: !isActive
        onActiveChange(isActive)
    }

    SearchBar(
        query = searchText,
        active = isActive,
        placeholder = {
            Text(
                when (searchMode) {
                    SearchMode.KEYWORD -> "搜索"
                    SearchMode.EXPLORE -> "发现"
                },
            )
        },
        leadingIcon = {
            IconButton({ toggleActive() }) {
                Icon(
                    if (isActive) {
                        Icons.AutoMirrored.Outlined.ArrowBack
                    } else {
                        Icons.Outlined.Search
                    },
                    null,
                )
            }
        },
        trailingIcon = {
            if (searchText.isNotEmpty()) {
                IconButton({ searchText = "" }) {
                    Icon(Icons.Outlined.Close, null)
                }
            }
        },
        colors = SearchBarDefaults.colors(dividerColor = Color.Transparent),
        tonalElevation = SearchBarDefaults.TonalElevation,
        shape = RoundedCornerShape(shapeSize),
        modifier = Modifier
            .padding(horizontal = horizontalPadding)
            .clearFocusOnKeyboardDismiss()
            .onFocusEvent { state ->
                // 标签搜索模式下不允许输入文字或拉起键盘
                if ((state.isFocused || state.isCaptured) && searchMode == SearchMode.EXPLORE) {
                    focusManager.clearFocus(true)
                    if (!isActive) {
                        toggleActive(true)
                    }
                }
            }
            .focusRequester(focusRequester)
            .then(modifier),
        onActiveChange = { toggleActive(it) },
        onQueryChange = {
            if (searchMode == SearchMode.KEYWORD) {
                searchText = it
            }
        },
        onSearch = {
            if (!editingTagMode) {
                toggleActive(false)
                onSearch(it, false)
            }
            keyboard?.hide()
        },
    ) {

        var fabRotateAngle by remember { mutableStateOf(0f) }
        val fabRotateAnimated by animateFloatAsState(
            targetValue = fabRotateAngle,
            animationSpec = tween(easing = FastOutSlowInEasing),
        )
        var fabHeight by remember { mutableStateOf(0) }

        val bottomPadding by remember {
            derivedStateOf {
                PaddingValues(
                    top = 24.dp,
                    start = 24.dp,
                    end = 24.dp,
                    bottom = maxDp(
                        imePadding.calculateBottomPadding(),
                        contentPadding.calculateBottomPadding(),
                    ) + with(density) { fabHeight.toDp() } + 24.dp,
                )
            }
        }

        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                AnimatedVisibility(
                    !editingTagMode,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.layoutId("fab").zIndex(1.1f),
                ) {
                    if (false) ExtendedFloatingActionButton(
                        shape = CircleShape,
                        onClick = {
                            fabRotateAngle -= 180f
                            if (searchMode == SearchMode.EXPLORE) {
                                searchMode = SearchMode.KEYWORD
                                searchText = savedSearchText
                                focusRequester.requestFocus()
                                keyboard?.show()
                            } else {
                                searchMode = SearchMode.EXPLORE
                                savedSearchText = searchText
                                searchText = ""
                                keyboard?.hide()
                                focusManager.clearFocus(true)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Autorenew,
                            contentDescription = null,
                            modifier = Modifier
                                .scale(-1f, 1f) // flip horizontally
                                .rotate(fabRotateAnimated),
                        )
                        Text(
                            text = if (searchMode == SearchMode.EXPLORE) "关键词搜索" else "标签搜索",
                            modifier = Modifier.padding(start = 8.dp).animateContentSize(),
                        )
                    }
                }

                Crossfade(
                    targetState = searchMode,
                    modifier = Modifier
                        .layoutId("content")
                        .fillMaxSize(),
                ) { mode ->
                    when (mode) {
                        SearchMode.KEYWORD -> SearchHistoryList(
                            list = searchHistory,
                            contentPadding = bottomPadding,
                            modifier = Modifier.fillMaxSize(),
                            onClickItem = { historyId ->
                                toggleActive()
                                searchText = searchHistory.first { it.id == historyId }.content
                                onSearch(searchText, true)
                                keyboard?.hide()
                            },
                            onDeleteItem = onDeleteHistory,
                        )

                        SearchMode.EXPLORE -> SearchFilterPage(
                            tags = searchTag,
                            editingTagMode = editingTagMode,
                            showDeleteTagTip = showDeleteTagTip,
                            contentPadding = bottomPadding,
                            modifier = Modifier.fillMaxSize(),
                            onStartEditingTagMode = {
                                keyboard?.hide()
                                onStartEditingTagMode()
                            },
                            onSearchFilterEvent = onSearchFilterEvent,
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            val contentMeasurable = measurables.find { it.layoutId == "content" }
            val contentPlaceable = contentMeasurable?.measure(constraints.copy(minWidth = 0, minHeight = 0))
            val fabMeasurable = measurables.find { it.layoutId == "fab" }
            val fabPlaceable = fabMeasurable?.measure(constraints.copy(minWidth = 0, minHeight = 0))

            val width = contentPlaceable?.width ?: 0
            val height = contentPlaceable?.height ?: 0
            val fabWidth = fabPlaceable?.width ?: 0
            fabHeight = fabPlaceable?.height ?: 0

            val fabPadding = 24.dp.roundToPx()
            val bottomNavigatorPadding = contentPadding.calculateBottomPadding().roundToPx()
            val bottomImePadding = imePadding.calculateBottomPadding().roundToPx()

            layout(width, height) {
                contentPlaceable?.placeRelative(0, 0)
                fabPlaceable?.placeRelative(
                    width - fabWidth - fabPadding,
                    height - fabHeight - fabPadding - max(bottomNavigatorPadding, bottomImePadding),
                )
            }
        }
    }
}

@Composable
private fun SearchHistoryList(
    list: List<SearchHistory>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onClickItem: (Int) -> Unit,
    onDeleteItem: (Int) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        item("tag_history") {
            Text(
                text = "搜索历史",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item("history") {
            val verticalPadding by animateDpAsState(
                targetValue = if (list.isEmpty()) 24.dp else 12.dp,
                animationSpec = tween(easing = FastOutSlowInEasing),
            )
            Column(
                modifier = Modifier.padding(vertical = verticalPadding).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (list.isEmpty()) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "无搜索历史",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                } else {
                    list.forEach { history ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onClickItem(history.id) }
                                .padding(vertical = 12.dp)
                                .padding(start = 16.dp, end = 8.dp)
                                .animateItem(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = history.content,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            IconButton(
                                modifier = Modifier.size(32.dp),
                                onClick = { onDeleteItem(history.id) },
                            ) {
                                Icon(
                                    modifier = Modifier.size(22.dp),
                                    imageVector = Icons.Outlined.Close,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFilterPage(
    tags: List<SearchTag>,
    editingTagMode: Boolean,
    showDeleteTagTip: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onStartEditingTagMode: () -> Unit,
    onSearchFilterEvent: (SearchFilterEvent) -> Unit,
) {

    var newCustomFilterDialogOpened by rememberSaveable { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    if (newCustomFilterDialogOpened) {
        NewCustomSearchFilterDialog(
            onConfirm = { newTag ->
                newCustomFilterDialogOpened = false
                onSearchFilterEvent(SearchFilterEvent.AddTag(newTag))
            },
            onDismiss = { newCustomFilterDialogOpened = false },
        )
    }

    DatePicker(datePickerState)
    
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        item("tag_tag") {
            Text(
                text = "标签",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item("tag_row") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                tags.forEach { tag ->
                    TagFilterChip(
                        selected = tag.checked && !editingTagMode,
                        label = { Text(text = tag.content) },
                        showCloseTag = editingTagMode,
                        onClick = { onSearchFilterEvent(SearchFilterEvent.UpdateTag(tag.id, !tag.checked)) },
                        onCloseTag = { onSearchFilterEvent(SearchFilterEvent.DeleteTag(tag.id)) },
                    )
                }
                AnimatedVisibility(
                    visible = !editingTagMode,
                    enter = expandHorizontally(),
                    exit = shrinkHorizontally(),
                ) {
                    AddNewTagChip(
                        // TODO: Chip composable 好像有 vertical padding 不知道从哪里来的。
                        // 我们自定义的支持长按的 chip 也需要加上。
                        modifier = Modifier.padding(vertical = 8.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        onLongClick = onStartEditingTagMode,
                        onClick = { newCustomFilterDialogOpened = true },
                    )
                }
            }
        }
        item("tag_tip") {
            AnimatedVisibility(
                visible = showDeleteTagTip,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                OneshotTip(
                    text = "您可以点击加号标签添加新搜索标签，长按加号标签进入标签删除模式。",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    onClose = { onSearchFilterEvent(SearchFilterEvent.DismissTagTip) },
                )
            }
        }
    }
}

enum class SearchMode {
    /**
     * 按关键词搜索
     */
    KEYWORD,

    /**
     * 按过滤器搜索
     */
    EXPLORE
}

/**
 * simplify from FilterChip
 */
@Composable
private fun AddNewTagChip(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {

    Surface(
        modifier = modifier.clip(FilterChipDefaults.shape).combinedClickable(
            role = Role.Checkbox,
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        color = containerColor,
        shape = FilterChipDefaults.shape,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = FilterChipDefaults.Height)
                .padding(16.dp, 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                    content = { Icon(Icons.Outlined.Add, contentDescription = null) },
                )
            },
        )
    }
}

@Composable
fun NewCustomSearchFilterDialog(
    modifier: Modifier = Modifier,
    onConfirm: (text: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isBlank()) {
                        onDismiss()
                    } else {
                        onConfirm(text)
                    }
                },
            ) {
                Text(text = "确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
        icon = { Icon(imageVector = Icons.Outlined.NewLabel, contentDescription = null) },
        title = {
            Text(text = "新增自定义标签")
        },
        text = {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.clearFocusOnKeyboardDismiss(),
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("自定义标签") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            if (text.isBlank()) {
                                onDismiss()
                            } else {
                                onConfirm(text)
                            }
                        },
                    ),
                )
                Text(
                    text = "新增自定义过滤标签，搜索结果将包含该标签。",
                )
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = true,
        ),
    )
}