package com.mtd.data.repository.gasless

import com.mtd.data.dto.EvmSponsorApproveRequestDto
import com.mtd.data.dto.EvmSponsorApproveResponseDto
import com.mtd.data.dto.EvmApproveQuoteRequestDto
import com.mtd.data.dto.EvmApproveQuoteResponseDto
import com.mtd.data.dto.EvmApproveTxTemplateDto
import com.mtd.data.dto.GaslessEligibilityRequestDto
import com.mtd.data.dto.GaslessEligibilityResponseDto
import com.mtd.data.dto.GaslessSupportedTokenDto
import com.mtd.data.dto.TronSponsorApproveRequestDto
import com.mtd.data.dto.TronSponsorApproveResponseDto
import com.mtd.data.service.GaslessApiService
import com.mtd.domain.model.EvmApproveQuoteRequest
import com.mtd.domain.model.EvmSponsorApproveRequest
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.GaslessServiceType
import com.mtd.domain.model.TronSponsorApproveRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * Phase 2 (API transport abstraction) — verifies gasless API routing derives from the
 * data-driven relayPrefix (path segment) and that the request-body `chain` equals the
 * prefix UPPERCASED, while the default (`relayPrefix = chain.apiPath`) keeps current
 * GaslessChain callers byte-identical (EVM→/api/evm, TRON→/api/tron).
 */
class GaslessApiGatewayTest {

    private lateinit var service: GaslessApiService
    private lateinit var gateway: GaslessApiGateway

    @Before
    fun setUp() {
        service = mockk()
        gateway = GaslessApiGateway(service)
    }

    // --- path routing -------------------------------------------------------
    @Test
    fun `tokens default prefix routes to api evm`() = runTest {
        coEvery { service.getSupportedTokens(any()) } returns Response.success(emptyList<GaslessSupportedTokenDto>())
        gateway.getSupportedTokens(NetworkType.EVM, "evm")
        coVerify { service.getSupportedTokens("evm") }
    }

    @Test
    fun `tokens explicit bsc prefix routes to api bsc (data-driven)`() = runTest {
        coEvery { service.getSupportedTokens(any()) } returns Response.success(emptyList<GaslessSupportedTokenDto>())
        gateway.getSupportedTokens(NetworkType.EVM, "bsc")
        coVerify { service.getSupportedTokens("bsc") }
    }

    @Test
    fun `tokens tron prefix routes to api tron`() = runTest {
        coEvery { service.getSupportedTokens(any()) } returns Response.success(emptyList<GaslessSupportedTokenDto>())
        gateway.getSupportedTokens(NetworkType.TVM, "tron")
        coVerify { service.getSupportedTokens("tron") }
    }

    // --- path + body chain --------------------------------------------------
    @Test
    fun `eligibility bsc routes path bsc and body chain BSC`() = runTest {
        val req = slot<GaslessEligibilityRequestDto>()
        coEvery { service.checkEligibility(any(), capture(req)) } returns Response.success(mockk<GaslessEligibilityResponseDto>(relaxed = true))
        gateway.checkEligibility(NetworkType.EVM, GaslessServiceType.GASLESS, "0xUser", "0xTok", "bsc")
        coVerify { service.checkEligibility("bsc", any()) }
        assertEquals("BSC", req.captured.chain)
    }

    @Test
    fun `eligibility default keeps body chain EVM`() = runTest {
        val req = slot<GaslessEligibilityRequestDto>()
        coEvery { service.checkEligibility(any(), capture(req)) } returns Response.success(mockk<GaslessEligibilityResponseDto>(relaxed = true))
        gateway.checkEligibility(NetworkType.EVM, GaslessServiceType.GASLESS, "0xUser", "0xTok", "evm")
        coVerify { service.checkEligibility("evm", any()) }
        assertEquals("EVM", req.captured.chain)
    }

    // --- sponsor-approve: previously HARDCODED api/evm + api/tron paths ------
    @Test
    fun `sponsor evm default routes path evm and body chain EVM`() = runTest {
        val req = slot<EvmSponsorApproveRequestDto>()
        coEvery { service.sponsorEvmApprove(any(), capture(req)) } returns Response.success(mockk<EvmSponsorApproveResponseDto>(relaxed = true))
        gateway.sponsorEvmApprove(EvmSponsorApproveRequest("0xUser", "0xTok"), "evm")
        coVerify { service.sponsorEvmApprove("evm", any()) }
        assertEquals("EVM", req.captured.chain)
    }

    @Test
    fun `sponsor evm explicit bsc routes path bsc and body chain BSC`() = runTest {
        val req = slot<EvmSponsorApproveRequestDto>()
        coEvery { service.sponsorEvmApprove(any(), capture(req)) } returns Response.success(mockk<EvmSponsorApproveResponseDto>(relaxed = true))
        gateway.sponsorEvmApprove(EvmSponsorApproveRequest("0xUser", "0xTok"), relayPrefix = "bsc")
        coVerify { service.sponsorEvmApprove("bsc", any()) }
        assertEquals("BSC", req.captured.chain)
    }

    @Test
    fun `quote evm approve maps unlimited template amount and routes by relay prefix`() = runTest {
        val maxUint256 = "115792089237316195423570985008687907853269984665640564039457584007913129639935"
        val req = slot<EvmApproveQuoteRequestDto>()
        coEvery { service.quoteEvmApprove(any(), capture(req)) } returns Response.success(
            EvmApproveQuoteResponseDto(
                chain = "BSC",
                approveRequired = true,
                approvalAmount = null,
                approvalAmountMode = null,
                approveTxTemplate = EvmApproveTxTemplateDto(
                    to = "0xTok",
                    spender = "0xSpender",
                    data = "0x095ea7b3",
                    approvalAmount = maxUint256,
                    approvalAmountMode = "unlimited",
                    gasLimit = "120000",
                    gasPriceWei = "1000000000",
                    maxFeePerGasWei = null,
                    maxPriorityFeePerGasWei = null,
                    valueWei = "0"
                ),
                requiredAllowance = maxUint256,
                estimatedApproveGasLimit = "120000",
                gasPriceWei = "1000000000",
                maxFeePerGasWei = null,
                maxPriorityFeePerGasWei = null,
                requiredApproveWei = "90000000000000",
                requiredWithBufferWei = "108000000000000",
                source = "estimate",
                displayPolicy = null
            )
        )

        val result = gateway.quoteEvmApprove(EvmApproveQuoteRequest("0xUser", "0xTok"), relayPrefix = "bsc")
        val data = (result as com.mtd.domain.model.ResultResponse.Success).data

        coVerify { service.quoteEvmApprove("bsc", any()) }
        assertEquals("BSC", req.captured.chain)
        assertEquals(maxUint256.toBigInteger(), data.approvalAmount)
        assertEquals("unlimited", data.approvalAmountMode)
        assertEquals(maxUint256.toBigInteger(), data.approveTxTemplate!!.approvalAmount)
    }

    @Test
    fun `sponsor tron default routes path tron and body chain TRON`() = runTest {
        val req = slot<TronSponsorApproveRequestDto>()
        coEvery { service.sponsorTronApprove(any(), capture(req)) } returns Response.success(mockk<TronSponsorApproveResponseDto>(relaxed = true))
        gateway.sponsorTronApprove(TronSponsorApproveRequest("Tuser", "Ttok"), "tron")
        coVerify { service.sponsorTronApprove("tron", any()) }
        assertEquals("TRON", req.captured.chain)
    }
}
