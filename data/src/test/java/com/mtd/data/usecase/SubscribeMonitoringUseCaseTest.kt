package com.mtd.data.usecase

import com.mtd.domain.interfaceRepository.IActiveWalletProvider
import com.mtd.domain.interfaceRepository.IMonitoringRepository
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.NetworkInfo
import com.mtd.domain.interfaceRepository.IUserPreferencesRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TASK-32 — verifies [SubscribeMonitoringUseCase]: it enrolls the ACTIVE wallet's real derived keys
 * exactly once (gated by the persisted subscribed-wallet-id set), maps `NetworkName`→bundle id, dedups,
 * chunks to 25, records the wallet only on full success, and never re-sends for an already-known wallet.
 */
class SubscribeMonitoringUseCaseTest {

    private lateinit var activeWalletProvider: IActiveWalletProvider
    private lateinit var monitoringRepository: IMonitoringRepository
    private lateinit var networkCatalog: INetworkCatalog
    private lateinit var userPreferences: IUserPreferencesRepository
    private lateinit var useCase: SubscribeMonitoringUseCase

    @Before
    fun setUp() {
        activeWalletProvider = mockk(relaxed = true)
        monitoringRepository = mockk(relaxed = true)
        networkCatalog = mockk(relaxed = true)
        userPreferences = mockk(relaxed = true)
        useCase = SubscribeMonitoringUseCase(
            activeWalletProvider = activeWalletProvider,
            monitoringRepository = dagger.Lazy { monitoringRepository },
            networkCatalog = networkCatalog,
            userPreferences = userPreferences
        )

        every { networkCatalog.getNetworkInfoByName(any()) } answers {
            networkInfo(firstArg())
        }
        coEvery { monitoringRepository.subscribe(any()) } returns
            ResultResponse.Success(MonitoringSubscribeResult(0, 0))
        coEvery { userPreferences.getMonitoringSubscribedWalletIds() } returns emptySet()
    }

    private fun activeWallet(wallet: Wallet?) {
        every { activeWalletProvider.activeWallet } returns MutableStateFlow(wallet)
    }

    @Test
    fun `enrolls a new active wallet's pairs and records its id`() = runTest {
        activeWallet(
            wallet("w1", key(NetworkName.SEPOLIA, "0xA"), key(NetworkName.SHASTA, "Tx"))
        )
        val captured = slot<List<MonitoringSubscription>>()
        coEvery { monitoringRepository.subscribe(capture(captured)) } returns
            ResultResponse.Success(MonitoringSubscribeResult(0, 0))
        val recorded = slot<Set<String>>()
        coEvery { userPreferences.setMonitoringSubscribedWalletIds(capture(recorded)) } returns Unit

        useCase()

        assertEquals(
            setOf(
                MonitoringSubscription("0xA", "sepolia"),
                MonitoringSubscription("Tx", "shasta")
            ),
            captured.captured.toSet()
        )
        assertEquals(setOf("w1"), recorded.captured)
    }

    @Test
    fun `already-subscribed wallet is a no-op`() = runTest {
        activeWallet(wallet("w1", key(NetworkName.SEPOLIA, "0xA")))
        coEvery { userPreferences.getMonitoringSubscribedWalletIds() } returns setOf("w1")

        useCase()

        coVerify(exactly = 0) { monitoringRepository.subscribe(any()) }
        coVerify(exactly = 0) { userPreferences.setMonitoringSubscribedWalletIds(any()) }
    }

    @Test
    fun `chunks pair-sets larger than the 25 bound`() = runTest {
        val keys = (1..30).map { key(NetworkName.SEPOLIA, "0x$it") }
        activeWallet(wallet("w1", *keys.toTypedArray()))

        val chunks = mutableListOf<List<MonitoringSubscription>>()
        coEvery { monitoringRepository.subscribe(capture(chunks)) } returns
            ResultResponse.Success(MonitoringSubscribeResult(0, 0))

        useCase()

        assertEquals(listOf(25, 5), chunks.map { it.size })
        assertEquals(30, chunks.flatten().distinct().size)
    }

    @Test
    fun `drops keys whose network is not in the catalog`() = runTest {
        every { networkCatalog.getNetworkInfoByName(NetworkName.DOGE) } returns null
        activeWallet(wallet("w1", key(NetworkName.SEPOLIA, "0xA"), key(NetworkName.DOGE, "Dx")))

        val captured = slot<List<MonitoringSubscription>>()
        coEvery { monitoringRepository.subscribe(capture(captured)) } returns
            ResultResponse.Success(MonitoringSubscribeResult(0, 0))

        useCase()

        assertEquals(listOf(MonitoringSubscription("0xA", "sepolia")), captured.captured)
    }

    @Test
    fun `no active wallet means no subscribe call`() = runTest {
        activeWallet(null)

        val result = useCase()

        assertTrue(result is ResultResponse.Success)
        coVerify(exactly = 0) { monitoringRepository.subscribe(any()) }
    }

    @Test
    fun `partial chunk failure does not record the wallet`() = runTest {
        val keys = (1..30).map { key(NetworkName.SEPOLIA, "0x$it") }
        activeWallet(wallet("w1", *keys.toTypedArray()))
        // First chunk ok, second fails → wallet must NOT be recorded (retries next activation).
        coEvery { monitoringRepository.subscribe(any()) } returnsMany listOf(
            ResultResponse.Success(MonitoringSubscribeResult(0, 0)),
            ResultResponse.Error(IllegalStateException("boom"))
        )

        useCase()

        coVerify(exactly = 0) { userPreferences.setMonitoringSubscribedWalletIds(any()) }
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
