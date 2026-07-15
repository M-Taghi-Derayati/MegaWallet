package com.mtd.data.repository

import com.google.gson.Gson
import com.mtd.data.datasource.RelayerPriceDataSource
import com.mtd.data.dto.AssetPriceDataDto
import com.mtd.data.dto.AssetPriceResponse
import com.mtd.data.dto.RelayerPricesResponseDto
import com.mtd.data.service.CoinDetailApiService
import com.mtd.data.service.RelayerPriceApiService
import com.mtd.data.service.USDTApiService
import com.mtd.domain.interfaceRepository.IMarketDataRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.assets.AssetPriceDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigDecimal

/**
 * E2E for the market-price migration: CoinDesk-direct → relayer `/api/v1/prices` (ANDROID_API_CONTRACT §1.6).
 *
 *      MockWebServer(real relayer /api/v1/prices JSON) → RelayerPriceDataSource
 *          → MarketDataRepositoryImpl (relayer PRIMARY, CoinDesk FALLBACK) → UiState reducer
 *
 * Asserts, independently and with layer-naming messages:
 *   A) the relayer body parses with per-symbol error isolation (priced symbol vs `{error}` symbol),
 *   B) the repository returns priced symbols and OMITS errored ones (no crash, no hidden failure),
 *   C) the ViewModel UiState renders the priced symbol and gracefully lacks the unpriced one,
 *   D) on an all-error relayer round the repo FALLS BACK to CoinDesk, and
 *   E) Wallex is NEVER called during price fetching (it serves USD→IRR for trading, out of scope).
 */
class RelayerPriceMigrationE2ETest {

    private lateinit var server: MockWebServer
    private lateinit var relayerDataSource: RelayerPriceDataSource

    // Fallback + out-of-scope dependencies are mocked so we can assert (non-)interaction precisely.
    private val coinDesk = mockk<CoinDetailApiService>()
    private val wallex = mockk<USDTApiService>(relaxed = true)

    private lateinit var repo: MarketDataRepositoryImpl

    @Before
    fun setup() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(RelayerPriceApiService::class.java)
        relayerDataSource = RelayerPriceDataSource(api)
        repo = MarketDataRepositoryImpl(coinDesk, wallex, relayerDataSource)
    }

    @After
    fun tearDown() = server.shutdown()

    // ───────────────────────── LAYER A — WIRE / PARSE ──────────────────────────
    @Test
    fun `layer A - relayer body parses with per-symbol error isolation`() {
        val dto: RelayerPricesResponseDto = Gson().fromJson(RELAYER_PRICES_JSON, RelayerPricesResponseDto::class.java)

        val eth = dto.prices?.get("ETH")
        val usdt = dto.prices?.get("USDT")
        assertNotNull("WIRE/PARSE LAYER FAILED → ETH entry missing from `prices` map.", eth)
        assertTrue(
            "WIRE/PARSE LAYER FAILED → ETH.usd did not decode (server `usd` is a JSON number).",
            eth!!.hasPrice && BigDecimal("3500.12").compareTo(eth.usd) == 0
        )
        // Per-symbol isolation: USDT carries an error and therefore no price.
        assertNotNull("WIRE/PARSE LAYER FAILED → USDT error not captured.", usdt?.error)
        assertFalse("WIRE/PARSE LAYER FAILED → USDT should have no usable price.", usdt!!.hasPrice)
        assertNull(usdt.usd)
    }

    // ───────────────────────── LAYER B — DATASOURCE / REPOSITORY ───────────────
    @Test
    fun `layer B - repository returns priced symbols and omits errored ones, no fallback, no Wallex`() = runTest {
        server.enqueue(MockResponse().setBody(RELAYER_PRICES_JSON))

        val result = repo.getLatestPrices(listOf("ETH", "USDT"))
        assertTrue("DOMAIN LAYER FAILED → expected Success, got $result", result is ResultResponse.Success)
        val prices = (result as ResultResponse.Success).data

        assertEquals(
            "DOMAIN/MAPPER LAYER FAILED → expected exactly 1 priced asset (ETH); USDT errored and must " +
                "be omitted, not crash or fail the call. Got ${prices.map { it.assetId }}.",
            1, prices.size
        )
        val eth = prices.single()
        assertEquals("ETH", eth.assetId)
        assertTrue(
            "DOMAIN/MAPPER LAYER FAILED → ETH price not mapped (expected 3500.12), got ${eth.priceUsd}.",
            BigDecimal("3500.12").compareTo(eth.priceUsd) == 0
        )

        // Relayer succeeded → CoinDesk fallback must NOT be touched, and Wallex never participates.
        coVerify(exactly = 0) { coinDesk.getPrices(any(), any(), any(), any()) }
        coVerify(exactly = 0) { wallex.getMarketStats() }
    }

    // ───────────────────────── LAYER C — VIEWMODEL / UISTATE ────────────────────
    @Test
    fun `layer C - UiState renders the priced symbol and gracefully lacks the unpriced one`() = runTest {
        server.enqueue(MockResponse().setBody(RELAYER_PRICES_JSON))
        val vm = MarketPriceViewModel(repo)

        vm.load(listOf("ETH", "USDT"))
        val state = vm.uiState.value

        assertNull("VIEWMODEL LAYER → price load should complete without error.", state.error)
        assertNotNull(
            "VIEWMODEL/UISTATE LAYER FAILED → ETH price absent from UiState.",
            state.pricesBySymbol["ETH"]
        )
        assertNull(
            "VIEWMODEL/UISTATE LAYER → USDT must be gracefully absent (UI shows \"—\"), not crash.",
            state.pricesBySymbol["USDT"]
        )
    }

    // ───────────────────────── LAYER D — FALLBACK TO COINDESK ───────────────────
    @Test
    fun `layer D - all-error relayer round falls back to CoinDesk, still no Wallex`() = runTest {
        server.enqueue(MockResponse().setBody(RELAYER_ALL_ERROR_JSON))
        coEvery { coinDesk.getPrices(any(), any(), any(), any()) } returns Response.success(
            AssetPriceResponse(
                data = mapOf(
                    "ETH-USDT" to AssetPriceDataDto(assetId = "ETH", priceUsd = BigDecimal("12.5"), priceChanges24h = BigDecimal("1.1"))
                )
            )
        )

        val result = repo.getLatestPrices(listOf("ETH"))
        assertTrue("FALLBACK LAYER FAILED → expected Success from CoinDesk fallback, got $result", result is ResultResponse.Success)
        val prices = (result as ResultResponse.Success).data
        assertEquals(
            "FALLBACK LAYER FAILED → price should come from CoinDesk (12.5) after relayer returned all-errors.",
            0, BigDecimal("12.5").compareTo(prices.single().priceUsd)
        )

        coVerify(exactly = 1) { coinDesk.getPrices(any(), any(), any(), any()) }
        coVerify(exactly = 0) { wallex.getMarketStats() }
    }

    /** Faithful single-StateFlow reducer mirroring the app's price→UiState shape. */
    private class MarketPriceViewModel(private val repository: IMarketDataRepository) {
        data class UiState(
            val isLoading: Boolean = true,
            val pricesBySymbol: Map<String, AssetPriceDto> = emptyMap(),
            val error: String? = null
        )

        private val _uiState = MutableStateFlow(UiState())
        val uiState = _uiState.asStateFlow()

        suspend fun load(symbols: List<String>) {
            _uiState.value = when (val r = repository.getLatestPrices(symbols)) {
                is ResultResponse.Success -> UiState(isLoading = false, pricesBySymbol = r.data.associateBy { it.assetId })
                is ResultResponse.Error -> UiState(isLoading = false, error = r.exception.message)
            }
        }
    }

    private companion object {
        // Shape per ANDROID_API_CONTRACT §1.6 (usd/irr are JSON numbers; per-symbol {error} isolation).
        const val RELAYER_PRICES_JSON =
            """{"quoteSymbol":"USD","usdToIrr":700000,"fetchedAt":1780000000000,"prices":{"ETH":{"usd":3500.12,"irr":2450084000,"source":"coindesk","fetchedAt":1780000000000},"USDT":{"error":"symbol not supported"}}}"""

        const val RELAYER_ALL_ERROR_JSON =
            """{"quoteSymbol":"USD","usdToIrr":700000,"fetchedAt":1780000000000,"prices":{"ETH":{"error":"upstream unavailable"}}}"""
    }
}
