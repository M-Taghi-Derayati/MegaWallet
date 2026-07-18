package com.mtd.data.dto

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.lang.reflect.Type
import java.math.BigInteger

sealed class HistoryItemDto {
    abstract val type: String
    abstract val hash: String
    abstract val network: String      // NetworkName enum name
    abstract val status: String       // CONFIRMED | PENDING | FAILED
    abstract val timestamp: Long
    abstract val isOutgoing: Boolean
    abstract val fromAddress: String?
    abstract val toAddress: String?
    abstract val valueRaw: BigInteger
    abstract val feeRaw: BigInteger

    data class EvmHistoryDto(
        override val hash: String,
        override val network: String,
        override val status: String,
        override val timestamp: Long,
        override val isOutgoing: Boolean,
        override val valueRaw: BigInteger,
        override val feeRaw: BigInteger,
        override val fromAddress: String? = null,
        override val toAddress: String? = null,
        override val type: String = "evm",
        val gasPriceRaw: BigInteger? = null,
        val gasUsedRaw: BigInteger? = null,
        val nonce: Long? = null,
        val contractAddress: String? = null,
        val tokenSymbol: String? = null,
        val tokenDecimals: Int? = null,
        val tokenAmountRaw: BigInteger? = null
    ) : HistoryItemDto()

    data class TronHistoryDto(
        override val hash: String,
        override val network: String,
        override val status: String,
        override val timestamp: Long,
        override val isOutgoing: Boolean,
        override val valueRaw: BigInteger,
        override val feeRaw: BigInteger,
        override val fromAddress: String? = null,
        override val toAddress: String? = null,
        override val type: String = "tron",
        val energyUsed: Long? = null,
        val bandwidthUsed: Long? = null,
        val contractAddress: String? = null,
        val tokenSymbol: String? = null,
        val tokenDecimals: Int? = null,
        val tokenAmountRaw: BigInteger? = null
    ) : HistoryItemDto()

    data class BitcoinHistoryDto(
        override val hash: String,
        override val network: String,
        override val status: String,
        override val timestamp: Long,
        override val isOutgoing: Boolean,
        override val valueRaw: BigInteger,
        override val feeRaw: BigInteger,
        override val fromAddress: String? = null,
        override val toAddress: String? = null,
        override val type: String = "bitcoin",
        val feeRateSatPerVByte: Long? = null
    ) : HistoryItemDto()
}

/**
 * The unified `/history` item is **nested**, not flat: token info lives under `tokenTransfer{}`, and
 * the per-family fee/energy/gas under `tron{}` / `evm{}` / `bitcoin{}` (with `display{}` describing the
 * asset). A plain `context.deserialize(...)` into the flat DTOs left `tokenSymbol`/`energyUsed`/
 * `gasPriceRaw`/… null — so token transfers were mis-mapped as native and energy/gas were lost. This
 * deserializer reads the real nested shape and flattens it into the DTOs the mapper consumes.
 */
class HistoryItemDtoDeserializer : JsonDeserializer<HistoryItemDto> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): HistoryItemDto {
        val o = json.asJsonObject
        val type = o.string("type")?.lowercase()

        val hash = o.string("hash").orEmpty()
        val network = o.string("network").orEmpty()
        val status = o.string("status").orEmpty()
        val timestamp = o.long("timestamp") ?: 0L
        val isOutgoing = o.bool("isOutgoing") ?: false
        val from = o.string("fromAddress")
        val to = o.string("toAddress")
        val valueRaw = o.bigInteger("valueRaw") ?: BigInteger.ZERO
        val feeRaw = o.bigInteger("feeRaw") ?: BigInteger.ZERO

        // Token transfer block (null for native transfers).
        val token = o.obj("tokenTransfer")
        val tokenSymbol = token?.string("symbol")
        val tokenDecimals = token?.int("decimals")
        val tokenAmountRaw = token?.bigInteger("amountRaw")
        val tokenContract = token?.string("contractAddress")

        return when (type) {
            "evm" -> {
                val evm = o.obj("evm")
                HistoryItemDto.EvmHistoryDto(
                    hash = hash, network = network, status = status, timestamp = timestamp,
                    isOutgoing = isOutgoing, valueRaw = valueRaw, feeRaw = feeRaw,
                    fromAddress = from, toAddress = to,
                    gasPriceRaw = evm?.bigInteger("gasPriceRaw"),
                    gasUsedRaw = evm?.bigInteger("gasUsedRaw"),
                    nonce = evm?.long("nonce"),
                    contractAddress = tokenContract ?: evm?.string("contractAddress"),
                    tokenSymbol = tokenSymbol,
                    tokenDecimals = tokenDecimals,
                    tokenAmountRaw = tokenAmountRaw
                )
            }
            "tron" -> {
                val tron = o.obj("tron")
                HistoryItemDto.TronHistoryDto(
                    hash = hash, network = network, status = status, timestamp = timestamp,
                    isOutgoing = isOutgoing, valueRaw = valueRaw, feeRaw = feeRaw,
                    fromAddress = from, toAddress = to,
                    energyUsed = tron?.long("energyUsed"),
                    bandwidthUsed = tron?.long("bandwidthUsed"),
                    contractAddress = tokenContract ?: tron?.string("contractAddress"),
                    tokenSymbol = tokenSymbol,
                    tokenDecimals = tokenDecimals,
                    tokenAmountRaw = tokenAmountRaw
                )
            }
            "bitcoin" -> HistoryItemDto.BitcoinHistoryDto(
                hash = hash, network = network, status = status, timestamp = timestamp,
                isOutgoing = isOutgoing, valueRaw = valueRaw, feeRaw = feeRaw,
                fromAddress = from, toAddress = to,
                feeRateSatPerVByte = o.obj("bitcoin")?.long("feeRateSatPerVByte")
            )
            else -> throw JsonParseException("Unknown history item type: $type")
        }
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.bigInteger(key: String): BigInteger? =
        string(key)?.toBigIntegerOrNull()

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeIf { !it.isJsonNull }?.asLong

    private fun JsonObject.int(key: String): Int? =
        get(key)?.takeIf { !it.isJsonNull }?.asInt

    private fun JsonObject.bool(key: String): Boolean? =
        get(key)?.takeIf { !it.isJsonNull }?.asBoolean

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject
}
