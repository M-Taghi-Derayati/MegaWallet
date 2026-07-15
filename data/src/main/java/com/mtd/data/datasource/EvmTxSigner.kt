package com.mtd.data.datasource

import com.mtd.domain.model.TransactionParams
import java.math.BigInteger

/**
 * Signing seam for PROXY-mode EVM sends.
 *
 * Keeps web3j / BouncyCastle out of [ProxyChainDataSource] itself, so the data source can be unit
 * tested without loading the native crypto stack (which is class-version sensitive to the test JVM).
 * Production default is [Web3jEvmTxSigner]; tests inject a fake.
 */
interface EvmTxSigner {

    /** Derive the EVM sender address from a raw private key (for the proxy `prepare` request). */
    fun deriveAddress(privateKeyHex: String): String

    /** Build + locally sign the raw transaction, returning the `0x…`-prefixed signed tx hex. */
    fun signRawTransaction(
        privateKeyHex: String,
        params: TransactionParams.Evm,
        nonce: BigInteger,
        chainId: Long
    ): String

    /**
     * Sign a server-prepared EVM transaction (proxy `/prepare`), honoring `type`: 2 → EIP-1559
     * (`maxFeePerGas`/`maxPriorityFeePerGas`), 0 → legacy (`gasPrice`). The backend emits type-2 on
     * Ethereum/Base/Arbitrum and type-0 on chains without a priority fee (e.g. BSC), so a legacy-only
     * signer is wrong — we branch on [PreparedEvmTx.type]. Returns the `0x…`-prefixed signed RLP.
     *
     * Default throws so non-send fakes (history/balance tests) need not implement it.
     */
    fun signPreparedTransaction(privateKeyHex: String, tx: PreparedEvmTx): String {
        throw NotImplementedError("signPreparedTransaction not implemented by this signer")
    }
}

/**
 * Decoded form of the proxy `/prepare` EVM `transaction` block (hex quantities already parsed to
 * [BigInteger]). [type] 2 = EIP-1559, 0 = legacy.
 */
data class PreparedEvmTx(
    val to: String,
    val value: BigInteger,
    val data: String,
    val nonce: BigInteger,
    val gasLimit: BigInteger,
    val chainId: Long,
    val type: Int,
    val gasPrice: BigInteger? = null,
    val maxFeePerGas: BigInteger? = null,
    val maxPriorityFeePerGas: BigInteger? = null
)
