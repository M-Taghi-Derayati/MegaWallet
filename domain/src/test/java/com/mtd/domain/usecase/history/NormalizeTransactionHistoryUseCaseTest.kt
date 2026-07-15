package com.mtd.domain.usecase.history

import com.mtd.domain.interfaceRepository.IAssetCatalog
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.NetworkInfo
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import com.mtd.domain.model.assets.AssetConfig
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import org.junit.Assert.assertEquals
import java.math.BigInteger
import org.junit.Test

/**
 * TASK-10 — proves the incremental [NormalizeTransactionHistoryUseCase.merge] used for paginated
 * history is **output-identical** to re-normalizing the full concatenation (the old O(pages²) path),
 * so the perf change can't alter what the user sees (ordering, dedup, pending-first).
 *
 * The fixtures use `networkName = null` so `isTransactionSupported` short-circuits before any catalog
 * lookup — the fakes below are never consulted, keeping the test focused on merge/dedup/sort semantics.
 */
class NormalizeTransactionHistoryUseCaseTest {

    private val useCase = NormalizeTransactionHistoryUseCase(EmptyNetworkCatalog, EmptyAssetCatalog)

    private fun evm(
        hash: String,
        timestamp: Long,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
        submittedAt: Long? = null
    ): TransactionRecord = EvmTransaction(
        hash = hash,
        timestamp = timestamp,
        submittedAt = submittedAt,
        fee = BigInteger.ZERO,
        status = status,
        networkName = null,
        fromAddress = "0xfrom",
        toAddress = "0xto",
        amount = BigInteger.ONE,
        isOutgoing = true
    )

    @Test
    fun `merge equals full normalize for a two-page sequence`() {
        val page1 = listOf(evm("a", 100), evm("b", 300))
        val page2 = listOf(evm("c", 200), evm("d", 400))

        val existing = useCase(page1)
        val merged = useCase.merge(existing, page2)
        val full = useCase(page1 + page2)

        assertEquals(full, merged)
    }

    @Test
    fun `merge dedupes an item that reappears in a later page (existing wins)`() {
        val page1 = listOf(evm("a", 100), evm("dup", 300))
        val page2 = listOf(evm("dup", 300), evm("c", 200)) // "dup" repeats

        val existing = useCase(page1)
        val merged = useCase.merge(existing, page2)
        val full = useCase(page1 + page2)

        assertEquals(full, merged)
        // "dup" collapses to a single record across the union.
        assertEquals(1, merged.count { it.hash == "dup" })
        assertEquals(3, merged.size)
    }

    @Test
    fun `merge keeps pending transactions ahead of confirmed ones`() {
        val page1 = listOf(evm("old", 100))
        val page2 = listOf(evm("pending", 50, status = TransactionStatus.PENDING, submittedAt = 10))

        val existing = useCase(page1)
        val merged = useCase.merge(existing, page2)

        assertEquals("pending", merged.first().hash)
        assertEquals(useCase(page1 + page2), merged)
    }

    @Test
    fun `merge drops zero-amount records just like full normalize`() {
        val zero = EvmTransaction(
            hash = "zero", timestamp = 500, fee = BigInteger.ZERO,
            status = TransactionStatus.CONFIRMED, networkName = null,
            fromAddress = "0xfrom", toAddress = "0xto",
            amount = BigInteger.ZERO, isOutgoing = true
        )
        val existing = useCase(listOf(evm("a", 100)))
        val merged = useCase.merge(existing, listOf(zero, evm("b", 200)))

        assertEquals(useCase(listOf(evm("a", 100)) + listOf(zero, evm("b", 200))), merged)
        assertEquals(0, merged.count { it.hash == "zero" })
    }

    private object EmptyNetworkCatalog : INetworkCatalog {
        override fun getAllNetworkInfos(): List<NetworkInfo> = emptyList()
        override fun getNetworkInfoByName(name: NetworkName): NetworkInfo? = null
        override fun getNetworkInfoById(id: String): NetworkInfo? = null
        override fun getNetworkTypeForAddress(address: String): NetworkType? = null
        override fun getNetworkTypeForNetworkId(networkId: String): NetworkType? = null
        override fun isValidAddressForNetworkId(address: String, networkId: String): Boolean = false
    }

    private object EmptyAssetCatalog : IAssetCatalog {
        override fun getAllAssetConfigs(): List<AssetConfig> = emptyList()
        override fun getAssetConfigById(id: String): AssetConfig? = null
        override fun getAssetConfigsForNetwork(networkId: String): List<AssetConfig> = emptyList()
    }
}
