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

    /**
     * Sign a **server-built** TRON transaction (swap/bridge legs, `family: "TVM"`).
     *
     * Two things separate this from [signRawDataHex]:
     *
     *  - **Integrity is checked, not assumed.** `txID` must equal `sha256(rawDataHex)`. A mismatch
     *    means the two fields describe different transactions, and the node validates against the
     *    bytes — so it throws rather than substituting a locally recomputed id. What gets signed
     *    has to be exactly what the server quoted and simulated.
     *  - **The recovery byte is written as 27/28** (`0x1B`/`0x1C`), which is what TronWeb emits.
     *    [signRawDataHex] writes 0/1 for the node-built transfer path; that convention is left
     *    alone rather than changed underneath a working money path.
     *
     * @param expectedTxId the `txID` exactly as the server sent it.
     * @throws IllegalStateException when [expectedTxId] does not match `sha256(rawDataHex)`.
     */
    fun signPreparedTronTx(
        rawDataHex: String,
        expectedTxId: String,
        privateKeyHex: String
    ): String
}
