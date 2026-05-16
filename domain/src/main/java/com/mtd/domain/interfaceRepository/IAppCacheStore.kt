package com.mtd.domain.interfaceRepository

interface IAppCacheStore {
    suspend fun <T> get(key: String, type: java.lang.reflect.Type): T?
    suspend fun <T> get(key: String, type: Class<T>): T?
    suspend fun <T> put(key: String, value: T, ttl: Long = DEFAULT_TTL)

    companion object {
        const val DEFAULT_TTL = 5 * 60 * 1000L
        const val ASSETS_TTL = 5 * 24 * 3600 * 1000000L
    }
}
