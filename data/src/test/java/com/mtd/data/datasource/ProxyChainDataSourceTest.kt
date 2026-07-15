package com.mtd.data.datasource

import com.google.gson.GsonBuilder
import com.mtd.core.network.BlockchainNetwork
import com.mtd.data.network.wire.BigIntegerStringAdapter
import com.mtd.data.service.MobileProxyApiService
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.TimeUnit

class ProxyChainDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: ProxyChainDataSource
    private val network = mockk<BlockchainNetwork>(relaxed = true)

    // Fake signer keeps web3j/BouncyCastle out of the unit test (the data source orchestration is
    // what we're testing here, not the secp256k1 implementation).
    private val fakeSigner = object : EvmTxSigner {
        override fun deriveAddress(privateKeyHex: String): String =
            "0x000000000000000000000000000000000000bEEF"

        override fun signRawTransaction(
            privateKeyHex: String,
            params: TransactionParams.Evm,
            nonce: BigInteger,
            chainId: Long
        ): String = "0xdeadbeefsigned"

        override fun signPreparedTransaction(privateKeyHex: String, tx: PreparedEvmTx): String =
            "0xdeadbeefsigned"
    }

    // Fake TRON signer — keeps web3j/BouncyCastle out of the unit test.
    private val fakeTronSigner = object : TronTxSigner {
        override fun deriveTronAddress(privateKeyHex: String): String =
            "TJRyWwFs9wTFGZg3JbrVriFbNfCug5tDeC"

        override fun signRawDataHex(rawDataHex: String, privateKeyHex: String): String =
            "deadbeefsig"
    }

    // Fake UTXO builder — keeps bitcoinj out of the unit test; we assert the orchestration
    // (prepare → build from the returned UTXO set → broadcast the signed hex), not the signing math.
    private val fakeUtxoBuilder = object : UtxoTxBuilder {
        override fun deriveAddress(privateKeyHex: String): String = "ltc1qsender"

        override fun buildSignedTx(
            privateKeyHex: String,
            recipient: String,
            amountSat: Long,
            feeRateSatPerVByte: Long,
            utxos: List<UtxoInput>
        ): String = "0200000signedutxohex"
    }

    private fun serviceFor(client: OkHttpClient): MobileProxyApiService {
        val gson = GsonBuilder()
            .registerTypeAdapter(BigInteger::class.java, BigIntegerStringAdapter())
            .create()
        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(MobileProxyApiService::class.java)
    }

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        every { network.id } returns "evm"
        every { network.name } returns NetworkName.ETHEREUM
        every { network.decimals } returns 18
        every { network.networkType } returns NetworkType.EVM
        dataSource = ProxyChainDataSource(network, serviceFor(OkHttpClient()), fakeSigner, fakeTronSigner)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun err(ex: Throwable): ApiException = ex as ApiException

    @Test
    fun `balances success maps native + token with full BigInteger precision`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"networkId":"evm","data":{
                   "address":"0x1",
                   "native":{"symbol":"ETH","name":"Ethereum","decimals":18,"balanceRaw":"1500000000000000000"},
                   "tokens":[{"symbol":"USDC","name":"USD Coin","decimals":6,"contractAddress":"0xA0b","balanceRaw":"250000000"}]
                }}"""
            )
        )

        val res = dataSource.getBalanceAssets("0x1")
        assertTrue(res is ResultResponse.Success)
        val assets = (res as ResultResponse.Success).data

        assertEquals(2, assets.size)
        assertEquals("ETH", assets[0].symbol)
        assertNull(assets[0].contractAddress)              // native → no contract
        assertTrue(BigDecimal("1.5").compareTo(assets[0].balance) == 0)
        assertEquals("USDC", assets[1].symbol)
        assertEquals("0xA0b", assets[1].contractAddress)
        assertTrue(BigDecimal("250").compareTo(assets[1].balance) == 0)
    }

    @Test
    fun `huge token balance beyond Long_MAX is not truncated`() = runTest {
        // 5_000_000 tokens at 18 decimals = 5e24 raw, far above Long.MAX (~9.2e18)
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"data":{"native":{"symbol":"ETH","decimals":18,"balanceRaw":"5000000000000000000000000"}}}"""
            )
        )
        val assets = (dataSource.getBalanceAssets("0x1") as ResultResponse.Success).data
        assertTrue(BigDecimal("5000000").compareTo(assets[0].balance) == 0)
    }

    @Test
    fun `404 envelope maps to NetworkNotFound and surfaces reasonFa (but branches on code)`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(
                """{"ok":false,"networkId":"evm","error":{"code":"NETWORK_NOT_FOUND","message":"شبکه پیدا نشد"}}"""
            )
        )
        val res = dataSource.getBalanceAssets("0x1")
        val ex = err((res as ResultResponse.Error).exception)
        assertEquals(ApiError.NetworkNotFound, ex.apiError)
        assertEquals("شبکه پیدا نشد", ex.reasonFa)
        assertEquals(404, ex.httpStatus)
    }

    @Test
    fun `ok-false on HTTP 200 still maps via error_code`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"ok":false,"error":{"code":"UPSTREAM_UNAVAILABLE","message":"x"}}"""
            )
        )
        val res = dataSource.getFeeOptions(null, null, null, null)
        assertEquals(ApiError.UpstreamUnavailable, err((res as ResultResponse.Error).exception).apiError)
    }

    @Test
    fun `422 simulation reverted on broadcast path`() = runTest {
        every { network.chainId } returns 11155111L
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"data":{"transaction":{"to":"0x000000000000000000000000000000000000dEaD","value":"0x3e8","data":"0x","nonce":"0x5","gasLimit":"0x5208","gasPrice":"0x3b9aca00","chainId":11155111,"type":0}}}"""
            )
        ) // prepare
        server.enqueue(
            MockResponse().setResponseCode(422)
                .setBody("""{"ok":false,"error":{"code":"SIMULATION_REVERTED","message":"revert"}}""")
        )
        val params = TransactionParams.Evm(
            networkName = NetworkName.SEPOLIA,
            to = "0x000000000000000000000000000000000000dEaD",
            amount = BigInteger("1000"),
            gasPrice = BigInteger("1000000000"),
            gasLimit = BigInteger("21000"),
            assetId = "ETH-SEPOLIA"
        )
        val res = dataSource.sendTransaction(params, TEST_PK)
        assertEquals(ApiError.SimulationReverted, err((res as ResultResponse.Error).exception).apiError)
    }

    @Test
    fun `429 carries Retry-After`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "30")
                .setBody("""{"ok":false,"error":{"code":"RATE_LIMIT"}}""")
        )
        val res = dataSource.getBalanceAssets("0x1")
        val ex = err((res as ResultResponse.Error).exception)
        assertEquals(ApiError.RateLimited(30L), ex.apiError)
        assertEquals(30L, ex.retryAfterSec)
    }

    @Test
    fun `malformed body yields a clean error, never a crash`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("this is not json"))
        val res = dataSource.getBalanceAssets("0x1")
        assertTrue(res is ResultResponse.Error)
    }

    @Test
    fun `read timeout maps to an error and does not hang`() = runTest {
        val slowClient = OkHttpClient.Builder()
            .readTimeout(300, TimeUnit.MILLISECONDS)
            .build()
        val slowDs = ProxyChainDataSource(network, serviceFor(slowClient), fakeSigner, fakeTronSigner)
        server.enqueue(MockResponse().setBodyDelay(2, TimeUnit.SECONDS).setBody("""{"ok":true,"data":{}}"""))
        val res = slowDs.getBalanceAssets("0x1")
        assertTrue(res is ResultResponse.Error)
    }

    @Test
    fun `EVM send signs locally and broadcasts rawSignedTx`() = runTest {
        every { network.chainId } returns 11155111L
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"data":{"transaction":{"to":"0x000000000000000000000000000000000000dEaD","value":"0x3e8","data":"0x","nonce":"0x5","gasLimit":"0x5208","gasPrice":"0x3b9aca00","chainId":11155111,"type":0}}}"""
            )
        ) // prepare
        server.enqueue(MockResponse().setBody("""{"ok":true,"data":{"txHash":"0xabc123"}}""")) // broadcast

        val params = TransactionParams.Evm(
            networkName = NetworkName.SEPOLIA,
            to = "0x000000000000000000000000000000000000dEaD",
            amount = BigInteger("1000"),
            gasPrice = BigInteger("1000000000"),
            gasLimit = BigInteger("21000"),
            assetId = "ETH-SEPOLIA"
        )
        val res = dataSource.sendTransaction(params, TEST_PK)
        assertEquals("0xabc123", (res as ResultResponse.Success).data)

        assertTrue(server.takeRequest().path!!.endsWith("/transactions/prepare"))
        val broadcast = server.takeRequest()
        assertTrue(broadcast.path!!.endsWith("/transactions/broadcast"))
        // The locally-signed raw tx produced by the signer must be what we relay.
        assertTrue(broadcast.body.readUtf8().contains("0xdeadbeefsigned"))
    }

    @Test
    fun `EVM raw contract call uses prepare-contract-call instead of transfer prepare`() = runTest {
        every { network.chainId } returns 11155111L
        val approveData = "0x095ea7b30000000000000000000000000000000000000000000000000000000000000001"
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"data":{"transaction":{"to":"0x186cca6904490818AB0DC409ca59D932A2366031","value":"0x0","data":"$approveData","nonce":"0x5","gasLimit":"0x1d4c0","gasPrice":"0x3b9aca00","chainId":11155111,"type":0}}}"""
            )
        )
        server.enqueue(MockResponse().setBody("""{"ok":true,"data":{"txHash":"0xapprove"}}"""))

        val params = TransactionParams.Evm(
            networkName = NetworkName.SEPOLIA,
            to = "0x186cca6904490818AB0DC409ca59D932A2366031",
            amount = BigInteger.ZERO,
            data = approveData,
            gasPrice = BigInteger("1000000000"),
            gasLimit = BigInteger("120000"),
            assetId = "USDC-SEPOLIA"
        )
        val res = dataSource.sendTransaction(params, TEST_PK)
        assertEquals("0xapprove", (res as ResultResponse.Success).data)

        val prepare = server.takeRequest()
        assertTrue(prepare.path!!.endsWith("/transactions/prepare-contract-call"))
        val prepareBody = prepare.body.readUtf8()
        assertTrue(prepareBody.contains("0x095ea7b3"))
        assertTrue(prepareBody.contains("valueWei"))
        val broadcast = server.takeRequest()
        assertTrue(broadcast.path!!.endsWith("/transactions/broadcast"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `TVM send signs via tron signer and broadcasts the assembled tx`() = runTest {
        every { network.networkType } returns NetworkType.TVM
        // prepare → node-built unsigned tx with raw_data_hex
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"data":{"transaction":{"txID":"abc123","raw_data":{},"raw_data_hex":"0a02deadbeef","visible":false}}}"""
            )
        )
        server.enqueue(MockResponse().setBody("""{"ok":true,"data":{"txHash":"abc123"}}""")) // broadcast

        val params = TransactionParams.Tvm(
            networkName = NetworkName.TRON,
            toAddress = "TJRyWwFs9wTFGZg3JbrVriFbNfCug5tDeC",
            amount = BigInteger.TEN,
            assetId = "TRX-TRON"
        )
        val res = dataSource.sendTransaction(params, TEST_PK)
        assertEquals("abc123", (res as ResultResponse.Success).data)

        assertTrue(server.takeRequest().path!!.endsWith("/transactions/prepare"))
        val broadcast = server.takeRequest()
        assertTrue(broadcast.path!!.endsWith("/transactions/broadcast"))
        // The assembled signed tx (unsigned object + signature array) must be what we relay.
        assertTrue(broadcast.body.readUtf8().contains("deadbeefsig"))
    }

    @Test
    fun `TVM raw contract call uses prepare-contract-call instead of transfer prepare`() = runTest {
        every { network.networkType } returns NetworkType.TVM
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"data":{"transaction":{"txID":"approve123","raw_data":{},"raw_data_hex":"0a02deadbeef","visible":false}}}"""
            )
        )
        server.enqueue(MockResponse().setBody("""{"ok":true,"data":{"txHash":"approve123"}}"""))

        val params = TransactionParams.Tvm(
            networkName = NetworkName.TRON,
            toAddress = "TVjsyZ7fYF3qLF6BQgPmTEZy1xrNNyVAAA",
            amount = BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935"),
            contractAddress = "THHQqmx9XMj5N77a6SCr3dhgz6YJbArWzU",
            contractFunction = "approve(address,uint256)",
            feeLimit = 120_000_000L,
            assetId = "USDT-TRON"
        )
        val res = dataSource.sendTransaction(params, TEST_PK)
        assertEquals("approve123", (res as ResultResponse.Success).data)

        val prepare = server.takeRequest()
        assertTrue(prepare.path!!.endsWith("/transactions/prepare-contract-call"))
        val prepareBody = prepare.body.readUtf8()
        assertTrue(prepareBody.contains("approve(address,uint256)"))
        assertTrue(prepareBody.contains("feeLimitSun"))
        val broadcast = server.takeRequest()
        assertTrue(broadcast.path!!.endsWith("/transactions/broadcast"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `TVM send without assetId is a typed ValidationError`() = runTest {
        every { network.networkType } returns NetworkType.TVM
        val params = TransactionParams.Tvm(
            networkName = NetworkName.TRON,
            toAddress = "TJRyWwFs9wTFGZg3JbrVriFbNfCug5tDeC",
            amount = BigInteger.TEN
        )
        val res = dataSource.sendTransaction(params, TEST_PK)
        assertEquals(ApiError.ValidationError, err((res as ResultResponse.Error).exception).apiError)
    }

    @Test
    fun `UTXO send selects inputs from prepare, signs locally, and broadcasts raw hex`() = runTest {
        every { network.networkType } returns NetworkType.UTXO
        val ds = ProxyChainDataSource(
            network, serviceFor(OkHttpClient()), fakeSigner, fakeTronSigner, fakeUtxoBuilder
        )
        // prepare → spendable UTXO set + fee rate (no scriptPubKey / change / fee — client builds them)
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"data":{"model":"utxo","sender":"ltc1qsender","utxos":[{"txid":"9f8e","vout":0,"value":"250000","confirmed":true},{"txid":"1a2b","vout":1,"value":"500000","confirmed":true}],"outputs":[{"address":"ltc1qto","value":"100000"}],"feeRate":8}}"""
            )
        )
        server.enqueue(MockResponse().setBody("""{"ok":true,"data":{"txHash":"ltctxhash"}}""")) // broadcast

        val params = TransactionParams.Utxo(
            chainId = 2L,
            toAddress = "ltc1qto",
            amountInSatoshi = 100_000L,
            feeRateInSatsPerByte = 8L,
            assetId = "LTC-LITECOIN_MAINNET"
        )
        val res = ds.sendTransaction(params, TEST_PK)
        assertEquals("ltctxhash", (res as ResultResponse.Success).data)

        assertTrue(server.takeRequest().path!!.endsWith("/transactions/prepare"))
        val broadcast = server.takeRequest()
        assertTrue(broadcast.path!!.endsWith("/transactions/broadcast"))
        assertTrue(broadcast.body.readUtf8().contains("0200000signedutxohex"))
    }

    @Test
    fun `UTXO send without a builder is a typed UnsupportedOperation`() = runTest {
        every { network.networkType } returns NetworkType.UTXO
        // dataSource from setup() has no UTXO builder injected.
        val params = TransactionParams.Utxo(
            chainId = 2L,
            toAddress = "ltc1qto",
            amountInSatoshi = 100_000L,
            feeRateInSatsPerByte = 8L,
            assetId = "LTC-LITECOIN_MAINNET"
        )
        val res = dataSource.sendTransaction(params, TEST_PK)
        assertEquals(ApiError.UnsupportedOperation, err((res as ResultResponse.Error).exception).apiError)
    }

    @Test
    fun `history via proxy is a typed UnsupportedOperation (Phase 2)`() = runTest {
        val res = dataSource.getTransactionHistory("0x1")
        assertEquals(ApiError.UnsupportedOperation, err((res as ResultResponse.Error).exception).apiError)
    }

    @Test
    fun `multi-address balances isolate per-address failures`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":true,"data":{"native":{"symbol":"ETH","decimals":18,"balanceRaw":"1000000000000000000"}}}"""))
        server.enqueue(MockResponse().setResponseCode(502).setBody("""{"ok":false,"error":{"code":"UPSTREAM_UNAVAILABLE"}}"""))
        val res = dataSource.getBalancesForMultipleAddresses(listOf("0x1", "0x2"))
        val map = (res as ResultResponse.Success).data
        // TASK-10 — the per-address calls now run concurrently, so which address draws the success vs the
        // 502 from the FIFO mock queue is nondeterministic. Assert the isolation *property* order-agnostically:
        // exactly one address resolved (size 1) and one failed to empty, with no error propagated.
        assertEquals(2, map.size)
        assertEquals(listOf(0, 1), listOf(map["0x1"]!!.size, map["0x2"]!!.size).sorted())
    }

    private companion object {
        // Canonical web3j sample private key (valid secp256k1).
        const val TEST_PK = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318"
    }
}
