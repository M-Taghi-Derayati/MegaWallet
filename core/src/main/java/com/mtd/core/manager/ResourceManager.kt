package com.mtd.core.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import okhttp3.WebSocket
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدیریت متمرکز منابع (Resources) در کل اپلیکیشن
 * برای جلوگیری از memory leak و cleanup خودکار
 */
@Singleton
class ResourceManager @Inject constructor() {
    private val activeResources = ConcurrentHashMap<String, Resource>()

    /**
     * ثبت یک resource
     */
    fun registerResource(id: String, resource: Resource) {
        activeResources[id] = resource
        Timber.d("Resource registered: $id")
    }

    /**
     * حذف یک resource
     */
    fun unregisterResource(id: String) {
        activeResources.remove(id)?.let { resource ->
            resource.cleanup()
            Timber.d("Resource unregistered and cleaned: $id")
        }
    }

    /**
     * Cleanup همه resources
     */
    fun cleanupAll() {
        Timber.d("Cleaning up ${activeResources.size} resources")
        activeResources.values.forEach { it.cleanup() }
        activeResources.clear()
    }

    /**
     * Cleanup resources با prefix خاص
     */
    fun cleanupByPrefix(prefix: String) {
        activeResources.filterKeys { it.startsWith(prefix) }.forEach { (id, resource) ->
            resource.cleanup()
            activeResources.remove(id)
            Timber.d("Resource cleaned by prefix: $id")
        }
    }

    /**
     * Interface برای resources
     */
    interface Resource {
        fun cleanup()
    }
}

/**
 * Resource برای WebSocket
 */
class WebSocketResource(
    private val webSocket: WebSocket?,
    private val scope: CoroutineScope
) : ResourceManager.Resource {
    override fun cleanup() {
        try {
            webSocket?.close(1000, "Resource cleanup")
        } catch (e: Exception) {
            Timber.e(e, "Error closing WebSocket")
        }
        
        try {
            scope.cancel()
        } catch (e: Exception) {
            Timber.e(e, "Error cancelling scope")
        }
    }
}

/**
 * Resource برای Timer
 */
class TimerResource(
    private val timer: java.util.Timer?
) : ResourceManager.Resource {
    override fun cleanup() {
        try {
            timer?.cancel()
        } catch (e: Exception) {
            Timber.e(e, "Error cancelling timer")
        }
    }
}

/**
 * Resource برای CoroutineScope
 */
class CoroutineScopeResource(
    private val scope: CoroutineScope
) : ResourceManager.Resource {
    override fun cleanup() {
        try {
            scope.cancel()
        } catch (e: Exception) {
            Timber.e(e, "Error cancelling coroutine scope")
        }
    }
}

