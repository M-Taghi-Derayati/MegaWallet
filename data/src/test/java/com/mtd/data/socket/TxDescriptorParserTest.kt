package com.mtd.data.socket

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TASK-59a — [TxDescriptorParser] must find the `tx.new` display hint whether the server puts it at
 * the payload root or inside a container, and whether it uses the documented key or a common synonym.
 * The previous root-only/one-spelling reader silently produced an empty descriptor, which downgraded
 * the notification to the generic alert and lost the amount and token the user cares about.
 */
class TxDescriptorParserTest {

    @Test
    fun `reads the documented root-level shape`() {
        val payload = JSONObject(
            """
            {"eventId":"e1","txHash":"0xaa","networkId":"sepolia",
             "direction":"in","assetKind":"token","asset":"0xabc",
             "amountRaw":"1500000","tokenSymbol":"USDT","tokenDecimal":6}
            """.trimIndent()
        )

        val d = TxDescriptorParser.fromJson(payload)

        assertEquals("in", d?.direction)
        assertEquals("1500000", d?.amountRaw)
        assertEquals("USDT", d?.tokenSymbol)
        assertEquals(6, d?.tokenDecimal)
    }

    @Test
    fun `reads a hint nested under a container`() {
        val payload = JSONObject(
            """
            {"eventId":"e1","networkId":"sepolia",
             "display":{"direction":"out","amountRaw":"2000000","tokenSymbol":"USDC","tokenDecimal":6}}
            """.trimIndent()
        )

        val d = TxDescriptorParser.fromJson(payload)

        assertEquals("out", d?.direction)
        assertEquals("2000000", d?.amountRaw)
        assertEquals("USDC", d?.tokenSymbol)
    }

    @Test
    fun `accepts synonym keys`() {
        val payload = JSONObject(
            """{"dir":"in","amount":"500","symbol":"TRX","decimals":"6"}"""
        )

        val d = TxDescriptorParser.fromJson(payload)

        assertEquals("in", d?.direction)
        assertEquals("500", d?.amountRaw)
        assertEquals("TRX", d?.tokenSymbol)
        assertEquals(6, d?.tokenDecimal)
    }

    // The event name lives at `payload.type` on some frames; treating it as a direction would both
    // produce nonsense and shadow a real nested direction.
    @Test
    fun `event type is never mistaken for a direction`() {
        val payload = JSONObject(
            """{"type":"tx.new","display":{"direction":"in","tokenSymbol":"USDT"}}"""
        )

        assertEquals("in", TxDescriptorParser.fromJson(payload)?.direction)
    }

    @Test
    fun `numeric amounts survive as raw strings`() {
        val payload = JSONObject("""{"direction":"in","amountRaw":1500000,"tokenSymbol":"USDT"}""")

        assertEquals("1500000", TxDescriptorParser.fromJson(payload)?.amountRaw)
    }

    @Test
    fun `payload with no hint fields yields null`() {
        val payload = JSONObject("""{"eventId":"e1","txHash":"0xaa","networkId":"sepolia"}""")

        assertNull(TxDescriptorParser.fromJson(payload))
    }

    @Test
    fun `flat fcm map is read the same way, including dotted nesting`() {
        val flat = mapOf(
            "name" to "tx.new",
            "display.direction" to "in",
            "display.amountRaw" to "1500000",
            "display.tokenSymbol" to "USDT",
            "display.tokenDecimal" to "6"
        )

        val d = TxDescriptorParser.fromMap(flat)

        assertEquals("in", d?.direction)
        assertEquals("1500000", d?.amountRaw)
        assertEquals(6, d?.tokenDecimal)
    }

    @Test
    fun `fcm map without hint fields yields null`() {
        assertNull(TxDescriptorParser.fromMap(mapOf("name" to "tx.new", "txHash" to "0xaa")))
    }
}
