package me.him188.ani.app.ui.settings.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.him188.ani.app.data.media.MediaAutoCacheService
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.session.SessionManager
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.rememberViewModel
import me.him188.ani.app.ui.profile.AniHelpSection
import me.him188.ani.app.ui.profile.DebugInfo
import me.him188.ani.app.ui.settings.SettingsTab
import moe.tlaster.precompose.flow.collectAsStateWithLifecycle
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext

class AboutTabViewModel : AbstractViewModel(), KoinComponent {
    val browserNavigator: BrowserNavigator by inject()
    private val sessionManager: SessionManager by inject()


    val debugInfo = debugInfoFlow().shareInBackground(started = SharingStarted.Eagerly)

    private fun debugInfoFlow() = combine(
        sessionManager.session,
        sessionManager.processingRequest.flatMapLatest { it?.state ?: flowOf(null) },
        sessionManager.isSessionValid,
    ) { session, processingRequest, isSessionValid ->
        DebugInfo(
            properties = buildMap {
                val buildConfig = currentAniBuildConfig
                put("isDebug", buildConfig.isDebug.toString())
                if (buildConfig.isDebug) {
                    put("accessToken", session?.accessToken)
                }
                put("processingRequest.state", processingRequest.toString())
                put("sessionManager.isSessionValid", isSessionValid.toString())
            },
        )
    }
}

@Stable
private const val GITHUB_REPO = "https://github.com/him188/ani"

@Stable
private const val BANGUMI = "https://bangumi.tv"

@Stable
private const val DANDANPLAY = "https://www.dandanplay.com/"

@Stable
private const val DMHY = "https://dmhy.b168.net/"

@Stable
private const val ACG_RIP = "https://acg.rip/"

@Stable
private const val MIKAN = "https://mikanime.tv/"

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun AboutTab(
    vm: AboutTabViewModel = rememberViewModel { AboutTabViewModel() },
    modifier: Modifier = Modifier,
    onTriggerDebugMode: () -> Unit = { },
) {
    val context by rememberUpdatedState(LocalContext.current)

    SettingsTab(modifier) {
        Group(
            title = { Text("关于 Ani") },
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                val style by rememberUpdatedState(
                    MaterialTheme.typography.bodyMedium.toSpanStyle()
                        .copy(color = MaterialTheme.colorScheme.onSurface),
                )
                val primaryColor by rememberUpdatedState(MaterialTheme.colorScheme.primary)
                val text by remember {
                    derivedStateOf {
                        buildAnnotatedString {
                            pushStyle(style)
                            append("Ani 完全免费无广告且开源, 源代码可在 ")
                            pushLink(
                                LinkAnnotation.Url(
                                    GITHUB_REPO,
                                    TextLinkStyles(
                                        SpanStyle(
                                            color = primaryColor,
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                    ),
                                ) {
                                    vm.browserNavigator.openBrowser(context, GITHUB_REPO)
                                },
                            )
                            append("GitHub")
                            pop()
                            append(" 找到")
                            pop()
                        }

                    }
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Group(title = { Text("感谢你的支持") }) {
                AniHelpSection(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
        }

        Group(
            title = { Text("鸣谢") },
        ) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ani 使用了许多爱好者用爱发电维护的免费服务.", style = MaterialTheme.typography.bodyMedium)

                Text("特别感谢:", style = MaterialTheme.typography.bodyMedium)

                val listStyle = MaterialTheme.typography.bodyMedium.copy(MaterialTheme.colorScheme.primary)
                Text(
                    buildAnnotatedString {
                        pushLink(
                            LinkAnnotation.Url(GITHUB_REPO, TextLinkStyles()) {
                                vm.browserNavigator.openBrowser(context, GITHUB_REPO)
                            },
                        )
                        append("· Bangumi 番组计划")
                    },
                    style = listStyle,
                )

                Text(
                    buildAnnotatedString {
                        pushLink(
                            LinkAnnotation.Url(MIKAN, TextLinkStyles()) {
                                vm.browserNavigator.openBrowser(context, MIKAN)
                            },
                        )
                        append("· Mikan 蜜柑计划")
                    },
                    style = listStyle,
                )

                Text(
                    buildAnnotatedString {
                        pushLink(
                            LinkAnnotation.Url(DMHY, TextLinkStyles()) {
                                vm.browserNavigator.openBrowser(context, DMHY)
                            },
                        )
                        append("· 動漫花園資源網")
                    },
                    style = listStyle,
                )

                Text(
                    buildAnnotatedString {
                        pushLink(
                            LinkAnnotation.Url(ACG_RIP, TextLinkStyles()) {
                                vm.browserNavigator.openBrowser(context, ACG_RIP)
                            },
                        )
                        append("· acg.rip")
                    },
                    style = listStyle,
                )

                Text(
                    buildAnnotatedString {
                        pushLink(
                            LinkAnnotation.Url(DANDANPLAY, TextLinkStyles()) {
                                vm.browserNavigator.openBrowser(context, DANDANPLAY)
                            },
                        )
                        append("· 弹弹play")
                    },
                    style = listStyle,
                )
            }
        }

        Group(
            title = {
                Text(
                    text = "调试信息",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTriggerDebugMode,
                    ),
                )
            },
            description = { Text("在反馈问题时附上日志可能有用") },
        ) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (currentAniBuildConfig.isDebug) {
                    val debugInfo by vm.debugInfo.collectAsStateWithLifecycle(null)
                    val clipboard = LocalClipboardManager.current
                    for ((name, value) in debugInfo?.properties.orEmpty()) {
                        Text(
                            "$name: $value",
                            Modifier.fillMaxWidth().clickable {
                                value?.let { clipboard.setText(AnnotatedString(it)) }
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    FilledTonalButton(
                        {
                            GlobalScope.launch {
                                GlobalContext.get().get<MediaAutoCacheService>().checkCache()
                            }
                        },
                    ) {
                        Text("执行自动缓存")
                    }
                }

                FilledTonalButton(
                    {
                        GlobalScope.launch {
                            GlobalContext.get().get<SessionManager>().logout()
                        }
                    },
                ) {
                    Text("退出登录")
                }

                PlatformDebugInfoItems()
            }
        }
    }
}


@Composable
internal expect fun ColumnScope.PlatformDebugInfoItems()
