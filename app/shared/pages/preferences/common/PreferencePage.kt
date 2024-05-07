package me.him188.ani.app.ui.preference

import androidx.annotation.IntRange
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.TopAppBarGoBackButton
import me.him188.ani.app.ui.foundation.effects.defaultFocus
import me.him188.ani.app.ui.foundation.effects.onKey
import me.him188.ani.app.ui.foundation.pagerTabIndicatorOffset
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.foundation.widgets.RichDialogLayout
import me.him188.ani.app.ui.preference.tabs.AboutTab
import me.him188.ani.app.ui.preference.tabs.NetworkPreferenceTab
import me.him188.ani.app.ui.preference.tabs.media.MediaPreferenceTab
import me.him188.ani.app.ui.theme.stronglyWeaken
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

enum class PreferenceTab {
    //    ABOUT,
    MEDIA,
    NETWORK,
    ABOUT,
    ;

    companion object {
        val Default = MEDIA
    }
}

@Composable
fun PreferencePage(
    modifier: Modifier = Modifier,
    initialTab: PreferenceTab = PreferenceTab.Default,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    TopAppBarGoBackButton()
                },
            )
        }
    ) { topBarPaddings ->
        val pagerState =
            rememberPagerState(initialPage = initialTab.ordinal) { PreferenceTab.entries.size }
        val scope = rememberCoroutineScope()

        // Pager with TabRow
        Column(Modifier.padding(topBarPaddings).fillMaxSize()) {
            SecondaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                indicator = @Composable { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.pagerTabIndicatorOffset(pagerState, tabPositions),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                PreferenceTab.entries.forEachIndexed { index, tabId ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(text = renderPreferenceTab(tabId))
                        }
                    )
                }
            }

            HorizontalPager(state = pagerState, Modifier.fillMaxSize()) { index ->
                val type = PreferenceTab.entries[index]
                Column(Modifier.fillMaxSize().padding(contentPadding)) {
                    when (type) {
                        PreferenceTab.MEDIA -> MediaPreferenceTab(modifier = Modifier.fillMaxSize())
                        PreferenceTab.NETWORK -> NetworkPreferenceTab(modifier = Modifier.fillMaxSize())
                        PreferenceTab.ABOUT -> AboutTab(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}


@Composable
private fun renderPreferenceTab(
    tab: PreferenceTab,
): String {
    return when (tab) {
//        PreferenceTab.GENERAL -> "通用"
        PreferenceTab.NETWORK -> "网络"
        PreferenceTab.MEDIA -> "播放与缓存"
        PreferenceTab.ABOUT -> "关于"
    }
}

@Composable
internal fun PreferenceTab(
    modifier: Modifier = Modifier,
    content: @Composable PreferenceScope.() -> Unit,
) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val scope = remember(this) {
            object : PreferenceScope(), ColumnScope by this {}
        }
        scope.content()
    }
}

@DslMarker
annotation class PreferenceDsl

private const val LABEL_ALPHA = 0.8f

@PreferenceDsl
abstract class PreferenceScope {
    @Stable
    @PublishedApi
    internal val itemHorizontalPadding = 16.dp

    @PreferenceDsl
    @Composable
    fun Group(
        title: @Composable () -> Unit,
        description: (@Composable () -> Unit)? = null,
        modifier: Modifier = Modifier,
        useThinHeader: Boolean = false,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        Surface(modifier = modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = if (useThinHeader) 12.dp else 16.dp)) {
                // Group header
                Column(
                    Modifier.padding(horizontal = itemHorizontalPadding)
                        .padding(bottom = 8.dp)
                        .fillMaxWidth()
                        .heightIn(min = if (description != null) 48.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ProvideTextStyleContentColor(
                        MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Row { title() }
                    }

                    description?.let {
                        ProvideTextStyleContentColor(
                            MaterialTheme.typography.labelMedium,
                            LocalContentColor.current.copy(LABEL_ALPHA),
                        ) {
                            Row(Modifier.padding()) { it() }
                        }
                    }
                }

                // items
                content()
            }
        }
    }

    @Composable
    private fun ItemHeader(
        title: @Composable RowScope.() -> Unit,
        description: @Composable (() -> Unit)?,
        modifier: Modifier = Modifier,
    ) {
        Column(
            modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CompositionLocalProvider(
                LocalContentColor providesDefault MaterialTheme.colorScheme.onSurface
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.bodyLarge,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) { title() }
                }
            }
            ProvideTextStyleContentColor(
                MaterialTheme.typography.labelMedium,
                LocalContentColor.current.copy(LABEL_ALPHA)
            ) {
                description?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) { it() }
                }
            }
        }
    }

    @Composable
    inline fun SubGroup(
        content: () -> Unit,
    ) {
        Column(Modifier.padding(start = itemHorizontalPadding)) {
            content()
        }
    }

    @Composable
    fun Item(
        modifier: Modifier = Modifier,
        icon: @Composable (() -> Unit)? = null,
        action: @Composable (() -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        Row(
            modifier
                .padding(horizontal = itemHorizontalPadding)
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(Modifier.padding(end = 8.dp).size(28.dp), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalContentColor providesDefault MaterialTheme.colorScheme.onSurface) {
                        icon()
                    }
                }
            }

            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                content()
            }

            action?.let {
                Box(
                    Modifier.padding(start = 16.dp)
                        .widthIn(min = 48.dp), contentAlignment = Alignment.Center
                ) {
                    ProvideTextStyleContentColor(
                        MaterialTheme.typography.labelLarge,
                        MaterialTheme.colorScheme.primary
                    ) {
                        it()
                    }
                }
            }
        }
    }

    @PreferenceDsl
    @Composable
    fun HorizontalDividerItem(modifier: Modifier = Modifier) {
        Row(
            modifier
                .padding(horizontal = itemHorizontalPadding)
                .fillMaxWidth() // no min 48.dp height
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.stronglyWeaken())
        }
    }


    /**
     * A switch item that only the switch is interactable.
     */
    @PreferenceDsl
    @Composable
    fun SwitchItem(
        title: @Composable RowScope.() -> Unit,
        modifier: Modifier = Modifier,
        description: @Composable (() -> Unit)? = null,
        switch: @Composable () -> Unit,
    ) {
        Item(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemHeader(title, description, Modifier.weight(1f).padding(end = 16.dp))
                switch()
            }
        }
    }


    @PreferenceDsl
    @Composable
    fun SliderItem(
        title: @Composable RowScope.() -> Unit,
        modifier: Modifier = Modifier,
        description: @Composable (() -> Unit)? = null,
        valueLabel: @Composable (() -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        Item(modifier) {
            Column {
                Row(
                    Modifier,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ItemHeader(title, description, Modifier.weight(1f))

                    valueLabel?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                                valueLabel()
                            }
                        }
                    }
                }
                content()
            }
        }
    }

    @PreferenceDsl
    @Composable
    fun TextFieldItem(
        value: String,
        onValueChange: (String) -> Unit,
        title: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        description: @Composable (() -> Unit)? = null,
        icon: @Composable (() -> Unit)? = null,
        placeholder: @Composable (() -> Unit)? = null,
        onValueChangeCompleted: () -> Unit = {},
        inverseTitleDescription: Boolean = false,
        isErrorProvider: () -> Boolean = { false }, // calculated in a derivedState
        textFieldDescription: @Composable (() -> Unit)? = description,
    ) {
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Item(
            modifier.clickable(onClick = { showDialog = true }),
            icon = icon,
        ) {
            Row(
                Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val valueText = @Composable {
                    if (placeholder != null && value.isEmpty()) {
                        placeholder()
                    } else {
                        Text(value)
                    }
                }
                ItemHeader(
                    title = {
                        if (inverseTitleDescription) {
                            valueText()
                        } else {
                            title()
                        }

                    },
                    description = {
                        if (inverseTitleDescription) {
                            title()
                        } else {
                            valueText()
                        }
                    },
                    Modifier.weight(1f)
                )

                IconButton({ showDialog = true }) {
                    Icon(Icons.Rounded.Edit, "编辑", tint = MaterialTheme.colorScheme.primary)
                }

                if (showDialog) {
                    val error by remember {
                        derivedStateOf {
                            isErrorProvider()
                        }
                    }
                    val onConfirm = {
                        onValueChangeCompleted()
                        showDialog = false
                    }

                    TextFieldDialog(
                        onDismissRequest = { showDialog = false },
                        onConfirm = onConfirm,
                        title = title,
                        confirmEnabled = !error,
                        description = textFieldDescription,
                    ) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = onValueChange,
                            shape = MaterialTheme.shapes.medium,
                            keyboardActions = KeyboardActions {
                                if (!error) {
                                    onConfirm()
                                }
                            },
                            keyboardOptions = KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier.fillMaxWidth()
                                .defaultFocus()
                                .onKey(Key.Enter) {
                                    if (!error) {
                                        onConfirm()
                                    }
                                },
                            isError = error,
                        )
                    }
                }
            }
        }
    }


    @PreferenceDsl
    @Composable
    fun TextButtonItem(
        onClick: () -> Unit,
        title: @Composable () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Item(
            modifier.clickable(onClick = onClick),
            action = {
                TextButton(onClick) {
                    title()
                }
            }
        ) {
        }
    }

    @PreferenceDsl
    @Composable
    fun RowButtonItem(
        onClick: () -> Unit,
        title: @Composable () -> Unit,
        description: @Composable (() -> Unit)? = null,
        modifier: Modifier = Modifier,
    ) {
        Item(
            modifier.clickable(onClick = onClick),
        ) {
            ItemHeader(
                title = {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                        title()
                    }
                },
                description
            )
        }
    }

    /**
     * Can become a text button if [onClick] is not null.
     */
    @PreferenceDsl
    @Composable
    fun TextItem(
        title: @Composable RowScope.() -> Unit,
        modifier: Modifier = Modifier,
        description: @Composable (() -> Unit)? = null,
        icon: @Composable (() -> Unit)? = null,
        action: @Composable (() -> Unit)? = null,
        onClick: (() -> Unit)? = null
    ) {
        Item(
            modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            icon = kotlin.run {
                if (icon != null && onClick != null) {
                    {
                        CompositionLocalProvider(LocalContentColor providesDefault MaterialTheme.colorScheme.primary) {
                            icon()
                        }
                    }
                } else {
                    icon
                }
            },
            action = action
        ) {
            if (onClick != null) {
                ItemHeader(
                    {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                            title()
                        }
                    },
                    description,
                    Modifier
                )
            } else {
                ItemHeader(title, description, Modifier)
            }
        }
    }

    @PreferenceDsl
    @Composable
    fun <T> DropdownItem(
        selected: () -> T,
        values: () -> List<T>,
        itemText: @Composable (T) -> Unit,
        onSelect: (T) -> Unit,
        itemIcon: @Composable ((T) -> Unit)? = null,
        modifier: Modifier = Modifier,
        description: @Composable (() -> Unit)? = null,
        icon: @Composable (() -> Unit)? = null,
        title: @Composable (RowScope.() -> Unit),
    ) {
        var showDropdown by rememberSaveable { mutableStateOf(false) }

        val selectedState by remember {
            derivedStateOf { selected() }
        }
        TextItem(
            title = title,
            modifier = modifier,
            description = description,
            icon = icon,
            action = {
                TextButton(onClick = { showDropdown = true }) {
                    itemText(selectedState)
                }
                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false },
                ) {
                    values().forEach { value ->
                        val color = if (value == selectedState) {
                            MaterialTheme.colorScheme.primary
                        } else Color.Unspecified
                        CompositionLocalProvider(LocalContentColor providesDefault color) {
                            DropdownMenuItem(
                                text = { itemText(value) },
                                leadingIcon = if (itemIcon != null) {
                                    {
                                        itemIcon(value)
                                    }
                                } else null,
                                onClick = {
                                    onSelect(value)
                                    showDropdown = false
                                }
                            )
                        }
                    }
                }
            },
        )
    }

    /**
     * 用户排序
     *
     * @param exposed 未展开时显示在项目右侧的标签, 来表示当前的排序
     * @param key 用于区分每个项目的唯一键, 必须快速且稳定
     */
    @PreferenceDsl
    @Composable
    fun <T> SorterItem(
        values: () -> List<SelectableItem<T>>,
        onSort: (List<SelectableItem<T>>) -> Unit,
        exposed: @Composable (List<SelectableItem<T>>) -> Unit,
        item: @Composable (T) -> Unit,
        key: (T) -> Any,
        modifier: Modifier = Modifier,
        description: @Composable (() -> Unit)? = null,
        dialogDescription: @Composable (() -> Unit)? = description,
        dialogItemDescription: @Composable ((T) -> Unit)? = null,
        icon: @Composable (() -> Unit)? = null,
        onConfirm: (() -> Unit)? = null,
        title: @Composable (RowScope.() -> Unit),
    ) {
        var showDialog by rememberSaveable { mutableStateOf(false) }

        TextItem(
            title = title,
            modifier = modifier,
            description = description,
            icon = icon,
            action = {
                val valuesState by remember {
                    derivedStateOf { values() }
                }
                TextButton(onClick = { showDialog = true }, Modifier.widthIn(max = 128.dp)) {
                    exposed(valuesState)
                }

                if (showDialog) {
                    var sortingData by remember(valuesState) {
                        mutableStateOf(valuesState)
                    }
                    val state = rememberReorderableLazyListState(
                        onMove = { from, to ->
                            sortingData = sortingData.toMutableList().apply {
                                add(to.index, removeAt(from.index))
                            }
                        }
                    )
                    BasicAlertDialog(onDismissRequest = { showDialog = false }) {
                        RichDialogLayout(
                            title = { title() },
                            description = dialogDescription?.let { { it() } },
                            buttons = {
                                TextButton({ showDialog = false }) {
                                    Text("取消")
                                }
                                Button({
                                    showDialog = false
                                    onConfirm?.invoke()
                                    onSort(sortingData)
                                }) {
                                    Text("完成")
                                }
                            },
                        ) {
                            LazyColumn(
                                state = state.listState,
                                modifier = Modifier
                                    .reorderable(state)
                                    .detectReorderAfterLongPress(state)
                            ) {
                                itemsIndexed(sortingData, key = { _, it -> key(it.item) }) { index, item ->
                                    ReorderableItem(state, key = key(item.item)) { isDragging ->
                                        val elevation = animateDpAsState(if (isDragging) 16.dp else 0.dp)
                                        Row(
                                            modifier = Modifier
                                                .shadow(elevation.value)
                                                .background(MaterialTheme.colorScheme.surfaceVariant) // match card background
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = item.selected,
                                                onCheckedChange = {
                                                    sortingData = sortingData.toMutableList().apply {
                                                        set(index, SelectableItem(item.item, it))
                                                    }
                                                },
                                                modifier = Modifier.padding(end = 4.dp)
                                            )

                                            Row(Modifier.weight(1f)) {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    item(item.item)
                                                    ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                                                        dialogItemDescription?.invoke(item.item)
                                                    }
                                                }
                                            }

                                            Icon(
                                                Icons.Rounded.Reorder,
                                                "长按排序",
                                                Modifier.detectReorder(state),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

@Stable
class SelectableItem<T>(
    val item: T,
    val selected: Boolean
)

@Composable
internal fun TextFieldDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    title: @Composable () -> Unit,
    confirmEnabled: Boolean = true,
    description: @Composable (() -> Unit)? = null,
    textField: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row {
                    ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                        title()
                    }
                }

                Row {
                    textField()
                }

                ProvideTextStyleContentColor(
                    MaterialTheme.typography.labelMedium,
                    LocalContentColor.current.copy(LABEL_ALPHA)
                ) {
                    description?.let {
                        Row(Modifier.padding(horizontal = 8.dp)) {
                            it()
                        }
                    }
                }

                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismissRequest) { Text("取消") }

                    Button(
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                    ) {
                        Text("确认")
                    }
                }
            }
        }
    }
}

/**
 * A switch item that the entire item is clickable.
 */
@PreferenceDsl
@Composable
fun PreferenceScope.SwitchItem(
    onClick: () -> Unit,
    title: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    description: @Composable (() -> Unit)? = null,
    switch: @Composable () -> Unit,
) {
    SwitchItem(
        title, modifier.clickable(onClick = onClick), description, switch
    )
}

/**
 * A switch item that the entire item is clickable.
 */
@PreferenceDsl
@Composable
fun PreferenceScope.SwitchItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    description: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    SwitchItem(
        { onCheckedChange(!checked) },
        title,
        modifier,
        description,
    ) {
        Switch(
            checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}


@PreferenceDsl
@Composable
fun PreferenceScope.SliderItem(
    value: Float,
    onValueChange: (Float) -> Unit,
    title: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    @IntRange(from = 0)
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    valueLabel: @Composable (() -> Unit)? = {
        Text(value.toString())
    },
    description: @Composable (() -> Unit)? = null,
) {
    SliderItem(title, modifier, description, valueLabel) {
        Slider(
            value,
            onValueChange,
            Modifier,
            enabled,
            valueRange,
            steps,
            onValueChangeFinished,
            colors,
            interactionSource
        )
    }
}
