package com.mtd.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.mtd.data.di.NetworkModule
import com.mtd.data.dto.EvmSponsorApproveParamsDto
import com.mtd.data.dto.EvmSponsorApproveRequestDto
import com.mtd.data.dto.TronApproveQuoteRequestDto
import com.mtd.data.dto.TronSponsorApproveParamsDto
import com.mtd.data.dto.TronSponsorApproveRequestDto
import com.mtd.data.service.GaslessApiService
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class GaslessBackendSmokeTest {

    private lateinit var service: GaslessApiService
    private val host = NetworkModule.serverIp
    private val port = 3000

    @Before
    fun setUp() {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        service = Retrofit.Builder()
            .baseUrl("http://$host:$port/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .client(okHttp)
            .build()
            .create(GaslessApiService::class.java)
    }

    @Test
    fun evm_prepare_endpoint_smoke() {
        assumeTrue("Gasless backend is unreachable", isBackendReachable(host, port))

        val response = kotlinx.coroutines.runBlocking {
            service.prepareGasless(
                chain = "evm",
                userAddress = "0x17b51d4928668B50065C589bAfBC32736f196216"
            )
        }

        assertTrue("Unexpected HTTP code: ${response.code()}", response.code() in 200..499)
        if (response.isSuccessful) {
            assertNotNull(response.body()?.relayerContract)
            assertNotNull(response.body()?.chainId)
        }
    }

    @Test
    fun tron_prepare_endpoint_smoke() {
        assumeTrue("Gasless backend is unreachable", isBackendReachable(host, port))

        val response = kotlinx.coroutines.runBlocking {
            service.prepareGasless(
                chain = "tron",
                userAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp"
            )
        }

        assertTrue("Unexpected HTTP code: ${response.code()}", response.code() in 200..499)
        if (response.isSuccessful) {
            assertNotNull(response.body()?.relayerContract)
            assertNotNull(response.body()?.chainId)
        }
    }

    @Test
    fun tx_status_endpoint_smoke() {
        assumeTrue("Gasless backend is unreachable", isBackendReachable(host, port))

        val response = kotlinx.coroutines.runBlocking {
            service.getGaslessTxStatus(chain = "evm", txId = "queue-id-that-does-not-exist")
        }

        assertTrue("Unexpected HTTP code: ${response.code()}", response.code() in 200..499)
    }

    @Test
    fun tron_sponsor_approve_endpoint_smoke() {
        assumeTrue("Gasless backend is unreachable", isBackendReachable(host, port))

        val response = kotlinx.coroutines.runBlocking {
            service.sponsorTronApprove(
                TronSponsorApproveRequestDto(
                    chain = "TRON",
                    params = TronSponsorApproveParamsDto(
                        user = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
                        token = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
                    ),
                    mode = "gift"
                )
            )
        }

        assertTrue("Unexpected HTTP code: ${response.code()}", response.code() in 200..499)
    }

    @Test
    fun tron_quote_approve_endpoint_smoke() {
        assumeTrue("Gasless backend is unreachable", isBackendReachable(host, port))

        val response = kotlinx.coroutines.runBlocking {
            service.quoteTronApprove(
                chain = "tron",
                TronApproveQuoteRequestDto(
                    chain = "TRON",
                    params = TronSponsorApproveParamsDto(
                        user = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
                        token = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
                    )
                )
            )
        }

        assertTrue("Unexpected HTTP code: ${response.code()}", response.code() in 200..499)
    }

    @Test
    fun evm_sponsor_approve_endpoint_smoke() {
        assumeTrue("Gasless backend is unreachable", isBackendReachable(host, port))

        val response = kotlinx.coroutines.runBlocking {
            service.sponsorEvmApprove(
                EvmSponsorApproveRequestDto(
                    chain = "EVM",
                    params = EvmSponsorApproveParamsDto(
                        user = "0x17b51d4928668B50065C589bAfBC32736f196216",
                        token = "0x186cca6904490818AB0DC409ca59D932A2366031"
                    ),
                    mode = "gift"
                )
            )
        }

        assertTrue("Unexpected HTTP code: ${response.code()}", response.code() in 200..499)
    }

    private fun isBackendReachable(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1500)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
