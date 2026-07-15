package com.mtd.domain.interfaceRepository

interface IAppCacheStore {
    suspend fun <T> get(key: String, type: java.lang.reflect.Type): T?
    suspend fun <T> get(key: String, type: Class<T>): T?
    suspend fun <T> put(key: String, value: T, ttl: Long = DEFAULT_TTL)

    companion object {
        const val DEFAULT_TTL = 5 * 60 * 1000L // 5 minutes
        // TASK-20: was `* 1000000L`, which made the "5 days" TTL ≈13,700 years (assets never expired).
        // Milliseconds = days * 24 * 3600 * 1000.
        const val ASSETS_TTL = 5L * 24 * 3600 * 1000 // 5 days
    }
}
