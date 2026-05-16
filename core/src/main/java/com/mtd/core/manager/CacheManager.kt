package com.mtd.core.manager

import android.content.Context
import com.google.gson.Gson
import com.mtd.domain.interfaceRepository.IAppCacheStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدیریت متمرکز Cache برای کل اپلیکیشن
 * از Memory Cache و Disk Cache استفاده می‌کند
 */
@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : IAppCacheStore {
    // Memory Cache با TTL
    private val memoryCache = ConcurrentHashMap<String, CacheEntry<Any>>()
    
    // Disk Cache Directory
    private val cacheDir = File(context.cacheDir, "app_cache").apply {
        if (!exists()) mkdirs()
    }

    override suspend fun <T> get(
        key: String,
        type: java.lang.reflect.Type
    ): T? = withContext(Dispatchers.IO) {
        Timber.d("🔍 CacheManager.get: key=$key, type=$type")
        
        // 1. Check memory cache first
        memoryCache[key]?.let { entry ->
            if (!entry.isExpired()) {
                Timber.d("✅ Found in memory cache: $key")
                @Suppress("UNCHECKED_CAST")
                return@withContext entry.data as? T
            } else {
                Timber.d("⏰ Memory cache expired: $key")
                memoryCache.remove(key)
            }
        }

        // 2. Check disk cache
        val diskFile = File(cacheDir, key)
        if (diskFile.exists()) {
            try {
                val json = diskFile.readText()
                val cached = gson.fromJson<T>(json, type)
                
                if (cached != null) {
                    @Suppress("UNCHECKED_CAST")
                    memoryCache[key] = CacheEntry(cached as Any, System.currentTimeMillis() + DEFAULT_TTL)
                    return@withContext cached
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Error reading from disk cache: $key")
                diskFile.delete()
            }
        }

        return@withContext null
    }

    override suspend fun <T> get(
        key: String,
        type: Class<T>
    ): T? = get(key, type as java.lang.reflect.Type)

    /**
     * ذخیره داده در cache
     */
    override suspend fun <T> put(
        key: String,
        value: T,
        ttl: Long
    ) = withContext(Dispatchers.IO) {
        try {
            Timber.d("💾 CacheManager.put: key=$key, ttl=${ttl}ms")
            
            // 1. Put in memory cache
            @Suppress("UNCHECKED_CAST")
            memoryCache[key] = CacheEntry(value as Any, System.currentTimeMillis() + ttl)
            Timber.d("✅ Added to memory cache: $key")

            // 2. Put in disk cache
            val diskFile = File(cacheDir, key)
            val json = gson.toJson(value)
            Timber.d("📄 JSON length: ${json.length} chars")
            diskFile.writeText(json)
            Timber.i("✅ Successfully wrote to disk cache: $key, file size: ${diskFile.length()} bytes")
        } catch (e: Exception) {
            Timber.e(e, "❌ Error writing to cache: $key")
            e.printStackTrace()
        }
    }

    /**
     * حذف یک key از cache
     */
    suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        memoryCache.remove(key)
        File(cacheDir, key).delete()
    }

    /**
     * پاک کردن همه cache
     */
    suspend fun clear() = withContext(Dispatchers.IO) {
        memoryCache.clear()
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * پاک کردن cache های expired
     */
    suspend fun clearExpired() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        memoryCache.entries.removeAll { it.value.expiresAt < now }
        
        // Disk cache cleanup می‌تواند در background انجام شود
    }

    /**
     * بررسی وجود key در cache
     */
    suspend fun contains(key: String): Boolean = withContext(Dispatchers.IO) {
        // Check memory
        memoryCache[key]?.let { entry ->
            if (!entry.isExpired()) return@withContext true
            memoryCache.remove(key)
        }

        // Check disk
        File(cacheDir, key).exists()
    }

   companion object {
        private const val DEFAULT_TTL = 5 * 60 * 1000L // 5 minutes
         const val ASSETS_TTL = 5 *24* 3600 * 1000000L // 5 Days
    }

    /**
     * Entry در cache با TTL
     */
    private data class CacheEntry<T>(
        val data: T,
        val expiresAt: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt
    }
}

