package com.mtd.data.datasource

import com.google.gson.GsonBuilder
import com.mtd.core.network.BlockchainNetwork
import com.mtd.data.dto.HistoryAddressDto
import com.mtd.data.dto.HistoryItemDto
import com.mtd.data.dto.HistoryItemDtoDeserializer
import com.mtd.data.network.wire.BigIntegerStringAdapter
import com.mtd.data.service.MobileProxyApiService
import com.mtd.domain.model.BitcoinTransaction
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.TronTransaction
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

class ProxyChainDataSourceHistoryTest {

    private lateinit var server: MockWebServer
    private lateinit var ds: ProxyChainDataSource

    private val noSigner = object : EvmTxSigner {
        override fun deriveAddress(privateKeyHex: String) = ""
        override fun signRawTransaction(
            privateKeyHex: String, params: TransactionParams.Evm, nonce: BigInteger, chainId: Long
        ) = ""
    }

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        // Use the REAL production Gson so this test fails if the deserializer isn't registered
        // in NetworkModule (guards against the false-confidence gap).
        val gson = com.mtd.data.di.NetworkModule.provideGson()
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build().create(MobileProxyApiService::class.java)
        ds = ProxyChainDataSource(mockk<BlockchainNetwork>(relaxed = true), api, noSigner)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `mixed history maps to types, token amount, precision, cursor and staleSources`() = runTest {
        val bigEvm = BigInteger.valueOf(Long.MAX_VALUE) + BigInteger.ONE // > Long.MAX (native value)
        server.enqueue(MockResponse().setBody("""{"items":[
          {"type":"evm","hash":"0x1","network":"ETHEREUM","status":"CONFIRMED","timestamp":10,"isOutgoing":true,"valueRaw":"$bigEvm","feeRaw":"21000","gasPriceRaw":"1000000000","nonce":7},
          {"type":"tron","hash":"t1","network":"TRON","status":"CONFIRMED","timestamp":9,"isOutgoing":false,"valueRaw":"0","feeRaw":"27000000","energyUsed":64895,"tokenSymbol":"USDT","tokenDecimals":6,"tokenAmountRaw":"75000000","contractAddress":"TR7"},
          {"type":"bitcoin","hash":"b1","network":"BITCOIN","status":"PENDING","timestamp":0,"isOutgoing":true,"valueRaw":"4500000","feeRaw":"3500","feeRateSatPerVByte":12}
        ],"pageInfo":{"nextCursor":"CURSOR_2","hasMore":true},"staleSources":[{"networkId":"tron","address":"t1","error":"timeout"}]}"""))

        val res = ds.getHistory(listOf(HistoryAddressDto("evm", "0x1")), cursor = null, limit = null)
        assertTrue(res is ResultResponse.Success)
        val page = (res as ResultResponse.Success).data

        assertEquals(3, page.items.size)
        val evm = page.items[0]
        val tron = page.items[1]
        val btc = page.items[2]
        assertTrue(evm is EvmTransaction)
        assertTrue(tron is TronTransaction)
        assertTrue(btc is BitcoinTransaction)
        assertEquals(bigEvm, evm.amount)                                  // native value, > Long.MAX
        assertEquals(BigInteger("1000000000"), (evm as EvmTransaction).gasPrice)
        // token transfer: amount uses the TOKEN amount, not native valueRaw (which is 0 here)
        assertEquals(BigInteger("75000000"), tron.amount)
        assertEquals("USDT", (tron as TronTransaction).tokenTransferDetails?.tokenSymbol)
        assertEquals(BigInteger("75000000"), tron.tokenTransferDetails?.amount)
        assertEquals(12L, (btc as BitcoinTransaction).feeRateSatsPerByte)
        assertEquals("CURSOR_2", page.nextCursor)
        assertTrue(page.hasMore)
        assertEquals(listOf("tron:t1"), page.staleSources)
    }
}
