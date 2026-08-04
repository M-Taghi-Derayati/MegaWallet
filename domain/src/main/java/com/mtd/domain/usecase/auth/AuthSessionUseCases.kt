package com.mtd.domain.usecase.auth

import com.mtd.domain.interfaceRepository.IAuthMessageSigner
import com.mtd.domain.interfaceRepository.IAuthRepository
import com.mtd.domain.interfaceRepository.IRealtimeConnectionGateway
import com.mtd.domain.interfaceRepository.ITokenStore
import com.mtd.domain.model.AuthSession
import com.mtd.domain.model.ResultResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

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
@Singleton
class EnsureAuthenticatedUseCase(
    private val tokenStore: ITokenStore,
    private val signInWithWallet: SignInWithWalletUseCase,
    private val realtimeGateway: IRealtimeConnectionGateway,
    private val clockEpochSec: () -> Long
) {
    /**
     * Single-flight. Sign-in is challenge → sign → verify, and the backend keeps **one** live challenge
     * per address: a second concurrent flow requests a fresh nonce, which invalidates the first. Both
     * verifies then fail — `Signature does not match address` for the one holding the stale nonce, then
     * `Challenge not found or expired` — and the app ends up with no session at all.
     *
     * Callers are legitimately plural (the session coordinator at startup, the 401 authenticator, the
     * pre-flight gate before proxy reads), so the exclusion has to live here rather than at each call
     * site. [Singleton] is load-bearing: an unscoped use case would hand every injection point its own
     * lock and change nothing.
     */
    private val signInMutex = Mutex()

    @Inject
    constructor(
        tokenStore: ITokenStore,
        signInWithWallet: SignInWithWalletUseCase,
        realtimeGateway: IRealtimeConnectionGateway
    ) : this(tokenStore, signInWithWallet, realtimeGateway, { System.currentTimeMillis() / 1000 })

    suspend operator fun invoke(forceFresh: Boolean = false): ResultResponse<AuthSession?> {
        // Fast path — a live token needs no coordination, so callers never queue behind an unrelated
        // sign-in just to be told they already had one.
        if (!forceFresh && tokenStore.isTokenValid(clockEpochSec())) {
            realtimeGateway.connect()
            return ResultResponse.Success(null)
        }

        return signInMutex.withLock {
            // `clear` belongs inside the lock too, or a wallet switch can wipe a token another flow
            // just minted.
            if (forceFresh) tokenStore.clear()

            // Re-check under the lock: whoever held it may have minted exactly what we came for, and
            // signing in again would invalidate their nonce — the very collision this guards.
            if (!forceFresh && tokenStore.isTokenValid(clockEpochSec())) {
                realtimeGateway.connect()
                return@withLock ResultResponse.Success(null)
            }

            when (val r = signInWithWallet()) {
                is ResultResponse.Success -> {
                    realtimeGateway.connect()
                    ResultResponse.Success(r.data)
                }
                is ResultResponse.Error -> r
            }
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
