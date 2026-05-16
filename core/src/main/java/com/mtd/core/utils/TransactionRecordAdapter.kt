package com.mtd.core.utils

import com.google.gson.*
import com.mtd.domain.model.*
import java.lang.reflect.Type

/**
 * Adapter برای سریالایز و دیسریالایز کردن صحیح Sealed Class های [TransactionRecord] در Gson.
 * برای استفاده در CacheManager جهت ذخیره لیست تراکنش‌ها.
 */
class TransactionRecordAdapter : JsonSerializer<TransactionRecord>, JsonDeserializer<TransactionRecord> {
    override fun serialize(src: TransactionRecord, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val jsonObject = context.serialize(src).asJsonObject
        val typeName = when (src) {
            is EvmTransaction -> "evm"
            is TronTransaction -> "tron"
            is BitcoinTransaction -> "bitcoin"
        }
        jsonObject.addProperty("record_type", typeName)
        return jsonObject
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): TransactionRecord {
        val jsonObject = json.asJsonObject
        val typeName = jsonObject.get("record_type")?.asString 
            ?: throw JsonParseException("Missing record_type in TransactionRecord JSON")
            
        return when (typeName) {
            "evm" -> context.deserialize(json, EvmTransaction::class.java)
            "tron" -> context.deserialize(json, TronTransaction::class.java)
            "bitcoin" -> context.deserialize(json, BitcoinTransaction::class.java)
            else -> throw JsonParseException("Unknown record_type: $typeName")
        }
    }
}
