package me.him188.ani.app.ui.profile

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.kamel.image.asyncPainterResource
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.AniKamelImage
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import org.openapitools.client.models.Avatar
import org.openapitools.client.models.User
import org.openapitools.client.models.UserGroup
import java.io.File


@Composable
fun UserInfoRow(
    self: User?,
    onClickEditNickname: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    Row(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(contentPadding)
            .height(IntrinsicSize.Min)
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AvatarImage(
                url = self?.avatar?.medium,
                Modifier
                    .placeholder(self == null)
                    .size(64.dp),
            )
        }

        Column(
            Modifier
                .padding(start = 16.dp)
                .fillMaxHeight()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = self?.nickname ?: "Loading...",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.placeholder(self?.nickname == null),
                )

//                Box(
//                    modifier = Modifier
//                        .padding(start = 12.dp)
//                        .size(20.dp)
//                        .clickable(onClick = onClickEditNickname)
//                ) {
//                    Icon(
//                        Icons.Default.Edit,
//                        contentDescription = "Edit",
//                        tint = MaterialTheme.colorScheme.primary,
//                    )
//                }
            }

            Row(
                Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var iconHeight by remember { mutableStateOf(24.dp) }
//                Icon(
//                    Icons.Default.SimCard, null,
//                    Modifier
//                        .padding(end = 4.dp)
//                        .size(iconHeight),
//                )
                val density = LocalDensity.current
                Text(
                    text = self?.username ?: "Loading...",
                    Modifier
                        .onPlaced {
                            iconHeight = density.run { it.size.height.toDp() }
                        }
                        .placeholder(self?.username == null),
                    style = MaterialTheme.typography.labelLarge,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}


// AvatarImage can be retrieved from a file path or a url
@Composable
fun AvatarImage(
    url: String?, modifier: Modifier = Modifier,
    filePath: String? = null,
    colorFilter: ColorFilter? = null,
) {
    AniKamelImage(
        asyncPainterResource(if (filePath != null) File(filePath) else url ?: ""),
        contentDescription = "Avatar",
        modifier = modifier,
        contentScale = ContentScale.Crop,
        colorFilter = colorFilter
    )
}


private val sampleUser = User(
    username = "username",
    avatar = Avatar(
        "https://example.com/avatar.jpg",
        "https://example.com/avatar.jpg",
        "https://example.com/avatar.jpg"
    ),
    id = 1,
    nickname = "Nickname",
    sign = "Sign ".repeat(3),
    userGroup = UserGroup.User,
)

@Preview
@Composable
private fun PreviewUserInfoRow() {
    ProvideCompositionLocalsForPreview {
        UserInfoRow(
            self = sampleUser,
            onClickEditNickname = {}
        )
    }
}