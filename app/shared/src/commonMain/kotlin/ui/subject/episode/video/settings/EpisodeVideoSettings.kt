package me.him188.ani.app.ui.subject.episode.video.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repositories.SettingsRepository
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.platform.currentPlatform
import me.him188.ani.app.platform.isDesktop
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.AbstractSettingsViewModel
import me.him188.ani.app.ui.settings.framework.components.SliderItem
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.danmaku.ui.DanmakuRegexFilter
import me.him188.ani.danmaku.ui.DanmakuRegexFilterConfig
import me.him188.ani.danmaku.ui.DanmakuStyle
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Stable
interface EpisodeVideoSettingsViewModel {
    val danmakuConfig: DanmakuConfig
    val isLoading: Boolean
    val danmakuRegexFilterConfig: DanmakuRegexFilterConfig

    fun setDanmakuConfig(config: DanmakuConfig)
    fun addDanmakuRegexFilter(filter: DanmakuRegexFilter)
    fun editDanmakuRegexFilter(filter: DanmakuRegexFilter)
    fun removeDanmakuRegexFilter(filter: DanmakuRegexFilter)
    
    fun switchDanmakuRegexFilterCompletely()
    
    fun switchDanmakuRegexFilter(filter:DanmakuRegexFilter)
}

fun EpisodeVideoSettingsViewModel(): EpisodeVideoSettingsViewModel = EpisodeVideoSettingsViewModelImpl()

private class EpisodeVideoSettingsViewModelImpl : EpisodeVideoSettingsViewModel, AbstractSettingsViewModel(),
    KoinComponent {
    private val settingsRepository by inject<SettingsRepository>()

    val danmakuConfigSettings by settings(
        settingsRepository.danmakuConfig,
        DanmakuConfig(_placeholder = -1),
    )
    
    val danmakuRegexFilterConfigSettings by settings(
        settingsRepository.danmakuRegexFilterConfig,
        DanmakuRegexFilterConfig(_placeholder = -1)
    )

    override val danmakuConfig: DanmakuConfig by danmakuConfigSettings
    override val danmakuRegexFilterConfig: DanmakuRegexFilterConfig by danmakuRegexFilterConfigSettings
    override val isLoading: Boolean get() = danmakuConfigSettings.loading

    override fun setDanmakuConfig(config: DanmakuConfig) {
        danmakuConfigSettings.updateDebounced(config, 100.milliseconds)
    }

    override fun addDanmakuRegexFilter(filter: DanmakuRegexFilter) {
        danmakuRegexFilterConfigSettings.update(
            danmakuRegexFilterConfig.copy(
                danmakuRegexFilterList = danmakuRegexFilterConfig.danmakuRegexFilterList.toMutableList().apply {
                    add(filter)
                }
            )
        )
    }
    
    override fun editDanmakuRegexFilter(filter: DanmakuRegexFilter) {
        danmakuRegexFilterConfigSettings.update(
            danmakuRegexFilterConfig.copy(
                danmakuRegexFilterList = danmakuRegexFilterConfig.danmakuRegexFilterList.toMutableList().apply {
                    val index = indexOfFirst { it.instanceID == filter.instanceID }
                    if (index != -1) {
                        set(index, filter)
                    }
                }
            )
        )
    }
    
    override fun removeDanmakuRegexFilter(filter: DanmakuRegexFilter) {
        danmakuRegexFilterConfigSettings.update(
            danmakuRegexFilterConfig.copy(
                danmakuRegexFilterList = danmakuRegexFilterConfig.danmakuRegexFilterList.toMutableList().apply {
                    removeIf { it.instanceID == filter.instanceID }
                }
            )
        )
    }
    
    override fun switchDanmakuRegexFilterCompletely() {
        danmakuRegexFilterConfigSettings.update(
            danmakuRegexFilterConfig.copy(
                enabled = !danmakuRegexFilterConfig.enabled,
            ),
        )
    }

    // turn off a particular filter
    override fun switchDanmakuRegexFilter(filter: DanmakuRegexFilter) {
        danmakuRegexFilterConfigSettings.update(
            danmakuRegexFilterConfig.copy(
                danmakuRegexFilterList = danmakuRegexFilterConfig.danmakuRegexFilterList.toMutableList().apply {
                    val index = indexOfFirst { it.instanceID == filter.instanceID }
                    if (index != -1) {
                        set(index, filter.copy(isEnabled = !filter.isEnabled))
                    }
                }
            )
        )
    }
}

@Composable
fun EpisodeVideoSettings(
    vm: EpisodeVideoSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    return EpisodeVideoSettings(
        danmakuConfig = vm.danmakuConfig,
        danmakuRegexFilterConfig = vm.danmakuRegexFilterConfig,
        setDanmakuConfig = remember(vm) {
            vm::setDanmakuConfig
        },
        addDanmakuRegexFilter = remember(vm) {
            vm::addDanmakuRegexFilter
        },
        editDanmakuRegexFilter = remember(vm) {
            vm::editDanmakuRegexFilter
        },
        removeDanmakuRegexFilter = remember(vm) {
            vm::removeDanmakuRegexFilter
        },
        switchDanmakuRegexFilterCompletely = remember(vm) {
            vm::switchDanmakuRegexFilterCompletely
        },
        switchDanmakuRegexFilter = remember(vm) {
            vm::switchDanmakuRegexFilter
        },
        isLoading = remember(vm) {
            { vm.isLoading }
        },
        modifier = modifier,
    )
}

@Stable
private val LOADING_FALSE = { false }

@Composable
fun EpisodeVideoSettings(
    danmakuConfig: DanmakuConfig,
    danmakuRegexFilterConfig: DanmakuRegexFilterConfig,
    setDanmakuConfig: (config: DanmakuConfig) -> Unit,
    addDanmakuRegexFilter: (filter: DanmakuRegexFilter) -> Unit,
    editDanmakuRegexFilter: (filter: DanmakuRegexFilter) -> Unit,
    removeDanmakuRegexFilter: (filter: DanmakuRegexFilter) -> Unit,
    switchDanmakuRegexFilterCompletely: () -> Unit,
    switchDanmakuRegexFilter: (filter: DanmakuRegexFilter) -> Unit,
    isLoading: () -> Boolean = LOADING_FALSE,
    modifier: Modifier = Modifier,
) {
    val isLoadingState by remember(isLoading) {
        derivedStateOf(isLoading)
    }
    SettingsTab(modifier) {
        Column {
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ElevatedFilterChip(
                    selected = danmakuConfig.enableTop,
                    onClick = { setDanmakuConfig(danmakuConfig.copy(enableTop = !danmakuConfig.enableTop)) },
                    leadingIcon = {
                        if (danmakuConfig.enableTop) Icon(Icons.Rounded.Check, contentDescription = null)
                        else Icon(Icons.Rounded.Close, contentDescription = null)
                    },
                    label = { Text("顶部", maxLines = 1) },
                    modifier = Modifier.placeholder(isLoadingState),
                )
                ElevatedFilterChip(
                    selected = danmakuConfig.enableFloating,
                    onClick = { setDanmakuConfig(danmakuConfig.copy(enableFloating = !danmakuConfig.enableFloating)) },
                    label = { Text("滚动", maxLines = 1) },
                    leadingIcon = {
                        if (danmakuConfig.enableFloating) Icon(Icons.Rounded.Check, contentDescription = null)
                        else Icon(Icons.Rounded.Close, contentDescription = null)
                    },
                    modifier = Modifier.placeholder(isLoadingState),
                )
                ElevatedFilterChip(
                    selected = danmakuConfig.enableBottom,
                    onClick = { setDanmakuConfig(danmakuConfig.copy(enableBottom = !danmakuConfig.enableBottom)) },
                    label = { Text("底部", maxLines = 1) },
                    leadingIcon = {
                        if (danmakuConfig.enableBottom) Icon(Icons.Rounded.Check, contentDescription = null)
                        else Icon(Icons.Rounded.Close, contentDescription = null)
                    },
                    modifier = Modifier.placeholder(isLoadingState),
                )
                ElevatedFilterChip(
                    selected = danmakuConfig.enableColor,
                    onClick = {
                        setDanmakuConfig(
                            danmakuConfig.copy(enableColor = !danmakuConfig.enableColor),
                        )
                    },
                    leadingIcon = {
                        if (danmakuConfig.enableColor) Icon(Icons.Rounded.Check, contentDescription = null)
                        else Icon(Icons.Rounded.Close, contentDescription = null)
                    },
                    label = { Text("彩色", maxLines = 1) },
                    modifier = Modifier.placeholder(isLoadingState),
                )
            }

            val fontSize by remember(danmakuConfig) {
                mutableFloatStateOf(danmakuConfig.style.fontSize.value / DanmakuStyle.Default.fontSize.value)
            }
            SliderItem(
                value = fontSize,
                onValueChange = {
                    // 故意每次改都更新, 可以即时预览
                    setDanmakuConfig(
                        danmakuConfig.copy(style = danmakuConfig.style.copy(fontSize = DanmakuStyle.Default.fontSize * it)),
                    )
                },
                valueRange = 0.50f..3f,
                steps = ((3f - 0.50f) / 0.05f).toInt() - 1,
                title = { Text("弹幕字号") },
                valueLabel = { Text(remember(fontSize) { "${(fontSize * 100).roundToInt()}%" }) },
                modifier = Modifier.placeholder(isLoadingState),
            )

            val alpha by remember(danmakuConfig) {
                mutableFloatStateOf(danmakuConfig.style.alpha)
            }
            SliderItem(
                value = alpha,
                onValueChange = {
                    setDanmakuConfig(
                        danmakuConfig.copy(style = danmakuConfig.style.copy(alpha = it)),
                    )
                },
                valueRange = 0f..1f,
                steps = ((1f - 0f) / 0.05f).toInt() - 1,
                title = { Text("不透明度") },
                valueLabel = { Text(remember(alpha) { "${(alpha * 100).roundToInt()}%" }) },
                modifier = Modifier.placeholder(isLoadingState),
            )

            val strokeWidth by remember(danmakuConfig) {
                mutableFloatStateOf(danmakuConfig.style.strokeWidth / DanmakuStyle.Default.strokeWidth)
            }
            SliderItem(
                value = strokeWidth,
                onValueChange = {
                    setDanmakuConfig(
                        danmakuConfig.copy(style = danmakuConfig.style.copy(strokeWidth = it * DanmakuStyle.Default.strokeWidth)),
                    )
                },
                valueRange = 0f..2f,
                steps = ((2f - 0f) / 0.1f).toInt() - 1,
                title = { Text("描边宽度") },
                valueLabel = { Text(remember(strokeWidth) { "${(strokeWidth * 100).roundToInt()}%" }) },
                modifier = Modifier.placeholder(isLoadingState),
            )

            val fontWeight by remember(danmakuConfig) {
                derivedStateOf {
                    danmakuConfig.style.fontWeight.weight.toFloat()
                }
            }
            SliderItem(
                value = fontWeight,
                onValueChange = {
                    if (it != fontWeight) {
                        setDanmakuConfig(
                            danmakuConfig.copy(style = danmakuConfig.style.copy(fontWeight = FontWeight(it.toInt()))),
                        )
                    }
                },
                valueRange = 100f..900f,
                steps = ((900 - 100) / 100) - 1,
                title = { Text("弹幕字重") },
                valueLabel = { Text(remember(fontWeight) { "${fontWeight.toInt()}" }) },
                modifier = Modifier.placeholder(isLoadingState),
            )

            var speed by remember(danmakuConfig) {
                mutableFloatStateOf(
                    danmakuConfig.speed / DanmakuConfig.Default.speed,
                )
            }
            SliderItem(
                value = speed,
                onValueChange = { speed = it },
                valueRange = 0.2f..3f,
                steps = ((3f - 0.2f) / 0.1f).toInt() - 1,
                title = { Text("弹幕速度") },
                description = { Text("弹幕速度不会跟随视频倍速变化") },
                onValueChangeFinished = {
                    setDanmakuConfig(
                        danmakuConfig.copy(
                            speed = speed * DanmakuConfig.Default.speed,
                        ),
                    )
                },
                valueLabel = { Text(remember(speed) { "${(speed * 100).roundToInt()}%" }) },
                modifier = Modifier.placeholder(isLoadingState),
            )

            val displayDensityRange = remember {
                // 100% .. 0%
                36.dp..(if (currentPlatform.isDesktop()) 720.dp else 240.dp)
            }
            var displayDensity by remember(danmakuConfig) {
                mutableFloatStateOf(
                    1.minus(
                        (danmakuConfig.safeSeparation - displayDensityRange.start) /
                                (displayDensityRange.endInclusive - displayDensityRange.start + 1.dp),
                    ).div(0.1f).roundToInt().toFloat(),
                )
            }
            SliderItem(
                value = displayDensity,
                onValueChange = {
                    displayDensity = it
                },
                onValueChangeFinished = {
                    setDanmakuConfig(
                        danmakuConfig.copy(
                            safeSeparation = displayDensityRange.start +
                                    ((displayDensityRange.endInclusive - displayDensityRange.start + 1.dp)
                                        .times((1 - displayDensity * 0.1f))),
                        ),
                    )
                },
                valueRange = 0f..10f,
                steps = 9,
                title = { Text("同屏密度") },
                valueLabel = {
                    when (displayDensity.toInt()) {
                        in 7..10 -> Text("密集")
                        in 4..6 -> Text("适中")
                        in 0..3 -> Text("稀疏")
                    }
                },
                modifier = Modifier.placeholder(isLoadingState),
            )


            var displayArea by remember(danmakuConfig) {
                mutableFloatStateOf(
                    when (danmakuConfig.displayArea) {
                        0.125f -> 1f
                        0.25f -> 2f
                        0.50f -> 3f
                        0.75f -> 4f
                        1f -> 5f
                        else -> 2f
                    },
                )
            }
            SliderItem(
                value = displayArea,
                onValueChange = {
                    displayArea = it
                },
                onValueChangeFinished = {
                    setDanmakuConfig(
                        danmakuConfig.copy(
                            displayArea = when (displayArea) {
                                1f -> 0.125f
                                2f -> 0.25f
                                3f -> 0.50f
                                4f -> 0.75f
                                5f -> 1f
                                else -> 0.25f
                            },
                        ),
                    )
                },
                valueRange = 1f..5f,
                steps = 3,
                title = { Text("显示区域") },
                valueLabel = {
                    when (displayArea) {
                        1f -> Text("1/8 屏")
                        2f -> Text("1/4 屏")
                        3f -> Text("半屏")
                        4f -> Text("3/4 屏")
                        5f -> Text("全屏")
                    }
                },
                modifier = Modifier.placeholder(isLoadingState),
            )

            SwitchItem(
                danmakuRegexFilterConfig.enabled,
                onCheckedChange = {
                        switchDanmakuRegexFilterCompletely()
                },
                title = { Text("弹幕正则过滤") },
                modifier = Modifier.placeholder(isLoadingState),
            )
            
            DanmakuRegexFilterGroup(
                danmakuRegexFilterConfig,
                addDanmakuRegexFilter,
                editDanmakuRegexFilter,
                removeDanmakuRegexFilter,
                switchDanmakuRegexFilter,
                isLoadingState
            )

            if (currentAniBuildConfig.isDebug) {
                SwitchItem(
                    danmakuConfig.isDebug,
                    onCheckedChange = {
                        setDanmakuConfig(
                            danmakuConfig.copy(isDebug = it),
                        )
                    },
                    title = { Text("弹幕调试模式") },
                    modifier = Modifier.placeholder(isLoadingState),
                )
            }
        }
    }
}

