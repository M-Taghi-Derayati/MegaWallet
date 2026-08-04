package com.mtd.data.network.interceptor

import com.mtd.domain.interfaceRepository.ITokenStore
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.usecase.auth.EnsureAuthenticatedUseCase
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber

/**
 * Mints a session on a `401` from the relayer and replays the request once.
 *
 * [AuthInterceptor] deliberately sends a request bare when no valid token exists, because the relayer
 * treats quote/relay as optional-auth. The **mobile-proxy reads are not** optional-auth: at cold start
 * a balance refresh can outrun the coordinator's asynchronous mint, so those calls came back
 * `401 {"error":"Missing bearer token"}` and the screen simply had no data until the user refreshed by
 * hand. Reacting to the 401 covers that race *and* mid-session expiry with one mechanism, and needs no
 * readiness signal threaded through the data layer.
 *
 * Runs on OkHttp's dispatcher thread, so blocking here is expected. The nested sign-in call shares this
 * client; that is safe at OkHttp's default concurrency but is the reason this must never fire for the
 * auth endpoints themselves.
 */
class RelayerTokenAuthenticator(
    private val ensureAuthenticated: Lazy<EnsureAuthenticatedUseCase>,
    private val tokenStore: ITokenStore,
    private val relayerHost: String
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // SECURITY: only ever mint for — and attach a token to — the relayer.
        if (!response.request.url.host.equals(relayerHost, ignoreCase = true)) return null

        // Signing in must not be able to trigger signing in.
        if (isAuthEndpoint(response.request)) return null

        // OkHttp re-invokes this while it keeps getting 401. One attempt per request, or a rejected
        // credential becomes an infinite challenge loop.
        if (response.priorResponse != null) return null

        val session = runBlocking {
            // Every network's balance call 401s at once, so this runs N times in parallel. It is safe
            // only because EnsureAuthenticatedUseCase is single-flight: without that lock, N concurrent
            // sign-ins each request a fresh challenge and invalidate each other's nonce, and none of
            // them completes. forceFresh = false so the losers of the race reuse the winner's token.
            runCatching { ensureAuthenticated.get().invoke(forceFresh = false) }.getOrNull()
        }
        if (session !is ResultResponse.Success) {
            Timber.w("Relayer 401 and no session could be minted; not replaying")
            return null
        }

        val token = tokenStore.getTokenDevice() ?: return null
        // Never replay with the credential that was just rejected.
        if (response.request.header("Authorization") == "Bearer $token") return null

        Timber.i("Relayer 401 → session minted, replaying %s", response.request.url.encodedPath)
        return response.request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }

    private fun isAuthEndpoint(request: Request): Boolean =
        request.url.encodedPath.contains("/auth/", ignoreCase = true)
}
