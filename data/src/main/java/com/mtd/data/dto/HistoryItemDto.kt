package com.mtd.data.dto

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
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

class HistoryItemDtoDeserializer : JsonDeserializer<HistoryItemDto> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): HistoryItemDto {
        val obj = json.asJsonObject
        return when (val t = obj.get("type")?.asString?.lowercase()) {
            "evm" -> context.deserialize(json, HistoryItemDto.EvmHistoryDto::class.java)
            "tron" -> context.deserialize(json, HistoryItemDto.TronHistoryDto::class.java)
            "bitcoin" -> context.deserialize(json, HistoryItemDto.BitcoinHistoryDto::class.java)
            else -> throw JsonParseException("Unknown history item type: $t")
        }
    }
}
