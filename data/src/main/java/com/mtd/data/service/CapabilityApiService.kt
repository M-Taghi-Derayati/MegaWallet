package com.mtd.data.service

import com.mtd.data.dto.CapabilitiesResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * Capability Platform (Android Migration, Step 1) — the official Android-facing
 * capability endpoint. Returns a `Response<…>` (not a bare body) so the manager can
 * read the `ETag` header and detect a `304 Not Modified` from `If-None-Match`.
 */
interface CapabilityApiService {

    /**
     * `GET /api/v1/capabilities`. Pass the cached [ifNoneMatch] ETag for cheap
     * revalidation — the server replies `304` (empty body) when nothing changed.
     */
    @GET("api/v1/capabilities")
    suspend fun getCapabilities(
        @Header("If-None-Match") ifNoneMatch: String? = null
    ): Response<CapabilitiesResponseDto>
}
