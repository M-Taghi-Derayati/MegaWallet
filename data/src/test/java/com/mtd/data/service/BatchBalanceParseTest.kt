package com.mtd.data.service

import com.google.gson.GsonBuilder
import com.mtd.data.dto.BatchBalanceRequestDto
import com.mtd.data.dto.BatchBalanceWalletDto
import com.mtd.data.network.wire.BigIntegerStringAdapter
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigInteger

class BatchBalanceParseTest {

    private lateinit var server: MockWebServer
    private lateinit var api: MobileProxyApiService

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        val gson = GsonBuilder()
            .registerTypeAdapter(BigInteger::class.java, BigIntegerStringAdapter())
            .create()
        api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build().create(MobileProxyApiService::class.java)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `partial failure - one network ok, one error`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":true,"data":{
            "evm":{"status":"ok","balances":[{"symbol":"ETH","decimals":18,"balanceRaw":"1500000000000000000"}]},
            "tron":{"status":"error","error":{"code":"UPSTREAM_UNAVAILABLE","message":"x"}}
        }}"""))

        val resp = api.batchBalances(
            BatchBalanceRequestDto(
                listOf(
                    BatchBalanceWalletDto("evm", "0x1"),
                    BatchBalanceWalletDto("tron", "T1")
                )
            )
        )
        val data = resp.body()!!.data!!

        assertEquals("ok", data["evm"]!!.status)
        assertEquals(BigInteger("1500000000000000000"), data["evm"]!!.balances!![0].balanceRaw)
        assertEquals("error", data["tron"]!!.status)
        assertEquals("UPSTREAM_UNAVAILABLE", data["tron"]!!.error!!.code)
    }
}
