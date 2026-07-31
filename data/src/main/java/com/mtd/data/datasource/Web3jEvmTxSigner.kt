package com.mtd.data.datasource

import com.mtd.domain.model.PreparedEvmTx
import com.mtd.domain.model.TransactionParams
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigInteger

/**
 * Production [EvmTxSigner] — web3j local signing (secp256k1 via BouncyCastle). The private key never
 * leaves the device; only the resulting `rawSignedTx` is relayed through the proxy `/broadcast`.
 *
 * This is the only place in the PROXY path that touches the crypto stack, and it is loaded lazily
 * (only when the default signer is actually used), so unit tests that inject a fake signer never
 * load BouncyCastle.
 */
object Web3jEvmTxSigner : EvmTxSigner {

    override fun deriveAddress(privateKeyHex: String): String =
        Credentials.create(privateKeyHex.removePrefix("0x")).address

    override fun signRawTransaction(
        privateKeyHex: String,
        params: TransactionParams.Evm,
        nonce: BigInteger,
        chainId: Long
    ): String {
        val credentials = Credentials.create(privateKeyHex.removePrefix("0x"))
        val raw = RawTransaction.createTransaction(
            nonce, params.gasPrice, params.gasLimit, params.to, params.amount, params.data ?: ""
        )
        val signed = TransactionEncoder.signMessage(raw, chainId, credentials)
        return Numeric.toHexString(signed)
    }

    override fun signPreparedTransaction(privateKeyHex: String, tx: PreparedEvmTx): String {
        val credentials = Credentials.create(privateKeyHex.removePrefix("0x"))
        val data = tx.data.ifBlank { "" }

        // Type-2 (EIP-1559): chainId is embedded in the RawTransaction; sign without the chainId arg.
        val signed = if (tx.type == 2 && tx.maxFeePerGas != null && tx.maxPriorityFeePerGas != null) {
            val raw = RawTransaction.createTransaction(
                tx.chainId,
                tx.nonce,
                tx.gasLimit,
                tx.to,
                tx.value,
                data,
                tx.maxPriorityFeePerGas,
                tx.maxFeePerGas
            )
            TransactionEncoder.signMessage(raw, credentials)
        } else {
            // Legacy (type-0): EIP-155 chainId is applied at sign time.
            val gasPrice = tx.gasPrice
                ?: throw IllegalStateException("Legacy prepared tx is missing gasPrice")
            val raw = RawTransaction.createTransaction(
                tx.nonce, gasPrice, tx.gasLimit, tx.to, tx.value, data
            )
            TransactionEncoder.signMessage(raw, tx.chainId, credentials)
        }
        return Numeric.toHexString(signed)
    }
}
