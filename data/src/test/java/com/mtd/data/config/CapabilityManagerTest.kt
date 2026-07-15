package com.mtd.data.config

import com.mtd.data.dto.CapabilitiesResponseDto
import com.mtd.data.dto.FeatureCapabilityDto
import com.mtd.data.dto.NetworkCapabilityDto
import com.mtd.data.service.CapabilityApiService
import com.mtd.domain.model.capability.CapabilitySnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * Capability Platform (Android Migration, Step 1) — verifies the CapabilityManager
 * Offline-First + ETag + Fail-Safe contract (mirrors ConfigManagerOfflineFirstTest):
 *  - 200 → maps to domain + caches (with ETag),
 *  - 304 → serves the cache without re-mapping,
 *  - offline / HTTP error → cache, else EMPTY, and never throws.
 */
class CapabilityManagerTest {

    private lateinit var api: CapabilityApiService
    private lateinit var cacheStore: CapabilityCacheStore
    private lateinit var manager: CapabilityManager

    @Before
    fun setUp() {
        api = mockk(relaxed = true)
        cacheStore = mockk(relaxed = true)
        manager = CapabilityManager(capabilityApiService = api, cacheStore = cacheStore)
    }

    private fun sampleBody() = CapabilitiesResponseDto(
        ok = true,
        version = "1.0.4",
        capabilities = listOf(
            NetworkCapabilityDto(
                networkId = "base_mainnet", chainId = 8453, relayPrefix = "base",
                mounted = true, gasless = true, sponsor = false,
                features = mapOf(
                    "gasless" to FeatureCapabilityDto(featureId = "gasless", available = true, visible = true, reasonCode = "OK", relayPrefix = "base"),
                    "sponsor" to FeatureCapabilityDto(featureId = "sponsor", available = false, visible = true, reasonCode = "MAINTENANCE")
                )
            )
        )
    )

    @Test
    fun `200 maps to domain, caches with etag, exposes typed gasless and sponsor`() = runTest {
        every { cacheStore.read() } returns null
        coEvery { api.getCapabilities(any()) } returns
            Response.success(sampleBody(), Headers.headersOf("ETag", "\"cap-abc\""))

        val snap = manager.getCapabilities()

        assertEquals("1.0.4", snap.version)
        assertEquals("\"cap-abc\"", snap.etag)
        val net = snap.network("base_mainnet")!!
        assertEquals("base", net.relayPrefix)
        assertTrue(net.gasless.available)
        assertFalse(net.sponsor.available)
        assertEquals("MAINTENANCE", net.sponsor.reasonCode)
        // fresh snapshot is persisted
        val written = slot<CapabilitySnapshot>()
        verify(exactly = 1) { cacheStore.write(capture(written)) }
        assertEquals("\"cap-abc\"", written.captured.etag)
    }

    @Test
    fun `304 serves the cached snapshot without rewriting`() = runTest {
        val cached = CapabilitySnapshot(version = "1.0.4", etag = "\"cap-abc\"", fetchedAtEpochMs = 1L)
        every { cacheStore.read() } returns cached
        // 304 is not a 2xx, so Response.success(code,...) would reject it — mock the Response.
        val notModified = mockk<Response<CapabilitiesResponseDto>>(relaxed = true)
        every { notModified.code() } returns 304
        every { notModified.isSuccessful } returns false
        every { notModified.body() } returns null
        coEvery { api.getCapabilities("\"cap-abc\"") } returns notModified

        val result = manager.getCapabilities()

        assertSame(cached, result)
        verify(exactly = 0) { cacheStore.write(any()) }
    }

    @Test
    fun `offline with a cache returns the cache and never throws`() = runTest {
        val cached = CapabilitySnapshot(version = "1.0.1", etag = "\"old\"", fetchedAtEpochMs = 1L)
        every { cacheStore.read() } returns cached
        coEvery { api.getCapabilities(any()) } throws IOException("offline")

        val result = manager.getCapabilities()

        assertSame(cached, result)
    }

    @Test
    fun `offline with no cache returns EMPTY (fail-safe, no throw)`() = runTest {
        every { cacheStore.read() } returns null
        coEvery { api.getCapabilities(any()) } throws IOException("offline")

        val result = manager.getCapabilities()

        assertSame(CapabilitySnapshot.EMPTY, result)
        assertTrue(result.isEmpty)
    }

    @Test
    fun `getNetworkCapability returns unavailable for an unknown network`() = runTest {
        every { cacheStore.read() } returns null
        coEvery { api.getCapabilities(any()) } returns Response.success(sampleBody(), Headers.headersOf("ETag", "\"e\""))

        val unknown = manager.getNetworkCapability("does_not_exist")

        assertEquals("does_not_exist", unknown.networkId)
        assertFalse(unknown.gasless.available)
        assertNull(unknown.relayPrefix)
    }

    @Test
    fun `repeated network capability reads within memory ttl share one api call`() = runTest {
        every { cacheStore.read() } returns null
        coEvery { api.getCapabilities(any()) } returns Response.success(sampleBody(), Headers.headersOf("ETag", "\"e\""))

        val first = manager.getNetworkCapability("base_mainnet")
        val second = manager.getNetworkCapability("base_mainnet")

        assertTrue(first.gasless.available)
        assertTrue(second.gasless.available)
        coVerify(exactly = 1) { api.getCapabilities(any()) }
        verify(exactly = 1) { cacheStore.read() }
        verify(exactly = 1) { cacheStore.write(any()) }
    }
}
