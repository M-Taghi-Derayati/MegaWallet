package com.mtd.data.network

import com.mtd.domain.model.error.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorMappingTest {

    @Test
    fun `every proxy code maps to its typed error`() {
        assertEquals(ApiError.NetworkNotFound, ApiError.from("NETWORK_NOT_FOUND", 404))
        assertEquals(ApiError.AssetNotFound, ApiError.from("ASSET_NOT_FOUND", 404))
        assertEquals(ApiError.ValidationError, ApiError.from("VALIDATION_ERROR", 400))
        assertEquals(ApiError.InvalidAddress, ApiError.from("INVALID_ADDRESS", 400))
        assertEquals(ApiError.InvalidSignedTx, ApiError.from("INVALID_SIGNED_TX", 400))
        assertEquals(ApiError.UnsupportedOperation, ApiError.from("UNSUPPORTED_OPERATION", 400))
        assertEquals(ApiError.SimulationReverted, ApiError.from("SIMULATION_REVERTED", 422))
        assertEquals(ApiError.NetworkFamilyUnsupported, ApiError.from("NETWORK_FAMILY_UNSUPPORTED", 501))
        assertEquals(ApiError.UpstreamUnavailable, ApiError.from("UPSTREAM_UNAVAILABLE", 502))
        assertEquals(ApiError.BroadcastRejected, ApiError.from("BROADCAST_REJECTED", 502))
    }

    @Test
    fun `cross-cutting codes map`() {
        assertEquals(ApiError.RaceConditionLock, ApiError.from("RACE_CONDITION_LOCK", 409))
        assertEquals(ApiError.InsufficientGasCredit, ApiError.from("INSUFFICIENT_GAS_CREDIT", 400))
        assertEquals(ApiError.InsufficientGasCredit, ApiError.from("INSUFFICIENT_CREDIT", 409))
        assertEquals(ApiError.SimulationReverted, ApiError.from("SWAP_SIMULATION_FAILED", 422))
    }

    @Test
    fun `unknown code falls back to the HTTP status discriminator`() {
        assertEquals(ApiError.RequoteRequired, ApiError.from(null, 409))
        assertEquals(ApiError.SimulationReverted, ApiError.from(null, 422))
        assertEquals(ApiError.ServiceUnavailable, ApiError.from("WHATEVER", 503))
        assertEquals(ApiError.InternalError, ApiError.from(null, 500))
    }

    @Test
    fun `429 carries retryAfter`() {
        assertEquals(ApiError.RateLimited(30L), ApiError.from(null, 429, 30L))
        assertEquals(ApiError.RateLimited(null), ApiError.from("RATE_LIMIT", 429))
    }

    @Test
    fun `code matching is case-insensitive (never depends on Persian message)`() {
        assertEquals(ApiError.NetworkNotFound, ApiError.from("network_not_found", 404))
    }

    @Test
    fun `a genuinely new code degrades to Unknown carrying the raw code`() {
        val e = ApiError.from("SOME_FUTURE_CODE", 400)
        assertTrue(e is ApiError.Unknown)
        assertEquals("SOME_FUTURE_CODE", (e as ApiError.Unknown).code)
    }
}
