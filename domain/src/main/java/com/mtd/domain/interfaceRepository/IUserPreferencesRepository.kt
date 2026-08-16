package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.BlockchainConnectionMode
import com.mtd.domain.model.FiatCurrency
import com.mtd.domain.model.ThemeMode


interface IUserPreferencesRepository {


    /**
     * KAN-9 / KAN-19 — transport mode for blockchain reads/writes (DIRECT RPC vs. backend PROXY).
     * Persisted so the choice survives process death; defaults to [BlockchainConnectionMode.DIRECT].
     */
    suspend fun getConnectionMode(): BlockchainConnectionMode
    suspend fun setConnectionMode(mode: BlockchainConnectionMode)

    /**
     * TASK-53 — آیا شبکه‌های تست در فهرست‌های UI دیده شوند؟ ذخیره می‌شود تا انتخاب پس از مرگِ
     * پروسه هم بماند. پیش‌فرض به نوعِ بیلد بستگی دارد و در لایهٔ `data` تعیین می‌شود
     * (debug: روشن، release: خاموش)، چون `domain` به BuildConfig دسترسی ندارد.
     *
     * این‌ها فقط نیمهٔ **ذخیره‌سازی** هستند؛ برای خواندن همگام از
     * [ITestnetVisibilityProvider.showTestnets] استفاده کنید.
     */
    suspend fun getShowTestnets(): Boolean
    suspend fun setShowTestnets(show: Boolean)

    /**
     * TASK-56 / TASK S §2.2-D — the fiat unit every money value is displayed in. Persisted so the
     * choice survives process death; defaults to [FiatCurrency.DEFAULT] (USD).
     *
     * These are the **persistence** half only. Do not read the preference through this getter to
     * render a screen: a suspend getter forces each caller to snapshot the value and stop noticing
     * changes, which is the exact mechanism that froze the Toman rate before TASK-54. Observe
     * [IFiatCurrencyProvider.currency] instead.
     */
    suspend fun getFiatCurrency(): FiatCurrency
    suspend fun setFiatCurrency(currency: FiatCurrency)

    /**
     * وضعیت فعال بودن قفل اپ.
     */
    suspend fun isAppLockEnabled(): Boolean
    suspend fun setAppLockEnabled(enabled: Boolean)

    /**
     * هش و salt مربوط به passcode.
     */
    suspend fun getPasscodeHash(): String?
    suspend fun getPasscodeSalt(): String?
    suspend fun savePasscodeHash(hash: String, salt: String)
    suspend fun clearPasscode()

    /**
     * تنظیمات بیومتریک و تایم‌اوت.
     */
    suspend fun isBiometricUnlockEnabled(): Boolean
    suspend fun setBiometricUnlockEnabled(enabled: Boolean)
    suspend fun getLockTimeoutSeconds(): Int
    suspend fun setLockTimeoutSeconds(seconds: Int)

    /**
     * وضعیت موقت امنیتی (برای لاک‌اوت و زمان بک‌گراند).
     */
    suspend fun getLastBackgroundAt(): Long
    suspend fun setLastBackgroundAt(timestampMs: Long)
    suspend fun getFailedUnlockAttempts(): Int
    suspend fun setFailedUnlockAttempts(count: Int)
    suspend fun getLockoutUntil(): Long
    suspend fun setLockoutUntil(timestampMs: Long)

    /**
     * TASK-32 — wallet ids already enrolled for backend monitoring (`/monitoring/subscribe`).
     * Persisted so enrollment fires **once per wallet** (on create/import), never again on a plain
     * wallet switch. Pruned when a wallet is deleted so a re-import re-enrolls.
     */
    suspend fun getMonitoringSubscribedWalletIds(): Set<String>
    suspend fun setMonitoringSubscribedWalletIds(ids: Set<String>)

    /**
     * Item 10 (FCM) — the FCM device token last successfully registered with the relayer
     * (`/api/notifications/devices`). Persisted so we skip re-registering an unchanged token and know
     * which token to unregister on logout. Null when no token is currently registered.
     */
    suspend fun getRegisteredFcmToken(): String?
    suspend fun setRegisteredFcmToken(token: String?)

    /**
     * پوستهٔ برنامه. ذخیره می‌شود تا انتخاب پس از مرگِ پروسه هم بماند؛ پیش‌فرض
     * [ThemeMode.DEFAULT] یعنی «مثل سیستم».
     *
     * این فقط نیمهٔ **ذخیره‌سازی** است. برای رندرکردنِ صفحه از این getter نخوانید — یک getterِ
     * suspend هر فراخوان را وادار می‌کند مقدار را یک‌بار عکس بگیرد و دیگر تغییرش را نبیند، که
     * برای پوسته یعنی تعویضِ پوسته تا استارتِ بعدی دیده نمی‌شود.
     * [IThemeModeProvider.themeMode] را collect کنید.
     */
    suspend fun getThemeMode(): ThemeMode
    suspend fun setThemeMode(mode: ThemeMode)

    /**
     * آیا کاربر اعلان‌های push را می‌خواهد. پیش‌فرض روشن.
     *
     * مثلِ بالا فقط نیمهٔ ذخیره‌سازی است؛ برای مشاهده از
     * [INotificationPreferenceProvider.pushEnabled] استفاده کنید.
     */
    suspend fun isPushNotificationsEnabled(): Boolean
    suspend fun setPushNotificationsEnabled(enabled: Boolean)

    /**
     * پاک‌کردنِ همهٔ ترجیحات — برای وقتی که آخرین کیف‌پول حذف می‌شود و برنامه باید مثل نصبِ
     * تازه بالا بیاید.
     *
     * ⚠️ شاملِ شناسه‌های ثبت‌شدهٔ مانیتورینگ و توکنِ FCM هم هست. عمدی است: آن‌ها به کیف‌پولی
     * اشاره می‌کنند که دیگر وجود ندارد، و ماندنشان یعنی کاربرِ بعدی اعلانِ کیف‌پولِ قبلی را
     * بگیرد.
     */
    suspend fun clearAll()
}
