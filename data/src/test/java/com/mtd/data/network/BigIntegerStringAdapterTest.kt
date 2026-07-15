package com.mtd.data.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.mtd.data.network.wire.BigIntegerStringAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class BigIntegerStringAdapterTest {

    private data class Holder(val v: BigInteger?)

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(BigInteger::class.java, BigIntegerStringAdapter())
        .create()

    @Test
    fun `uint256 max round-trips as a string with zero precision loss`() {
        val max = BigInteger.TWO.pow(256) - BigInteger.ONE // 78 digits
        val json = gson.toJson(Holder(max))
        assertEquals("""{"v":"$max"}""", json) // serialized as a JSON STRING, never a number
        assertEquals(max, gson.fromJson(json, Holder::class.java).v)
    }

    @Test
    fun `value exceeding Long_MAX decodes correctly (regression guard for the Long habit)`() {
        val big = BigInteger.valueOf(Long.MAX_VALUE) + BigInteger.ONE
        assertEquals(big, gson.fromJson("""{"v":"$big"}""", Holder::class.java).v)
    }

    @Test
    fun `defensively parses a huge integer JSON number without double truncation`() {
        val n = "12345678901234567890" // > 2^53, would lose precision as a double
        assertEquals(BigInteger(n), gson.fromJson("""{"v":$n}""", Holder::class.java).v)
    }

    @Test
    fun `zero and leading zeros parse`() {
        assertEquals(BigInteger.ZERO, gson.fromJson("""{"v":"0"}""", Holder::class.java).v)
        assertEquals(BigInteger("7"), gson.fromJson("""{"v":"007"}""", Holder::class.java).v)
    }

    @Test
    fun `null, empty string, and missing field all decode to null`() {
        assertNull(gson.fromJson("""{"v":null}""", Holder::class.java).v)
        assertNull(gson.fromJson("""{"v":""}""", Holder::class.java).v)
        assertNull(gson.fromJson("""{}""", Holder::class.java).v)
    }

    @Test
    fun `writes a normal value as a quoted string`() {
        assertEquals("""{"v":"10"}""", gson.toJson(Holder(BigInteger.TEN)))
    }

    @Test
    fun `float string is rejected (no silent truncation)`() {
        val ex = runCatching { gson.fromJson("""{"v":"3.14"}""", Holder::class.java) }.exceptionOrNull()
        assertTrue("expected parse failure, got $ex", ex is JsonParseException || ex is NumberFormatException)
    }

    @Test
    fun `float number is rejected`() {
        val ex = runCatching { gson.fromJson("""{"v":3.14}""", Holder::class.java) }.exceptionOrNull()
        assertTrue("expected parse failure, got $ex", ex is JsonParseException || ex is NumberFormatException)
    }
}
