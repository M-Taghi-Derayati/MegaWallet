package com.mtd.data.socket

import com.mtd.domain.interfaceRepository.NetworkInfo
import com.mtd.domain.model.TxDescriptor
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [TransactionNotificationText] builds the user-facing tx alert from a [TxDescriptor] hint:
 * direction chooses the wording, `amountRaw`+`tokenDecimal` render the human amount, and an
 * absent/insufficient hint yields null so callers fall back to a generic alert.
 *
 * TASK-59a widened this: the builder now degrades one field at a time instead of returning null the
 * moment `direction` or `tokenDecimal` is missing, because that was turning perfectly good
 * amount+symbol frames into the generic "یک تراکنش جدید…" alert.
 */
class TransactionNotificationTextTest {

    private val sepolia = NetworkInfo(
        id = "sepolia",
        networkType = NetworkType.EVM,
        name = NetworkName.SEPOLIA,
        currencySymbol = "ETH",
        iconUrl = null,
        faName = "سپولیا",
        decimals = 18
    )

    @Test
    fun `null descriptor yields no text`() {
        assertNull(TransactionNotificationText.forTx(null))
    }

    @Test
    fun `incoming token shows the formatted amount and symbol`() {
        val text = TransactionNotificationText.forTx(
            TxDescriptor(
                direction = "in", assetKind = "token", asset = "0xabc",
                amountRaw = "1500000", tokenSymbol = "USDT", tokenDecimal = 6
            )
        )
        assertEquals("دریافت USDT", text?.title)
        assertEquals("مبلغ 1.5 USDT دریافت شد.", text?.body)
    }

    @Test
    fun `outgoing without an amount still names the asset`() {
        val text = TransactionNotificationText.forTx(
            TxDescriptor(direction = "out", tokenSymbol = "ETH")
        )
        assertEquals("ارسال ETH", text?.title)
        assertEquals("مبلغ ETH ارسال شد.", text?.body)
    }

    @Test
    fun `self transfer has its own wording`() {
        val text = TransactionNotificationText.forTx(TxDescriptor(direction = "self"))
        assertEquals("انتقال داخلی", text?.title)
        assertEquals("یک انتقال بین حساب‌های شما ثبت شد.", text?.body)
    }

    // Regression: this used to return null, throwing away a usable amount+symbol.
    @Test
    fun `unknown direction still reports the amount instead of going generic`() {
        val text = TransactionNotificationText.forTx(
            TxDescriptor(direction = "sideways", amountRaw = "1500000", tokenSymbol = "USDT", tokenDecimal = 6)
        )
        assertEquals("تراکنش جدید", text?.title)
        assertTrue(text?.body?.contains("1.5 USDT") == true)
    }

    // Regression: a native transfer carries no tokenSymbol/tokenDecimal — they come from the network.
    @Test
    fun `native transfer falls back to the network symbol and decimals`() {
        val text = TransactionNotificationText.forTx(
            TxDescriptor(direction = "in", assetKind = "native", amountRaw = "1000000000000000000"),
            sepolia
        )
        assertEquals("دریافت ETH", text?.title)
        assertEquals("مبلغ 1 ETH دریافت شد.", text?.body)
        assertEquals("سپولیا", text?.subText)
    }

    @Test
    fun `direction synonyms are accepted`() {
        assertEquals("دریافت USDT", TransactionNotificationText.forTx(
            TxDescriptor(direction = "received", tokenSymbol = "USDT")
        )?.title)
        assertEquals("ارسال USDT", TransactionNotificationText.forTx(
            TxDescriptor(direction = "WITHDRAW", tokenSymbol = "USDT")
        )?.title)
    }

    @Test
    fun `descriptor with nothing displayable yields null`() {
        assertNull(TransactionNotificationText.forTx(TxDescriptor(assetKind = "token")))
    }

    @Test
    fun `missing symbol still renders the direction`() {
        val text = TransactionNotificationText.forTx(TxDescriptor(direction = "in", amountRaw = null))
        assertEquals("دریافت وجه", text?.title)
        assertEquals("یک واریز جدید ثبت شد.", text?.body)
    }
}
