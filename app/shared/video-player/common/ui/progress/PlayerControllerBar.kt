package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SubtitlesOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults.Container
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import me.him188.ani.app.platform.PlatformPopupProperties
import me.him188.ani.app.ui.foundation.effects.onKey
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.theme.aniDarkColorTheme
import me.him188.ani.app.ui.foundation.theme.aniLightColorTheme
import me.him188.ani.app.ui.foundation.theme.slightlyWeaken
import me.him188.ani.app.ui.foundation.theme.stronglyWeaken
import me.him188.ani.app.videoplayer.ui.top.needWorkaroundForFocusManager


@Stable
object PlayerControllerDefaults {
    /**
     * To pause/play
     */
    @Composable
    fun PlaybackIcon(
        isPlaying: () -> Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        IconButton(
            onClick = onClick,
            modifier,
        ) {
            if (isPlaying()) {
                Icon(Icons.Rounded.Pause, contentDescription = "Pause", Modifier.size(36.dp))
            } else {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", Modifier.size(36.dp))
            }
        }
    }

    /**
     * To turn danmaku on/off
     */
    @Composable
    fun DanmakuIcon(
        danmakuEnabled: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        IconButton(
            onClick = onClick,
            modifier,
        ) {
            if (danmakuEnabled) {
                Icon(Icons.Rounded.Subtitles, contentDescription = "禁用弹幕")
            } else {
                Icon(Icons.Rounded.SubtitlesOff, contentDescription = "启用弹幕")
            }
        }
    }

    @Composable
    fun NextEpisodeIcon(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        IconButton(
            onClick,
            modifier,
        ) {
            Icon(Icons.Rounded.SkipNext, "下一集", Modifier.size(36.dp))
        }
    }

    @Composable
    fun SelectEpisodeIcon(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        TextButton(
            onClick,
            modifier,
            colors = ButtonDefaults.textButtonColors(
                contentColor = LocalContentColor.current,
            ),
        ) {
            Text("选集")
        }
    }

    // TODO: DANMAKU_PLACEHOLDERS i18n
    // See #120
    @Stable
    private val DANMAKU_PLACEHOLDERS = listOf(
        "来发一条弹幕吧~",
        "小心，我要发射弹幕啦！",
        "每一条弹幕背后，都有一个不为人知的秘密",
        "召唤弹幕精灵！",
        "这一刻的感受，只有你最懂",
        "让弹幕变得不一样",
        "弹幕世界大门已开",
        "字里行间，藏着宇宙的秘密",
        "在光与影的交织中，你的话语是唯一的真实",
        "有趣的灵魂万里挑一",
        "说点什么",
        "长期征集有趣的弹幕广告词",
        "广告位招租",
        "\uD83E\uDD14",
        "梦开始的地方",
        "心念成形",
        "發個彈幕炒熱氣氛！",
        "來個彈幕吧！",
        "發個友善的彈幕吧！",
        "是不是忍不住想發彈幕了呢？",
    )

    fun randomDanmakuPlaceholder(): String = DANMAKU_PLACEHOLDERS.random()

    /**
     * To send danmaku
     */
    @Composable
    fun DanmakuSendButton(
        onClick: () -> Unit,
        enabled: Boolean = true,
        modifier: Modifier = Modifier,
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "发送")
        }
    }

    /**
     * To edit danmaku and send it by [trailingIcon]
     */
    @Composable
    fun DanmakuTextField(
        value: String,
        onValueChange: (String) -> Unit,
        modifier: Modifier = Modifier,
        onSend: () -> Unit = {},
        isSending: Boolean = false,
        interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
        placeholder: @Composable () -> Unit = {
            Text(
                remember { randomDanmakuPlaceholder() },
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        },
        leadingIcon: @Composable (() -> Unit)? = null,
        trailingIcon: @Composable (() -> Unit)? = {
            if (isSending) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
//                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.surface,
                )
            } else {
                DanmakuSendButton(
                    onClick = { onSend() },
                    enabled = value.isNotBlank(),
                )
            }
        },
        enabled: Boolean = true,
        singleLine: Boolean = true,
        isError: Boolean = false,
        shape: Shape = MaterialTheme.shapes.medium,
        style: TextStyle = MaterialTheme.typography.bodyMedium,
    ) {
        val colors: TextFieldColors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.stronglyWeaken(),
            focusedContainerColor = MaterialTheme.colorScheme.surface.stronglyWeaken(),
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface.slightlyWeaken(),
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
        )
        MaterialTheme(aniLightColorTheme()) {
            BasicTextField(
                value,
                onValueChange,
                modifier.onKey(Key.Enter) {
                    onSend()
                }.height(38.dp),
                textStyle = style.copy(color = colors.unfocusedTextColor),
                cursorBrush = SolidColor(rememberUpdatedState(if (isError) colors.errorCursorColor else colors.cursorColor).value),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                decorationBox = { innerTextField ->
                    OutlinedTextFieldDefaults.DecorationBox(
                        value,
                        innerTextField,
                        enabled = enabled,
                        singleLine = singleLine,
                        visualTransformation = VisualTransformation.None,
                        interactionSource = interactionSource,
                        contentPadding = PaddingValues(vertical = 7.dp, horizontal = 16.dp),
                        colors = colors,
                        placeholder = {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.weight(1f)) {
                                    placeholder()
                                }
                            }
                        },
                        leadingIcon = leadingIcon,
                        trailingIcon = trailingIcon,
                        container = {
                            Container(
                                enabled = enabled,
                                isError = isError,
                                interactionSource = interactionSource,
                                colors = colors,
                                shape = shape,
                            )
                        },
                    )
                },
            )
        }
    }

    /**
     * To enter/exit fullscreen
     */
    @Composable
    fun FullscreenIcon(
        isFullscreen: Boolean,
        onClickFullscreen: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val focusManager by rememberUpdatedState(LocalFocusManager.current) // workaround for #288
        IconButton(
            onClick = onClickFullscreen,
            modifier.ifThen(needWorkaroundForFocusManager) {
                onFocusEvent {
                    if (it.hasFocus) {
                        focusManager.clearFocus()
                    }
                }
            },
        ) {
            if (isFullscreen) {
                Icon(Icons.Rounded.FullscreenExit, contentDescription = "Exit Fullscreen", Modifier.size(32.dp))
            } else {
                Icon(Icons.Rounded.Fullscreen, contentDescription = "Enter Fullscreen", Modifier.size(32.dp))
            }
        }
    }

    /**
     * Set 1x, 2x playback speed.
     * @param optionsProvider The options to choose from. Note that when the value changes, it will not reflect in the UI.
     */
    @Composable
    fun SpeedSwitcher(
        value: Float,
        onValueChange: (Float) -> Unit,
        modifier: Modifier = Modifier,
        optionsProvider: () -> List<Float> = { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f) },
    ) {
        return OptionsSwitcher(
            value = value,
            onValueChange = onValueChange,
            optionsProvider = optionsProvider,
            renderValue = { Text(remember(it) { "${it}x" }) },
            renderValueExposed = { Text(remember(it) { if (it == 1.0f) "倍速" else """${it}x""" }) },
            modifier,
            properties = PlatformPopupProperties(
                clippingEnabled = false,
            ),
        )
    }

    /**
     * @param optionsProvider The options to choose from. Note that when the value changes, it will not reflect in the UI.
     */
    @Composable
    fun <T> OptionsSwitcher(
        value: T,
        onValueChange: (T) -> Unit,
        optionsProvider: () -> List<T>,
        renderValue: @Composable (T) -> Unit,
        renderValueExposed: @Composable (T) -> Unit = renderValue,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        properties: PopupProperties = PopupProperties(),
    ) {
        Box(modifier, contentAlignment = Alignment.Center) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            TextButton(
                { expanded = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = LocalContentColor.current,
                ),
                enabled = enabled,
            ) {
                renderValueExposed(value)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                properties = properties,
            ) {
                val options = remember(optionsProvider) { optionsProvider() }
                for (option in options) {
                    DropdownMenuItem(
                        text = {
                            val color = if (value == option) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            }
                            CompositionLocalProvider(LocalContentColor provides color) {
                                renderValue(option)
                            }
                        },
                        onClick = {
                            expanded = false
                            onValueChange(option)
                        },
                    )
                }
            }
        }
    }
}

/**
 * The controller bar of a video player. Usually at the bottom of the screen (the video player).
 *
 * See [PlayerControllerDefaults] for components.
 *
 * @param startActions [PlayerControllerDefaults.PlaybackIcon], [PlayerControllerDefaults.DanmakuIcon]
 * @param progressIndicator [ProgressIndicator]
 * @param progressSlider [ProgressSlider]
 * @param danmakuEditor [PlayerControllerDefaults.DanmakuTextField]
 * @param endActions [PlayerControllerDefaults.FullscreenIcon]
 * @param expanded Whether the controller bar is expanded.
 * If `true`, the [progressIndicator] and [progressSlider] will be shown on a separate row above. The bottom row will contain a [danmakuEditor].
 * If `false`, the entire bar will be only one row. [danmakuEditor] will be ignored.
 */
@Composable
fun PlayerControllerBar(
    startActions: @Composable RowScope.() -> Unit,
    progressIndicator: @Composable RowScope.() -> Unit,
    progressSlider: @Composable RowScope.() -> Unit,
    danmakuEditor: @Composable RowScope.() -> Unit,
    endActions: @Composable RowScope.() -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clickable(remember { MutableInteractionSource() }, null, onClick = {}) // Consume touch event
            .padding(
                horizontal = if (expanded) 8.dp else 4.dp,
                vertical = if (expanded) 4.dp else 2.dp,
            ),
    ) {
        Column {
            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                Row(
                    Modifier
                        .padding(start = if (expanded) 8.dp else 4.dp)
                        .padding(vertical = if (expanded) 4.dp else 2.dp),
                ) {
                    progressIndicator()
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    progressSlider()
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 4.dp),
        ) {
            // 播放 / 暂停按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                startActions()
            }

            ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                Column(Modifier.weight(1f)) {
                    Row {
                        danmakuEditor()
                    }
                }
            }


            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MaterialTheme(aniDarkColorTheme()) {
                    endActions()
                }
            }
        }
    }
}