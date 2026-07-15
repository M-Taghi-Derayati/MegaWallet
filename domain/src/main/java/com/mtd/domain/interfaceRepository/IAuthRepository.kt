package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.AuthChallenge
import com.mtd.domain.model.AuthSession
import com.mtd.domain.model.ResultResponse

/**
 * Phase 3 — Web3 challenge/verify auth. Implementations persist the minted session into
 * [ITokenStore] on a successful verify, so the AuthInterceptor can attach the bearer JWT.
 */
interface IAuthRepository {

    /** Step 1 — request a nonce + message to sign for (address, chain). */
    suspend fun requestChallenge(address: String, chain: String): ResultResponse<AuthChallenge>

    /**
     * Step 2 — submit the signed challenge. On success the token + deviceId are persisted via
     * [ITokenStore] before the [AuthSession] is returned.
     */
    suspend fun verify(
        address: String,
        chain: String,
        signature: String,
        deviceId: String?
    ): ResultResponse<AuthSession>

    /**
     * Step 3 (optional) — slide the session before expiry using the currently-stored, still-valid
     * JWT. On success the refreshed token is re-persisted via [ITokenStore] (deviceId preserved).
     */
    suspend fun refresh(): ResultResponse<AuthSession>

    /**
     * Step 4 — end the session. Best-effort backend `/api/auth/logout` (failures are swallowed) is
     * always followed by a local [ITokenStore.clear]. Called on wallet delete / wallet switch.
     */
    suspend fun logout(): ResultResponse<Unit>
}
