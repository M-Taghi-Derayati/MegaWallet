package com.mtd.domain.model.error

/**
 * TASK-57 / TASK-25 — scrubs anything that could identify the user or their funds out of a
 * technical error string before it can reach the screen.
 *
 * Exception messages routinely carry the very things a non-custodial wallet must never render:
 * an RPC error echoes the `from`/`to` address, a broadcast failure echoes the signed payload, a
 * derivation failure can echo a mnemonic. The user-facing *short* message never comes from here
 * (it is always curated Persian copy from [ApiErrorMessageMapper] / [ErrorMapper]); this only
 * guards the technical text shown behind the "جزئیات" dialog and written to the log.
 *
 * Pure Kotlin, no Android deps — unit-tested in `ErrorTextSanitizerTest`.
 */
object ErrorTextSanitizer {

    /** Placeholder substituted for every redacted run. */
    const val REDACTED = "[پنهان‌شده]"

    /** Technical detail is a diagnostic hint, not a log dump — keep it dialog-sized. */
    private const val MAX_LENGTH = 400

    private val PATTERNS: List<Regex> = listOf(
        // EVM address / tx hash / signed payload — any 0x-prefixed hex run.
        Regex("0[xX][0-9a-fA-F]{8,}"),
        // Bare hex blobs: raw signatures, private keys, unsigned or signed transaction bodies.
        Regex("(?<![0-9A-Za-z])[0-9a-fA-F]{32,}(?![0-9A-Za-z])"),
        // JWT / bearer-style tokens.
        Regex("(?<![0-9A-Za-z])eyJ[0-9A-Za-z_-]{8,}\\.[0-9A-Za-z_-]{8,}(?:\\.[0-9A-Za-z_-]+)?"),
        // Tron base58 addresses (always 34 chars, leading T).
        Regex("(?<![0-9A-Za-z])T[1-9A-HJ-NP-Za-km-z]{33}(?![0-9A-Za-z])"),
        // Bitcoin / Dogecoin / Litecoin base58 addresses.
        Regex("(?<![0-9A-Za-z])[139ADLM][1-9A-HJ-NP-Za-km-z]{25,39}(?![0-9A-Za-z])"),
        // bech32 (bc1…, tb1…, ltc1…).
        Regex("(?<![0-9A-Za-z])(?:bc1|tb1|ltc1)[02-9ac-hj-np-z]{11,}", RegexOption.IGNORE_CASE),
        // BIP-39 mnemonic: a run of 12 or more plain ASCII lowercase words.
        Regex("(?:\\b[a-z]{3,8}\\b[ \\t]+){11,}\\b[a-z]{3,8}\\b")
    )

    /**
     * Returns [raw] with every address/key/hash/payload-shaped run replaced by [REDACTED] and the
     * result clamped to [MAX_LENGTH]. Blank or null input yields an empty string.
     */
    fun sanitize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var out = raw.trim()
        for (pattern in PATTERNS) {
            out = pattern.replace(out, REDACTED)
        }
        return if (out.length > MAX_LENGTH) out.take(MAX_LENGTH).trimEnd() + "…" else out
    }
}
