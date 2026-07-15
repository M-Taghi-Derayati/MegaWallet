package com.mtd.data.datasource

import com.mtd.core.utils.TronAddressConverter
import org.web3j.crypto.Credentials
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.security.MessageDigest

/**
 * Production [TronTxSigner] — secp256k1 local signing via web3j, reusing the exact primitives the
 * DIRECT `TronDataSource` uses (sha256 of `raw_data_hex` → `Sign.signMessage` → `r||s||v` hex). The
 * private key never leaves the device; only the assembled signed tx is relayed through `/broadcast`.
 *
 * Loaded lazily (only when the default signer is actually used), so unit tests that inject a fake
 * never load BouncyCastle.
 */
object Web3jTronTxSigner : TronTxSigner {

    override fun deriveTronAddress(privateKeyHex: String): String {
        val evmAddress = Credentials.create(privateKeyHex.removePrefix("0x")).address
        return TronAddressConverter.evmToTron(evmAddress)
    }

    override fun signRawDataHex(rawDataHex: String, privateKeyHex: String): String {
        val data = Numeric.hexStringToByteArray(rawDataHex)
        val hash = MessageDigest.getInstance("SHA-256").digest(data)
        val keyPair = Credentials.create(privateKeyHex.removePrefix("0x")).ecKeyPair
        val signatureData = Sign.signMessage(hash, keyPair, false)

        val r = Numeric.toHexStringNoPrefix(signatureData.r).padStart(64, '0')
        val s = Numeric.toHexStringNoPrefix(signatureData.s).padStart(64, '0')
        val recoveryId = ((signatureData.v.firstOrNull()?.toInt() ?: 27) - 27).coerceIn(0, 1)
        val v = recoveryId.toString(16).padStart(2, '0')
        return r + s + v
    }
}
