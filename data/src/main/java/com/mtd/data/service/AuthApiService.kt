package com.mtd.data.service

import com.mtd.data.dto.AuthChallengeRequestDto
import com.mtd.data.dto.AuthChallengeResponseDto
import com.mtd.data.dto.AuthVerifyRequestDto
import com.mtd.data.dto.AuthVerifyResponseDto
import com.mtd.data.dto.DeviceChallengeRequest
import com.mtd.data.dto.DeviceChallengeResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Phase 3 — Web3 challenge/verify auth. Mints the bearer JWT consumed by the AuthInterceptor.
 */
interface AuthApiService {

    @POST("api/auth/challenge")
    suspend fun challenge(
        @Body request: AuthChallengeRequestDto
    ): Response<AuthChallengeResponseDto>

    /**
     * Phase 2 — device challenge. Exchanges the resilient `deviceId` for a single-use `nonce` +
     * attestation message, hashed into the `attestationSignature` posted to [verify].
     */
    @POST("/api/auth/device-challenge")
    suspend fun getDeviceChallenge(
        @Body request: DeviceChallengeRequest
    ): DeviceChallengeResponse

    @POST("api/auth/verify")
    suspend fun verify(
        @Body request: AuthVerifyRequestDto
    ): Response<AuthVerifyResponseDto>

    /**
     * Slide the session: mints a new JWT with the same claims (new jti + exp). Requires a still-valid
     * Bearer token, which the host-scoped AuthInterceptor attaches automatically — no request body.
     */
    @POST("api/auth/refresh")
    suspend fun refresh(): Response<AuthVerifyResponseDto>

    /**
     * Ends the session server-side. The still-valid Bearer token is attached automatically by the
     * host-scoped AuthInterceptor — no request body. Best-effort: the client clears local state
     * regardless of the response.
     */
    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>
}
