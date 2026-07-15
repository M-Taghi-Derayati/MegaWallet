package com.mtd.data.dto

import com.google.gson.GsonBuilder
import com.mtd.data.network.wire.BigIntegerStringAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class HistoryItemDtoParserTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(BigInteger::class.java, BigIntegerStringAdapter())
        .registerTypeAdapter(HistoryItemDto::class.java, HistoryItemDtoDeserializer())
        .create()

    private fun parse(json: String) = gson.fromJson(json, HistoryItemDto::class.java)

    @Test fun `evm token transfer parses with BigInteger precision`() {
        val item = parse("""{"type":"evm","hash":"0x1","network":"ETHEREUM","status":"CONFIRMED",
            "timestamp":1717372800,"isOutgoing":true,"valueRaw":"250000000",
            "feeRaw":"21000000000000","gasPriceRaw":"1000000000","tokenSymbol":"USDC","tokenDecimals":6}""")
        assertTrue(item is HistoryItemDto.EvmHistoryDto)
        item as HistoryItemDto.EvmHistoryDto
        assertEquals(BigInteger("250000000"), item.valueRaw)
        assertEquals(BigInteger("1000000000"), item.gasPriceRaw)
        assertEquals("USDC", item.tokenSymbol)
    }

    @Test fun `tron parses and preserves over-Long value`() {
        val big = BigInteger.valueOf(Long.MAX_VALUE) + BigInteger.ONE
        val item = parse("""{"type":"tron","hash":"t1","network":"TRON","status":"CONFIRMED",
            "timestamp":1,"isOutgoing":false,"valueRaw":"$big","feeRaw":"27000000","energyUsed":64895}""")
        assertTrue(item is HistoryItemDto.TronHistoryDto)
        assertEquals(big, (item as HistoryItemDto.TronHistoryDto).valueRaw)
        assertEquals(64895L, item.energyUsed)
    }

    @Test fun `bitcoin parses fee rate`() {
        val item = parse("""{"type":"bitcoin","hash":"f1","network":"BITCOIN","status":"PENDING",
            "timestamp":0,"isOutgoing":true,"valueRaw":"4500000","feeRaw":"3500","feeRateSatPerVByte":12}""")
        assertTrue(item is HistoryItemDto.BitcoinHistoryDto)
        assertEquals(12L, (item as HistoryItemDto.BitcoinHistoryDto).feeRateSatPerVByte)
        assertEquals(BigInteger("4500000"), item.valueRaw)
    }
}
