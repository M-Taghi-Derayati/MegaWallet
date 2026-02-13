package com.mtd.data.utils

import com.mtd.core.model.NetworkName
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

object AssetNormalizer {

    fun normalize(
        rawAmount: Any,
        decimals: Int,
        networkName: NetworkName
    ): BigDecimal {
        return try {
            val amount = when {
                // ۱. منطق مخصوص شبکه‌های EVM (مثل اتریوم، پالیگان، بایننس)
                // این شبکه‌ها اکثراً خروجی را به صورت Hex برمی‌گردانند
                isEvm(networkName) -> {
                    when (rawAmount) {
                        is String -> parseEvmHex(rawAmount)
                        else -> toBigDecimal(rawAmount)
                    }
                }

                // ۲. منطق مخصوص ترون
                // ترون در APIهای مختلف هم Hex (با شروع 41) و هم Decimal برمی‌گرداند
                networkName == NetworkName.TRON || networkName == NetworkName.SHASTA -> {
                    parseTronAmount(rawAmount)
                }

                // ۳. شبکه‌های UTXO مثل بیت‌کوین
                // معمولاً مقدار را به صورت عدد صحیح (Satoshi) در قالب String یا Long می‌دهند
                networkName ==NetworkName.BITCOINTESTNET || networkName ==NetworkName.BITCOIN-> {
                    toBigDecimal(rawAmount)
                }

                else -> toBigDecimal(rawAmount)
            }

            // انجام تقسیم نهایی بر اساس Decimals هر شبکه
            val divisor = BigDecimal.TEN.pow(decimals)
            amount.divide(divisor, decimals, RoundingMode.DOWN).stripTrailingZeros()

        } catch (e: Exception) {
            BigDecimal.ZERO
        }
    }

    private fun isEvm(network: NetworkName): Boolean {
        val evmNetworks = setOf(
            NetworkName.BSCTESTNET,
            NetworkName.BINANCESMARTCHAIN,
            NetworkName.POLTESTNET,
            NetworkName.SEPOLIA,
            NetworkName.ETHEREUM
        )
        return evmNetworks.contains(network)
    }

    private fun parseEvmHex(hex: String): BigDecimal {
        return if (hex.startsWith("0x")) {
            BigInteger(hex.substring(2), 16).toBigDecimal()
        } else {
            BigDecimal(hex)
        }
    }

    private fun parseTronAmount(raw: Any): BigDecimal {
        return if (raw is String && (raw.startsWith("0x") || raw.startsWith("41"))) {
            // ترون گاهی هگز با پیشوند 41 می‌دهد
            val cleanHex = if (raw.startsWith("0x")) raw.substring(2) else raw
            BigInteger(cleanHex, 16).toBigDecimal()
        } else {
            toBigDecimal(raw)
        }
    }

    private fun toBigDecimal(raw: Any): BigDecimal {
        return when (raw) {
            is BigDecimal -> raw
            is BigInteger -> raw.toBigDecimal()
            is Double -> BigDecimal(raw.toString())
            is Long -> BigDecimal(raw)
            is String -> BigDecimal(raw)
            else -> BigDecimal.ZERO
        }
    }
}