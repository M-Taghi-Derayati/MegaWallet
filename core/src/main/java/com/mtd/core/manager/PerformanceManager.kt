package com.mtd.core.manager

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * مدیریت و tracking پرفورمنس در کل اپلیکیشن
 */
@Singleton
class PerformanceManager @Inject constructor() {
    private val performanceMetrics = ConcurrentHashMap<String, PerformanceMetric>()

    /**
     * شروع tracking یک operation
     */
    fun startTracking(operation: String): PerformanceTracker {
        return PerformanceTracker(operation, this)
    }

    /**
     * ثبت یک metric
     */
    fun recordMetric(operation: String, duration: Long, success: Boolean) {
        val metric = performanceMetrics.getOrPut(operation) {
            PerformanceMetric(operation)
        }
        metric.record(duration, success)

        // Alert if slow
        if (duration > metric.threshold) {
            Timber.w("Slow operation detected: $operation took ${duration}ms (threshold: ${metric.threshold}ms)")
        }
    }

    /**
     * دریافت metrics
     */
    fun getMetrics(): Map<String, PerformanceMetric> {
        return performanceMetrics.toMap()
    }

    /**
     * دریافت metric برای یک operation خاص
     */
    fun getMetric(operation: String): PerformanceMetric? {
        return performanceMetrics[operation]
    }

    /**
     * پاک کردن metrics
     */
    fun clearMetrics() {
        performanceMetrics.clear()
    }
}

/**
 * Performance Tracker برای tracking یک operation
 */
class PerformanceTracker(
    private val operation: String,
    private val performanceManager: PerformanceManager
) {
    private var startTime: Long = 0

    fun start() {
        startTime = System.currentTimeMillis()
    }

    fun stop(success: Boolean = true) {
        if (startTime == 0L) {
            Timber.w("PerformanceTracker stopped without starting: $operation")
            return
        }
        
        val duration = System.currentTimeMillis() - startTime
        performanceManager.recordMetric(operation, duration, success)
        startTime = 0
    }
}

/**
 * Performance Metric برای یک operation
 */
data class PerformanceMetric(
    val operation: String,
    var count: Long = 0,
    var totalDuration: Long = 0,
    var minDuration: Long = Long.MAX_VALUE,
    var maxDuration: Long = 0,
    var successCount: Long = 0,
    var failureCount: Long = 0,
    val threshold: Long = 1000 // 1 second default threshold
) {
    val averageDuration: Long
        get() = if (count > 0) totalDuration / count else 0

    val successRate: Double
        get() = if (count > 0) successCount.toDouble() / count else 0.0

    fun record(duration: Long, success: Boolean) {
        count++
        totalDuration += duration
        minDuration = minOf(minDuration, duration)
        maxDuration = maxOf(maxDuration, duration)
        
        if (success) {
            successCount++
        } else {
            failureCount++
        }
    }
}

