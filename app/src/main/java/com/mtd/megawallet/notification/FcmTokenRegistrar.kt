package com.mtd.megawallet.notification

import com.google.firebase.messaging.FirebaseMessaging
import com.mtd.domain.interfaceRepository.INotificationPreferenceProvider
import com.mtd.domain.interfaceRepository.INotificationRepository
import com.mtd.domain.interfaceRepository.ITokenStore
import com.mtd.domain.model.DeviceRegistration
import com.mtd.domain.interfaceRepository.IUserPreferencesRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.megawallet.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Item 10 (FCM) — registers this install's FCM token with the relayer (`/api/notifications/devices`)
 * so it can deliver background deposit/tx pushes, and keeps it in sync.
 *
 * Registration needs the login JWT (device identity), so [syncToken] is driven from
 * `WalletSessionAuthCoordinator` after each successful auth. [onNewToken] handles Firebase rotating the
 * token at any time (registered immediately if already authenticated, otherwise picked up by the next
 * [syncToken]). The last successfully-registered token is persisted so an unchanged token is never
 * re-sent, and so we know which token to [unregister] on logout.
 */
@Singleton
class FcmTokenRegistrar @Inject constructor(
    private val notificationRepository: INotificationRepository,
    private val userPreferences: IUserPreferencesRepository,
    private val tokenStore: ITokenStore,
    private val notificationPreference: INotificationPreferenceProvider
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    /** Fetch the current FCM token and register it (post-auth). No-op if unchanged or unauthenticated. */
    fun syncToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Timber.w(task.exception, "[FCM] failed to fetch token")
                    return@addOnCompleteListener
                }
                task.result?.takeIf { it.isNotBlank() }?.let { register(it) }
            }
    }

    /** Firebase rotated the token — register the new one (idempotent; needs an active session). */
    fun onNewToken(token: String) {
        if (token.isBlank()) return
        register(token)
    }

    /** Logout — unregister the currently-registered token and forget it. */
    fun unregister() {
        scope.launch {
            mutex.withLock {
                val token = userPreferences.getRegisteredFcmToken() ?: return@withLock
                when (val r = notificationRepository.unregisterDevice(token)) {
                    is ResultResponse.Error -> Timber.w(r.exception, "[FCM] unregister failed")
                    is ResultResponse.Success -> Unit
                }
                // Clear locally regardless — the session is ending; a stale server row is harmless and
                // gets overwritten on the next registration.
                userPreferences.setRegisteredFcmToken(null)
            }
        }
    }

    /**
     * ترجیحِ اعلان‌ها عوض شد.
     *
     * روشن‌شدن یعنی همین حالا توکن را ثبت کن، و خاموش‌شدن یعنی همین حالا از رله برش دار — نه اینکه
     * فقط یک کلید جابه‌جا شود و اعلان‌ها همچنان برسند. تا وقتی توکنِ ثبت‌شده روی رله باشد، رله
     * push می‌فرستد؛ پس تنها راهِ درستِ خاموش‌کردن همان unregister است.
     */
    fun onPushPreferenceChanged(enabled: Boolean) {
        if (enabled) syncToken() else unregister()
    }

    private fun register(token: String) {
        scope.launch {
            mutex.withLock {
                // کاربر اعلان نمی‌خواهد — ثبت نکن. ensurePrimed چون register ممکن است پیش از
                // گرم‌شدنِ provider در استارتِ سرد صدا زده شود و آن‌وقت پیش‌فرضِ «روشن» را می‌دید.
                notificationPreference.ensurePrimed()
                if (!notificationPreference.pushEnabled.value) {
                    Timber.d("[FCM] push disabled by the user; skipping registration.")
                    return@withLock
                }

                // Needs the login JWT for device identity; deferred until syncToken() runs post-auth.
                if (tokenStore.getTokenDevice().isNullOrBlank()) {
                    Timber.d("[FCM] no session yet; deferring token registration.")
                    return@withLock
                }
                if (userPreferences.getRegisteredFcmToken() == token) return@withLock // already registered

                val registration = DeviceRegistration(
                    fcmToken = token,
                    appVersion = BuildConfig.VERSION_NAME,
                    locale = Locale.getDefault().toLanguageTag()
                )
                when (val r = notificationRepository.registerDevice(registration)) {
                    is ResultResponse.Success -> {
                        userPreferences.setRegisteredFcmToken(token)
                        Timber.i("[FCM] device token registered.")
                    }
                    // Not persisted → retried on the next syncToken() (e.g. next unlock).
                    is ResultResponse.Error -> Timber.w(r.exception, "[FCM] device token registration failed")
                }
            }
        }
    }
}
