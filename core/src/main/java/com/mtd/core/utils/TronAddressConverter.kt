package com.mtd.core.utils

import com.mtd.core.network.tron.TronUtils
import com.mtd.core.network.tron.TronUtils.Base58.decodeBase58

object TronAddressConverter {

    fun tronToEvm(address: String): String {
        val trimmed = address.trim()

        // Base58 TRON (T...)
        if (trimmed.startsWith("T")) {
            val converted = TronUtils.Base58.decodeTronAddressToEthFormat(trimmed)
            require(converted.matches(Regex("^0x[0-9a-fA-F]{40}$"))) {
                "Invalid TRON address: $address"
            }
            return converted
        }

        // Hex TRON with 41 prefix (41 + 20-byte address)
        val clean = trimmed.removePrefix("0x")
        if (clean.matches(Regex("^[0-9a-fA-F]{42}$")) &&
            clean.startsWith("41", ignoreCase = true)
        ) {
            return "0x${clean.substring(2)}"
        }

        // Already EVM address without/with 0x
        if (clean.matches(Regex("^[0-9a-fA-F]{40}$"))) {
            return "0x$clean"
        }

        throw IllegalArgumentException("Invalid TRON address: $address")
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun base58ToHex(base58Address: String): String {
        val decoded = decodeBase58(base58Address) // استفاده از یک کتابخانه Base58 معمولی
        // در ترون، ۴ بایت آخر Checksum است که باید حذف شود
        val addressBytes = decoded.copyOfRange(0, decoded.size - 4)
        return addressBytes.toHexString() // تبدیل بایت به رشته هگز
    }

    fun evmToTron(address: String): String {
        val trimmed = address.trim()

        // Already Base58 TRON
        if (trimmed.startsWith("T")) {
            // Validate format by converting once
            tronToEvm(trimmed)
            return trimmed
        }

        val clean = trimmed.removePrefix("0x")

        // TRON hex with 41 prefix
        if (clean.matches(Regex("^[0-9a-fA-F]{42}$")) &&
            clean.startsWith("41", ignoreCase = true)
        ) {
            return TronUtils.Base58.hexToBase58(clean)
        }

        // Plain EVM hex (20 bytes)
        if (clean.matches(Regex("^[0-9a-fA-F]{40}$"))) {
            return TronUtils.Base58.hexToBase58("41$clean")
        }

        throw IllegalArgumentException("Invalid EVM/TRON address: $address")
    }
}
