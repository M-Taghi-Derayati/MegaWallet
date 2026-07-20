package com.mtd.core.utils

/**
 * TASK-24 — single home for the plain hex ⇄ bytes helpers that were copy-pasted, byte-identical, into
 * [com.mtd.core.network.bitcoin.AbstractUtxoNetwork], `BitcoinDataSource`, and `BitcoinjUtxoTxBuilder`.
 *
 * These operate on **plain** hex only: no `0x` prefix, lowercase output, even-length input assumed (each
 * byte is two hex chars). They intentionally do NOT strip `0x`/whitespace or validate — the Tron/EIP-712
 * and signature-verifier paths keep their own prefix-aware decoders, which are a different contract.
 */

/** Lowercase hex string of the raw bytes, no `0x` prefix (`[0xAB, 0x01] → "ab01"`). */
fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

/** Raw bytes from a plain, even-length hex string, no `0x` prefix (`"ab01" → [0xAB, 0x01]`). */
fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
