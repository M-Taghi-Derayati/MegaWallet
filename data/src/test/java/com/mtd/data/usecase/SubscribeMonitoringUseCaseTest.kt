package com.mtd.data.usecase

import com.mtd.domain.interfaceRepository.IMonitoringRepository
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.interfaceRepository.NetworkInfo
import com.mtd.domain.model.MonitoringSubscribeResult
import com.mtd.domain.model.MonitoringSubscription
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.core.Wallet
import com.mtd.domain.model.core.WalletKey
import com.mtd.domain.usecase.monitoring.SubscribeMonitoringUseCase
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

/**
 * TASK-32 — verifies the pair-gathering + chunking of [SubscribeMonitoringUseCase]: it collects
 * (address, networkId) across ALL wallets, maps each key's [NetworkName] to its bundle id, dedups,
 * drops keys whose network isn't in the catalog, and chunks to the server's 25-pair bound.
 */
class SubscribeMonitoringUseCaseTest {

    private lateinit var walletRepository: IWalletRepository
    private lateinit var monitoringRepository: IMonitoringRepository
    private lateinit var networkCatalog: INetworkCatalog
    private lateinit var useCase: SubscribeMonitoringUseCase

    @Before
    fun setUp() {
        walletRepository = mockk(relaxed = true)
        monitoringRepository = mockk(relaxed = true)
        networkCatalog = mockk(relaxed = true)
        useCase = SubscribeMonitoringUseCase(
            walletRepository = dagger.Lazy { walletRepository },
            monitoringRepository = dagger.Lazy { monitoringRepository },
            networkCatalog = networkCatalog
        )

        // networkId = the enum name lowercased, so we can assert mapping without the real catalog.
        every { networkCatalog.getNetworkInfoByName(any()) } answers {
            val name = firstArg<NetworkName>()
            networkInfo(name)
        }
        coEvery { monitoringRepository.subscribe(any()) } returns
            ResultResponse.Success(MonitoringSubscribeResult(subscribed = 0, total = 0))
    }

    @Test
    fun `gathers pairs across all wallets, maps networkId, and dedups`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns ResultResponse.Success(
            listOf(
                wallet("w1", key(NetworkName.SEPOLIA, "0xA"), key(NetworkName.SHASTA, "Tx")),
                // Duplicate of the first pair (same address+network) must collapse.
                wallet("w2", key(NetworkName.SEPOLIA, "0xA"), key(NetworkName.BASESEPOLIA, "0xB"))
            )
        )

        val captured = slot<List<MonitoringSubscription>>()
        coEvery { monitoringRepository.subscribe(capture(captured)) } returns
            ResultResponse.Success(MonitoringSubscribeResult(0, 0))

        val result = useCase()

        assertTrue(result is ResultResponse.Success)
        // 4 keys, one is a dup → 3 distinct pairs, single chunk.
        coVerify(exactly = 1) { monitoringRepository.subscribe(any()) }
        assertEquals(
            setOf(
                MonitoringSubscription("0xA", "sepolia"),
                MonitoringSubscription("Tx", "shasta"),
                MonitoringSubscription("0xB", "basesepolia")
            ),
            captured.captured.toSet()
        )
    }

    @Test
    fun `chunks pair-sets larger than the 25 bound`() = runTest {
        // 30 distinct pairs on one network → 2 chunks (25 + 5).
        val keys = (1..30).map { key(NetworkName.SEPOLIA, "0x$it") }
        coEvery { walletRepository.getAllWallets() } returns
            ResultResponse.Success(listOf(wallet("w1", *keys.toTypedArray())))

        val chunks = mutableListOf<List<MonitoringSubscription>>()
        coEvery { monitoringRepository.subscribe(capture(chunks)) } returns
            ResultResponse.Success(MonitoringSubscribeResult(0, 0))

        useCase()

        assertEquals(listOf(25, 5), chunks.map { it.size })
        assertTrue(chunks.all { it.size <= 25 })
        assertEquals(30, chunks.flatten().distinct().size)
    }

    @Test
    fun `drops keys whose network is not in the catalog`() = runTest {
        every { networkCatalog.getNetworkInfoByName(NetworkName.DOGE) } returns null
        coEvery { walletRepository.getAllWallets() } returns ResultResponse.Success(
            listOf(wallet("w1", key(NetworkName.SEPOLIA, "0xA"), key(NetworkName.DOGE, "Dx")))
        )

        val captured = slot<List<MonitoringSubscription>>()
        coEvery { monitoringRepository.subscribe(capture(captured)) } returns
            ResultResponse.Success(MonitoringSubscribeResult(0, 0))

        useCase()

        assertEquals(listOf(MonitoringSubscription("0xA", "sepolia")), captured.captured)
    }

    @Test
    fun `no wallets means no subscribe call`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns ResultResponse.Success(emptyList())

        val result = useCase()

        assertTrue(result is ResultResponse.Success)
        coVerify(exactly = 0) { monitoringRepository.subscribe(any()) }
    }

    @Test
    fun `propagates a wallet-read failure without subscribing`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns
            ResultResponse.Error(IllegalStateException("boom"))

        val result = useCase()

        assertTrue(result is ResultResponse.Error)
        coVerify(exactly = 0) { monitoringRepository.subscribe(any()) }
    }

    private fun networkInfo(name: NetworkName) = NetworkInfo(
        id = name.name.lowercase(),
        networkType = NetworkType.EVM,
        name = name,
        currencySymbol = "",
        iconUrl = null,
        faName = null
    )

    private fun wallet(id: String, vararg keys: WalletKey) =
        Wallet(id = id, hasMnemonic = true, keys = keys.toList())

    private fun key(network: NetworkName, address: String) = WalletKey(
        networkName = network,
        networkType = NetworkType.EVM,
        chainId = null,
        derivationPath = null,
        address = address,
        publicKeyHex = ""
    )
}
