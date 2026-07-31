package com.mtd.data.datasource

import com.mtd.domain.model.UtxoInput

/**
 * Builder seam for PROXY-mode UTXO sends. The proxy `/prepare` returns only the spendable UTXO set
 * (no scriptPubKey, no change output, no absolute fee, no coin selection — Q2.12–14), so the client
 * performs coin selection, change computation, fee math, assembly, and signing itself with bitcoinj.
 *
 * Kept behind this seam so [ProxyChainDataSource] stays free of bitcoinj for unit tests; the
 * production impl is [BitcoinjUtxoTxBuilder] (built per-network by the data-source factory).
 */
interface UtxoTxBuilder {

    /** Derive the network-appropriate sender address from a raw private key (for the proxy prepare). */
    fun deriveAddress(privateKeyHex: String): String

    /**
     * Select inputs from [utxos], build outputs (recipient + change), sign, and return the final
     * signed raw tx hex. Throws on insufficient funds.
     */
    fun buildSignedTx(
        privateKeyHex: String,
        recipient: String,
        amountSat: Long,
        feeRateSatPerVByte: Long,
        utxos: List<UtxoInput>
    ): String
}

