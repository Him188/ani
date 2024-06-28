package me.him188.ani.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.ThemeKind
import me.him188.ani.app.data.repositories.SettingsRepository
import me.him188.ani.app.i18n.LocalI18n
import me.him188.ani.app.i18n.loadResourceBundle
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.Platform
import me.him188.ani.app.platform.isAndroid
import me.him188.ani.app.platform.isSystemInFullscreen
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.LocalImageLoader
import me.him188.ani.app.ui.foundation.getDefaultImageLoader
import me.him188.ani.app.ui.foundation.rememberViewModel
import me.him188.ani.app.ui.foundation.theme.AppTheme
import me.him188.ani.app.ui.foundation.theme.aniColorScheme
import moe.tlaster.precompose.PreComposeApp
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class AniAppViewModel : AbstractViewModel(), KoinComponent {
    private val settings: SettingsRepository by inject()
    val themeKind: ThemeKind? by settings.uiSettings.flow.map { it.theme.kind }.produceState(null)
}

@Composable
fun AniApp(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val currentBundle = remember(Locale.current.language) { loadResourceBundle(context) }
    val coilContext = LocalPlatformContext.current
    val imageLoader by remember {
        derivedStateOf {
            getDefaultImageLoader(coilContext)
        }
    }

    PreComposeApp {
        CompositionLocalProvider(
            LocalI18n provides currentBundle,
            LocalImageLoader provides imageLoader,
            LocalTimeFormatter provides remember { TimeFormatter() },
        ) {
            val focusManager by rememberUpdatedState(LocalFocusManager.current)
            val keyboard by rememberUpdatedState(LocalSoftwareKeyboardController.current)

            val viewModel = rememberViewModel { AniAppViewModel() }

            val isDark = if (Platform.currentPlatform.isAndroid()) { // 安卓需要考虑系统 UI 如状态栏的颜色, 比较麻烦
                isSystemInDarkTheme()
            } else {
                // desktop
                if (isSystemInFullscreen()) true
                else when (viewModel.themeKind) {
                    null -> return@CompositionLocalProvider
                    ThemeKind.AUTO -> isSystemInDarkTheme()
                    ThemeKind.LIGHT -> false
                    ThemeKind.DARK -> true
                }
            }
            MaterialTheme(aniColorScheme(isDark)) {
                Box(
                    modifier = modifier
                        .background(AppTheme.colorScheme.background)
                        .focusable(false)
                        .clickable(
                            remember { MutableInteractionSource() },
                            null,
                        ) {
                            keyboard?.hide()
                            focusManager.clearFocus()
                        },
                ) {
                    Column {
                        content()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CommonAppScaffold(
    snackbarHost: @Composable () -> Unit = {},
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    clearFocus: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val currentClearFocus by rememberUpdatedState(newValue = clearFocus)

    val keyboard by rememberUpdatedState(newValue = LocalSoftwareKeyboardController.current)
    val focus = remember { FocusRequester() }
    Scaffold(
        Modifier
            .focusRequester(focus)
            .focusProperties { canFocus = false }
            .clickable(remember { MutableInteractionSource() }, null) {
                keyboard?.hide()
                focus.freeFocus()
                currentClearFocus?.invoke()
            },
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
    ) {
        content()
    }
}

@Composable
fun RowScope.TabNavigationItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.weight(1f)) {
        Box(
            modifier = modifier
                .align(Alignment.Center)
                .minimumInteractiveComponentSize()
                .fillMaxSize()
                .toggleable(
                    value = checked,
                    onValueChange = onCheckedChange,
                    enabled = true,
                    role = Role.Checkbox,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val contentColor = if (checked) {
                MaterialTheme.colorScheme.primary
            } else {
                LocalContentColor.current
            }
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(24.dp)) {
                        icon()
                    }
                    ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                        title()
                    }
                }
            }
        }

//        IconToggleButton(
//            colors = colors,
//            checked = checked,
//            onCheckedChange = onCheckedChange,
//            modifier = modifier.size(48.dp),
//        ) {
//            Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                icon()
//                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
//                    title()
//                }
//            }
//        }
    }
}
