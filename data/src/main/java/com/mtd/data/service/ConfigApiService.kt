package com.mtd.data.service

import com.mtd.data.dto.ConfigBundleDto
import com.mtd.data.dto.ConfigPublicKeyDto
import com.mtd.data.dto.ConfigVersionDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET

/**
 * Phase 3 — dynamic config. The relayer serves the signed networks/assets catalog so the client can
 * refresh its chain/token list without an app update.
 */
interface ConfigApiService {

    /** Full signed bundle `{ version, networks[], assets[], signature }`. Header `X-Config-Version`. */
    @GET("api/v1/config/bundle")
    suspend fun getConfigBundle(): Response<ConfigBundleDto>

    /**
     * Same bundle as raw bytes — required for signature verification. The secp256k1 signature is over
     * the server's exact canonical payload, so the digest must be recomputed from the untouched body
     * (a Gson round-trip would drop fields the DTO doesn't model and break the signature).
     */
    @GET("api/v1/config/bundle")
    suspend fun getConfigBundleRaw(): Response<ResponseBody>

    /** Lightweight version probe `{ version }` — used to skip a full bundle fetch when unchanged. */
    @GET("api/v1/config/version")
    suspend fun getConfigVersion(): Response<ConfigVersionDto>

    /** secp256k1 signer info for pinning; verify the bundle [signature] against this key. */
    @GET("api/v1/config/public-key")
    suspend fun getConfigPublicKey(): Response<ConfigPublicKeyDto>
}
