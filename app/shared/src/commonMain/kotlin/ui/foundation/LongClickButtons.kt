@file:Suppress("ERROR_SUPPRESSION")

package me.him188.ani.app.ui.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor

/**
 * <a href="https://m3.material.io/components/buttons/overview" class="external" target="_blank">Material Design filled tonal button</a>.
 *
 * Buttons help people initiate actions, from sending an email, to sharing a document, to liking a
 * post.
 *
 * ![Filled tonal button image](https://developer.android.com/images/reference/androidx/compose/material3/filled-tonal-button.png)
 *
 * Filled tonal buttons are medium-emphasis buttons that is an alternative middle ground between
 * default [Button]s (filled) and [OutlinedButton]s. They can be used in contexts where
 * lower-priority button requires slightly more emphasis than an outline would give, such as "Next"
 * in an onboarding flow. Tonal buttons use the secondary color mapping.
 *
 * @sample androidx.compose.material3.samples.FilledTonalButtonSample
 *
 * Choose the best button for an action based on the amount of emphasis it needs. The more important
 * an action is, the higher emphasis its button should be.
 *
 * - See [Button] for a high-emphasis button without a shadow, also known as a filled button.
 * - See [ElevatedButton] for a [FilledTonalCombinedClickButton] with a shadow.
 * - See [OutlinedButton] for a medium-emphasis button with a border.
 * - See [TextButton] for a low-emphasis button with no border.
 *
 * The default text style for internal [Text] components will be set to [Typography.labelLarge].
 *
 * @param onClick called when this button is clicked
 * @param modifier the [Modifier] to be applied to this button
 * @param enabled controls the enabled state of this button. When `false`, this component will not
 * respond to user input, and it will appear visually disabled and disabled to accessibility
 * services.
 * @param shape defines the shape of this button's container, border (when [border] is not null),
 * and shadow (when using [elevation])
 * @param colors [ButtonColors] that will be used to resolve the colors for this button in different
 * states. See [ButtonDefaults.filledTonalButtonColors].
 * @param elevation [ButtonElevation] used to resolve the elevation for this button in different
 * states. This controls the size of the shadow below the button. Additionally, when the container
 * color is [ColorScheme.surface], this controls the amount of primary color applied as an overlay.
 * @param border the border to draw around the container of this button
 * @param contentPadding the spacing values to apply internally between the container and the
 * content
 * @param interactionSource the [MutableInteractionSource] representing the stream of [Interaction]s
 * for this button. You can create and pass in your own `remember`ed instance to observe
 * [Interaction]s and customize the appearance / behavior of this button in different states.
 */
@Composable
fun FilledTonalCombinedClickButton(
    onClick: () -> Unit,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = {},
    onLongClickLabel: String? = null,
    onDoubleClick: (() -> Unit)? = null,
    indication: Indication = LocalIndication.current,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) =
    CombinedClickButton(
        onClick = onClick,
        onClickLabel = onClickLabel,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
        onDoubleClick = onDoubleClick,
        indication = indication,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )


/**
 * <a href="https://m3.material.io/components/buttons/overview" class="external" target="_blank">Material Design button</a>.
 *
 * Buttons help people initiate actions, from sending an email, to sharing a document, to liking a
 * post.
 *
 * ![Filled button image](https://developer.android.com/images/reference/androidx/compose/material3/filled-button.png)
 *
 * Filled buttons are high-emphasis buttons. Filled buttons have the most visual impact after the
 * [FloatingActionButton], and should be used for important, final actions that complete a flow,
 * like "Save", "Join now", or "Confirm".
 *
 * @sample androidx.compose.material3.samples.ButtonSample
 * @sample androidx.compose.material3.samples.ButtonWithIconSample
 *
 * Choose the best button for an action based on the amount of emphasis it needs. The more important
 * an action is, the higher emphasis its button should be.
 *
 * - See [OutlinedButton] for a medium-emphasis button with a border.
 * - See [ElevatedButton] for an [OutlinedButton] with a shadow.
 * - See [TextButton] for a low-emphasis button with no border.
 * - See [FilledTonalCombinedClickButton] for a middle ground between [OutlinedButton] and [Button].
 *
 * The default text style for internal [Text] components will be set to [Typography.labelLarge].
 *
 * @param onClick called when this button is clicked
 * @param modifier the [Modifier] to be applied to this button
 * @param enabled controls the enabled state of this button. When `false`, this component will not
 * respond to user input, and it will appear visually disabled and disabled to accessibility
 * services.
 * @param shape defines the shape of this button's container, border (when [border] is not null),
 * and shadow (when using [elevation])
 * @param colors [ButtonColors] that will be used to resolve the colors for this button in different
 * states. See [ButtonDefaults.buttonColors].
 * @param elevation [ButtonElevation] used to resolve the elevation for this button in different
 * states. This controls the size of the shadow below the button. Additionally, when the container
 * color is [ColorScheme.surface], this controls the amount of primary color applied as an overlay.
 * See [ButtonElevation.shadowElevation] and [ButtonElevation.tonalElevation].
 * @param border the border to draw around the container of this button
 * @param contentPadding the spacing values to apply internally between the container and the
 * content
 * @param interactionSource the [MutableInteractionSource] representing the stream of [Interaction]s
 * for this button. You can create and pass in your own `remember`ed instance to observe
 * [Interaction]s and customize the appearance / behavior of this button in different states.
 */
@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@Composable
fun CombinedClickButton(
    onClick: () -> Unit,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = {},
    onLongClickLabel: String? = null,
    onDoubleClick: (() -> Unit)? = null,
    indication: Indication = LocalIndication.current,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val containerColor = colors.containerColor(enabled)
    val contentColor = colors.contentColor(enabled)
    val shadowElevation = elevation?.shadowElevation(enabled, interactionSource)?.value ?: 0.dp
    Surface(
        modifier = modifier.semantics { role = Role.Button }.combinedClickable(
            interactionSource = interactionSource,
            indication = indication,
            role = Role.Button,
            enabled = enabled,
            onClick = onClick,
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick,
        ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = shadowElevation,
        border = border,
    ) {
        ProvideTextStyleContentColor(
            MaterialTheme.typography.labelLarge,
            contentColor,
        ) {
            Row(
                Modifier.defaultMinSize(
                    minWidth = ButtonDefaults.MinWidth,
                    minHeight = ButtonDefaults.MinHeight,
                )
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}
