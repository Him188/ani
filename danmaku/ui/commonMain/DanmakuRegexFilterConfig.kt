package me.him188.ani.danmaku.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.him188.ani.danmaku.api.Danmaku
import java.util.UUID

/**4
 * Defines which [Danmaku] can be displayed.
 */
@Immutable
@Serializable
data class DanmakuRegexFilterConfig (

    /**
     * Defines wheather Danmaku filter is on.
     */
    
    val enabled: Boolean = true,

    /**
     * Defines which [Danmaku] can be displayed according to a list of regular expression.
     */
    
    val danmakuRegexFilterList: List<DanmakuRegexFilter> = mutableListOf(),
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,


    ) {
    companion object {
        @Stable
        val Default = DanmakuRegexFilterConfig()
    }
}

@Serializable
data class DanmakuRegexFilter (
    val instanceID: String,
    val name: String = "",
    val re: String = "",
    val isEnabled: Boolean = true
) {
    companion object {
        @Stable
        val Default = DanmakuRegexFilter(UUID.randomUUID().toString())
    }
}

