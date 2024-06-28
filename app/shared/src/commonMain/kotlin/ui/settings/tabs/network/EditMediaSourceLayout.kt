package me.him188.ani.app.ui.settings.tabs.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.ktor.utils.io.core.Closeable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import me.him188.ani.app.ui.foundation.BackgroundScope
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.app.ui.icons.MediaSourceIcon
import me.him188.ani.datasources.api.source.BooleanParameter
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceParameter
import me.him188.ani.datasources.api.source.SimpleEnumParameter
import me.him188.ani.datasources.api.source.StringParameter
import moe.tlaster.precompose.flow.collectAsStateWithLifecycle
import kotlin.coroutines.CoroutineContext


sealed class EditType {
    data object Add : EditType()
    data class Edit(
        val instanceId: String,
    ) : EditType()
}

@Stable
class EditMediaSourceState(
    val info: MediaSourceInfo,
    persistedArguments: Flow<MediaSourceConfig>, // 加载会有延迟
    val editType: EditType,
    parentCoroutineContext: CoroutineContext,
) : HasBackgroundScope by BackgroundScope(parentCoroutineContext), Closeable {
    val arguments = info.parameters.list.map { param ->
        when (param) {
            is BooleanParameter -> BooleanArgumentState(param)
            is SimpleEnumParameter -> SimpleEnumArgumentState(param)
            is StringParameter -> StringArgumentState(param)
            else -> throw IllegalArgumentException("Unsupported parameter type: $param")
        }
    }

    private val persistLoader = persistedArguments.map { config ->
        for ((name, value) in config.arguments) {
            arguments.find { it.name == name }?.loadFromPersisted(value)
        }
    }.stateInBackground(null, started = SharingStarted.Eagerly)

    val isLoading = persistLoader.map { it != null }
    val hasError by derivedStateOf { arguments.any { it.isError } }

    override fun close() {
        backgroundScope.cancel()
    }
}

@Stable
sealed class ArgumentState(
    private val parameter: MediaSourceParameter<*>,
) {
    val name: String get() = parameter.name
    val description: String? get() = parameter.description

    abstract val isError: Boolean

    abstract fun loadFromPersisted(value: String?)

    abstract fun toPersisted(): String?
}

@Stable
class StringArgumentState(
    val parameter: StringParameter,
) : ArgumentState(parameter) {
    var value: String by mutableStateOf(parameter.default)
    override val isError: Boolean by derivedStateOf { !parameter.validate(value) }

    override fun loadFromPersisted(value: String?) {
        this.value = value ?: ""
    }

    override fun toPersisted(): String = value
}

@Stable
class BooleanArgumentState(
    private val origin: BooleanParameter,
) : ArgumentState(origin) {
    var value: Boolean by mutableStateOf(origin.default)
    override val isError: Boolean get() = false

    override fun loadFromPersisted(value: String?) {
        this.value = value?.toBooleanStrictOrNull() ?: origin.default
    }

    override fun toPersisted() = value.toString()
}

@Stable
class SimpleEnumArgumentState(
    private val origin: SimpleEnumParameter,
) : ArgumentState(origin) {
    val options get() = origin.oneOf
    var value: String by mutableStateOf(origin.default)
    override val isError: Boolean by derivedStateOf { value !in origin.oneOf }

    override fun loadFromPersisted(value: String?) {
        this.value = value.takeIf { it in origin.oneOf } ?: origin.default
    }

    override fun toPersisted() = value
}

@Composable
internal fun EditMediaSourceDialog(
    state: EditMediaSourceState,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading by state.isLoading.collectAsStateWithLifecycle(true)
    AlertDialog(
        onDismissRequest,
        title = {
            Text(state.info.name)
        },
        icon = {
            Box(Modifier.clip(MaterialTheme.shapes.extraSmall).size(24.dp)) {
                MediaSourceIcon(state.info.mediaSourceId, Modifier.size(24.dp), state.info.iconUrl)
            }
        },
        text = {
            if (state.arguments.isEmpty()) {
                Text("无配置项")
                return@AlertDialog
            }

            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (argument in state.arguments) {
                    when (argument) {
                        is BooleanArgumentState -> {
                            BooleanArgument(argument)
                        }

                        is SimpleEnumArgumentState -> {
                            SimpleEnumArgument(argument)
                        }

                        is StringArgumentState -> {
                            OutlinedTextField(
                                value = argument.value,
                                onValueChange = { argument.value = argument.parameter.sanitize(it) },
                                label = { Text(argument.name) },
                                supportingText = argument.description?.let { { Text(it) } },
                                isError = argument.isError,
                                shape = MaterialTheme.shapes.medium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canSave by remember(state) {
                derivedStateOf {
//                    !isLoading && todo 不知道为什么监听不到 isLoading, 但加载速度反正很快
                    !state.hasError
                }
            }
            when (state.editType) {
                EditType.Add -> Button(onConfirm, enabled = canSave) { Text("添加") }
                is EditType.Edit -> Button(onConfirm, enabled = canSave) { Text("保存") }
            }
        },
        dismissButton = {
            TextButton(onDismissRequest) {
                Text("取消")
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun SimpleEnumArgument(argument: SimpleEnumArgumentState, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier,
        content = {
            OutlinedTextField(
                // The `menuAnchor` modifier must be passed to the text field to handle
                // expanding/collapsing the menu on click. A read-only text field has
                // the anchor type `PrimaryNotEditable`.
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                value = argument.value,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(argument.name) },
                supportingText = argument.description?.let { { Text(it) } },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = MaterialTheme.shapes.medium,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                argument.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            argument.value = option
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        },
    )
}

@Composable
private fun BooleanArgument(argument: BooleanArgumentState, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 8.dp).padding(end = 16.dp)) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                Text(
                    argument.name,
                )
            }
            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                argument.description?.let { desc ->
                    Text(
                        desc,
                        Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        Switch(
            checked = argument.value,
            onCheckedChange = {
                argument.value = it
            },
        )
    }
}
