package com.mtd.data.socket

import com.mtd.core.utils.BalanceFormatter
import com.mtd.domain.interfaceRepository.NetworkInfo
import com.mtd.domain.model.TxDescriptor
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Pure builder for the user-facing transaction notification from a [TxDescriptor]. Shared by the
 * foreground (WS, [NotificationSocketManager]) and background (FCM data-only, [PushMessageHandler])
 * paths so both show identical wording.
 *
 * TASK-59a — previously this gave up and returned null unless `direction` was exactly in/out/self,
 * and dropped the amount entirely whenever `tokenDecimal` was absent. A frame that carried a perfectly
 * good amount and symbol therefore rendered as the generic "یک تراکنش جدید روی آدرس شما ثبت شد." Now
 * it degrades one field at a time: unknown direction still shows the amount, and a native transfer
 * falls back to the network's own decimals/symbol.
 *
 * Returns null only when the descriptor carries nothing worth showing, so callers can fall back to a
 * generic alert.
 */
internal object TransactionNotificationText {

    /**
     * @param title notification title, e.g. "دریافت USDT".
     * @param body full sentence, e.g. "۱٫۵ USDT دریافت شد." — used as BigText too.
     * @param subText short context (network display name) for the notification header.
     */
    data class Content(
        val title: String,
        val body: String,
        val subText: String? = null
    )

    fun forTx(descriptor: TxDescriptor?, network: NetworkInfo? = null): Content? {
        val d = descriptor ?: return null

        val isNative = d.assetKind?.equals("native", ignoreCase = true) == true ||
            d.asset.isNullOrBlank()

        // Symbol: the token's own, else the network's native currency for a native transfer.
        val symbol = d.tokenSymbol?.takeIf { it.isNotBlank() }
            ?: network?.currencySymbol?.takeIf { isNative && it.isNotBlank() }

        // Decimals: the token's own, else the network's for a native transfer. Without decimals the
        // raw base units are meaningless, so the amount is omitted rather than shown wrong.
        val decimals = d.tokenDecimal ?: network?.decimals?.takeIf { isNative && it > 0 }
        val amount = formatAmount(d.amountRaw, decimals)

        val amountAndSymbol = when {
            amount != null && symbol != null -> "$amount $symbol"
            amount != null -> amount
            symbol != null -> symbol
            else -> null
        }

        val direction = normalizeDirection(d.direction)
        // Nothing identifiable at all → let the caller use its generic alert.
        if (amountAndSymbol == null && direction == null) return null

        val subText = network?.faName?.takeIf { it.isNotBlank() }
            ?: network?.name?.name?.lowercase()?.replaceFirstChar { it.uppercase() }

        val title = when (direction) {
            Direction.IN -> if (symbol != null) "دریافت $symbol" else "دریافت وجه"
            Direction.OUT -> if (symbol != null) "ارسال $symbol" else "ارسال وجه"
            Direction.SELF -> "انتقال داخلی"
            null -> "تراکنش جدید"
        }

        val body = when (direction) {
            Direction.IN -> if (amountAndSymbol != null) "مبلغ $amountAndSymbol دریافت شد." else "یک واریز جدید ثبت شد."
            Direction.OUT -> if (amountAndSymbol != null) "مبلغ $amountAndSymbol ارسال شد." else "یک برداشت جدید ثبت شد."
            Direction.SELF -> "یک انتقال بین حساب‌های شما ثبت شد."
            null -> if (amountAndSymbol != null) "تراکنش $amountAndSymbol ثبت شد." else "یک تراکنش جدید ثبت شد."
        }

        return Content(title = title, body = body, subText = subText)
    }

    private enum class Direction { IN, OUT, SELF }

    /** Accepts the contract's in/out/self plus the wordier spellings a server might send. */
    private fun normalizeDirection(raw: String?): Direction? = when (raw?.trim()?.lowercase()) {
        "in", "incoming", "received", "receive", "deposit", "credit" -> Direction.IN
        "out", "outgoing", "sent", "send", "withdraw", "withdrawal", "debit" -> Direction.OUT
        "self", "internal" -> Direction.SELF
        else -> null
    }

    private fun formatAmount(amountRaw: String?, decimals: Int?): String? {
        val raw = amountRaw?.takeIf { it.isNotBlank() } ?: return null
        val dec = decimals ?: return null
        return try {
            BalanceFormatter.formatBalance(BigDecimal(BigInteger(raw), dec), dec)
        } catch (e: Exception) {
            null
        }
    }
}
