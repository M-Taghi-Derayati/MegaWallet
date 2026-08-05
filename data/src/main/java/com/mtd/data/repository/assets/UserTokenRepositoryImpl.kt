package com.mtd.data.repository.assets

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mtd.domain.interfaceRepository.IActiveWalletProvider
import com.mtd.domain.interfaceRepository.IAppEventBus
import com.mtd.domain.interfaceRepository.IUserTokenRepository
import com.mtd.domain.model.AppEvent
import com.mtd.domain.model.assets.UserToken
import com.mtd.domain.model.assets.UserTokenSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * فهرستِ توکنِ کاربر، ماندگار در [SharedPreferences] و کلیددارِ کیف پول.
 *
 * ذخیره به‌ازای هر کیف پول یک کلید است (`user_token_selection_<walletId>`) و نه یک نگاشتِ بزرگ،
 * تا حذفِ یک کیف پول یک `remove` ساده باشد و نوشتنِ یک کیف پول هرگز فهرستِ کیف پولِ دیگری را
 * بازنویسی نکند.
 *
 * [current] همگام است چون [com.mtd.domain.interfaceRepository.IAssetCatalog] هم همگام است — همان
 * الگوی [com.mtd.data.datasource.DefaultBlockchainConnectionModeProvider]: کشِ حافظه، بدونِ دیسک،
 * بدونِ بلاک‌کردنِ تردِ فراخوان. تا [prime] کامل نشده مقدارِ خالی برمی‌گردد، پس بدترین حالت همان
 * رفتارِ «فقط باندل»ِ امروز است.
 */
@Singleton
class UserTokenRepositoryImpl @Inject constructor(
    private val prefs: SharedPreferences,
    private val gson: Gson,
    private val activeWalletProvider: IActiveWalletProvider,
    private val appEventBus: IAppEventBus
) : IUserTokenRepository {

    private companion object {
        const val KEY_PREFIX = "user_token_selection_"
        fun keyFor(walletId: String) = "$KEY_PREFIX$walletId"
    }

    /** شکلِ روی دیسک. جدا از مدلِ دامنه نگه داشته شده تا تغییرِ مدل، دادهٔ ذخیره‌شده را نشکند. */
    private data class StoredSelection(
        val added: List<UserToken> = emptyList(),
        val hidden: List<String> = emptyList()
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** کشِ حافظه به‌ازای walletId؛ [current] فقط از این می‌خواند. */
    @Volatile
    private var cache: Map<String, UserTokenSelection> = emptyMap()

    private val _selection = MutableStateFlow(UserTokenSelection.EMPTY)
    override val selection: StateFlow<UserTokenSelection> = _selection.asStateFlow()

    init {
        // سوئیچِ کیف پول باید فهرست را عوض کند، وگرنه انتخابِ کیف پولِ قبلی روی کیف پولِ جدید نشت
        // می‌کند — همان اشتباهی که HomeViewModel با fullRawAssets یک بار پرداختش را داد.
        scope.launch {
            activeWalletProvider.activeWalletId.collect { walletId ->
                _selection.value = walletId?.let { load(it) } ?: UserTokenSelection.EMPTY
            }
        }
    }

    /**
     * برخلافِ [selection]، این مسیر منتظرِ collector نمی‌ماند.
     *
     * لازم است چون هم این کلاس و هم `HomeViewModel` روی همان `activeWalletId` نشسته‌اند و ترتیبشان
     * تضمینی نیست: اگر ViewModel اول برسد، اولین فهرست پس از باز شدنِ قفل بدونِ توکن‌های کاربر
     * ساخته می‌شد. [load] در این نقطه دیسک نمی‌خواند — نمونهٔ SharedPreferences در استارتِ برنامه
     * hydrate شده و `getString` از حافظه می‌آید — پس ترد فراخوان بلاک نمی‌شود.
     */
    override fun current(): UserTokenSelection {
        val walletId = activeWalletProvider.activeWalletId.value ?: return UserTokenSelection.EMPTY
        return cache[walletId] ?: load(walletId)
    }

    override suspend fun prime() {
        val walletId = activeWalletProvider.activeWalletId.value ?: return
        _selection.value = load(walletId)
    }

    override suspend fun add(token: UserToken) {
        val walletId = activeWalletProvider.activeWalletId.value ?: return
        mutate(walletId) { existing ->
            existing.copy(
                // جایگزینی به‌جای افزودنِ تکراری: اضافه‌کردنِ دوبارهٔ همان قرارداد باید متادیتای
                // تازه (نام/آیکون/decimals) را بنشاند، نه یک ردیفِ دوم بسازد.
                added = existing.added.filterNot { it.dedupeKey == token.dedupeKey } + token,
                // افزودن یعنی «می‌خواهمش» — پس اگر قبلاً پنهان شده بود، دیگر نیست.
                hiddenAssetIds = existing.hiddenAssetIds - token.assetId
            )
        }

        // دو رویداد: اولی فهرست را با کاتالوگِ تازه بازمی‌سازد، دومی موجودیِ همین توکن را می‌خواهد.
        // بدونِ دومی، توکنِ تازه تا انقضای TTLِ موجودی روی «...» می‌ماند، چون CatalogChanged عمداً
        // هیچ رفت‌وبرگشتِ شبکه‌ای را اجباری نمی‌کند.
        appEventBus.postEvent(AppEvent.CatalogChanged)
        appEventBus.postEvent(
            AppEvent.WalletAssetNeedsRefresh(
                assetId = token.assetId,
                networkId = token.networkId,
                contractAddress = token.contractAddress
            )
        )
    }

    override suspend fun remove(networkId: String, contractAddress: String) {
        val walletId = activeWalletProvider.activeWalletId.value ?: return
        val key = "$networkId:${contractAddress.lowercase()}"
        mutate(walletId) { existing ->
            existing.copy(added = existing.added.filterNot { it.dedupeKey == key })
        }
        appEventBus.postEvent(AppEvent.CatalogChanged)
    }

    override suspend fun setHidden(assetId: String, hidden: Boolean) {
        val walletId = activeWalletProvider.activeWalletId.value ?: return
        mutate(walletId) { existing ->
            existing.copy(
                hiddenAssetIds = if (hidden) {
                    existing.hiddenAssetIds + assetId
                } else {
                    existing.hiddenAssetIds - assetId
                }
            )
        }
        appEventBus.postEvent(AppEvent.CatalogChanged)
    }

    override suspend fun forgetWallet(walletId: String) {
        prefs.edit { remove(keyFor(walletId)) }
        cache = cache - walletId
        if (activeWalletProvider.activeWalletId.value == walletId) {
            _selection.value = UserTokenSelection.EMPTY
        }
    }

    private fun mutate(walletId: String, block: (UserTokenSelection) -> UserTokenSelection) {
        val updated = block(load(walletId))
        persist(walletId, updated)
        cache = cache + (walletId to updated)
        if (activeWalletProvider.activeWalletId.value == walletId) {
            _selection.value = updated
        }
    }

    private fun load(walletId: String): UserTokenSelection {
        cache[walletId]?.let { return it }

        val raw = prefs.getString(keyFor(walletId), null)
        val parsed = if (raw.isNullOrBlank()) {
            UserTokenSelection.EMPTY
        } else {
            // JSONِ خراب نباید کیف پول را از کار بیندازد؛ به فهرستِ خالی برمی‌گردیم، یعنی دقیقاً
            // رفتارِ «فقط باندل».
            runCatching {
                val type = object : TypeToken<StoredSelection>() {}.type
                val stored: StoredSelection? = gson.fromJson(raw, type)
                UserTokenSelection(
                    added = stored?.added.orEmpty(),
                    hiddenAssetIds = stored?.hidden.orEmpty().toSet()
                )
            }.onFailure {
                Timber.w(it, "Corrupt user-token selection for wallet %s; falling back to empty", walletId)
            }.getOrDefault(UserTokenSelection.EMPTY)
        }

        cache = cache + (walletId to parsed)
        return parsed
    }

    private fun persist(walletId: String, value: UserTokenSelection) {
        val json = gson.toJson(
            StoredSelection(added = value.added, hidden = value.hiddenAssetIds.toList())
        )
        prefs.edit { putString(keyFor(walletId), json) }
    }
}
