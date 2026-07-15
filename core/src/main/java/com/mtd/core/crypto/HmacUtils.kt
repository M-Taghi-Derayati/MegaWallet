package com.mtd.core.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Device attestation HMAC helper — matches the server's two-level scheme
 * (`services/deviceAttestation.js`, `DEVICE_ATTEST_METHOD = "hmac"`):
 *
 * ```
 * deviceKey            = HMAC-SHA256(key = masterSecret, msg = deviceId)          // RAW bytes
 * attestationSignature = HMAC-SHA256(key = deviceKey,     msg = "$nonce-$deviceId") // lowercase hex
 * ```
 *
 * - `masterSecret` is the app-embedded `BuildConfig.DEVICE_ATTEST_HMAC_SECRET`; it MUST byte-for-byte
 *   equal the server's `DEVICE_ATTEST_HMAC_SECRET`, or `/api/auth/verify` rejects the device.
 * - `nonce` is the lowercase-hex challenge from `POST /api/auth/device-challenge` (no hyphens); the
 *   message separator is a single hyphen. Example: nonce `a1b2c3`, deviceId `android-998877`
 *   ⇒ message `"a1b2c3-android-998877"`.
 * - The intermediate `deviceKey` is consumed as **raw bytes** (never hex-encoded) as the key of the
 *   second HMAC — this is the part the previous single-level implementation got wrong.
 *
 * TASK-04. Only device-bound features (gas-credit quote/relay, Mystery Box) require attestation;
 * baseline login and normal relay work "device-less" without it.
 */
object HmacUtils {

    private const val HMAC_SHA256 = "HmacSHA256"

    /** Full two-level device-attestation signature as lowercase hex. See the class doc. */
    fun generateDeviceAttestation(masterSecret: String, deviceId: String, nonce: String): String {
        val deviceKey = hmacRaw(masterSecret.toByteArray(Charsets.UTF_8), deviceId.toByteArray(Charsets.UTF_8))
        val signature = hmacRaw(deviceKey, "$nonce-$deviceId".toByteArray(Charsets.UTF_8))
        return signature.toHex()
    }

    private fun hmacRaw(key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(key, HMAC_SHA256))
        return mac.doFinal(message)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
