package com.mtd.domain.usecase.auth

import com.mtd.domain.interfaceRepository.IAuthMessageSigner
import com.mtd.domain.interfaceRepository.IAuthRepository
import com.mtd.domain.interfaceRepository.IRealtimeConnectionGateway
import com.mtd.domain.interfaceRepository.ITokenStore
import com.mtd.domain.model.AuthSession
import com.mtd.domain.model.ResultResponse
import javax.inject.Inject

/**
 * Phase 2 — Web3 baseline sign-in: `challenge → personal_sign → verify → persist JWT`.
 *
 * "Baseline" means the device is NOT cryptographically attested (no HMAC device-challenge), so the
 * minted session has `deviceVerified = false`. That is sufficient for `proxy:read`/`proxy:write`
 * (balances, history, gasless WALLET/SPONSOR, WebSocket) but NOT for device-bound gas credit /
 * Mystery Box — see `docs/BACKEND_GAP_DEVICE_KEY_PROVISIONING.md`.
 *
 * No throwing across layers: every failure is folded into [ResultResponse.Error].
 */
class SignInWithWalletUseCase @Inject constructor(
    private val authRepository: IAuthRepository,
    private val authMessageSigner: IAuthMessageSigner
) {
    suspend operator fun invoke(): ResultResponse<AuthSession> {
        val account = authMessageSigner.activeEvmAccount()
            ?: return ResultResponse.Error(IllegalStateException("No unlocked EVM account to sign in with"))

        val challenge = when (val r = authRepository.requestChallenge(account.address, account.chain)) {
            is ResultResponse.Success -> r.data
            is ResultResponse.Error -> return r
        }

        val signature = authMessageSigner.signEvmMessage(challenge.message)
            ?: return ResultResponse.Error(IllegalStateException("Failed to sign the auth challenge"))

        // deviceId = null → the repository resolves it via the resilient provider (baseline, no attestation).
        return authRepository.verify(
            address = account.address,
            chain = account.chain,
            signature = signature,
            deviceId = null
        )
    }
}

/**
 * Phase 2 — guarantees a live session before authed work, and (re)connects the realtime gateway.
 *
 * - [forceFresh] = true (wallet switch): drop any existing session and sign in from scratch.
 * - token still valid: reuse it.
 * - otherwise: sign in fresh (an expired token cannot be `refresh`ed — the backend requires a valid one).
 *
 * On success the [IRealtimeConnectionGateway] is connected (idempotent). Returns the minted session,
 * or `null` data when an already-valid token was reused (no new session object available).
 */
class EnsureAuthenticatedUseCase(
    private val tokenStore: ITokenStore,
    private val signInWithWallet: SignInWithWalletUseCase,
    private val realtimeGateway: IRealtimeConnectionGateway,
    private val clockEpochSec: () -> Long
) {
    @Inject
    constructor(
        tokenStore: ITokenStore,
        signInWithWallet: SignInWithWalletUseCase,
        realtimeGateway: IRealtimeConnectionGateway
    ) : this(tokenStore, signInWithWallet, realtimeGateway, { System.currentTimeMillis() / 1000 })

    suspend operator fun invoke(forceFresh: Boolean = false): ResultResponse<AuthSession?> {
        if (forceFresh) tokenStore.clear()

        if (!forceFresh && tokenStore.isTokenValid(clockEpochSec())) {
            realtimeGateway.connect()
            return ResultResponse.Success(null)
        }

        return when (val r = signInWithWallet()) {
            is ResultResponse.Success -> {
                realtimeGateway.connect()
                ResultResponse.Success(r.data)
            }
            is ResultResponse.Error -> r
        }
    }
}

/** Phase 2 — proactive session slide before expiry (wraps [IAuthRepository.refresh]). */
class RefreshSessionUseCase @Inject constructor(
    private val authRepository: IAuthRepository
) {
    suspend operator fun invoke(): ResultResponse<AuthSession> = authRepository.refresh()
}

/** Phase 2 — end the session and tear down realtime (wallet delete / switch). */
class SignOutUseCase @Inject constructor(
    private val authRepository: IAuthRepository,
    private val realtimeGateway: IRealtimeConnectionGateway
) {
    suspend operator fun invoke(): ResultResponse<Unit> {
        realtimeGateway.disconnect()
        return authRepository.logout()
    }
}
