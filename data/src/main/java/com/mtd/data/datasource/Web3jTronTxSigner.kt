package com.mtd.data.datasource

import com.mtd.core.utils.TronAddressConverter
import org.web3j.crypto.Credentials
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.security.MessageDigest

/**
 * Production [TronTxSigner] — secp256k1 local signing via web3j. A TRON signature is the same
 * primitive as an EVM one: `sha256(raw_data_hex)` → `Sign.signMessage` → `r||s||v`. No TRON SDK is
 * involved. The private key never leaves the device; only the assembled signed tx is broadcast.
 *
 * ⚠️ The two entry points differ **only** in the recovery byte, and deliberately so:
 * [signRawDataHex] writes 0/1 (the long-standing node-built transfer path, left as-is rather than
 * changed underneath a working money path), while [signPreparedTronTx] writes 27/28 as TronWeb
 * does, which is what §1.5 of the swap contract specifies for server-built swap/bridge legs.
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
        return sign(hash, privateKeyHex, recoveryOffset = 0)
    }

    override fun signPreparedTronTx(
        rawDataHex: String,
        expectedTxId: String,
        privateKeyHex: String
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(Numeric.hexStringToByteArray(rawDataHex))
        val computed = Numeric.toHexStringNoPrefix(digest).padStart(64, '0')

        // اختلاف یعنی `txID` و `raw_data_hex` دو تراکنشِ متفاوت را توصیف می‌کنند. نود در برابرِ
        // بایت‌ها اعتبارسنجی می‌کند، پس جایگزین‌کردنِ شناسهٔ محاسبه‌شدهٔ خودمان فقط خطا را از
        // این‌جا به زنجیره منتقل می‌کند — شکستِ صریح تنها پاسخِ درست است.
        check(computed.equals(expectedTxId.trim().removePrefix("0x"), ignoreCase = true)) {
            "TRON txID mismatch: raw_data_hex hashes to $computed but the server sent $expectedTxId"
        }

        // ⚠️ ۲۷/۲۸ (0x1B/0x1C)، نه ۰/۱ — همان چیزی که TronWeb می‌نویسد.
        return sign(digest, privateKeyHex, recoveryOffset = 27)
    }

    /**
     * `Sign.signMessage(..., needToHash = false)` بایتِ `v` را ۲۷/۲۸ برمی‌گرداند؛ [recoveryOffset]
     * تعیین می‌کند خروجی همان را نگه دارد (۲۷) یا به ۰/۱ برگرداند (۰).
     */
    private fun sign(digest: ByteArray, privateKeyHex: String, recoveryOffset: Int): String {
        val keyPair = Credentials.create(privateKeyHex.removePrefix("0x")).ecKeyPair
        val signatureData = Sign.signMessage(digest, keyPair, false)

        val r = Numeric.toHexStringNoPrefix(signatureData.r).padStart(64, '0')
        val s = Numeric.toHexStringNoPrefix(signatureData.s).padStart(64, '0')
        val recoveryId = ((signatureData.v.firstOrNull()?.toInt() ?: 27) - 27).coerceIn(0, 1)
        val v = (recoveryId + recoveryOffset).toString(16).padStart(2, '0')
        return r + s + v
    }
}
