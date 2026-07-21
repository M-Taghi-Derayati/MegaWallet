package com.mtd.megawallet.viewmodel.history

import com.mtd.domain.interfaceRepository.IAssetCatalog
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.NetworkInfo
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.TokenTransferDetails
import com.mtd.domain.model.TransactionStatus
import com.mtd.domain.model.TronTransaction
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

/**
 * Unit tests for [TransactionDisplayFormatter] — the presentation logic extracted out of
 * [TransactionHistoryViewModel] (TASK-14). Because the formatter is a pure function of its inputs
 * plus the injected catalogs, it can be exercised directly with mocked catalogs and no coroutines
 * or Android runtime.
 */
class TransactionDisplayFormatterTest {

    private val networkCatalog: INetworkCatalog = mockk()
    private val assetCatalog: IAssetCatalog = mockk()

    private lateinit var formatter: TransactionDisplayFormatter

    private val sepoliaInfo = NetworkInfo(
        id = "eip155:11155111",
        networkType = NetworkType.EVM,
        name = NetworkName.SEPOLIA,
        currencySymbol = "ETH",
        iconUrl = null,
        faName = "سپولیا",
        decimals = 18,
        explorers = listOf("https://sepolia.etherscan.io")
    )

    private val tronInfo = NetworkInfo(
        id = "tron:mainnet",
        networkType = NetworkType.TVM,
        name = NetworkName.TRON,
        currencySymbol = "TRX",
        iconUrl = null,
        faName = "ترون",
        decimals = 6,
        explorers = listOf("https://tronscan.org")
    )

    @Before
    fun setUp() {
        every { networkCatalog.getNetworkInfoByName(NetworkName.SEPOLIA) } returns sepoliaInfo
        every { networkCatalog.getNetworkInfoByName(NetworkName.TRON) } returns tronInfo
        every { assetCatalog.getAssetConfigsForNetwork(any()) } returns emptyList()
        formatter = TransactionDisplayFormatter(networkCatalog, assetCatalog)
    }

    // ---- fee ----

    @Test
    fun `unknown fee renders the neutral placeholder, never zero`() {
        val tx = tron(fee = null)
        assertEquals(TransactionDisplayFormatter.FEE_UNKNOWN_PLACEHOLDER, formatter.transactionFee(tx, null))
    }

    @Test
    fun `genuine zero fee renders 0 with the network symbol`() {
        val tx = evm(fee = BigInteger.ZERO)
        assertEquals("0 ETH", formatter.transactionFee(tx, null))
    }

    @Test
    fun `nonzero native fee is formatted and carries the network symbol`() {
        val tx = evm(fee = BigInteger.valueOf(21_000_000_000_000L)) // 0.000021 ETH
        val result = formatter.transactionFee(tx, null)
        assertTrue("expected an ETH-suffixed fee, was '$result'", result.endsWith("ETH"))
        assertFalse(result == "0 ETH")
        assertFalse(result == TransactionDisplayFormatter.FEE_UNKNOWN_PLACEHOLDER)
    }

    // ---- amount ----

    @Test
    fun `incoming list amount is prefixed with plus, outgoing is not`() {
        val incoming = formatter.listAmount(evm(isOutgoing = false))
        val outgoing = formatter.listAmount(evm(isOutgoing = true))
        assertTrue("incoming should start with +, was '$incoming'", incoming.startsWith("+"))
        assertFalse("outgoing should not start with +, was '$outgoing'", outgoing.startsWith("+"))
    }

    @Test
    fun `transaction amount is never signed`() {
        assertFalse(formatter.transactionAmount(evm(isOutgoing = false)).startsWith("+"))
    }

    // ---- labels ----

    @Test
    fun `status label maps every state`() {
        assertEquals("Pending", formatter.statusLabel(TransactionStatus.PENDING))
        assertEquals("Confirmed", formatter.statusLabel(TransactionStatus.CONFIRMED))
        assertEquals("Failed", formatter.statusLabel(TransactionStatus.FAILED))
    }

    @Test
    fun `type label reflects direction`() {
        assertEquals("Withdraw", formatter.transactionTypeLabel(evm(isOutgoing = true)))
        assertEquals("Deposit", formatter.transactionTypeLabel(evm(isOutgoing = false)))
    }

    @Test
    fun `primary label reflects direction and pending state`() {
        assertEquals("در حال ارسال به", formatter.historyPrimaryLabel(evm(isOutgoing = true, status = TransactionStatus.PENDING)))
        assertEquals("در حال دریافت از", formatter.historyPrimaryLabel(evm(isOutgoing = false, status = TransactionStatus.PENDING)))
        assertEquals("ارسال به", formatter.historyPrimaryLabel(evm(isOutgoing = true, status = TransactionStatus.CONFIRMED)))
        assertEquals("دریافت از", formatter.historyPrimaryLabel(evm(isOutgoing = false, status = TransactionStatus.CONFIRMED)))
    }

    @Test
    fun `network display name prefers the Persian name`() {
        assertEquals("سپولیا", formatter.networkDisplayName(evm()))
    }

    // ---- counterparty ----

    @Test
    fun `counterparty label uses the address book name when the address is known`() {
        val tx = evm(isOutgoing = true, toAddress = "0xABC")
        val book = mapOf("0xabc" to WalletAddressReference(name = "My Savings", color = 0))
        assertEquals("My Savings", formatter.historyCounterpartyLabel(tx, book))
    }

    @Test
    fun `counterparty label shortens an unknown address`() {
        val tx = evm(isOutgoing = true, toAddress = "0x1234567890abcdef1234567890abcdef12345678")
        val result = formatter.historyCounterpartyLabel(tx, emptyMap())
        assertFalse(result == "My Savings")
        assertTrue(result.isNotBlank())
    }

    // ---- pending duration ----

    @Test
    fun `pending duration buckets into h m s`() {
        assertEquals("1h", formatter.pendingDuration(evm(pendingDurationSeconds = 3_661L)))
        assertEquals("2m", formatter.pendingDuration(evm(pendingDurationSeconds = 120L)))
        assertEquals("45s", formatter.pendingDuration(evm(pendingDurationSeconds = 45L)))
    }

    @Test
    fun `pending duration without a value depends on status`() {
        assertEquals("در حال انجام ...", formatter.pendingDuration(evm(status = TransactionStatus.PENDING, pendingDurationSeconds = null)))
        assertEquals("-", formatter.pendingDuration(evm(status = TransactionStatus.CONFIRMED, pendingDurationSeconds = null)))
    }

    // ---- explorer / asset title ----

    @Test
    fun `explorer url is built for an etherscan-family explorer`() {
        assertEquals("https://sepolia.etherscan.io/tx/0xhash", formatter.buildExplorerUrl(evm()))
    }

    @Test
    fun `asset title falls back to the symbol when no catalog config matches`() {
        assertEquals("ETH", formatter.historyAssetTitle(evm()))
    }

    // ---- tron energy ----

    @Test
    fun `tron energy used falls back to the transaction value when no fee detail`() {
        assertEquals("100", formatter.tronEnergyUsed(tron(energyUsed = 100L), null))
    }

    @Test
    fun `tron energy used is null for a non-tron transaction`() {
        assertNull(formatter.tronEnergyUsed(evm(), null))
    }

    // ---- helpers ----

    private fun evm(
        isOutgoing: Boolean = true,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
        fee: BigInteger = BigInteger.ZERO,
        amount: BigInteger = BigInteger.TEN.pow(18),
        toAddress: String = "0xabc",
        fromAddress: String = "0xdef",
        pendingDurationSeconds: Long? = null,
        token: TokenTransferDetails? = null
    ) = EvmTransaction(
        hash = "0xhash",
        timestamp = 1_000L,
        pendingDurationSeconds = pendingDurationSeconds,
        fee = fee,
        status = status,
        networkName = NetworkName.SEPOLIA,
        fromAddress = fromAddress,
        toAddress = toAddress,
        amount = amount,
        isOutgoing = isOutgoing,
        contractAddress = token?.contractAddress,
        tokenTransferDetails = token
    )

    private fun tron(
        fee: BigInteger? = null,
        energyUsed: Long? = null,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
        isOutgoing: Boolean = true,
        amount: BigInteger = BigInteger.TEN.pow(6)
    ) = TronTransaction(
        hash = "0xhash",
        timestamp = 1_000L,
        fee = fee,
        status = status,
        networkName = NetworkName.TRON,
        fromAddress = "Tfrom",
        toAddress = "Tto",
        amount = amount,
        isOutgoing = isOutgoing,
        energyUsed = energyUsed
    )
}
