package com.mtd.data.repository.auth

import com.mtd.domain.interfaceRepository.ITokenStore

/**
 * No-session [ITokenStore]. Used only as the default argument for manual (test) construction of the
 * OkHttpClient; Hilt always injects the real [SecureTokenStore] in production. With this store the
 * [com.mtd.data.network.interceptor.AuthInterceptor] is a clean no-op (the relayer accepts
 * `optionalAuth`), which is exactly what the un-authenticated integration tests want.
 */
object NoOpTokenStore : ITokenStore {
    override fun getTokenDevice(): String? = null
    override fun getDeviceId(): String? = null
    override fun isTokenValid(nowEpochSec: Long): Boolean = false
    override fun getExpiresAtEpochSec(): Long? = null
    override fun save(token: String, expiresAtEpochSec: Long, deviceId: String?) = Unit
    override fun clear() = Unit
}
