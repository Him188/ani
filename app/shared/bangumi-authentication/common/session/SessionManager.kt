/*
 * Ani
 * Copyright (C) 2022-2024 Him188
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.him188.ani.app.session

import androidx.compose.runtime.Stable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.ProfileRepository
import me.him188.ani.app.data.TokenRepository
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.ui.foundation.BackgroundScope
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openapitools.client.infrastructure.ApiClient

/**
 * Bangumi 授权状态管理器
 */
interface SessionManager {
    /**
     * 当前有效的会话. 优先使用 [username] 或 [isSessionValid].
     */
    @Stable
    val session: SharedFlow<Session?>

    /**
     * 当前有效会话的用户名. 当不为 `null` 时保证可以使用该用户名执行 API 请求.
     *
     * 若用户登录过, 但会话已经过期, 无论是否正在刷新会话, 此值都会是 `null`, 直到会话刷新成功.
     * 若用户未登录, 此值一直为 `null`.
     *
     * @see isSessionValid
     */
    @Stable
    val username: StateFlow<String?>

    /**
     * 当前授权是否有效. `null` means not yet known, i.e. waiting for database query on start up.
     */
    @Stable
    val isSessionValid: StateFlow<Boolean?>

    /**
     * 当前正在进行中的授权请求.
     */
    @Stable
    val processingRequest: StateFlow<ExternalOAuthRequest?>

    /**
     * 请求为线上状态.
     *
     * 1. 若用户已经登录且会话有效 ([isSessionValid] 为 `true`), 则此函数会立即返回.
     * 2. 若用户登录过, 但是当前会话已经过期, 则此函数会尝试使用 refresh token 刷新会话.
     *    - 若刷新成功, 此函数会返回, 不会弹出任何 UI 提示.
     *    - 若刷新失败, 则进行下一步.
     * 3. 通过 [AniNavigator] 跳转到登录页面, 并等待用户的登录结果. 若用户取消登录, 此函数会抛出 [AuthorizationCancelledException].
     *
     * ## Cancellation Support
     *
     * 此函数支持 coroutine cancellation. 当 coroutine 被取消时, 此函数会中断授权请求并抛出 [CancellationException].
     *
     * @throws AuthorizationCancelledException if user cancels the authorization.
     * @throws AuthorizationFailedException if authorization failed, maybe due to network error.
     */
    @Throws(AuthorizationException::class)
    suspend fun requireOnline(navigator: AniNavigator)

    fun requireOnlineAsync(navigator: AniNavigator)

    suspend fun logout()
}

class AuthorizationCancelledException(
    override val cause: Throwable? = null
) : AuthorizationException()

class AuthorizationFailedException(
    override val cause: Throwable? = null
) : AuthorizationException()

sealed class AuthorizationException : Exception()


internal class SessionManagerImpl(
) : KoinComponent, SessionManager, HasBackgroundScope by BackgroundScope() {
    private val tokenRepository: TokenRepository by inject()
    private val profileRepository: ProfileRepository by inject()
    private val client: BangumiClient by inject()

    private val logger = logger(SessionManager::class)

    override val session: SharedFlow<Session?> =
        tokenRepository.session.distinctUntilChanged().onEach {
            ApiClient.accessToken = it?.accessToken
        }.shareInBackground(SharingStarted.Eagerly)

    override val username: StateFlow<String?> =
        session
            .map {
                if (it == null || it.expiresAt <= System.currentTimeMillis()) {
                    null
                } else
                    profileRepository.getSelfOrNull()?.username
            }
            .distinctUntilChanged()
            .stateInBackground(SharingStarted.Eagerly)

    override val isSessionValid: StateFlow<Boolean?> =
        username.map { it != null }
            .stateInBackground(SharingStarted.Eagerly)

    private val refreshTokenLoaded = CompletableDeferred<Boolean>()
    private val refreshToken = tokenRepository.refreshToken
        .transformLatest {
            emit(it)
            refreshTokenLoaded.complete(true)
        }
        .shareInBackground(SharingStarted.Eagerly)

    private val singleAuthLock = Mutex()
    override val processingRequest: MutableStateFlow<ExternalOAuthRequest?> = MutableStateFlow(null)

    private suspend fun tryRefreshSessionByRefreshToken(): Boolean {
        val session = session.first() ?: return false
        if (session.expiresAt > System.currentTimeMillis()) {
            // session is valid
            return true
        }

        // session is invalid, refresh it
        val refreshToken = refreshToken.first() ?: return false
        val newAccessToken = runCatching {
            withContext(Dispatchers.IO) {
                client.refreshAccessToken(
                    refreshToken,
                    BangumiAuthorizationConstants.CALLBACK_URL
                )
            }
        }.getOrNull()
        if (newAccessToken == null) {
            logger.info { "Bangumi session refresh failed, refreshToken=$refreshToken" }
            return false
        }
        // success
        setSession(
            session.userId,
            newAccessToken.accessToken,
            System.currentTimeMillis() + newAccessToken.expiresIn,
            newAccessToken.refreshToken
        )
        return true
    }

    override suspend fun requireOnline(navigator: AniNavigator) {
        // fast path, already online
        if (isSessionValid.value == true) return

        singleAuthLock.withLock {
            // not online, try to refresh
            refreshTokenLoaded.await()
            if (isSessionValid.value == true) return // check again because this might have changed
            if (refreshToken.first() != null) {
                if (tryRefreshSessionByRefreshToken()) {
                    return
                }
            }

            // failed to refresh, possibly refresh token is invalid

            // Launch external oauth (e.g. browser)
            val req = BangumiOAuthRequest(navigator) { session, refreshToken ->
                setSession(session.userId, session.accessToken, session.expiresAt, refreshToken)
            }
            processingRequest.value = req
            try {
                req.invoke()
            } finally {
                processingRequest.value = null
            }

            // Throw exceptions according to state
            val state = req.state.value
            check(state is ExternalOAuthRequest.State.Result)
            when (state) {
                is ExternalOAuthRequest.State.Cancelled -> {
                    throw AuthorizationCancelledException(state.cause)
                }

                is ExternalOAuthRequest.State.Failed -> {
                    throw AuthorizationFailedException(state.throwable)
                }

                ExternalOAuthRequest.State.Success -> {
                    // nop
                }
            }
        }
    }

    override fun requireOnlineAsync(navigator: AniNavigator) {
        launchInBackground {
            try {
                processingRequest.value?.cancel()
                requireOnline(navigator)
                logger.info { "requireOnline: success" }
            } catch (e: AuthorizationException) {
                logger.error(e) { "Authorization failed" }
            }
        }
    }

    override suspend fun logout() {
        tokenRepository.clear()
    }

    private suspend fun setSession(userId: Long, accessToken: String, expiresAt: Long, refreshToken: String) {
        logger.info { "Bangumi session refreshed, userId=${userId}, new expiresAt=$expiresAt" }

        tokenRepository.setRefreshToken(refreshToken)
        tokenRepository.setSession(Session(userId, accessToken, expiresAt))
        // session updates automatically
    }
}
