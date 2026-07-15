package com.mtd.data.config

/**
 * Verifies the secp256k1 signature attached to a `/api/v1/config/bundle` response.
 *
 * Implementations recompute the SHA-256 digest over the bundle's canonical `{version, networks,
 * assets}` payload (signature field stripped) and verify it against the pinned signer key from
 * `/api/v1/config/public-key`. A `false` result means the network bundle is NOT trusted and the
 * caller must fall back to the bundled local assets.
 */
interface ConfigSignatureVerifier {

    /**
     * @param rawBundleJson the exact, untouched JSON body returned by `/config/bundle`.
     * @return `true` only if the embedded signature is valid for the pinned public key.
     */
    suspend fun verify(rawBundleJson: String): Boolean
}
