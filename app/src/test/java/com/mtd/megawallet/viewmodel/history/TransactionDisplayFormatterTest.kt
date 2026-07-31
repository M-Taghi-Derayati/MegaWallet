package com.mtd.megawallet.viewmodel.history

import com.mtd.core.utils.FiatConversion
import com.mtd.data.formatter.TransactionDisplayFormatter
import com.mtd.domain.interfaceRepository.IAssetCatalog
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.NetworkInfo
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.FiatCurrency
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
import java.math.BigDecimal
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

    // TASK-51 — the network's own template wins over guessing from the explorer API base.
    @Test
    fun `explorer url prefers the configured template over the api-base guess`() {
        every { networkCatalog.getNetworkInfoByName(NetworkName.SEPOLIA) } returns
            sepoliaInfo.copy(explorerTxUrl = "https://custom.example/transaction/{hash}")

        assertEquals("https://custom.example/transaction/0xhash", formatter.buildExplorerUrl(evm()))
    }

    // The regression this task exists for: TRON's configured explorer is the trongrid API host,
    // which matches no known web-explorer host, so the guess path returned null and the app could
    // never link a TRON transaction.
    @Test
    fun `tron api-host explorer yields no url without a template, and the real url with one`() {
        every { networkCatalog.getNetworkInfoByName(NetworkName.TRON) } returns
            tronInfo.copy(explorers = listOf("https://api.trongrid.io/"))
        assertNull(formatter.buildExplorerUrl(tron()))

        every { networkCatalog.getNetworkInfoByName(NetworkName.TRON) } returns
            tronInfo.copy(
                explorers = listOf("https://api.trongrid.io/"),
                explorerTxUrl = "https://tronscan.org/#/transaction/{hash}"
            )
        assertEquals("https://tronscan.org/#/transaction/0xhash", formatter.buildExplorerUrl(tron()))
    }

    @Test
    fun `explorer url falls through to the next explorer when the first is unrecognised`() {
        every { networkCatalog.getNetworkInfoByName(NetworkName.SEPOLIA) } returns
            sepoliaInfo.copy(
                explorers = listOf("https://unknown-host.example", "https://sepolia.etherscan.io")
            )

        assertEquals("https://sepolia.etherscan.io/tx/0xhash", formatter.buildExplorerUrl(evm()))
    }

    @Test
    fun `explorer url is null when the network has no usable explorer`() {
        every { networkCatalog.getNetworkInfoByName(NetworkName.SEPOLIA) } returns
            sepoliaInfo.copy(explorers = emptyList())

        assertNull(formatter.buildExplorerUrl(evm()))
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

    // ---- TASK-56: history fiat in both currencies ----

    @Test
    fun `history row fiat renders USD with a symbol`() {
        val tx = evm().copy(fiatValue = 12.5)
        assertEquals("$12.50", formatter.transactionFiat(tx, FiatCurrency.USD, null))
    }

    @Test
    fun `history row fiat renders Toman when Toman is selected`() {
        val tx = evm().copy(fiatValue = 2.0)
        assertEquals("140٬000 تومان", formatter.transactionFiat(tx, FiatCurrency.TOMAN, tomanRate))
    }

    @Test
    fun `history row fiat shows the placeholder when the rate is unknown`() {
        // Never "0" — a zero here reads as a worthless transaction.
        val tx = evm().copy(fiatValue = 2.0)
        assertEquals(
            FiatConversion.UNKNOWN_PLACEHOLDER,
            formatter.transactionFiat(tx, FiatCurrency.TOMAN, null)
        )
    }

    @Test
    fun `history row fiat is null when the transaction carries no fiat value at all`() {
        // Distinct from "rate unknown": there is nothing to show, so the row omits the line entirely.
        assertNull(formatter.transactionFiat(evm(), FiatCurrency.USD, null))
    }

    @Test
    fun `detail fiat is bare - the receipt draws its own currency glyph`() {
        val tx = evm().copy(fiatValue = 2.0)
        assertEquals("2.00", formatter.transactionFiatDetail(tx, emptyMap(), FiatCurrency.USD, null))
        assertEquals(
            "140٬000",
            formatter.transactionFiatDetail(tx, emptyMap(), FiatCurrency.TOMAN, tomanRate)
        )
    }

    private val tomanRate = CurrencyRate(
        quoteCurrency = "TMN",
        baseCurrency = "USDT",
        rate = BigDecimal("70000"),
        lastUpdated = 1_000L
    )

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
