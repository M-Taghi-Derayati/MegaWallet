package com.mtd.data.datasource

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mtd.core.network.BlockchainNetwork
import com.mtd.data.dto.BalancesDto
import com.mtd.data.dto.ProxyEnvelope
import com.mtd.data.network.wire.BigIntegerStringAdapter
import com.mtd.data.service.MobileProxyApiService
import com.mtd.domain.model.Asset
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigInteger

/**
 * DIAGNOSTIC E2E for the PROXY-mode "silent empty UI" bug.
 *
 * Reproduces the full read path with the **exact** bytes the live Mobile Proxy returns:
 *
 *      MockWebServer(real proxy JSON) → ProxyChainDataSource (PROXY repo layer) → UiState reducer
 *
 * The server response is valid, enveloped (`ok:true`), and parses WITHOUT any exception — yet the
 * UI renders nothing. This test pins WHY, at three independent layers, each with a failure message
 * naming the exact layer at fault.
 *
 * EXPECTED today: all three layers FAIL (red). After the Step-4 fix they all PASS (green).
 *
 * Why the existing ProxyChainDataSourceTest stays green while production is broken: it mocks the
 * GUESSED shape (`{"tokens":[...],"balanceRaw":"..."}` — see MobileProxyDto.kt's "marked for
 * verification" note), not the shape the server actually emits (`{"assets":[...],"balance":"..."}`).
 */
class ProxyBalanceSilentFailureE2ETest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: ProxyChainDataSource
    private val network = mockk<BlockchainNetwork>(relaxed = true)

    // Same Gson the app builds in NetworkModule (BigInt-as-String codec). No web3j/BouncyCastle.
    private fun realGson() = GsonBuilder()
        .registerTypeAdapter(BigInteger::class.java, BigIntegerStringAdapter())
        .create()

    private fun serviceFor(client: OkHttpClient): MobileProxyApiService =
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(realGson()))
            .build()
            .create(MobileProxyApiService::class.java)

    @Before
    fun setup() {
        server = MockWebServer().apply { start() }
        every { network.id } returns "base_sepolia"
        every { network.name } returns NetworkName.ETHEREUM
        every { network.decimals } returns 18
        every { network.networkType } returns NetworkType.EVM
        dataSource = ProxyChainDataSource(network, serviceFor(OkHttpClient()))
    }

    @After
    fun tearDown() = server.shutdown()

    // ───────────────────────── LAYER A — WIRE / PARSE ──────────────────────────
    // The body is valid and the envelope unwraps cleanly (ok:true, data != null), so NO exception
    // is thrown. The silent damage: the native amount lands in `balanceRaw` as null because the
    // server's key is `balance`, not `balanceRaw`.
    @Test
    fun `layer A - response body parses but the native amount is silently lost`() {
        val env: ProxyEnvelope<BalancesDto> = realGson().fromJson(
            BASE_SEPOLIA_JSON,
            object : TypeToken<ProxyEnvelope<BalancesDto>>() {}.type
        )

        // Sanity: the envelope itself is healthy — this is precisely why the failure is "silent".
        assertTrue("envelope should report ok:true", env.ok)
        assertNotNull("envelope data should be present", env.data)

        val nativeRaw = env.data?.native?.balanceRaw
        assertNotNull(
            "WIRE/PARSE LAYER FAILED → native amount deserialized to null. " +
                "Server sends native.balance=\"381686770\" but ProxyBalanceDto maps " +
                "@SerializedName(\"balanceRaw\"). Gson silently drops the unknown key → null → ZERO.",
            nativeRaw
        )
        assertEquals(
            "WIRE/PARSE LAYER FAILED → native amount mis-deserialized.",
            BigInteger("381686770"), nativeRaw
        )
    }

    // ───────────────────────── LAYER B — REPOSITORY / DOMAIN MAPPER ─────────────
    // The PROXY data source (what WalletRepositoryImpl.getAssets delegates to) returns Success, but
    // the domain List<Asset> is wrong: tokens are dropped (`assets` vs `tokens`) and the native
    // amount is zeroed.
    @Test
    fun `layer B - repository returns a non-null but data-empty domain object`() = runTest {
        // bsc_testnet carries a real ERC-20 (USDT) alongside the native coin → proves token dropping.
        server.enqueue(MockResponse().setBody(BSC_TESTNET_JSON))

        val result = dataSource.getBalanceAssets("0x1eee4e005d38566517c72ef5db4ac9bd129c8f6a")
        assertTrue(
            "DOMAIN LAYER FAILED → expected Success (the call does not error); got $result",
            result is ResultResponse.Success
        )
        val assets = (result as ResultResponse.Success).data

        assertEquals(
            "DOMAIN/MAPPER LAYER FAILED → expected 2 assets (native BNB + USDT token) but got " +
                "${assets.size}. The `assets` array is mapped to BalancesDto.tokens " +
                "(@SerializedName(\"tokens\")) which is null on the wire → every token is dropped.",
            2, assets.size
        )
        assertTrue(
            "DOMAIN/MAPPER LAYER FAILED → USDT token (contractAddress) missing from domain result.",
            assets.any { it.symbol == "USDT" && it.contractAddress != null }
        )

        // And the amount is preserved (use base_sepolia: a non-zero native balance).
        server.enqueue(MockResponse().setBody(BASE_SEPOLIA_JSON))
        val ethAssets = (dataSource.getBalanceAssets("0x1") as ResultResponse.Success).data
        assertTrue(
            "DOMAIN/MAPPER LAYER FAILED → native ETH balance was zeroed (server `balance` vs DTO " +
                "`balanceRaw`). Expected a positive balance, got ${ethAssets.firstOrNull()?.balance}.",
            ethAssets.any { it.balance.signum() > 0 }
        )
    }

    // ───────────────────────── LAYER C — VIEWMODEL / UISTATE ────────────────────
    // Faithful reduction of the proxy result into a single StateFlow<UiState>, exactly the app's
    // pattern (BaseViewModel → one StateFlow). The empty UI is reproduced here as `hasRenderableData
    // == false` even though loading completed with no error.
    @Test
    fun `layer C - final ViewModel UiState renders empty despite a successful load`() = runTest {
        server.enqueue(MockResponse().setBody(BASE_SEPOLIA_JSON))
        val vm = BalanceViewModel(dataSource)

        vm.load("0x1")
        val state = vm.uiState.value

        assertTrue(
            "VIEWMODEL LAYER → load should complete without error (it does — that's the trap).",
            state.errorMessage == null && !state.isLoading
        )
        assertTrue(
            "VIEWMODEL/UISTATE LAYER FAILED → UiState has no renderable data (all balances are 0), " +
                "so the screen paints empty. assets=${state.assets.map { it.symbol to it.balance }}",
            state.hasRenderableData
        )
    }

    /** Minimal stand-in mirroring the app's single-StateFlow pattern; reduces List<Asset> → UiState. */
    private class BalanceViewModel(private val source: IChainDataSource) {
        data class UiState(
            val isLoading: Boolean = true,
            val assets: List<Asset> = emptyList(),
            val errorMessage: String? = null
        ) {
            /** A wallet row is only painted when it has a positive balance (app hides zero rows). */
            val hasRenderableData: Boolean get() = assets.any { it.balance.signum() > 0 }
        }

        private val _uiState = MutableStateFlow(UiState())
        val uiState = _uiState.asStateFlow()

        suspend fun load(address: String) {
            _uiState.value = when (val r = source.getBalanceAssets(address)) {
                is ResultResponse.Success -> UiState(isLoading = false, assets = r.data)
                is ResultResponse.Error -> UiState(isLoading = false, errorMessage = r.exception.message)
            }
        }
    }

    private companion object {
        // VERBATIM bytes from the live Mobile Proxy (Network Inspector capture).
        const val BASE_SEPOLIA_JSON =
            """{"ok":true,"networkId":"base_sepolia","networkType":"EVM","data":{"address":"0x1eee4e005d38566517c72ef5db4ac9bd129c8f6a","native":{"balance":"381686770","decimals":18,"symbol":"ETH"},"assets":[{"id":"ETH-BASE_SEPOLIA","name":"ETHEREUM","symbol":"ETH","decimals":18,"networkId":"base_sepolia","contractAddress":null,"iconUrl":"https://s2.coinmarketcap.com/static/img/coins/64x64/1027.png","faName":"اتریوم","balance":"381686770"}]},"meta":{"engine":"PROXY","timestamp":"2026-06-16T16:26:56.283Z","configVersion":"1.0.3"}}"""

        const val BSC_TESTNET_JSON =
            """{"ok":true,"networkId":"bsc_testnet","networkType":"EVM","data":{"address":"0x1eee4e005d38566517c72ef5db4ac9bd129c8f6a","native":{"balance":"0","decimals":18,"symbol":"tBNB"},"assets":[{"id":"BNB-BSC_TESTNET","name":"Binance Coin","symbol":"BNB","decimals":18,"networkId":"bsc_testnet","contractAddress":null,"iconUrl":"https://s2.coinmarketcap.com/static/img/coins/64x64/1839.png","faName":"بایننس کوین","balance":"0"},{"id":"USDT-BSC_TESTNET","name":"Tether USD","symbol":"USDT","decimals":18,"networkId":"bsc_testnet","contractAddress":"0x337610d27c682E347C9cD60BD4b3b107C9d34dDd","iconUrl":"https://assets.coingecko.com/coins/images/325/standard/Tether.png","faName":"تتر","balance":"0"}]},"meta":{"engine":"PROXY","timestamp":"2026-06-16T16:26:57.794Z","configVersion":"1.0.3"}}"""
    }
}
