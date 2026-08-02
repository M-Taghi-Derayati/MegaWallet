package com.mtd.data.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Blockscout returns `"symbol": null` for the junk ERC-721/1155 airdrops that land in nearly every
 * real wallet. `TokenDto.symbol` used to be a non-null `String`, which Gson silently violates — it
 * instantiates through Unsafe, so no Kotlin null-check ever runs on the field itself. The null only
 * surfaced at the first real boundary, `TokenTransferDetails.<init>`, as an NPE — and because the
 * mapper ran inside a `forEach`, one spam NFT took the entire page of history down with it.
 */
class EVMTokenTransferDtoTest {

    private val gson = Gson()

    @Test
    fun `a token transfer with a null symbol decodes instead of throwing`() {
        // Trimmed from a live Base response: the "# TrustWallet Gift" ERC-1155.
        val json = """
            {
              "timestamp": "2026-07-26T21:35:49.000000Z",
              "from": {"hash": "0x0000000000000000000000000000000000000000"},
              "to": {"hash": "0xD07F3994a98d35FEC2BCaa4A308F3Dd221cE369c"},
              "transaction_hash": "0xab225f56ff46a40a066253aa53d1bc43bc211e9401941966507cd9d7348f220b",
              "token": {
                "address_hash": "0xEB1247fe1Ab4e90d556C9A96E4C877338a86519B",
                "name": null,
                "symbol": null,
                "decimals": null,
                "type": "ERC-1155"
              },
              "total": {"token_id": "1", "value": "1"}
            }
        """.trimIndent()

        val dto = gson.fromJson(json, EVMTokenTransferDto::class.java)

        assertNull(dto.token.symbol)
        assertNull(dto.token.decimals)
        assertEquals("0xEB1247fe1Ab4e90d556C9A96E4C877338a86519B", dto.token.address)
        assertEquals("1", dto.total.value)
    }

    /** An NFT carries `token_id` and no `value`; the amount must degrade, not explode. */
    @Test
    fun `a transfer with no value at all decodes`() {
        val json = """
            {
              "timestamp": "2025-05-25T05:33:01.000000Z",
              "from": {"hash": "0x0000000000000000000000000000000000000000"},
              "to": {"hash": "0xD07F3994a98d35FEC2BCaa4A308F3Dd221cE369c"},
              "transaction_hash": "0x430c1b97433214bf5f81631e3aafa98c154c8c85ced6fc02a86bc9b886743ac9",
              "token": {"address_hash": "0xDEcB017A62c01513FD92050cAD42fA73e6C22510", "symbol": "CAKE"},
              "total": {"token_id": "665"}
            }
        """.trimIndent()

        val dto = gson.fromJson(json, EVMTokenTransferDto::class.java)

        assertEquals("CAKE", dto.token.symbol)
        assertNull(dto.total.value)
    }

    @Test
    fun `an ordinary ERC-20 transfer still decodes`() {
        val json = """
            {
              "timestamp": "2026-07-04T13:42:43.000000Z",
              "from": {"hash": "0xDeB4fE25211555F7Bf86bF370f1De80a1D76a820"},
              "to": {"hash": "0xD07F3994a98d35FEC2BCaa4A308F3Dd221cE369c"},
              "transaction_hash": "0x124df401fbb8606c7c7f1b1b3f99ab43d33e9f40fea4f49faa1695fa041860e6",
              "token": {
                "address_hash": "0x7376e361245f68f91eD38398fBf14e8D2bAdeA63",
                "symbol": "MOGA",
                "decimals": "18"
              },
              "total": {"value": "5000000000000000000"}
            }
        """.trimIndent()

        val dto = gson.fromJson(json, EVMTokenTransferDto::class.java)

        assertEquals("MOGA", dto.token.symbol)
        assertEquals("18", dto.token.decimals)
        assertEquals("5000000000000000000", dto.total.value)
    }
}
