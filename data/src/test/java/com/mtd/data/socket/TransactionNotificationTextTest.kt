package com.mtd.data.socket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies [TransactionNotificationText] builds the user-facing tx alert from a [TxDescriptor] hint
 * (realtime-event-contract.md §2): direction chooses the wording, `amountRaw`+`tokenDecimal` render the
 * human amount, and an absent/insufficient hint yields null so callers fall back to a generic alert.
 */
class TransactionNotificationTextTest {

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
        assertEquals("دریافت وجه" to "مبلغ 1.5 USDT دریافت شد.", text)
    }

    @Test
    fun `outgoing without an amount still names the asset`() {
        val text = TransactionNotificationText.forTx(
            TxDescriptor(direction = "out", tokenSymbol = "ETH")
        )
        assertEquals("ارسال وجه" to "مبلغ ETH ارسال شد.", text)
    }

    @Test
    fun `self transfer has its own wording`() {
        val text = TransactionNotificationText.forTx(TxDescriptor(direction = "self"))
        assertEquals("انتقال داخلی" to "یک انتقال بین حساب‌های شما ثبت شد.", text)
    }

    @Test
    fun `unknown direction falls back to null`() {
        assertNull(TransactionNotificationText.forTx(TxDescriptor(direction = "sideways", tokenSymbol = "USDT")))
    }

    @Test
    fun `missing symbol degrades to a generic asset word`() {
        val text = TransactionNotificationText.forTx(TxDescriptor(direction = "in", amountRaw = null))
        assertEquals("دریافت وجه" to "مبلغ دارایی دریافت شد.", text)
    }
}
