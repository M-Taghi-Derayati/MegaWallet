package com.mtd.data.socket

import com.mtd.domain.model.TxDescriptor
import org.json.JSONObject

/**
 * Reads the `tx.new` display hint out of a realtime payload.
 *
 * TASK-59a — both transports previously read a fixed set of root-level keys (`direction`,
 * `amountRaw`, `tokenSymbol`, …). If the server nested the hint under a container, or spelled a key
 * differently, every field parsed as null and the notification fell back to the generic
 * "یک تراکنش جدید روی آدرس شما ثبت شد." — losing the amount and token the user actually cares about.
 *
 * This searches the payload root **and** the known nested containers, trying the documented key name
 * first and common aliases after it. It is deliberately permissive on *reading*: a wrong guess costs
 * nothing (the field stays null and we degrade exactly as before), while a correct one restores the
 * detail. Money values are kept as raw strings — never parsed to Long/Double — per the BigInt-as-String
 * invariant.
 *
 * Shared by [NotificationSocketManager] (WS, JSON) and [PushMessageHandler] (FCM, flat string map) so
 * the two transports can't drift apart again.
 */
internal object TxDescriptorParser {

    /** Containers a nested display hint may live under, in priority order; "" = the payload root. */
    private val CONTAINERS = listOf("", "display", "descriptor", "tx", "transaction", "meta")

    // Deliberately NOT "type": at the payload root that is the event name ("tx.new"), which would
    // shadow a real direction sitting in a nested container.
    private val DIRECTION_KEYS = listOf("direction", "dir", "txDirection", "flow")
    private val ASSET_KIND_KEYS = listOf("assetKind", "asset_kind", "kind", "assetType")
    private val ASSET_KEYS = listOf("asset", "contract", "contractAddress", "tokenAddress")
    private val AMOUNT_KEYS = listOf("amountRaw", "amount_raw", "amount", "value", "valueRaw", "rawAmount")
    private val SYMBOL_KEYS = listOf("tokenSymbol", "token_symbol", "symbol", "token", "assetSymbol")
    private val DECIMAL_KEYS = listOf("tokenDecimal", "token_decimal", "tokenDecimals", "decimals", "decimal")

    fun fromJson(payload: JSONObject): TxDescriptor? {
        val scopes = CONTAINERS.mapNotNull { name ->
            if (name.isEmpty()) payload else payload.optJSONObject(name)
        }
        fun str(keys: List<String>): String? = scopes.firstNotNullOfOrNull { scope ->
            keys.firstNotNullOfOrNull { key ->
                scope.opt(key)?.takeIf { it != JSONObject.NULL }?.toString()?.takeIf { it.isNotBlank() }
            }
        }
        return build(
            direction = str(DIRECTION_KEYS),
            assetKind = str(ASSET_KIND_KEYS),
            asset = str(ASSET_KEYS),
            amountRaw = str(AMOUNT_KEYS),
            tokenSymbol = str(SYMBOL_KEYS),
            tokenDecimal = str(DECIMAL_KEYS)?.toIntOrNull()
        )
    }

    /** FCM delivers a flat map with every value stringified, so nesting can only appear as `a.b` keys. */
    fun fromMap(data: Map<String, String>): TxDescriptor? {
        fun str(keys: List<String>): String? = CONTAINERS.firstNotNullOfOrNull { container ->
            keys.firstNotNullOfOrNull { key ->
                val full = if (container.isEmpty()) key else "$container.$key"
                data[full]?.takeIf { it.isNotBlank() }
            }
        }
        return build(
            direction = str(DIRECTION_KEYS),
            assetKind = str(ASSET_KIND_KEYS),
            asset = str(ASSET_KEYS),
            amountRaw = str(AMOUNT_KEYS),
            tokenSymbol = str(SYMBOL_KEYS),
            tokenDecimal = str(DECIMAL_KEYS)?.toIntOrNull()
        )
    }

    private fun build(
        direction: String?,
        assetKind: String?,
        asset: String?,
        amountRaw: String?,
        tokenSymbol: String?,
        tokenDecimal: Int?
    ): TxDescriptor? {
        // Nothing displayable → null, so the caller uses its generic alert.
        if (direction == null && amountRaw == null && tokenSymbol == null) return null
        return TxDescriptor(
            direction = direction,
            assetKind = assetKind,
            asset = asset,
            amountRaw = amountRaw,
            tokenSymbol = tokenSymbol,
            tokenDecimal = tokenDecimal
        )
    }
}
