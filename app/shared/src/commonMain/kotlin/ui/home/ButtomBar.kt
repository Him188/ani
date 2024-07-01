/*
 * Ani
 * Copyright (C) 2022-2024 Him188
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.him188.ani.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun TabIcon(
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clickable(onClick = onClick).width(72.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            text()
        }
    }
}

@Composable
fun <Tab> TabBar(
    selectedTab: MutableState<Tab>,
    modifier: Modifier = Modifier,
    selectedContentColor: Color = MaterialTheme.colorScheme.primary,
    unselectedContentColor: Color = LocalContentColor.current,
    block: @Composable TabBarScope<Tab>.() -> Unit,
) {
    val selectedTabState by rememberUpdatedState(selectedTab)
    val selectedContentColorState by rememberUpdatedState(selectedContentColor)
    val unselectedContentColorState by rememberUpdatedState(unselectedContentColor)

    BottomAppBar(horizontalArrangement = Arrangement.SpaceBetween, modifier = modifier) {
        val scope = remember {
            object : TabBarScope<Tab>(), RowScope by this {
                override val selectedTab: MutableState<Tab> get() = selectedTabState
                override val selectedContentColor: Color get() = selectedContentColorState
                override val unselectedContentColor: Color get() = unselectedContentColorState
            }
        }

        block(scope)
    }
}

abstract class TabBarScope<Tab> : RowScope {
    abstract val selectedTab: MutableState<Tab>
    abstract val selectedContentColor: Color
    abstract val unselectedContentColor: Color

    @Composable
    fun tab(
        tab: Tab,
        icon: @Composable () -> Unit,
        text: @Composable () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val newColor: Color = if (selectedTab.value == tab) {
            selectedContentColor
        } else {
            unselectedContentColor
        }

        CompositionLocalProvider(
            LocalContentColor provides newColor,
        ) {
            Box(
                modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = { selectedTab.value = tab },
                    )
                    .width(72.dp)
                    .padding(vertical = 8.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    icon()
                    text()
                }
            }
        }

    }
}

/**
 * <a href="https://m3.material.io/components/bottom-app-bar/overview" class="external" target="_blank">Material Design bottom app bar</a>.
 *
 * A bottom app bar displays navigation and key actions at the bottom of mobile screens.
 *
 * ![Bottom app bar image](https://developer.android.com/images/reference/androidx/compose/material3/bottom-app-bar.png)
 *
 * If you are interested in displaying a [FloatingActionButton], consider using another overload.
 *
 * Also see [NavigationBar].
 *
 * @param modifier the [Modifier] to be applied to this BottomAppBar
 * @param containerColor the color used for the background of this BottomAppBar. Use
 * [Color.Transparent] to have no color.
 * @param contentColor the preferred color for content inside this BottomAppBar. Defaults to either
 * the matching content color for [containerColor], or to the current [LocalContentColor] if
 * [containerColor] is not a color from the theme.
 * @param tonalElevation when [containerColor] is [ColorScheme.surface], a translucent primary color
 * overlay is applied on top of the container. A higher tonal elevation value will result in a
 * darker color in light theme and lighter color in dark theme. See also: [Surface].
 * @param contentPadding the padding applied to the content of this BottomAppBar
 * @param windowInsets a window insets that app bar will respect.
 * @param content the content of this BottomAppBar. The default layout here is a [Row],
 * so content inside will be placed horizontally.
 */
@Composable
fun BottomAppBar(
    modifier: Modifier = Modifier,
    containerColor: Color = BottomAppBarDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomAppBarDefaults.ContainerElevation,
    contentPadding: PaddingValues = BottomAppBarDefaults.ContentPadding,
    windowInsets: WindowInsets = BottomAppBarDefaults.windowInsets,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    height: Dp = 80.dp,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shape = RectangleShape,
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .height(height)
                .padding(contentPadding),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
