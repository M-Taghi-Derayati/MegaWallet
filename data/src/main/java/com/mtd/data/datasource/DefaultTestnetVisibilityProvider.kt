package com.mtd.data.datasource

import com.mtd.data.BuildConfig
import com.mtd.domain.interfaceRepository.ITestnetVisibilityProvider
import com.mtd.domain.interfaceRepository.IUserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TASK-53 — نمایش/عدم‌نمایشِ شبکه‌های تست، از روی ترجیحِ ذخیره‌شدهٔ کاربر.
 *
 * دقیقاً هم‌شکلِ [DefaultBlockchainConnectionModeProvider] است و به همان دلیل:
 * [showTestnets] همگام و از مسیرهای حساس به تأخیر (ساختِ فهرست شبکه‌ها در زمان composition)
 * صدا زده می‌شود، پس **هرگز نباید مسدود شود**. مقدار از کشِ درون‌حافظه‌ای خوانده می‌شود و
 * دیسک لمس نمی‌شود؛ کش با [prime] خارج از ترد اصلی پر و با [setShowTestnets] تازه می‌شود.
 *
 * تا وقتی [prime] در استارتِ سرد تمام نشده، پیش‌فرضِ نوعِ بیلد برگردانده می‌شود
 * (debug: نمایش، release: عدم نمایش). این خودتصحیح است: بعد از prime خواندن‌های بعدی
 * انتخابِ ذخیره‌شده را منعکس می‌کنند.
 *
 * توجه: این پرچم فقط روی **فهرست‌ها** اثر دارد. رجیستری همهٔ شبکه‌ها را ثبت می‌کند، پس تغییر
 * این کلید نه networks.json را دوباره پارس می‌کند و نه به ری‌استارت نیاز دارد.
 */
@Singleton
class DefaultTestnetVisibilityProvider @Inject constructor(
    private val userPreferencesRepository: IUserPreferencesRepository
) : ITestnetVisibilityProvider {

    @Volatile
    private var cached: Boolean? = null

    /** غیرمسدودکننده. تا پیش از [prime]، پیش‌فرضِ نوعِ بیلد. */
    override fun showTestnets(): Boolean = cached ?: BuildConfig.DEBUG

    /**
     * کشِ درون‌حافظه‌ای را از ترجیحِ ذخیره‌شده پر می‌کند. suspend — خارج از ترد اصلی صدا بزنید
     * (warm-up اپ یا init یک ViewModel). idempotent است.
     */
    suspend fun prime() {
        cached = userPreferencesRepository.getShowTestnets()
    }

    /** ذخیره می‌کند و کش را تازه می‌کند تا [showTestnets] بعدی بلافاصله آن را منعکس کند. */
    suspend fun setShowTestnets(show: Boolean) {
        userPreferencesRepository.setShowTestnets(show)
        cached = show
    }
}
