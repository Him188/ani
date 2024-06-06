package me.him188.ani.app.ui.settings.tabs.network

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import io.ktor.client.request.get
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.media.MediaSourceManager
import me.him188.ani.app.data.models.DanmakuSettings
import me.him188.ani.app.data.models.ProxySettings
import me.him188.ani.app.data.repositories.SettingsRepository
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.launchInMain
import me.him188.ani.app.ui.foundation.rememberViewModel
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.AbstractSettingsViewModel
import me.him188.ani.app.ui.settings.framework.ConnectionTestResult
import me.him188.ani.app.ui.settings.framework.ConnectionTester
import me.him188.ani.app.ui.settings.framework.MediaSourceTesterView
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.components.TextButtonItem
import me.him188.ani.app.ui.settings.framework.components.TextFieldItem
import me.him188.ani.danmaku.ani.client.AniBangumiSeverBaseUrls
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.subject.SubjectProvider
import me.him188.ani.datasources.bangumi.BangumiSubjectProvider
import me.him188.ani.utils.ktor.ClientProxyConfigValidator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * @see MediaSourceInfo
 */
@Stable
class MediaSourcePresentation(
    val id: String,
    val name: String,
    val description: String,
    val iconUrl: String?,
    val website: String?,
    val connectionTester: ConnectionTester,
)

@Stable
class NetworkSettingsViewModel : AbstractSettingsViewModel(), KoinComponent {
    private val settingsRepository: SettingsRepository by inject()
    private val mediaSourceManager: MediaSourceManager by inject()
    private val bangumiSubjectProvider by inject<SubjectProvider>()

    ///////////////////////////////////////////////////////////////////////////
    // Media Testing
    ///////////////////////////////////////////////////////////////////////////

    val mediaSources = mediaSourceManager.allIdsExceptLocal
        .map { id -> createMediaSourceTester(id) }
        .sortedBy { it.id.lowercase() }

    private fun createMediaSourceTester(id: String): ConnectionTester {
        val source = mediaSourceManager.enabledSources.map { sources ->
            sources.firstOrNull { it.mediaSourceId == id }
        }
        return ConnectionTester(
            id = id,
        ) {
            when (source.first()?.checkConnection()) {
                ConnectionStatus.SUCCESS -> ConnectionTestResult.SUCCESS
                ConnectionStatus.FAILED -> ConnectionTestResult.FAILED
                null -> ConnectionTestResult.NOT_ENABLED
            }
        }.apply {
            launchInMain {
                source.collect {
                    this@apply.reset()
                }
            }
        }
    }

    val nonConnectionTesters = listOf(
        ConnectionTester(
            id = BangumiSubjectProvider.ID, // Bangumi 顺便也测一下
        ) {
            if (bangumiSubjectProvider.testConnection() == ConnectionStatus.SUCCESS) {
                ConnectionTestResult.SUCCESS
            } else {
                ConnectionTestResult.FAILED
            }
        }
    )

    val allMediaTesters =
        Testers(mediaSources + nonConnectionTesters, backgroundScope)

    ///////////////////////////////////////////////////////////////////////////
    // Proxy
    ///////////////////////////////////////////////////////////////////////////

    val proxySettings by settings(
        settingsRepository.proxySettings,
        placeholder = ProxySettings(_placeHolder = -1)
    )

    ///////////////////////////////////////////////////////////////////////////
    // DanmakuSettings
    ///////////////////////////////////////////////////////////////////////////

    val danmakuSettings by settings(
        settingsRepository.danmakuSettings,
        placeholder = DanmakuSettings(_placeholder = -1)
    )

    val danmakuServerTesters = Testers(
        AniBangumiSeverBaseUrls.list.map {
            ConnectionTester(
                id = it,
            ) {
                httpClient.get("$it/status")
                ConnectionTestResult.SUCCESS
            }
        },
        backgroundScope
    )

//    private val placeholderDanmakuSettings = DanmakuSettings.Default.copy()
//    val danmakuSettings by preferencesRepository.danmakuSettings.flow
//        .produceState(placeholderDanmakuSettings)
//    val danmakuSettingsLoaded by derivedStateOf {
//        danmakuSettings != placeholderDanmakuSettings
//    }
//
//    private val danmakuSettingsUpdater = MonoTasker(backgroundScope)
//    fun updateDanmakuSettings(settings: DanmakuSettings) {
//        danmakuSettingsUpdater.launch {
//            logger.info { "Updating danmaku settings: $settings" }
//            preferencesRepository.danmakuSettings.set(settings)
//        }
//    }
}

@Composable
fun NetworkSettingsTab(
    vm: NetworkSettingsViewModel = rememberViewModel { NetworkSettingsViewModel() },
    modifier: Modifier = Modifier,
) {
    val proxySettings by vm.proxySettings

    SettingsTab(modifier) {
        GlobalProxyGroup(proxySettings, vm)
        MediaSourceGroup(vm)
        DanmakuGroup(vm)
    }
}

@Composable
private fun SettingsScope.MediaSourceGroup(vm: NetworkSettingsViewModel) {
    Group(
        title = { Text("数据源管理") },
    ) {
        for (tester in vm.mediaSources) {
            MediaSourceTesterView(tester, showTime = false)
        }

        HorizontalDividerItem()

        for (tester in vm.nonConnectionTesters) {
            MediaSourceTesterView(tester, showTime = false)
        }

        TextButtonItem(
            onClick = {
                vm.allMediaTesters.toggleTest()
            },
            title = {
                if (vm.allMediaTesters.anyTesting) {
                    Text("终止测试")
                } else {
                    Text("开始测试")
                }
            },
        )
    }
}

@Composable
private fun SettingsScope.GlobalProxyGroup(
    proxySettings: ProxySettings,
    vm: NetworkSettingsViewModel
) {
    Group(
        title = { Text("全局默认代理") },
        description = {
            Text("如果数据源没有单独配置代理，则使用此代理设置")
        }
    ) {
        SwitchItem(
            checked = proxySettings.default.enabled,
            onCheckedChange = {
                vm.proxySettings.update(proxySettings.copy(default = proxySettings.default.copy(enabled = it)))
            },
            title = { Text("启用代理") },
            Modifier.placeholder(vm.proxySettings.loading),
            description = { Text("启用后下面的配置才生效") },
        )

        HorizontalDividerItem()

        TextFieldItem(
            proxySettings.default.config.url,
            title = { Text("代理地址") },
            Modifier.placeholder(vm.proxySettings.loading),
            description = {
                Text(
                    "示例: http://127.0.0.1:7890 或 socks5://127.0.0.1:1080"
                )
            },
            onValueChangeCompleted = {
                vm.proxySettings.update(
                    proxySettings.copy(
                        default = proxySettings.default.copy(
                            config = proxySettings.default.config.copy(
                                url = it
                            )
                        )
                    )
                )
            },
            isErrorProvider = {
                !ClientProxyConfigValidator.isValidProxy(it)
            },
            sanitizeValue = { it.trim() }
        )

        HorizontalDividerItem()

        val username by remember {
            derivedStateOf {
                proxySettings.default.config.authorization?.username ?: ""
            }
        }

        val password by remember {
            derivedStateOf {
                proxySettings.default.config.authorization?.password ?: ""
            }
        }

        TextFieldItem(
            username,
            title = { Text("用户名") },
            Modifier.placeholder(vm.proxySettings.loading),
            description = { Text("可选") },
            placeholder = { Text("无") },
            onValueChangeCompleted = {
                vm.proxySettings.update(
                    proxySettings.copy(
                        default = proxySettings.default.copy(
                            config = proxySettings.default.config.copy(
                                authorization = proxySettings.default.config.authorization?.copy(
                                    username = it
                                )
                            )
                        )
                    )
                )
            },
            sanitizeValue = { it }
        )

        HorizontalDividerItem()

        TextFieldItem(
            password,
            title = { Text("密码") },
            Modifier.placeholder(vm.proxySettings.loading),
            description = { Text("可选") },
            placeholder = { Text("无") },
            onValueChangeCompleted = {
                vm.proxySettings.update(
                    proxySettings.copy(
                        default = proxySettings.default.copy(
                            config = proxySettings.default.config.copy(
                                authorization = proxySettings.default.config.authorization?.copy(
                                    password = password
                                )
                            )
                        )
                    )
                )
            },
            sanitizeValue = { it }
        )
    }
}

@Composable
private fun SettingsScope.DanmakuGroup(vm: NetworkSettingsViewModel) {
    Group(
        title = { Text("弹幕") },
    ) {
        val danmakuSettings by vm.danmakuSettings
        SwitchItem(
            checked = danmakuSettings.useGlobal,
            onCheckedChange = { vm.danmakuSettings.update(danmakuSettings.copy(useGlobal = it)) },
            title = { Text("全球加速") },
            Modifier.placeholder(vm.danmakuSettings.loading),
            description = { Text("提升在获取弹幕数据的速度\n在中国大陆内启用会减速") },
        )

        SubGroup {
            Group(
                title = { Text("连接速度测试") },
                useThinHeader = true
            ) {
                for (tester in vm.danmakuServerTesters.testers) {
                    val currentlySelected by derivedStateOf {
                        vm.danmakuSettings.value.useGlobal == (tester.id == AniBangumiSeverBaseUrls.GLOBAL)
                    }
                    MediaSourceTesterView(
                        tester,
                        icon = {
                            if (tester.id == AniBangumiSeverBaseUrls.GLOBAL)
                                Icon(
                                    Icons.Rounded.Public, null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            else Text("CN", fontFamily = FontFamily.Monospace)

                        },
                        title = {
                            val textColor =
                                if (currentlySelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Unspecified
                                }
                            if (tester.id == AniBangumiSeverBaseUrls.GLOBAL) {
                                Text("全球", color = textColor)
                            } else {
                                Text("中国大陆", color = textColor)
                            }
                        },
                        description = when {
                            currentlySelected -> {
                                { Text("当前使用") }
                            }

                            tester.id == AniBangumiSeverBaseUrls.GLOBAL -> {
                                { Text("建议在其他地区使用") }
                            }

                            else -> {
                                { Text("建议在中国大陆和香港使用") }
                            }
                        },
                        showTime = true,
                    )
                }

                TextButtonItem(
                    onClick = {
                        vm.danmakuServerTesters.toggleTest()
                    },
                    title = {
                        if (vm.danmakuServerTesters.anyTesting) {
                            Text("终止测试")
                        } else {
                            Text("开始测试")
                        }
                    },
                )
            }

        }
    }
}

