package com.mtd.data.datasource

import com.google.gson.GsonBuilder
import com.mtd.core.network.BlockchainNetwork
import com.mtd.data.network.wire.BigIntegerStringAdapter
import com.mtd.data.service.MobileProxyApiService
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.UtxoInput
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigInteger

/**
 * Regression for the PROXY-mode UTXO "zero-fee" bug: on low-fee testnets the `/prepare` fee rate is
 * fractional (e.g. 0.5 sat/vByte). The send path used a naive `feeRate.toLong()`, truncating 0.x → 0,
 * so the signed tx carried NO miner fee — the wallet spent the whole balance into recipient + change
 * and the relayer rejected it ("amount + change = inputs, fee = 0").
 *
 * The fee PREVIEW path (getFeeOptions) already rounded up + floored at 1; the SEND path did not.
 * This test feeds a fractional `feeRate` through the real sendUtxo path and asserts the rate handed to
 * the builder is rounded UP to a relayable integer (never 0), keeping preview and send in agreement.
 */
class ProxyUtxoFeeRateTruncationTest {

    private lateinit var server: MockWebServer
    private val network = mockk<BlockchainNetwork>(relaxed = true)

    /** Captures the integer sat/vByte the data source actually hands to the bitcoinj builder. */
    private class CapturingUtxoTxBuilder : UtxoTxBuilder {
        var capturedRate: Long? = null
        override fun deriveAddress(privateKeyHex: String): String =
            "tb1qdn0ckpkst4806cxpjxkhlhvjgaw53lmlmy4q2r"

        override fun buildSignedTx(
            privateKeyHex: String,
            recipient: String,
            amountSat: Long,
            feeRateSatPerVByte: Long,
            utxos: List<UtxoInput>
        ): String {
            capturedRate = feeRateSatPerVByte
            return "0200000000deadbeef" // dummy signed-tx hex; broadcast is mocked
        }
    }

    private val builder = CapturingUtxoTxBuilder()
    private lateinit var dataSource: ProxyChainDataSource

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
        server = MockWebServer().apply { start() }
        every { network.id } returns "bitcoin_testnet"
        every { network.name } returns NetworkName.BITCOINTESTNET
        every { network.decimals } returns 8
        every { network.networkType } returns NetworkType.BITCOIN
        dataSource = ProxyChainDataSource(network, serviceFor(OkHttpClient()), utxoTxBuilder = builder)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun params() = TransactionParams.Utxo(
        chainId = 1L,
        toAddress = "tb1qrecipient0000000000000000000000000000",
        amountInSatoshi = 15_000L,
        feeRateInSatsPerByte = 5L, // caller's chosen rate (used only if /prepare omits feeRate)
        assetId = "BTC-BITCOIN_TESTNET"
    )

    @Test
    fun `fractional prepare feeRate is rounded up, never truncated to zero`() = runTest {
        // /prepare → a single 25_000-sat UTXO and a fractional testnet rate of 0.5 sat/vByte.
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"networkId":"bitcoin_testnet","data":{
                   "model":"utxo",
                   "feeRate":0.5,
                   "utxos":[{"txid":"aa11","vout":0,"value":"25000","confirmed":true}]
                }}"""
            )
        )
        // broadcast → accepted
        server.enqueue(MockResponse().setBody("""{"ok":true,"data":{"txHash":"0xbtc"}}"""))

        val res = dataSource.sendTransaction(params(), TEST_PK)

        assertTrue("send should succeed, got $res", res is ResultResponse.Success)
        assertEquals("0xbtc", (res as ResultResponse.Success).data)
        // The bug produced 0 here (0.5.toLong()); the fix rounds 0.5 UP to 1 (relayable floor).
        assertEquals(
            "FEE-RATE TRUNCATION: builder must receive a rounded-up integer rate, never 0. " +
                "A 0 rate yields a zero-fee tx the relayer rejects.",
            1L, builder.capturedRate
        )
    }

    @Test
    fun `whole-number prepare feeRate passes through unchanged`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"data":{"model":"utxo","feeRate":12.0,
                   "utxos":[{"txid":"aa11","vout":0,"value":"25000","confirmed":true}]}}"""
            )
        )
        server.enqueue(MockResponse().setBody("""{"ok":true,"data":{"txHash":"0xbtc"}}"""))

        dataSource.sendTransaction(params(), TEST_PK)
        assertEquals(12L, builder.capturedRate)
    }

    @Test
    fun `missing prepare feeRate falls back to the caller rate, still never zero`() = runTest {
        // No `feeRate` in /prepare → fall back to params.feeRateInSatsPerByte (5).
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"data":{"model":"utxo",
                   "utxos":[{"txid":"aa11","vout":0,"value":"25000","confirmed":true}]}}"""
            )
        )
        server.enqueue(MockResponse().setBody("""{"ok":true,"data":{"txHash":"0xbtc"}}"""))

        dataSource.sendTransaction(params(), TEST_PK)
        assertEquals(5L, builder.capturedRate)
    }

    private companion object {
        const val TEST_PK = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318"
    }
}
