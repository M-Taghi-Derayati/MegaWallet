package com.mtd.data.network.wire

import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.math.BigInteger

/**
 * Phase 1 — the single, enforced BigInt-as-String codec.
 *
 * Every raw money value on the wire (`valueRaw`, `feeRaw`, `gasCredit`, swap `toAmount`,
 * box `amountRaw`, …) is a JSON **string** per the API contract's invariant #1. This adapter:
 *  - **writes** [BigInteger] → quoted JSON string (never a JS number, which would lose precision
 *    above 2^53 once it round-trips through a double),
 *  - **reads** a JSON string (or, defensively, an integer-valued number) → [BigInteger],
 *  - **rejects** floats / malformed values with [JsonParseException] instead of silently truncating.
 *
 * Register once in the shared Gson (see NetworkModule) so no DTO ever re-introduces `Long`/`Double`
 * for chain amounts.
 */
class BigIntegerStringAdapter : TypeAdapter<BigInteger>() {

    override fun write(out: JsonWriter, value: BigInteger?) {
        if (value == null) out.nullValue() else out.value(value.toString())
    }

    override fun read(reader: JsonReader): BigInteger? {
        return when (val token = reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            // Accept STRING (the contract) and NUMBER (defensive — server should send strings).
            // nextString() returns the *raw literal*, so precision is preserved for both.
            JsonToken.STRING, JsonToken.NUMBER -> {
                val raw = reader.nextString().trim()
                if (raw.isEmpty()) return null
                try {
                    BigInteger(raw) // throws on floats ("3.14"), hex, or any non-decimal-integer text
                } catch (e: NumberFormatException) {
                    throw JsonParseException("Invalid BigInteger-as-String: '$raw'", e)
                }
            }
            else -> throw JsonParseException("Expected a BigInteger-as-String, but was $token")
        }
    }
}
