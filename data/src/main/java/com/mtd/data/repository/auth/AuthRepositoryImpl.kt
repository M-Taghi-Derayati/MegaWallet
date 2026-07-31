package com.mtd.data.repository.auth

import com.mtd.core.crypto.HmacUtils
import com.mtd.data.BuildConfig
import com.mtd.data.dto.AuthChallengeRequestDto
import com.mtd.data.dto.AuthVerifyRequestDto
import com.mtd.data.dto.DeviceChallengeRequest
import com.mtd.data.service.AuthApiService
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.interfaceRepository.IAuthRepository
import com.mtd.domain.interfaceRepository.IDeviceIdProvider
import com.mtd.domain.interfaceRepository.ITokenStore
import com.mtd.domain.model.AuthChallenge
import com.mtd.domain.model.AuthSession
import com.mtd.domain.model.DeviceAttestation
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 — drives `/api/auth/challenge` → `/api/auth/verify` and persists the minted JWT into the
 * AndroidKeystore-backed [ITokenStore] (so [com.mtd.data.network.interceptor.AuthInterceptor] can
 * attach it). Failures are mapped to the Phase 1 [ApiException] taxonomy, never thrown across layers.
 *
 * [clockEpochSec] is injectable for deterministic tests; the absolute expiry is derived from the
 * server-provided relative `expiresInSec`.
 */
@Singleton
class AuthRepositoryImpl(
    private val authApiService: AuthApiService,
    private val tokenStore: ITokenStore,
    private val deviceIdProvider: IDeviceIdProvider,
    // TASK-04a — the device-attestation masterSecret. Injected (not read from BuildConfig here) so
    // it stays testable; "" disables attestation (baseline device-less path), which is the default
    // tests use to keep the auth flow deterministic.
    private val deviceAttestSecret: String = "",
    private val clockEpochSec: () -> Long
) : IAuthRepository {

    // Hilt entry point — the clock default isn't injectable, so the production graph uses the wall
    // clock and the environment's embedded attestation secret.
    @Inject
    constructor(
        authApiService: AuthApiService,
        tokenStore: ITokenStore,
        deviceIdProvider: IDeviceIdProvider
    ) : this(
        authApiService,
        tokenStore,
        deviceIdProvider,
        BuildConfig.DEVICE_ATTEST_HMAC_SECRET,
        { System.currentTimeMillis() / 1000 }
    )

    override suspend fun requestChallenge(
        address: String,
        chain: String
    ): ResultResponse<AuthChallenge> = safeApiCall {
        val response = authApiService.challenge(AuthChallengeRequestDto(address = address, chain = chain))
        val body = response.body()
        if (!response.isSuccessful || body == null) throw response.toApiException("challenge")

        AuthChallenge(
            address = body.address ?: address,
            chain = body.chain ?: chain,
            nonce = body.nonce ?: throw IllegalStateException("Missing nonce in challenge response"),
            message = body.message ?: throw IllegalStateException("Missing message in challenge response")
        )
    }

    override suspend fun verify(
        address: String,
        chain: String,
        signature: String,
        deviceId: String?
    ): ResultResponse<AuthSession> = safeApiCall {
        // Resilient deviceId: caller-supplied wins; otherwise resolve via Play-Integrity-first /
        // deterministic-local-fallback strategy. Always sent so the backend can bind the session.
        val resolvedRequestDeviceId = deviceId?.takeIf { it.isNotBlank() } ?: deviceIdProvider.getDeviceId()

        // TASK-04a — attempt cryptographic device attestation (SOFT): fetch a single-use nonce from
        // `/device-challenge` and sign `"$nonce-$deviceId"` with the two-level HMAC. Any failure
        // (network, or no embedded secret for this environment) yields null and we fall back to the
        // baseline device-less verify, so login always works — only device-bound features (gas credit,
        // Mystery Box) need the attested path.
        val attestation = tryDeviceAttestation(resolvedRequestDeviceId)

        val response = authApiService.verify(
            AuthVerifyRequestDto(
                address = address,
                chain = chain,
                signature = signature,
                deviceId = resolvedRequestDeviceId,
                nonce = attestation?.nonce,
                attestationSignature = attestation?.signature
            )
        )
        val body = response.body()
        if (!response.isSuccessful || body == null) throw response.toApiException("verify")

        val token = body.token?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing token in verify response")
        val expiresInSec = body.expiresInSec
            ?: throw IllegalStateException("Missing expiresInSec in verify response")
        val expiresAtEpochSec = clockEpochSec() + expiresInSec
        // Persist the deviceId we sent (server echo wins if present).
        val resolvedDeviceId = body.deviceId ?: resolvedRequestDeviceId

        // Persist the freshly minted session before returning — the interceptor reads it next call.
        tokenStore.save(token = token, expiresAtEpochSec = expiresAtEpochSec, deviceId = resolvedDeviceId)

        AuthSession(
            token = token,
            expiresInSec = expiresInSec,
            expiresAtEpochSec = expiresAtEpochSec,
            scope = body.scope,
            deviceId = resolvedDeviceId,
            deviceVerified = body.deviceVerified == true
        )
    }

    /**
     * TASK-04a — best-effort device attestation. Returns null (→ baseline device-less path) when the
     * environment has no embedded secret, the deviceId is blank, or the challenge/HMAC fails for any
     * reason. Never throws: attestation must never break login.
     *
     * Two-level scheme (see [HmacUtils.generateDeviceAttestation]):
     * `deviceKey = HMAC(masterSecret, deviceId)` → `HMAC(deviceKey, "$nonce-$deviceId")` (hex).
     */
    private suspend fun tryDeviceAttestation(deviceId: String): DeviceAttestation? {
        if (deviceAttestSecret.isBlank() || deviceId.isBlank()) return null
        return runCatching {
            val challenge = authApiService.getDeviceChallenge(DeviceChallengeRequest(deviceId = deviceId))
            val nonce = challenge.nonce
            require(nonce.isNotBlank()) { "empty device-challenge nonce" }
            DeviceAttestation(
                nonce = nonce,
                signature = HmacUtils.generateDeviceAttestation(deviceAttestSecret, deviceId, nonce)
            )
        }.getOrNull()
    }


    override suspend fun refresh(): ResultResponse<AuthSession> = safeApiCall {
        val response = authApiService.refresh()
        val body = response.body()
        if (!response.isSuccessful || body == null) throw response.toApiException("refresh")

        val token = body.token?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing token in refresh response")
        val expiresInSec = body.expiresInSec
            ?: throw IllegalStateException("Missing expiresInSec in refresh response")
        val expiresAtEpochSec = clockEpochSec() + expiresInSec
        // Refresh keeps the same claims; if the server omits deviceId, retain the attested one.
        val resolvedDeviceId = body.deviceId ?: tokenStore.getDeviceId()

        tokenStore.save(token = token, expiresAtEpochSec = expiresAtEpochSec, deviceId = resolvedDeviceId)

        AuthSession(
            token = token,
            expiresInSec = expiresInSec,
            expiresAtEpochSec = expiresAtEpochSec,
            scope = body.scope,
            deviceId = resolvedDeviceId,
            deviceVerified = body.deviceVerified == true
        )
    }

    override suspend fun logout(): ResultResponse<Unit> = safeApiCall {
        // Best-effort server-side revoke; local clear is unconditional so a network failure still
        // ends the session on-device.
        runCatching { authApiService.logout() }
        tokenStore.clear()
        Unit
    }

    private fun Response<*>.toApiException(step: String): ApiException {
        val retryAfter = headers()["Retry-After"]?.trim()?.toLongOrNull()
        return ApiException(
            apiError = ApiError.from(code = null, httpStatus = code(), retryAfterSec = retryAfter),
            httpStatus = code(),
            reasonFa = "auth $step failed (${code()})",
            retryAfterSec = retryAfter
        )
    }
}
