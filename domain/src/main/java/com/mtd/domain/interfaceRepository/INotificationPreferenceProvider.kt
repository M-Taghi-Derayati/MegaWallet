package com.mtd.domain.interfaceRepository

import kotlinx.coroutines.flow.StateFlow

/**
 * ترجیحِ کاربر برای اعلان‌های push.
 *
 * این ترجیح **مجوزِ سیستم‌عامل نیست**؛ آن جای دیگری گرفته می‌شود و کاربر می‌تواند مستقل از این
 * خاموشش کند. این‌جا فقط می‌گوید کاربر اعلان می‌خواهد یا نه، و ثبتِ توکنِ دستگاه روی رله بر همین
 * اساس انجام یا لغو می‌شود — یعنی خاموش‌کردنش واقعاً جلوی رسیدنِ اعلان را می‌گیرد، نه اینکه فقط
 * یک کلید را جابه‌جا کند.
 *
 * پیش‌فرض روشن است: کاربری که کیف پول نصب می‌کند می‌خواهد از رسیدنِ دارایی خبردار شود.
 */
interface INotificationPreferenceProvider {

    /** آیا کاربر اعلان‌ها را می‌خواهد. تا کامل‌شدنِ [ensurePrimed] برابرِ [DEFAULT_ENABLED] است. */
    val pushEnabled: StateFlow<Boolean>

    /** خواندنِ مقدارِ ماندگار از دیسک. خارج از ترد اصلی صدا زده شود؛ idempotent است. */
    suspend fun ensurePrimed()

    /** ذخیره و سپس انتشار — به همین ترتیب. */
    suspend fun set(enabled: Boolean)

    companion object {
        const val DEFAULT_ENABLED = true
    }
}
