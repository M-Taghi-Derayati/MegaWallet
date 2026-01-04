package com.mtd.core.manager

import com.mtd.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدیریت متمرکز Coroutines برای محدود کردن concurrent operations
 * و جلوگیری از overload سیستم
 */
@Singleton
class CoroutineManager @Inject constructor(
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val jobLimits = ConcurrentHashMap<String, Int>()
    private val mutex = Mutex()

    /**
     * Launch یک coroutine با محدودیت تعداد همزمان
     */
    suspend fun launchLimited(
        key: String,
        limit: Int = 5,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: suspend CoroutineScope.() -> Unit
    ): Job = mutex.withLock {
        // Check current active jobs for this key prefix
        val currentActive = activeJobs.values.count { it.isActive }
        
        if (currentActive >= limit) {
            // Cancel oldest job
            activeJobs.entries.firstOrNull { it.value.isActive }?.let { (oldKey, oldJob) ->
                oldJob.cancel()
                activeJobs.remove(oldKey)
                Timber.d("Cancelled old job: $oldKey to make room for: $key")
            }
        }

        val job = applicationScope.launch(dispatcher) {
            try {
                block()
            } finally {
                activeJobs.remove(key)
            }
        }

        activeJobs[key] = job
        jobLimits[key] = limit
        
        return job
    }

    /**
     * Launch چند coroutine به صورت موازی با محدودیت
     */
    suspend fun <T> launchLimitedParallel(
        items: List<T>,
        limit: Int = 5,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: suspend (T) -> Unit
    ) = mutex.withLock {
        val chunks = items.chunked(limit)
        
        chunks.forEach { chunk ->
            coroutineScope {
                chunk.map { item ->
                    async(dispatcher) {
                        block(item)
                    }
                }.awaitAll()
            }
        }
    }

    /**
     * Cancel همه jobs با prefix خاص
     */
    fun cancelAll(keyPrefix: String) {
        activeJobs.filterKeys { it.startsWith(keyPrefix) }.forEach { (key, job) ->
            job.cancel()
            activeJobs.remove(key)
            Timber.d("Cancelled job: $key")
        }
    }

    /**
     * Cancel همه jobs
     */
    fun cancelAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        Timber.d("Cancelled all jobs")
    }

    /**
     * تعداد jobs فعال
     */
    fun getActiveJobCount(): Int = activeJobs.values.count { it.isActive }
}

