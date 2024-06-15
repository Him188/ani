package me.him188.ani.app.ui.subject.episode.mediaFetch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.subject.episode.details.renderSubtitleLanguage


private inline val minWidth get() = 60.dp
private inline val maxWidth get() = 120.dp

/**
 * 筛选
 */
@Composable
fun MediaSelectorFilters(
    state: MediaSelectorPresentation,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
//        MediaSelectorFilterChip(
//            selected = state.selectedMediaSource,
//            allValues = { state.mediaSources },
//            onSelect = { state.preferMediaSource(it) },
//            onDeselect = { state.preferMediaSource(it, removeOnExist = true) },
//            name = { Text("数据源") },
//            Modifier.widthIn(min = minWidth, max = maxWidth),
//            label = { MediaSelectorFilterChipText(renderMediaSource(it)) },
//            leadingIcon = { id ->
//                MediaSourceIcon(id)
//            }
//        )
        MediaSelectorFilterChip(
            selected = state.resolution.finalSelected,
            allValues = { state.resolution.available },
            onSelect = { state.resolution.prefer(it) },
            onDeselect = { state.resolution.removePreference() },
            name = { Text("分辨率") },
            Modifier.widthIn(min = minWidth, max = maxWidth),
        )
        MediaSelectorFilterChip(
            selected = state.subtitleLanguageId.finalSelected,
            allValues = { state.subtitleLanguageId.available },
            onSelect = { state.subtitleLanguageId.prefer(it) },
            onDeselect = { state.subtitleLanguageId.removePreference() },
            name = { Text("字幕") },
            Modifier.widthIn(min = minWidth, max = maxWidth),
            label = { MediaSelectorFilterChipText(renderSubtitleLanguage(it)) }
        )
        MediaSelectorFilterChip(
            selected = state.alliance.finalSelected,
            allValues = { state.alliance.available },
            onSelect = { state.alliance.prefer(it) },
            onDeselect = { state.alliance.removePreference() },
            name = { Text("字幕组") },
            Modifier.widthIn(min = minWidth, max = maxWidth),
        )
    }
}

@Composable
private fun MediaSelectorFilterChipText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        overflow = TextOverflow.Clip,
        softWrap = false,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * @param selected 选中的值, 为 null 时表示未选中
 * @param name 未被选中时显示
 * @param label 选中时显示
 */
@Composable
private fun <T : Any> MediaSelectorFilterChip(
    selected: T?,
    allValues: () -> List<T>,
    onSelect: (T) -> Unit,
    onDeselect: (T) -> Unit,
    name: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> Unit = { MediaSelectorFilterChipText(it.toString()) },
    leadingIcon: @Composable ((T?) -> Unit)? = null,
) {
    var showDropdown by rememberSaveable {
        mutableStateOf(false)
    }

    val allValuesState by remember(allValues) {
        derivedStateOf(allValues)
    }
    val isSingleValue by remember { derivedStateOf { allValuesState.size == 1 } }
    val selectedState by rememberUpdatedState(selected)

    Box {
        InputChip(
            selected = isSingleValue || selected != null,
            onClick = {
                if (!isSingleValue) {
                    showDropdown = true
                }
            },
            label = {
                if (isSingleValue) {
                    allValuesState.firstOrNull()?.let {
                        label(it)
                    }
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.alpha(if (selectedState == null) 1f else 0f) // 总是占位
                        ) {
                            name()
                        }
                        selectedState?.let {
                            label(it)
                        }
                    }
                }
            },
            leadingIcon = leadingIcon?.let { { leadingIcon(selectedState) } },
            trailingIcon = if (isSingleValue) null else {
                {
                    if (selected == null) {
                        Icon(Icons.Default.ArrowDropDown, "展开")
                    } else {
                        Icon(
                            Icons.Default.Close, "取消筛选",
                            Modifier.clickable { selectedState?.let { onDeselect(it) } }
                        )
                    }
                }
            },
            modifier = modifier.heightIn(min = 40.dp),
        )

        DropdownMenu(showDropdown, onDismissRequest = { showDropdown = false }) {
            allValuesState.forEach { item ->
                DropdownMenuItem(
                    text = { label(item) },
                    trailingIcon = {
                        if (selectedState == item) {
                            Icon(Icons.Default.Check, "当前选中")
                        }
                    },
                    onClick = {
                        onSelect(item)
                        showDropdown = false
                    }
                )
            }
        }
    }
}
