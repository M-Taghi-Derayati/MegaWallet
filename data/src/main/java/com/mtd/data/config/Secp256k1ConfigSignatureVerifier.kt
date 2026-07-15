package com.mtd.data.config

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.mtd.data.service.ConfigApiService
import org.bouncycastle.jce.provider.BouncyCastleProvider
import timber.log.Timber
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * secp256k1 + SHA-256 verifier for the dynamic config bundle.
 *
 * Android's bundled Conscrypt provider does NOT register the `secp256k1` curve, so
 * `AlgorithmParameters.getInstance("EC")` → `init(ECGenParameterSpec("secp256k1"))` throws
 * `InvalidParameterSpecException: Unknown EC curve name: secp256k1`. We therefore pin every EC
 * operation to a dedicated BouncyCastle provider instance (which knows secp256k1), instead of
 * relying on the default provider resolution order.
 *
 * Verification steps (mirrors the relayer's `ConfigRegistry` signer contract):
 * 1. Parse the raw bundle JSON, strip the `signature` field.
 * 2. Recompute the canonical payload — keys recursively sorted, compact, UTF-8 — over the remaining
 *    `{version, networks, assets}` tree (full fidelity, not the lossy DTO).
 * 3. SHA-256 the canonical bytes and ECDSA-verify against the pinned `/public-key`.
 *
 * The pinned public key is fetched once and cached in-memory; both compressed (33-byte) and
 * uncompressed (65-byte) secp256k1 keys are accepted, and raw `r||s` (64-byte), Ethereum-style
 * `r||s||v` (65-byte), and DER signatures are all accepted.
 */
@Singleton
class Secp256k1ConfigSignatureVerifier @Inject constructor(
    private val configApiService: ConfigApiService
) : ConfigSignatureVerifier {

    @Volatile
    private var cachedPublicKeyHex: String? = null

    override suspend fun verify(rawBundleJson: String): Boolean {
        return try {
            val root = JsonParser.parseString(rawBundleJson).asJsonObject
            val signatureHex = root.get("signature")?.takeIf { it.isJsonPrimitive }?.asString
            if (signatureHex.isNullOrBlank()) {
                Timber.w("Config bundle has no signature")
                return false
            }
            // Sign-over payload excludes the detached signature itself.
            root.remove("signature")

            val canonical = StringBuilder().also { canonicalize(root, it) }.toString()
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))

            val publicKeyHex = resolvePublicKeyHex() ?: return false
            ecdsaVerify(digest, signatureHex, publicKeyHex)
        } catch (e: Exception) {
            Timber.w(e, "Config signature verification threw; treating as invalid")
            false
        }
    }

    private suspend fun resolvePublicKeyHex(): String? {
        cachedPublicKeyHex?.let { return it }
        return try {
            val resp = configApiService.getConfigPublicKey()
            val key = resp.body()?.publicKey
            if (resp.isSuccessful && !key.isNullOrBlank()) {
                cachedPublicKeyHex = key
                key
            } else {
                Timber.w("Could not fetch config public key (HTTP ${resp.code()})")
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "Fetching config public key failed")
            null
        }
    }

    // --- Canonical JSON (recursively key-sorted, compact) ------------------------------------------

    private fun canonicalize(element: JsonElement, sb: StringBuilder) {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                sb.append('{')
                obj.keySet().sorted().forEachIndexed { index, key ->
                    if (index > 0) sb.append(',')
                    appendJsonString(key, sb)
                    sb.append(':')
                    canonicalize(obj.get(key), sb)
                }
                sb.append('}')
            }

            element.isJsonArray -> {
                sb.append('[')
                element.asJsonArray.forEachIndexed { index, item ->
                    if (index > 0) sb.append(',')
                    canonicalize(item, sb)
                }
                sb.append(']')
            }

            element.isJsonNull -> sb.append("null")

            else -> {
                val primitive = element.asJsonPrimitive
                when {
                    primitive.isString -> appendJsonString(primitive.asString, sb)
                    primitive.isBoolean -> sb.append(primitive.asBoolean.toString())
                    // Number — preserve the original textual representation (Gson keeps it verbatim).
                    else -> sb.append(primitive.asString)
                }
            }
        }
    }

    private fun appendJsonString(value: String, sb: StringBuilder) {
        sb.append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    // --- secp256k1 ECDSA verification (JDK / Conscrypt) -------------------------------------------

    private fun ecdsaVerify(digest: ByteArray, signatureHex: String, publicKeyHex: String): Boolean {
        // Pin to the BouncyCastle provider instance: the default Android (Conscrypt) provider does
        // not recognize the "secp256k1" curve name and would throw InvalidParameterSpecException.
        val params = AlgorithmParameters.getInstance("EC", BC).apply {
            init(ECGenParameterSpec("secp256k1"))
        }
        val ecSpec = params.getParameterSpec(ECParameterSpec::class.java)
        val point = decodePoint(hexToBytes(publicKeyHex))
        val publicKey = KeyFactory.getInstance("EC", BC)
            .generatePublic(ECPublicKeySpec(point, ecSpec))

        val derSignature = toDerSignature(hexToBytes(signatureHex))

        // The digest is already SHA-256; verify the raw digest with NONEwithECDSA so we don't double-hash.
        return Signature.getInstance("NONEwithECDSA", BC).run {
            initVerify(publicKey)
            update(digest)
            verify(derSignature)
        }
    }

    /** Decode an uncompressed (0x04) or compressed (0x02/0x03) secp256k1 point. */
    private fun decodePoint(encoded: ByteArray): ECPoint {
        return when {
            encoded.size == 65 && encoded[0].toInt() == 0x04 -> {
                val x = BigInteger(1, encoded.copyOfRange(1, 33))
                val y = BigInteger(1, encoded.copyOfRange(33, 65))
                ECPoint(x, y)
            }

            encoded.size == 33 && (encoded[0].toInt() == 0x02 || encoded[0].toInt() == 0x03) -> {
                val x = BigInteger(1, encoded.copyOfRange(1, 33))
                decompressPoint(x, encoded[0].toInt() == 0x03)
            }

            else -> throw IllegalArgumentException("Unsupported public key encoding (${encoded.size} bytes)")
        }
    }

    /** y^2 = x^3 + 7 (mod p); p ≡ 3 (mod 4) ⇒ sqrt = v^((p+1)/4). */
    private fun decompressPoint(x: BigInteger, yOdd: Boolean): ECPoint {
        val ySquared = x.modPow(BigInteger.valueOf(3), SECP256K1_P)
            .add(SECP256K1_B).mod(SECP256K1_P)
        var y = ySquared.modPow(SECP256K1_P.add(BigInteger.ONE).shiftRight(2), SECP256K1_P)
        if (y.testBit(0) != yOdd) {
            y = SECP256K1_P.subtract(y)
        }
        return ECPoint(x, y)
    }

    /**
     * Accept a raw 64-byte `r||s` signature, an Ethereum-style 65-byte `r||s||v` signature (the
     * trailing recovery byte is dropped — it is not part of the ECDSA verification), or pass a
     * DER-encoded one through unchanged.
     */
    private fun toDerSignature(signature: ByteArray): ByteArray {
        val rs = when (signature.size) {
            64 -> signature
            65 -> signature.copyOfRange(0, 64) // strip the recovery id (v); verify uses only r,s
            else -> return signature // already DER (or unexpected length → let verify fail)
        }
        val r = BigInteger(1, rs.copyOfRange(0, 32))
        val s = BigInteger(1, rs.copyOfRange(32, 64))
        val rEncoded = encodeDerInteger(r)
        val sEncoded = encodeDerInteger(s)
        val seqLen = rEncoded.size + sEncoded.size
        return byteArrayOf(0x30, seqLen.toByte()) + rEncoded + sEncoded
    }

    private fun encodeDerInteger(value: BigInteger): ByteArray {
        // toByteArray() yields the minimal big-endian two's-complement form; for positive values with
        // the high bit set it prepends a 0x00, which is exactly what DER INTEGER requires.
        val bytes = value.toByteArray()
        return byteArrayOf(0x02, bytes.size.toByte()) + bytes
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.removePrefix("0x").trim()
        require(clean.length % 2 == 0) { "Odd-length hex" }
        return ByteArray(clean.length / 2) { i ->
            ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
    }

    private companion object {
        /**
         * A private BouncyCastle provider instance used only for this verifier's EC operations.
         * Passing the instance to getInstance(..) avoids mutating the global JVM provider list and
         * sidesteps Conscrypt, which cannot resolve the secp256k1 curve.
         */
        val BC: BouncyCastleProvider = BouncyCastleProvider()

        // secp256k1 field prime and curve constant b (a = 0).
        val SECP256K1_P: BigInteger = BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16
        )
        val SECP256K1_B: BigInteger = BigInteger.valueOf(7)
    }
}
