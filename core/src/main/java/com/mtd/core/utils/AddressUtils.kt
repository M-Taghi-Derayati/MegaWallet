package com.mtd.core.utils

/**
 * Utility for blockchain address operations.
 */
object AddressUtils {

    /**
     * Shortens a blockchain address for display.
     * Example: 0x1234567890abcdef1234567890abcdef12345678 -> 0x1234...5678
     */
    fun shortenAddress(address: String?): String {
        if (address.isNullOrBlank()) return ""
        val trimmed = address.trim()
        if (trimmed.length <= 10) return trimmed
        
        // Format: First 6 chars + ... + last 6 chars
        return "${trimmed.take(6)}...${trimmed.takeLast(6)}"
    }
}
