package com.mtd.data.network.interceptor

import com.mtd.domain.interfaceRepository.ITokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Bearer <jwt>` to relayer requests when a valid session exists.
 *
 * SECURITY: the token is **host-scoped** — it is only ever added to requests whose host equals
 * [relayerHost]. This prevents leaking the wallet's bearer JWT to third-party hosts that share the
 * OkHttpClient (CoinDesk, Wallex). No-ops when there is no valid token, since the relayer treats
 * quote/relay as `optionalAuth`.
 *
 * [clockEpochSec] is injectable for deterministic tests.
 */
class AuthInterceptor(
    private val tokenStore: ITokenStore,
    private val relayerHost: String,
    private val clockEpochSec: () -> Long = { System.currentTimeMillis() / 1000 }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Never send the JWT anywhere but the relayer.
        if (!request.url.host.equals(relayerHost, ignoreCase = true)) {
            return chain.proceed(request)
        }
        // Respect an explicitly-set header (don't clobber).
        if (request.header("Authorization") != null) {
            return chain.proceed(request)
        }

        val token = tokenStore.getTokenDevice()?.takeIf { tokenStore.isTokenValid(clockEpochSec()) }
            ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        )
    }
}
