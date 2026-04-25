package com.mtd.data.repository.gasless

import com.mtd.core.keymanager.KeyManager
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.TypedDataSigner
import com.mtd.domain.interfaceRepository.IGaslessEvmRepository
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.EvmGaslessSession
import com.mtd.domain.model.EvmGaslessTransferRequest
import com.mtd.domain.model.EvmSponsorApproveResult
import com.mtd.domain.model.EvmSponsorMode
import com.mtd.domain.model.GaslessCanonicalParams
import com.mtd.domain.model.GaslessChain
import com.mtd.domain.model.GaslessCoordinatorState
import com.mtd.domain.model.GaslessPrepareData
import com.mtd.domain.model.GaslessQueuedTx
import com.mtd.domain.model.GaslessQuoteData
import com.mtd.domain.model.GaslessTxStatus
import com.mtd.domain.model.ResultResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.web3j.crypto.Credentials
import java.math.BigInteger

class EvmGaslessCoordinatorTest {

    private lateinit var repository: IGaslessEvmRepository
    private lateinit var walletRepository: IWalletRepository
    private lateinit var keyManager: KeyManager
    private lateinit var blockchainRegistry: BlockchainRegistry
    private lateinit var coordinator: EvmGaslessCoordinator

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        walletRepository = mockk(relaxed = true)
        keyManager = mockk(relaxed = true)
        blockchainRegistry = mockk(relaxed = true)
        coordinator = EvmGaslessCoordinator(repository, walletRepository, keyManager, blockchainRegistry)
    }

    @After
    fun tearDown() {
        unmockkObject(TypedDataSigner)
    }

    @Test
    fun `prepareSession sets NEEDS_APPROVE when allowance is insufficient`() = runTest {
        every { blockchainRegistry.getNetworkById("sepolia") } returns evmNetwork()
        coEvery { walletRepository.getActiveAddressForNetwork("sepolia") } returns "0x17b51d4928668B50065C589bAfBC32736f196216"
        coEvery { repository.prepare(any(), any()) } returns ResultResponse.Success(
            GaslessPrepareData(
                userAddress = "0x17b51d4928668B50065C589bAfBC32736f196216",
                nonce = BigInteger.ONE,
                deadline = 1_900_000_000L,
                chainId = 11155111L,
                relayerContract = "0x1111111111111111111111111111111111111111",
                treasuryAddress = null,
                prepareToken = "prepare_token_1"
            )
        )
        coEvery { repository.getAllowance(any(), any(), any(), any()) } returns ResultResponse.Success(BigInteger.ZERO)
        coEvery { repository.getRelayerTreasury(any(), any()) } returns ResultResponse.Success("0x2222222222222222222222222222222222222222")

        val result = coordinator.prepareSession(
            EvmGaslessTransferRequest(
                networkId = "sepolia",
                tokenAddress = "0x186cca6904490818AB0DC409ca59D932A2366031",
                targetAddress = "0x000000000000000000000000000000000000dEaD",
                amount = BigInteger("100"),
                permit2Address = "0x000000000022D473030F116dDEE9F6B43aC78BA3"
            )
        )

        assertTrue(result is ResultResponse.Success)
        val session = (result as ResultResponse.Success).data
        assertTrue(session.needsApprove)
        assertEquals(GaslessCoordinatorState.NEEDS_APPROVE, coordinator.state.value)
    }

    @Test
    fun `prepareSession fails on chain mismatch`() = runTest {
        every { blockchainRegistry.getNetworkById("sepolia") } returns evmNetwork(chainId = 11155111L)
        coEvery { walletRepository.getActiveAddressForNetwork("sepolia") } returns "0x17b51d4928668B50065C589bAfBC32736f196216"
        coEvery { repository.prepare(any(), any()) } returns ResultResponse.Success(
            GaslessPrepareData(
                userAddress = "0x17b51d4928668B50065C589bAfBC32736f196216",
                nonce = BigInteger.ONE,
                deadline = 1_900_000_000L,
                chainId = 56L,
                relayerContract = "0x1111111111111111111111111111111111111111",
                treasuryAddress = "0x2222222222222222222222222222222222222222",
                prepareToken = "prepare_token_2"
            )
        )

        val result = coordinator.prepareSession(
            EvmGaslessTransferRequest(
                networkId = "sepolia",
                tokenAddress = "0x186cca6904490818AB0DC409ca59D932A2366031",
                targetAddress = "0x000000000000000000000000000000000000dEaD",
                amount = BigInteger("100"),
                permit2Address = "0x000000000022D473030F116dDEE9F6B43aC78BA3"
            )
        )

        assertTrue(result is ResultResponse.Error)
        assertEquals(GaslessCoordinatorState.FAILED, coordinator.state.value)
    }

    @Test
    fun `signAndSubmit builds relay payload and queues tx`() = runTest {
        assumeWeb3jCredentialsAvailable()
        val credentials = Credentials.create("4f3edf983ac636a65a842ce7c78d9aa706d3b113bce036f9e6f9446f06f2f8f4")
        every { keyManager.getCredentialsForChain(11155111L) } returns credentials

        mockkObject(TypedDataSigner)
        every {
            TypedDataSigner.signTypedDataHex(
                credentials = any(),
                primaryType = any(),
                types = any(),
                domain = any(),
                message = any()
            )
        } returnsMany listOf("0xpermitSig", "0xmegaSig")

        val payloadSlot = slot<com.mtd.domain.model.EvmRelayPayload>()
        coEvery {
            repository.quote(any())
        } returns ResultResponse.Success(
            GaslessQuoteData(
                quoteToken = "quote_token_1",
                canonicalParams = GaslessCanonicalParams(
                    user = "0x17b51d4928668B50065C589bAfBC32736f196216",
                    token = "0x186cca6904490818AB0DC409ca59D932A2366031",
                    target = "0x000000000000000000000000000000000000dEaD",
                    amount = BigInteger("100"),
                    feeAmount = BigInteger("1"),
                    nonce = BigInteger("7"),
                    deadline = 1_900_000_000L,
                    treasury = "0x2222222222222222222222222222222222222222"
                )
            )
        )

        coEvery { repository.submitRelay(capture(payloadSlot), any()) } returns ResultResponse.Success(
            GaslessQueuedTx(id = "queue_1", stage = "QUEUED")
        )

        val session = EvmGaslessSession(
            request = EvmGaslessTransferRequest(
                networkId = "sepolia",
                tokenAddress = "0x186cca6904490818AB0DC409ca59D932A2366031",
                targetAddress = "0x000000000000000000000000000000000000dEaD",
                amount = BigInteger("100"),
                permit2Address = "0x000000000022D473030F116dDEE9F6B43aC78BA3",
                feeAmount = BigInteger("1"),
                deadlineEpochSeconds = 1_900_000_000L
            ),
            networkName = NetworkName.SEPOLIA,
            userAddress = "0x17b51d4928668B50065C589bAfBC32736f196216",
            chainId = 11155111L,
            relayerContract = "0x1111111111111111111111111111111111111111",
            treasuryAddress = "0x2222222222222222222222222222222222222222",
            nonce = BigInteger.ONE,
            allowance = BigInteger("1000"),
            prepareToken = "prepare_token_1",
            idempotencyKey = "idem_1"
        )

        val result = coordinator.signAndSubmit(session)

        assertTrue(result is ResultResponse.Success)
        assertEquals(GaslessCoordinatorState.QUEUED, coordinator.state.value)
        assertEquals("0xpermitSig", payloadSlot.captured.permitSignature)
        assertEquals("0xmegaSig", payloadSlot.captured.megaSignature)
        assertEquals(GaslessChain.EVM, payloadSlot.captured.chain)
    }

    @Test
    fun `pollUntilFinal reaches SUCCESS after queued status`() = runTest {
        coEvery { repository.getTxStatus("queue_1") } returnsMany listOf(
            ResultResponse.Success(GaslessTxStatus("queue_1", "QUEUED", null, null)),
            ResultResponse.Success(GaslessTxStatus("queue_1", "SUCCESS", "0xabc", null))
        )

        val result = coordinator.pollUntilFinal(
            txId = "queue_1",
            pollIntervalMs = 1L,
            timeoutMs = 50L
        )

        assertTrue(result is ResultResponse.Success)
        assertEquals("SUCCESS", (result as ResultResponse.Success).data.status)
        assertEquals(GaslessCoordinatorState.SUCCESS, coordinator.state.value)
        coVerify(atLeast = 2) { repository.getTxStatus("queue_1") }
    }

    @Test
    fun `requestSponsorForApprove moves to AWAITING_APPROVE_CONFIRMATION when funded`() = runTest {
        val session = EvmGaslessSession(
            request = EvmGaslessTransferRequest(
                networkId = "sepolia",
                tokenAddress = "0x186cca6904490818AB0DC409ca59D932A2366031",
                targetAddress = "0x000000000000000000000000000000000000dEaD",
                amount = BigInteger("100"),
                permit2Address = "0x000000000022D473030F116dDEE9F6B43aC78BA3"
            ),
            networkName = NetworkName.SEPOLIA,
            userAddress = "0x17b51d4928668B50065C589bAfBC32736f196216",
            chainId = 11155111L,
            relayerContract = "0x1111111111111111111111111111111111111111",
            treasuryAddress = "0x2222222222222222222222222222222222222222",
            nonce = BigInteger.ONE,
            allowance = BigInteger.ZERO,
            prepareToken = "prepare_token_1",
            idempotencyKey = "idem_1"
        )

        coEvery { repository.sponsorApprove(any()) } returns ResultResponse.Success(
            EvmSponsorApproveResult(
                funded = true,
                mode = EvmSponsorMode.GIFT,
                amount = BigInteger("210000000000000"),
                reason = null,
                txHash = "0xfundhash"
            )
        )

        val result = coordinator.requestSponsorForApprove(session, EvmSponsorMode.GIFT)

        assertTrue(result is ResultResponse.Success)
        assertTrue((result as ResultResponse.Success).data.funded)
        assertEquals(GaslessCoordinatorState.AWAITING_APPROVE_CONFIRMATION, coordinator.state.value)
    }

    @Test
    fun `requestSponsorForApprove keeps NEEDS_APPROVE when sponsor denied`() = runTest {
        val session = EvmGaslessSession(
            request = EvmGaslessTransferRequest(
                networkId = "sepolia",
                tokenAddress = "0x186cca6904490818AB0DC409ca59D932A2366031",
                targetAddress = "0x000000000000000000000000000000000000dEaD",
                amount = BigInteger("100"),
                permit2Address = "0x000000000022D473030F116dDEE9F6B43aC78BA3"
            ),
            networkName = NetworkName.SEPOLIA,
            userAddress = "0x17b51d4928668B50065C589bAfBC32736f196216",
            chainId = 11155111L,
            relayerContract = "0x1111111111111111111111111111111111111111",
            treasuryAddress = "0x2222222222222222222222222222222222222222",
            nonce = BigInteger.ONE,
            allowance = BigInteger.ZERO,
            prepareToken = "prepare_token_1",
            idempotencyKey = "idem_1"
        )

        coEvery { repository.sponsorApprove(any()) } returns ResultResponse.Success(
            EvmSponsorApproveResult(
                funded = false,
                mode = EvmSponsorMode.DEBT,
                amount = null,
                reason = "NOT_ELIGIBLE",
                txHash = null
            )
        )

        val result = coordinator.requestSponsorForApprove(session, EvmSponsorMode.DEBT)

        assertTrue(result is ResultResponse.Success)
        assertFalse((result as ResultResponse.Success).data.funded)
        assertEquals(GaslessCoordinatorState.NEEDS_APPROVE, coordinator.state.value)
    }

    private fun evmNetwork(chainId: Long = 11155111L): BlockchainNetwork {
        val network = mockk<BlockchainNetwork>(relaxed = true)
        every { network.id } returns "sepolia"
        every { network.networkType } returns NetworkType.EVM
        every { network.name } returns NetworkName.SEPOLIA
        every { network.chainId } returns chainId
        return network
    }

    private fun assumeWeb3jCredentialsAvailable() {
        val supported = runCatching {
            Class.forName("org.web3j.crypto.Credentials")
            true
        }.getOrElse { false }
        assumeTrue("Skipping: org.web3j.crypto.Credentials requires newer JVM runtime", supported)
    }
}
