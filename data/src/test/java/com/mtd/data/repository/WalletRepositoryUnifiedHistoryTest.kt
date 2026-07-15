package com.mtd.data.repository

import com.google.gson.Gson
import com.mtd.core.encryption.SecureStorage
import com.mtd.core.keymanager.KeyManager
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.wallet.ActiveWalletManager
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.datasource.IChainDataSource
import com.mtd.data.dto.HistoryAddressDto
import com.mtd.domain.model.HistoryAddress
import com.mtd.domain.model.HistoryPage
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the new unified-history wiring seam in [WalletRepositoryImpl.getUnifiedHistory]:
 * PROXY success maps straight through to the [HistoryPage], and a DIRECT-mode data source's
 * `UnsupportedOperation` is preserved verbatim so the ViewModel can branch on it (the fallback trigger).
 */
class WalletRepositoryUnifiedHistoryTest {

    private val keyManager: KeyManager = mockk(relaxed = true)
    private val secureStorage: SecureStorage = mockk(relaxed = true)
    private val activeWalletManager: ActiveWalletManager = mockk(relaxed = true)
    private val blockchainRegistry: BlockchainRegistry = mockk(relaxed = true)
    private val gson: Gson = mockk(relaxed = true)
    private val factory: ChainDataSourceFactory = mockk(relaxed = true)
    private val dataSource: IChainDataSource = mockk(relaxed = true)
    private val network: BlockchainNetwork = mockk(relaxed = true)

    private lateinit var repo: WalletRepositoryImpl

    private val pairs = listOf(HistoryAddress("evm", "0x1"), HistoryAddress("tron", "T1"))

    @Before fun setup() {
        repo = WalletRepositoryImpl(
            keyManager, secureStorage, activeWalletManager, blockchainRegistry, gson,
            dagger.Lazy { factory }
        )
        every { blockchainRegistry.getNetworkById("evm") } returns network
        every { factory.create(network) } returns dataSource
    }

    @Test fun `success maps the page through verbatim`() = runTest {
        val page = HistoryPage(items = emptyList(), nextCursor = "C2", hasMore = true, staleSources = listOf("tron:T1"))
        coEvery { dataSource.getHistory(any(), null, 20) } returns ResultResponse.Success(page)

        val res = repo.getUnifiedHistory(pairs, cursor = null, limit = 20)

        assertTrue(res is ResultResponse.Success)
        assertEquals(page, (res as ResultResponse.Success).data)
    }

    @Test fun `DIRECT UnsupportedOperation is preserved for fallback branching`() = runTest {
        coEvery { dataSource.getHistory(any(), any(), any()) } returns
            ResultResponse.Error(ApiException(ApiError.UnsupportedOperation))

        val res = repo.getUnifiedHistory(pairs)

        assertTrue(res is ResultResponse.Error)
        val ex = (res as ResultResponse.Error).exception
        assertTrue(ex is ApiException)
        assertEquals(ApiError.UnsupportedOperation, (ex as ApiException).apiError)
    }

    @Test fun `empty pairs short-circuit to an empty page without touching the network`() = runTest {
        val res = repo.getUnifiedHistory(emptyList())

        assertTrue(res is ResultResponse.Success)
        val page = (res as ResultResponse.Success).data
        assertTrue(page.items.isEmpty())
        assertEquals(false, page.hasMore)
    }

    @Test fun `forwards the dto pairs to the data source`() = runTest {
        val captured = mutableListOf<List<HistoryAddressDto>>()
        coEvery { dataSource.getHistory(capture(captured), any(), any()) } returns
            ResultResponse.Success(HistoryPage(emptyList(), null, false))

        repo.getUnifiedHistory(pairs)

        assertEquals(listOf("evm", "tron"), captured.first().map { it.networkId })
        assertEquals(listOf("0x1", "T1"), captured.first().map { it.address })
    }
}
