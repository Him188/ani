package me.him188.ani.app.ui.preference.framework

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import me.him188.ani.app.data.repositories.Preference
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.logging.info
import org.koin.core.component.KoinComponent
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

@Stable
abstract class AbstractSettingsViewModel : AbstractViewModel(), KoinComponent {
    protected val httpClient by lazy {
        createDefaultHttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 30_000
            }
        }.also {
            addCloseable(it)
        }
    }

    private inline fun <T> propertyDelegateProvider(
        crossinline createProperty: (property: KProperty<*>) -> T,
    ): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, T>> {
        return PropertyDelegateProvider { _, property ->
            val value = createProperty(property)
            ReadOnlyProperty { _, _ ->
                value
            }
        }
    }

    fun <T> settings(
        pref: Preference<T>,
        placeholder: T,
    ) =
        propertyDelegateProvider {
            Settings(it.name, pref, placeholder)
        }

    @Stable
    inner class Settings<T>(
        private val debugName: String,
        private val pref: Preference<T>,
        private val placeholder: T,
    ) : State<T> by pref.flow.produceState(placeholder) {
        val loading by derivedStateOf { value === placeholder }

        private val tasker = MonoTasker(backgroundScope)
        fun update(value: T) {
            tasker.launch {
                logger.info { "Updating $debugName: $value" }
                pref.set(value)
            }
        }
    }

    /**
     * 创建一个单个的测试器, 需要使用 `val tester by connectionTester {}`
     */
    fun connectionTester(
        testConnection: suspend () -> ConnectionTestResult,
    ) = propertyDelegateProvider {
        SingleTester(ConnectionTester(it.name, testConnection), backgroundScope)
    }

    @Stable
    class SingleTester(
        tester: ConnectionTester,
        backgroundScope: CoroutineScope,
    ) : Testers(listOf(tester), backgroundScope) {
        val tester get() = testers.single()
    }

    @Stable
    open class Testers(
        val testers: List<ConnectionTester>,
        backgroundScope: CoroutineScope,
    ) {
        private val testScope = MonoTasker(backgroundScope)
        fun testAll() {
            testScope.launch {
                supervisorScope {
                    testers.forEach {
                        this@launch.launch {
                            it.test()
                        }
                    }
                }
            }
        }

        fun cancel() {
            testScope.cancel()
        }

        fun toggleTest() {
            if (testers.any { it.isTesting }) {
                cancel()
            } else {
                testAll()
            }
        }

        val anyTesting by derivedStateOf {
            testers.any { it.isTesting }
        }
    }
}