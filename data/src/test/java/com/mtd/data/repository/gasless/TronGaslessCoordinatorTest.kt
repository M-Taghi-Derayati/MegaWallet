package com.mtd.data.repository.gasless

import com.mtd.core.keymanager.KeyManager
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.TypedDataSigner
import com.mtd.domain.interfaceRepository.IGaslessTronRepository
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.GaslessCanonicalParams
import com.mtd.domain.model.GaslessChain
import com.mtd.domain.model.GaslessCoordinatorState
import com.mtd.domain.model.GaslessPrepareData
import com.mtd.domain.model.GaslessQueuedTx
import com.mtd.domain.model.GaslessQuoteData
import com.mtd.domain.model.GaslessRelayPayload
import com.mtd.domain.model.GaslessTxStatus
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TronApproveQuoteResult
import com.mtd.domain.model.TronGaslessSession
import com.mtd.domain.model.TronGaslessTransferRequest
import com.mtd.domain.model.TronSponsorApproveResult
import com.mtd.domain.model.TronSponsorMode
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

class TronGaslessCoordinatorTest {

    private lateinit var repository: IGaslessTronRepository
    private lateinit var walletRepository: IWalletRepository
    private lateinit var keyManager: KeyManager
    private lateinit var blockchainRegistry: BlockchainRegistry
    private lateinit var coordinator: TronGaslessCoordinator

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        walletRepository = mockk(relaxed = true)
        keyManager = mockk(relaxed = true)
        blockchainRegistry = mockk(relaxed = true)
        coordinator = TronGaslessCoordinator(repository, walletRepository, keyManager, blockchainRegistry)
    }

    @After
    fun tearDown() {
        unmockkObject(TypedDataSigner)
    }

    @Test
    fun `prepareSession sets NEEDS_APPROVE for low allowance`() = runTest {
        every { blockchainRegistry.getNetworkById("shasta_testnet") } returns tronNetwork()
        coEvery { walletRepository.getActiveAddressForNetwork("shasta_testnet") } returns "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp"
        coEvery { repository.prepare(any(), any()) } returns ResultResponse.Success(
            GaslessPrepareData(
                userAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
                nonce = BigInteger.ONE,
                deadline = 1_900_000_000L,
                chainId = 2494104990L,
                relayerContract = "TVjsyZ7fYF3qLF6BQgPmTEZy1xrNNyVAAA",
                treasuryAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
                prepareToken = "prepare_token_tron_1"
            )
        )
        coEvery { repository.getAllowance(any(), any(), any(), any()) } returns ResultResponse.Success(BigInteger.ZERO)

        val result = coordinator.prepareSession(
            TronGaslessTransferRequest(
                networkId = "shasta_testnet",
                tokenAddress = "THHQqmx9XMj5N77a6SCr3dhgz6YJbArWzU",
                targetAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
                amount = BigInteger("100"),
                feeAmount = BigInteger("1")
            )
        )

        assertTrue(result is ResultResponse.Success)
        val session = (result as ResultResponse.Success).data
        assertTrue(session.needsApprove)
        assertEquals(GaslessCoordinatorState.NEEDS_APPROVE, coordinator.state.value)
    }

    @Test
    fun `buildApproveTransaction creates TRON approve function payload`() {
        val session = TronGaslessSession(
            request = TronGaslessTransferRequest(
                networkId = "shasta_testnet",
                tokenAddress = "THHQqmx9XMj5N77a6SCr3dhgz6YJbArWzU",
                targetAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
                amount = BigInteger("100")
            ),
            networkName = NetworkName.SHASTA,
            userAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
            chainId = 2494104990L,
            relayerContract = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
            treasuryAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
            nonce = BigInteger.ONE,
            allowance = BigInteger.ZERO,
            prepareToken = "prepare_token_tron_1",
            idempotencyKey = "idem_tron_1"
        )

        val tx = coordinator.buildApproveTransaction(
            session = session,
            feeLimit = 20_000_000L,
            approveAmount = BigInteger("250")
        )

        assertEquals(NetworkName.SHASTA, tx.networkName)
        assertEquals("approve(address,uint256)", tx.contractFunction)
        assertTrue(!tx.contractParameter.isNullOrBlank())
        assertEquals(BigInteger("250"), tx.amount)
        assertEquals(GaslessCoordinatorState.AWAITING_APPROVE_CONFIRMATION, coordinator.state.value)
    }

    @Test
    fun `signAndSubmit builds signed relay payload and queues tx`() = runTest {
        assumeWeb3jCredentialsAvailable()
        val credentials = Credentials.create("4f3edf983ac636a65a842ce7c78d9aa706d3b113bce036f9e6f9446f06f2f8f4")
        every { keyManager.getCredentialsForChain(2494104990L) } returns credentials

        mockkObject(TypedDataSigner)
        every {
            TypedDataSigner.signTypedDataHex(
                credentials = any(),
                primaryType = any(),
                types = any(),
                domain = any(),
                message = any()
            )
        } returns "0xtronSig"

        val payloadSlot = slot<GaslessRelayPayload>()
        coEvery {
            repository.quote(any())
        } returns ResultResponse.Success(
            GaslessQuoteData(
                quoteToken = "quote_token_tron_1",
                canonicalParams = GaslessCanonicalParams(
                    user = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
                    token = "THHQqmx9XMj5N77a6SCr3dhgz6YJbArWzU",
                    target = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
                    amount = BigInteger("100"),
                    feeAmount = BigInteger("1"),
                    nonce = BigInteger("9"),
                    deadline = 1_900_000_000L,
                    treasury = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp"
                )
            )
        )
        coEvery { repository.submitRelay(capture(payloadSlot), any()) } returns ResultResponse.Success(
            GaslessQueuedTx(id = "queue_tron_1", stage = "QUEUED")
        )

        val session = TronGaslessSession(
            request = TronGaslessTransferRequest(
                networkId = "shasta_testnet",
                tokenAddress = "THHQqmx9XMj5N77a6SCr3dhgz6YJbArWzU",
                targetAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
                amount = BigInteger("100"),
                feeAmount = BigInteger("1"),
                deadlineEpochSeconds = 1_900_000_000L
            ),
            networkName = NetworkName.SHASTA,
            userAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
            chainId = 2494104990L,
            relayerContract = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
            treasuryAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
            nonce = BigInteger.ONE,
            allowance = BigInteger("1000"),
            prepareToken = "prepare_token_tron_1",
            idempotencyKey = "idem_tron_1"
        )

        val result = coordinator.signAndSubmit(session)

        assertTrue(result is ResultResponse.Success)
        assertEquals("0xtronSig", payloadSlot.captured.signature)
        assertEquals(GaslessChain.TRON, payloadSlot.captured.chain)
        assertEquals(GaslessCoordinatorState.QUEUED, coordinator.state.value)
    }

    @Test
    fun `pollUntilFinal returns FAILED status when backend marks tx failed`() = runTest {
        coEvery { repository.getTxStatus("queue_tron_1") } returns ResultResponse.Success(
            GaslessTxStatus(
                id = "queue_tron_1",
                status = "FAILED",
                txHash = null,
                lastError = "relay rejected"
            )
        )

        val result = coordinator.pollUntilFinal(
            txId = "queue_tron_1",
            pollIntervalMs = 1L,
            timeoutMs = 20L
        )

        assertTrue(result is ResultResponse.Success)
        assertEquals("FAILED", (result as ResultResponse.Success).data.status)
        assertEquals(GaslessCoordinatorState.FAILED, coordinator.state.value)
        coVerify(exactly = 1) { repository.getTxStatus("queue_tron_1") }
    }

    @Test
    fun `requestSponsorForApprove moves to AWAITING_APPROVE_CONFIRMATION when funded`() = runTest {
        val session = TronGaslessSession(
            request = TronGaslessTransferRequest(
                networkId = "shasta_testnet",
                tokenAddress = "THHQqmx9XMj5N77a6SCr3dhgz6YJbArWzU",
                targetAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
                amount = BigInteger("100")
            ),
            networkName = NetworkName.SHASTA,
            userAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
            chainId = 2494104990L,
            relayerContract = "TVjsyZ7fYF3qLF6BQgPmTEZy1xrNNyVAAA",
            treasuryAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
            nonce = BigInteger.ONE,
            allowance = BigInteger.ZERO,
            prepareToken = "prepare_token_tron_1",
            idempotencyKey = "idem_tron_1"
        )

        coEvery { repository.sponsorApprove(any()) } returns ResultResponse.Success(
            TronSponsorApproveResult(
                funded = true,
                mode = TronSponsorMode.GIFT,
                amount = BigInteger("4000000"),
                reason = null,
                txHash = "fund_tx_hash"
            )
        )
        coEvery { repository.quoteApprove(any()) } returns ResultResponse.Success(
            TronApproveQuoteResult(
                estimatedEnergy = BigInteger("65000"),
                estimatedBandwidthBytes = BigInteger("320"),
                energyFeeSun = BigInteger("210"),
                bandwidthFeeSun = BigInteger("1000"),
                requiredSun = BigInteger("13650000"),
                requiredTrx = "13.65",
                requiredUsdApprox = 3.85,
                source = "network-estimate"
            )
        )

        val result = coordinator.requestSponsorForApprove(session, TronSponsorMode.GIFT)

        assertTrue(result is ResultResponse.Success)
        assertTrue((result as ResultResponse.Success).data.funded)
        assertEquals(GaslessCoordinatorState.AWAITING_APPROVE_CONFIRMATION, coordinator.state.value)
        coVerify(exactly = 1) { repository.quoteApprove(any()) }
    }

    @Test
    fun `requestSponsorForApprove keeps NEEDS_APPROVE when sponsor denied`() = runTest {
        val session = TronGaslessSession(
            request = TronGaslessTransferRequest(
                networkId = "shasta_testnet",
                tokenAddress = "THHQqmx9XMj5N77a6SCr3dhgz6YJbArWzU",
                targetAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
                amount = BigInteger("100")
            ),
            networkName = NetworkName.SHASTA,
            userAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
            chainId = 2494104990L,
            relayerContract = "TVjsyZ7fYF3qLF6BQgPmTEZy1xrNNyVAAA",
            treasuryAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
            nonce = BigInteger.ONE,
            allowance = BigInteger.ZERO,
            prepareToken = "prepare_token_tron_1",
            idempotencyKey = "idem_tron_1"
        )

        coEvery { repository.sponsorApprove(any()) } returns ResultResponse.Success(
            TronSponsorApproveResult(
                funded = false,
                mode = TronSponsorMode.GIFT,
                amount = null,
                reason = "INSUFFICIENT_SPONSOR_POOL",
                txHash = null
            )
        )
        coEvery { repository.quoteApprove(any()) } returns ResultResponse.Success(
            TronApproveQuoteResult(
                estimatedEnergy = BigInteger("65000"),
                estimatedBandwidthBytes = BigInteger("320"),
                energyFeeSun = BigInteger("210"),
                bandwidthFeeSun = BigInteger("1000"),
                requiredSun = BigInteger("13650000"),
                requiredTrx = "13.65",
                requiredUsdApprox = 3.85,
                source = "network-estimate"
            )
        )

        val result = coordinator.requestSponsorForApprove(session, TronSponsorMode.GIFT)

        assertTrue(result is ResultResponse.Success)
        assertFalse((result as ResultResponse.Success).data.funded)
        assertEquals(GaslessCoordinatorState.NEEDS_APPROVE, coordinator.state.value)
        coVerify(exactly = 1) { repository.quoteApprove(any()) }
    }

    private fun tronNetwork(chainId: Long = 2494104990L): BlockchainNetwork {
        val network = mockk<BlockchainNetwork>(relaxed = true)
        every { network.id } returns "shasta_testnet"
        every { network.networkType } returns NetworkType.TVM
        every { network.name } returns NetworkName.SHASTA
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
