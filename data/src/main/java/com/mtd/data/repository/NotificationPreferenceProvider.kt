package com.mtd.data.repository

import com.mtd.domain.interfaceRepository.INotificationPreferenceProvider
import com.mtd.domain.interfaceRepository.IUserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * همان الگوی [FiatCurrencyProvider]، برای ترجیحِ اعلان‌ها.
 *
 * `@Singleton` است چون هم صفحهٔ تنظیمات آن را می‌خواند و هم ثبت‌کنندهٔ توکنِ FCM؛ دو نمونه یعنی
 * کلیدی که خاموش شده ولی ثبت‌کننده هنوز فکر می‌کند روشن است.
 */
@Singleton
class NotificationPreferenceProvider @Inject constructor(
    private val userPreferencesRepository: IUserPreferencesRepository
) : INotificationPreferenceProvider {

    private val _pushEnabled =
        MutableStateFlow(INotificationPreferenceProvider.DEFAULT_ENABLED)
    override val pushEnabled: StateFlow<Boolean> = _pushEnabled.asStateFlow()

    private val primeMutex = Mutex()

    @Volatile
    private var primed = false

    override suspend fun ensurePrimed() {
        if (primed) return
        primeMutex.withLock {
            if (primed) return
            _pushEnabled.value = userPreferencesRepository.isPushNotificationsEnabled()
            primed = true
        }
    }

    override suspend fun set(enabled: Boolean) = primeMutex.withLock {
        userPreferencesRepository.setPushNotificationsEnabled(enabled)
        primed = true
        _pushEnabled.value = enabled
    }
}
