package com.mtd.data.utils

import com.mtd.domain.model.core.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

/**
 * TASK-53 — [AssetNormalizer] روی خانوادهٔ شبکه کلید می‌خورد، نه روی نام شبکه.
 * نکتهٔ اصلیِ این تست: یک زنجیرهٔ EVM که سرور اضافه می‌کند (و در هیچ لیستِ دستی نیست)
 * باید دقیقاً مثل سپولیا/BSC رفتار کند.
 */
class AssetNormalizerTest {

    @Test
    fun `EVM hex balance is parsed as base-16`() {
        // 0x0de0b6b3a7640000 = 1e18 wei = 1.0
        val result = AssetNormalizer.normalize("0x0de0b6b3a7640000", 18, NetworkType.EVM)
        assertEquals(BigDecimal.ONE.stripTrailingZeros(), result)
    }

    @Test
    fun `EVM decimal string balance is parsed as base-10`() {
        val result = AssetNormalizer.normalize("1000000000000000000", 18, NetworkType.EVM)
        assertEquals(BigDecimal.ONE.stripTrailingZeros(), result)
    }

    @Test
    fun `TVM hex with 41 prefix is parsed as base-16`() {
        // 0x41 = 65 sun
        val result = AssetNormalizer.normalize("41", 6, NetworkType.TVM)
        assertEquals(BigDecimal("0.000065").stripTrailingZeros(), result)
    }

    @Test
    fun `TVM decimal string is parsed as base-10`() {
        val result = AssetNormalizer.normalize("1500000", 6, NetworkType.TVM)
        assertEquals(BigDecimal("1.5").stripTrailingZeros(), result)
    }

    @Test
    fun `UTXO and BITCOIN satoshi strings are base-10, never hex`() {
        listOf(NetworkType.UTXO, NetworkType.BITCOIN).forEach { type ->
            val result = AssetNormalizer.normalize("150000000", 8, type)
            assertEquals("family=$type", BigDecimal("1.5").stripTrailingZeros(), result)
        }
    }

    @Test
    fun `BigInteger input is family-independent`() {
        val raw = BigInteger("2500000000000000000")
        NetworkType.entries.forEach { type ->
            assertEquals(
                "family=$type",
                BigDecimal("2.5").stripTrailingZeros(),
                AssetNormalizer.normalize(raw, 18, type)
            )
        }
    }

    @Test
    fun `malformed input degrades to zero rather than throwing`() {
        assertEquals(BigDecimal.ZERO, AssetNormalizer.normalize("not-a-number", 18, NetworkType.EVM))
    }
}
