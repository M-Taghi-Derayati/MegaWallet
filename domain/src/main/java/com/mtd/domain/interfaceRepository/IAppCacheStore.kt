package com.mtd.domain.interfaceRepository

interface IAppCacheStore {
    suspend fun <T> get(key: String, type: java.lang.reflect.Type): T?
    suspend fun <T> get(key: String, type: Class<T>): T?
    suspend fun <T> put(key: String, value: T, ttl: Long = DEFAULT_TTL)

    /**
     * پاک‌کردنِ کلِ کش — حافظه و دیسک.
     *
     * برای وقتی است که آخرین کیف‌پول حذف می‌شود: مانده‌ی موجودی‌ها و تاریخچه و قیمت‌ها به
     * کیف‌پولی اشاره می‌کنند که دیگر وجود ندارد، و نگه‌داشتنشان یعنی نصبِ بعدی داده‌ی کاربرِ
     * قبلی را نشان می‌دهد.
     */
    suspend fun clear()

    /**
     * پاک‌کردنِ هر کلیدی که با [prefix] شروع می‌شود.
     *
     * برای حذفِ یک کیف‌پول لازم است: کش‌های هر کیف‌پول با پیشوندِ شناسه‌اش کلید می‌خورند و
     * بدونِ این، موجودی‌های کیفِ حذف‌شده روی دیسک می‌مانند.
     */
    suspend fun removeByPrefix(prefix: String)

    companion object {
        const val DEFAULT_TTL = 5 * 60 * 1000L // 5 minutes

        /**
         * Transaction history. Explicit rather than riding [DEFAULT_TTL]: this is the window in which
         * reopening the app must cost **no** service call, so it is a product decision, not a fallback.
         * Pull-to-refresh bypasses it.
         */
        const val HISTORY_TTL = 30 * 60 * 1000L // 30 minutes
        // TASK-20: was `* 1000000L`, which made the "5 days" TTL ≈13,700 years (assets never expired).
        // Milliseconds = days * 24 * 3600 * 1000.
        const val ASSETS_TTL = 5L * 24 * 3600 * 1000 // 5 days
    }
}
