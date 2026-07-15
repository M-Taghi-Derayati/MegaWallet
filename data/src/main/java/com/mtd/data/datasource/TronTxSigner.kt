package com.mtd.data.datasource

/**
 * Signing seam for PROXY-mode TRON sends — mirrors [EvmTxSigner]. Keeps web3j / BouncyCastle and the
 * TRON address codec out of [ProxyChainDataSource] so it can be unit tested without the native crypto
 * stack. Production default is [Web3jTronTxSigner]; tests inject a fake.
 *
 * The signing model is identical to the DIRECT path: the proxy `/prepare` returns the node-built
 * unsigned tx; the client computes `sha256(raw_data_hex)`, signs it, and appends the signature.
 */
interface TronTxSigner {

    /** Derive the base58 (`T…`) sender address from a raw private key (for the proxy `prepare`). */
    fun deriveTronAddress(privateKeyHex: String): String

    /** Sign `sha256(rawDataHex)` with secp256k1, returning the 65-byte `r||s||v` signature hex. */
    fun signRawDataHex(rawDataHex: String, privateKeyHex: String): String
}
