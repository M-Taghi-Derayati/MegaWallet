package com.mtd.core.utils

object AddressRegexUtils {

    fun compileAddressRegex(patternOrLiteral: String?): Regex? {
        val raw = patternOrLiteral?.trim().orEmpty()
        if (raw.isBlank()) return null

        // Supports JS-style literals: /^...$/i
        if (raw.startsWith("/") && raw.length > 1) {
            val lastSlash = raw.lastIndexOf('/')
            if (lastSlash > 0) {
                val body = raw.substring(1, lastSlash)
                val flags = raw.substring(lastSlash + 1)
                val options = buildSet {
                    if (flags.contains('i', ignoreCase = true)) add(RegexOption.IGNORE_CASE)
                    if (flags.contains('m', ignoreCase = true)) add(RegexOption.MULTILINE)
                    if (flags.contains('s', ignoreCase = true)) add(RegexOption.DOT_MATCHES_ALL)
                }
                return runCatching { Regex(body, options) }.getOrNull()
            }
        }

        return runCatching { Regex(raw) }.getOrNull()
    }

    fun matchesAddress(patternOrLiteral: String?, address: String): Boolean {
        val normalized = address.trim()
        if (normalized.isBlank()) return false
        val regex = compileAddressRegex(patternOrLiteral) ?: return false
        return regex.matches(normalized)
    }
}
