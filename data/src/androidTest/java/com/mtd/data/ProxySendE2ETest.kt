package com.mtd.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.di.NetworkConnectionInterceptor
import com.mtd.data.di.NetworkModule
import com.mtd.data.di.NetworkModule.httpLoggingInterceptorProvider
import com.mtd.data.di.NetworkModule.provideOkHttpClient
import com.mtd.data.di.NetworkModule.provideRetrofitBuilder
import com.mtd.data.service.MobileProxyApiService
import com.mtd.domain.interfaceRepository.IBlockchainConnectionModeProvider
import com.mtd.domain.model.BlockchainConnectionMode
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionParams
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket

/**
 * End-to-end PROXY send test against a **live testnet relayer**. Exercises the real path:
 * `ChainDataSourceFactory.create(network)` (PROXY) → `ProxyChainDataSource.sendTransaction(...)`
 * → `/prepare` → local sign (real web3j / bitcoinj) → `/broadcast`, then polls `/tx/{id}/status`.
 *
 * NOT hermetic and NOT for CI: each test broadcasts a REAL testnet transaction and spends real
 * testnet funds. Every test is gated on (a) the relayer being reachable and (b) the per-family
 * instrumentation args being supplied, so the suite no-ops (assumption-skip) when unconfigured.
 *
 * Run from a device/emulator with a funded testnet wallet, e.g.:
 *
 *   ./gradlew :data:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.mtd.data.ProxySendE2ETest \
 *     -Pandroid.testInstrumentationRunnerArguments.proxy_evm_network_id=sepolia \
 *     -Pandroid.testInstrumentationRunnerArguments.proxy_evm_pk=0xYOUR_FUNDED_PK \
 *     -Pandroid.testInstrumentationRunnerArguments.proxy_evm_to=0xRECIPIENT \
 *     -Pandroid.testInstrumentationRunnerArguments.proxy_evm_asset_id=ETH-SEPOLIA \
 *     -Pandroid.testInstrumentationRunnerArguments.proxy_evm_amount_raw=100000000000000
 *
 * TRON args:  proxy_tron_network_id (e.g. shasta_testnet), proxy_tron_pk, proxy_tron_to,
 *             proxy_tron_asset_id (e.g. TRX-TRON_SHASTA), proxy_tron_amount_raw
 * UTXO args:  proxy_utxo_network_id (e.g. litecoin_testnet), proxy_utxo_pk, proxy_utxo_to,
 *             proxy_utxo_asset_id (e.g. LTC-LITECOIN_TESTNET), proxy_utxo_amount_sat,
 *             [proxy_utxo_fee_rate] (sat/vByte, default 8)
 */
@RunWith(AndroidJUnit4::class)
class ProxySendE2ETest {

    private lateinit var context: Context
    private lateinit var blockchainRegistry: BlockchainRegistry
    private lateinit var assetRegistry: AssetRegistry
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var retrofitBuilder: Retrofit.Builder
    private lateinit var factory: ChainDataSourceFactory
    private lateinit var statusService: MobileProxyApiService

    private val proxyMode = object : IBlockchainConnectionModeProvider {
        override fun currentMode(): BlockchainConnectionMode = BlockchainConnectionMode.PROXY
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        val networkInterceptor = NetworkConnectionInterceptor(context)
        okHttpClient = provideOkHttpClient(httpLoggingInterceptorProvider(), networkInterceptor)
        // The BigInteger-aware Gson is required so amount/value fields decode from JSON strings.
        val gson = NetworkModule.provideGson()
        retrofitBuilder = provideRetrofitBuilder(okHttpClient, gson)

        blockchainRegistry = BlockchainRegistry().apply { loadNetworksFromAssets(context) }
        assetRegistry = AssetRegistry(blockchainRegistry).apply { loadAssetsFromAssets(context) }

        factory = ChainDataSourceFactory(
            blockchainRegistry = blockchainRegistry,
            modeProvider = proxyMode,
            retrofitBuilder = retrofitBuilder,
            assetRegistry = assetRegistry,
            okHttpClient = okHttpClient
        )

        statusService = retrofitBuilder
            .baseUrl(BuildConfig.RELAYER_BASE_URL)
            .build()
            .create(MobileProxyApiService::class.java)
    }

    @Test
    fun evm_proxy_send_end_to_end() {
        val networkId = arg("proxy_evm_network_id")
        val pk = arg("proxy_evm_pk")
        val to = arg("proxy_evm_to")
        val assetId = arg("proxy_evm_asset_id")
        val amountRaw = arg("proxy_evm_amount_raw")
        assumeConfigured(networkId, pk, to, assetId, amountRaw)

        val network = blockchainRegistry.getNetworkById(networkId!!)
            ?: throw IllegalStateException("Unknown networkId: $networkId")
        val params = TransactionParams.Evm(
            networkName = network.name,
            to = to!!,
            amount = BigInteger(amountRaw!!),
            data = null,
            gasPrice = BigInteger.ZERO,
            gasLimit = BigInteger.ZERO,
            assetId = assetId
        )

        val txHash = sendAndAssert(network.id, params, pk!!)
        val status = pollStatus(network.id, txHash)
        assertTrue("EVM tx reported FAILED (hash=$txHash)", !status.equals("FAILED", ignoreCase = true))
    }

    @Test
    fun tron_proxy_send_end_to_end() {
        val networkId = arg("proxy_tron_network_id")
        val pk = arg("proxy_tron_pk")
        val to = arg("proxy_tron_to")
        val assetId = arg("proxy_tron_asset_id")
        val amountRaw = arg("proxy_tron_amount_raw")
        assumeConfigured(networkId, pk, to, assetId, amountRaw)

        val network = blockchainRegistry.getNetworkById(networkId!!)
            ?: throw IllegalStateException("Unknown networkId: $networkId")
        val params = TransactionParams.Tvm(
            networkName = network.name,
            toAddress = to!!,
            amount = BigInteger(amountRaw!!),
            assetId = assetId
        )

        val txHash = sendAndAssert(network.id, params, pk!!)
        val status = pollStatus(network.id, txHash)
        assertTrue("TRON tx reported FAILED (hash=$txHash)", !status.equals("FAILED", ignoreCase = true))
    }

    @Test
    fun utxo_proxy_send_end_to_end() {
        val networkId = arg("proxy_utxo_network_id")
        val pk = arg("proxy_utxo_pk")
        val to = arg("proxy_utxo_to")
        val assetId = arg("proxy_utxo_asset_id")
        val amountSat = arg("proxy_utxo_amount_sat")
        assumeConfigured(networkId, pk, to, assetId, amountSat)

        val network = blockchainRegistry.getNetworkById(networkId!!)
            ?: throw IllegalStateException("Unknown networkId: $networkId")
        val params = TransactionParams.Utxo(
            chainId = network.chainId ?: 0L,
            toAddress = to!!,
            amountInSatoshi = amountSat!!.toLong(),
            feeRateInSatsPerByte = arg("proxy_utxo_fee_rate")?.toLong() ?: 8L,
            assetId = assetId
        )

        val txHash = sendAndAssert(network.id, params, pk!!)
        // UTXO never reports FAILED (it stays PENDING until confirmed) — assert a non-blank id + accepted.
        val status = pollStatus(network.id, txHash)
        assertTrue("UTXO tx reported FAILED (hash=$txHash)", !status.equals("FAILED", ignoreCase = true))
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun sendAndAssert(
        networkId: String,
        params: TransactionParams,
        pk: String
    ): String {
        val dataSource = factory.create(blockchainRegistry.getNetworkById(networkId)!!)
        val result = runBlocking { dataSource.sendTransaction(params, pk) }
        assertTrue("send failed: $result", result is ResultResponse.Success)
        val txHash = (result as ResultResponse.Success).data
        assertTrue("empty txHash returned", txHash.isNotBlank())
        return txHash
    }

    private fun pollStatus(
        networkId: String,
        txHash: String,
        attempts: Int = 12,
        intervalMs: Long = 5_000L
    ): String {
        var last = "UNKNOWN"
        repeat(attempts) {
            val resp = runBlocking { statusService.transactionStatus(networkId, txHash) }
            val body = resp.body()
            if (resp.isSuccessful && body?.ok == true && body.data != null) {
                last = body.data!!.status ?: "PENDING"
                if (last.equals("CONFIRMED", ignoreCase = true) ||
                    last.equals("FAILED", ignoreCase = true)
                ) {
                    return last
                }
            }
            runBlocking { delay(intervalMs) }
        }
        return last
    }

    private fun arg(key: String): String? =
        InstrumentationRegistry.getArguments().getString(key)?.takeIf { it.isNotBlank() }

    private fun assumeConfigured(vararg required: String?) {
        assumeTrue("Relayer unreachable at ${NetworkModule.serverIp}:3000", isBackendReachable())
        assumeTrue("Missing instrumentation args for this family", required.all { !it.isNullOrBlank() })
    }

    private fun isBackendReachable(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(NetworkModule.serverIp, 3000), 1500) }
        true
    } catch (_: Exception) {
        false
    }
}
