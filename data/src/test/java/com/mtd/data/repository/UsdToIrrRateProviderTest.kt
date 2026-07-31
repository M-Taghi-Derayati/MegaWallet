package com.mtd.data.repository

import com.mtd.domain.interfaceRepository.IAppCacheStore
import com.mtd.domain.interfaceRepository.IMarketDataRepository
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.ResultResponse
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * TASK-54 — the provider is the single observable owner of the Wallex USD→Toman rate. These cover the
 * properties the old scattered caches got wrong: staying observable, not re-fetching inside the TTL,
 * not stampeding when several screens ask at once, and never inventing a rate when the fetch fails.
 */
class UsdToIrrRateProviderTest {

    private lateinit var marketData: IMarketDataRepository
    private lateinit var cacheStore: IAppCacheStore
    private lateinit var provider: UsdToIrrRateProvider

    private fun rate(value: String, updatedAt: Long = System.currentTimeMillis()) = CurrencyRate(
        // TASK-56 — "TMN": the Wallex value is تومان, and `FiatConversion` resolves the unit from
        // this label, so the fixture must not claim rial.
        quoteCurrency = "TMN",
        baseCurrency = "USDT",
        rate = BigDecimal(value),
        lastUpdated = updatedAt
    )

    @Before
    fun setUp() {
        marketData = mockk()
        cacheStore = mockk(relaxed = true)
        coEvery { cacheStore.get(any(), String::class.java) } returns null
        coEvery { cacheStore.put<String>(any(), any(), any()) } just Runs
        provider = UsdToIrrRateProvider(marketData, cacheStore)
    }

    @Test
    fun `rate is null until the first successful refresh`() = runTest {
        assertNull(provider.rate.value)
    }

    @Test
    fun `successful refresh publishes the rate and persists it`() = runTest {
        coEvery { marketData.getUsdToIrrRate() } returns ResultResponse.Success(rate("70000"))

        provider.refresh()

        assertEquals(BigDecimal("70000"), provider.rate.value?.rate)
        coVerify(exactly = 1) { cacheStore.put<String>("LAST_IRR_RATE", "70000", any()) }
    }

    @Test
    fun `a second refresh inside the TTL does not hit the network again`() = runTest {
        coEvery { marketData.getUsdToIrrRate() } returns ResultResponse.Success(rate("70000"))

        provider.refresh()
        provider.refresh()

        coVerify(exactly = 1) { marketData.getUsdToIrrRate() }
    }

    @Test
    fun `force refreshes even inside the TTL and republishes the new value`() = runTest {
        coEvery { marketData.getUsdToIrrRate() } returnsMany listOf(
            ResultResponse.Success(rate("70000")),
            ResultResponse.Success(rate("83500"))
        )

        provider.refresh()
        provider.refresh(force = true)

        coVerify(exactly = 2) { marketData.getUsdToIrrRate() }
        assertEquals(BigDecimal("83500"), provider.rate.value?.rate)
    }

    /** Several screens observing at once must not each trigger their own Wallex round-trip. */
    @Test
    fun `concurrent refreshes collapse into a single fetch`() = runTest {
        coEvery { marketData.getUsdToIrrRate() } returns ResultResponse.Success(rate("70000"))

        (1..8).map { async { provider.refresh() } }.awaitAll()

        coVerify(exactly = 1) { marketData.getUsdToIrrRate() }
    }

    @Test
    fun `a failed refresh keeps the last known value instead of inventing one`() = runTest {
        coEvery { marketData.getUsdToIrrRate() } returnsMany listOf(
            ResultResponse.Success(rate("70000")),
            ResultResponse.Error(IllegalStateException("wallex down"))
        )

        provider.refresh()
        provider.refresh(force = true)

        assertEquals(BigDecimal("70000"), provider.rate.value?.rate)
    }

    @Test
    fun `a failed first refresh leaves the rate unknown rather than zero`() = runTest {
        coEvery { marketData.getUsdToIrrRate() } returns
            ResultResponse.Error(IllegalStateException("wallex down"))

        provider.refresh()

        assertNull(provider.rate.value)
    }

    @Test
    fun `cold start seeds from the persisted rate so the UI is not blank`() = runTest {
        coEvery { cacheStore.get("LAST_IRR_RATE", String::class.java) } returns "68000"
        coEvery { marketData.getUsdToIrrRate() } returns ResultResponse.Success(rate("70000"))

        provider.refresh()

        // The seed is treated as stale on purpose, so the refresh still runs and wins.
        assertEquals(BigDecimal("70000"), provider.rate.value?.rate)
        coVerify(exactly = 1) { marketData.getUsdToIrrRate() }
    }

    @Test
    fun `ensureSeeded publishes the persisted rate without touching the network`() = runTest {
        coEvery { cacheStore.get("LAST_IRR_RATE", String::class.java) } returns "68000"

        provider.ensureSeeded()

        assertEquals(BigDecimal("68000"), provider.rate.value?.rate)
        coVerify(exactly = 0) { marketData.getUsdToIrrRate() }
    }

    @Test
    fun `ensureSeeded reads storage only once`() = runTest {
        coEvery { cacheStore.get("LAST_IRR_RATE", String::class.java) } returns "68000"

        provider.ensureSeeded()
        provider.ensureSeeded()

        coVerify(exactly = 1) { cacheStore.get("LAST_IRR_RATE", String::class.java) }
    }

    @Test
    fun `seeded value survives when the very first refresh fails`() = runTest {
        coEvery { cacheStore.get("LAST_IRR_RATE", String::class.java) } returns "68000"
        coEvery { marketData.getUsdToIrrRate() } returns
            ResultResponse.Error(IllegalStateException("wallex down"))

        provider.refresh()

        assertEquals(BigDecimal("68000"), provider.rate.value?.rate)
    }
}
