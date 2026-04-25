package com.mtd.data.repository.transfer

import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.repository.gasless.EvmGaslessCoordinator
import com.mtd.data.repository.gasless.PendingGaslessTxStore
import com.mtd.data.repository.gasless.TronGaslessCoordinator
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.EvmGaslessSession
import com.mtd.domain.model.EvmGaslessTransferRequest
import com.mtd.domain.model.GaslessChain
import com.mtd.domain.model.GaslessQueuedTx
import com.mtd.domain.model.GaslessTxStatus
import com.mtd.domain.model.PendingGaslessTx
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.TransferMode
import com.mtd.domain.model.TronGaslessSession
import com.mtd.domain.model.TronGaslessTransferRequest
import com.mtd.domain.model.UnifiedGaslessSession
import com.mtd.domain.model.UnifiedTransferRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

class UnifiedTransferCoordinatorTest {

    private lateinit var walletRepository: IWalletRepository
    private lateinit var blockchainRegistry: BlockchainRegistry
    private lateinit var evmGaslessCoordinator: EvmGaslessCoordinator
    private lateinit var tronGaslessCoordinator: TronGaslessCoordinator
    private lateinit var pendingStore: PendingGaslessTxStore
    private lateinit var coordinator: UnifiedTransferCoordinator

    @Before
    fun setUp() {
        walletRepository = mockk(relaxed = true)
        blockchainRegistry = mockk(relaxed = true)
        evmGaslessCoordinator = mockk(relaxed = true)
        tronGaslessCoordinator = mockk(relaxed = true)
        pendingStore = mockk(relaxed = true)

        coordinator = UnifiedTransferCoordinator(
            walletRepository = walletRepository,
            blockchainRegistry = blockchainRegistry,
            evmGaslessCoordinator = evmGaslessCoordinator,
            tronGaslessCoordinator = tronGaslessCoordinator,
            pendingGaslessTxStore = pendingStore
        )
    }

    @Test
    fun `sendNormal maps EVM native request to walletRepository sendTransaction`() = runTest {
        val network = evmNetwork("sepolia", 11155111L)
        every { blockchainRegistry.getNetworkById("sepolia") } returns network
        coEvery { walletRepository.sendTransaction(any()) } returns ResultResponse.Success("0xhash")

        val request = UnifiedTransferRequest(
            networkId = "sepolia",
            mode = TransferMode.NORMAL,
            toAddress = "0x000000000000000000000000000000000000dEaD",
            amount = BigInteger("1000000000000000"),
            gasPrice = BigInteger("1000000000"),
            gasLimit = BigInteger("21000")
        )

        val result = coordinator.sendNormal(request)

        assertTrue(result is ResultResponse.Success)
        val slot = slot<TransactionParams>()
        coVerify(exactly = 1) { walletRepository.sendTransaction(capture(slot)) }
        val tx = slot.captured as TransactionParams.Evm
        assertEquals(NetworkName.SEPOLIA, tx.networkName)
        assertEquals(request.toAddress, tx.to)
        assertEquals(request.amount, tx.amount)
        assertEquals(request.gasPrice, tx.gasPrice)
        assertEquals(request.gasLimit, tx.gasLimit)
    }

    @Test
    fun `sendNormal maps TRON token request to TVM params`() = runTest {
        val network = tronNetwork("shasta_testnet", 2494104990L)
        every { blockchainRegistry.getNetworkById("shasta_testnet") } returns network
        coEvery { walletRepository.sendTransaction(any()) } returns ResultResponse.Success("trx_tx")

        val request = UnifiedTransferRequest(
            networkId = "shasta_testnet",
            mode = TransferMode.NORMAL,
            toAddress = "TAUNv6F6m3n9aYH6Q4qk8Vh8W5u7WkQF5A",
            amount = BigInteger("1000"),
            tokenAddress = "THHQqmx9XMj5N77a6SCr3dhgz6YJbArWzU",
            feeLimit = 15_000_000L
        )

        val result = coordinator.sendNormal(request)

        assertTrue(result is ResultResponse.Success)
        val slot = slot<TransactionParams>()
        coVerify(exactly = 1) { walletRepository.sendTransaction(capture(slot)) }
        val tx = slot.captured as TransactionParams.Tvm
        assertEquals(NetworkName.SHASTA, tx.networkName)
        assertEquals(request.toAddress, tx.toAddress)
        assertEquals(request.tokenAddress, tx.contractAddress)
        assertEquals(request.amount, tx.amount)
        assertEquals(request.feeLimit, tx.feeLimit)
    }

    @Test
    fun `sendNormal maps UTXO request to Utxo params`() = runTest {
        val network = utxoNetwork("doge_mainnet", 3L)
        every { blockchainRegistry.getNetworkById("doge_mainnet") } returns network
        coEvery { walletRepository.sendTransaction(any()) } returns ResultResponse.Success("doge_tx_hash")

        val request = UnifiedTransferRequest(
            networkId = "doge_mainnet",
            mode = TransferMode.NORMAL,
            toAddress = "DH5yaieqoZN36fDVciNyRueRGvGLR3mr7L",
            amount = BigInteger("100000000")
        )

        val result = coordinator.sendNormal(request)

        assertTrue(result is ResultResponse.Success)
        val slot = slot<TransactionParams>()
        coVerify(exactly = 1) { walletRepository.sendTransaction(capture(slot)) }
        val tx = slot.captured as TransactionParams.Utxo
        assertEquals(3L, tx.chainId)
        assertEquals(request.toAddress, tx.toAddress)
        assertEquals(100000000L, tx.amountInSatoshi)
        assertEquals(1500L, tx.feeRateInSatsPerByte)
    }

    @Test
    fun `sendNormal returns error for invalid request and does not call walletRepository`() = runTest {
        val request = UnifiedTransferRequest(
            networkId = "sepolia",
            mode = TransferMode.NORMAL,
            toAddress = "0x000000000000000000000000000000000000dEaD",
            amount = BigInteger.ZERO
        )

        val result = coordinator.sendNormal(request)

        assertTrue(result is ResultResponse.Error)
        coVerify(exactly = 0) { walletRepository.sendTransaction(any()) }
    }

    @Test
    fun `prepareGasless dispatches to EVM coordinator for EVM network`() = runTest {
        val network = evmNetwork("sepolia", 11155111L)
        every { blockchainRegistry.getNetworkById("sepolia") } returns network

        val evmSession = EvmGaslessSession(
            request = EvmGaslessTransferRequest(
                networkId = "sepolia",
                tokenAddress = "0x186cca6904490818AB0DC409ca59D932A2366031",
                targetAddress = "0x000000000000000000000000000000000000dEaD",
                amount = BigInteger("1"),
                permit2Address = "0x000000000022D473030F116dDEE9F6B43aC78BA3"
            ),
            networkName = NetworkName.SEPOLIA,
            userAddress = "0x17b51d4928668B50065C589bAfBC32736f196216",
            chainId = 11155111L,
            relayerContract = "0x1111111111111111111111111111111111111111",
            treasuryAddress = "0x2222222222222222222222222222222222222222",
            nonce = BigInteger.ONE,
            allowance = BigInteger("10"),
            prepareToken = "prepare_token_evm_1",
            idempotencyKey = "idem_evm_1"
        )
        coEvery { evmGaslessCoordinator.prepareSession(any()) } returns ResultResponse.Success(evmSession)

        val request = UnifiedTransferRequest(
            networkId = "sepolia",
            mode = TransferMode.GASLESS,
            toAddress = "0x000000000000000000000000000000000000dEaD",
            amount = BigInteger("1"),
            tokenAddress = "0x186cca6904490818AB0DC409ca59D932A2366031",
            permit2Address = "0x000000000022D473030F116dDEE9F6B43aC78BA3"
        )

        val result = coordinator.prepareGasless(request)

        assertTrue(result is ResultResponse.Success)
        assertTrue((result as ResultResponse.Success).data is UnifiedGaslessSession.Evm)
        coVerify(exactly = 1) { evmGaslessCoordinator.prepareSession(any()) }
        coVerify(exactly = 0) { tronGaslessCoordinator.prepareSession(any()) }
    }

    @Test
    fun `submitGasless stores pending EVM queue item`() = runTest {
        val session = UnifiedGaslessSession.Evm(
            EvmGaslessSession(
                request = EvmGaslessTransferRequest(
                    networkId = "sepolia",
                    tokenAddress = "0x186cca6904490818AB0DC409ca59D932A2366031",
                    targetAddress = "0x000000000000000000000000000000000000dEaD",
                    amount = BigInteger("1"),
                    permit2Address = "0x000000000022D473030F116dDEE9F6B43aC78BA3"
                ),
                networkName = NetworkName.SEPOLIA,
                userAddress = "0x17b51d4928668B50065C589bAfBC32736f196216",
                chainId = 11155111L,
                relayerContract = "0x1111111111111111111111111111111111111111",
                treasuryAddress = "0x2222222222222222222222222222222222222222",
                nonce = BigInteger.ONE,
                allowance = BigInteger("10"),
                prepareToken = "prepare_token_evm_1",
                idempotencyKey = "idem_evm_1"
            )
        )
        coEvery { evmGaslessCoordinator.signAndSubmit(any()) } returns ResultResponse.Success(
            GaslessQueuedTx(id = "queue_1", stage = "QUEUED")
        )
        coEvery { walletRepository.getActiveWalletId() } returns "wallet_1"

        val result = coordinator.submitGasless(session)

        assertTrue(result is ResultResponse.Success)
        val slot = slot<PendingGaslessTx>()
        every { pendingStore.put(capture(slot)) } returns Unit
        coordinator.submitGasless(session)
        assertEquals(GaslessChain.EVM, slot.captured.chain)
        assertEquals("queue_1", slot.captured.queueId)
        assertEquals("wallet_1", slot.captured.walletId)
    }

    @Test
    fun `pollGaslessUntilFinal removes pending item on final status`() = runTest {
        val session = UnifiedGaslessSession.Tron(
            TronGaslessSession(
                request = TronGaslessTransferRequest(
                    networkId = "shasta_testnet",
                    tokenAddress = "THHQqmx9XMj5N77a6SCr3dhgz6YJbArWzU",
                    targetAddress = "TAUNv6F6m3n9aYH6Q4qk8Vh8W5u7WkQF5A",
                    amount = BigInteger("1")
                ),
                networkName = NetworkName.SHASTA,
                userAddress = "TAUNv6F6m3n9aYH6Q4qk8Vh8W5u7WkQF5A",
                chainId = 2494104990L,
                relayerContract = "TVjsyZ7fYF3qLF6BQgPmTEZy1xrNNyVAAA",
                treasuryAddress = "TGzz8gjYiYRqpfmDwnLxfgPuLVNmpCswVp",
                nonce = BigInteger.ONE,
                allowance = BigInteger("100"),
                prepareToken = "prepare_token_tron_1",
                idempotencyKey = "idem_tron_1"
            )
        )
        coEvery {
            tronGaslessCoordinator.pollUntilFinal(
                txId = "queue_1",
                pollIntervalMs = 1L,
                timeoutMs = 20L
            )
        } returns ResultResponse.Success(
            GaslessTxStatus(
                id = "queue_1",
                status = "SUCCESS",
                txHash = "0x123",
                lastError = null
            )
        )

        val result = coordinator.pollGaslessUntilFinal(
            session = session,
            queueId = "queue_1",
            pollIntervalMs = 1L,
            timeoutMs = 20L
        )

        assertTrue(result is ResultResponse.Success)
        every { pendingStore.remove(any(), any()) } returns Unit
        coordinator.pollGaslessUntilFinal(session, "queue_1", pollIntervalMs = 1L, timeoutMs = 20L)
        io.mockk.verify { pendingStore.remove(GaslessChain.TRON, "queue_1") }
    }

    private fun evmNetwork(id: String, chainId: Long): BlockchainNetwork {
        val network = mockk<BlockchainNetwork>(relaxed = true)
        every { network.id } returns id
        every { network.networkType } returns NetworkType.EVM
        every { network.name } returns NetworkName.SEPOLIA
        every { network.chainId } returns chainId
        return network
    }

    private fun tronNetwork(id: String, chainId: Long): BlockchainNetwork {
        val network = mockk<BlockchainNetwork>(relaxed = true)
        every { network.id } returns id
        every { network.networkType } returns NetworkType.TVM
        every { network.name } returns NetworkName.SHASTA
        every { network.chainId } returns chainId
        return network
    }

    private fun utxoNetwork(id: String, chainId: Long): BlockchainNetwork {
        val network = mockk<BlockchainNetwork>(relaxed = true)
        every { network.id } returns id
        every { network.networkType } returns NetworkType.UTXO
        every { network.name } returns NetworkName.DOGE
        every { network.chainId } returns chainId
        return network
    }
}
