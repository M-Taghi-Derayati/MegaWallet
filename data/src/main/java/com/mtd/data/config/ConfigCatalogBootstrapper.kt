package com.mtd.data.config

import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.BuildConfig
import com.mtd.domain.interfaceRepository.IAppEventBus
import com.mtd.domain.model.AppEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TASK-53 — تنها جایی که باندلِ تأییدشده واقعاً به رجیستری‌ها می‌رسد.
 *
 * پیش از این، `ConfigManager.getValidatedConfig()` یک مصرف‌کننده داشت: یک warm-up بی‌نتیجه در
 * `MegaWalletApplication`. باندل گرفته، امضایش بررسی، کش، و بعد **دور ریخته** می‌شد؛ منبعِ حقیقت
 * همچنان فایلِ محلی بود. این کلاس همان حلقهٔ باز را می‌بندد.
 *
 * ### ترتیب (این یک مسئلهٔ درستیِ استارت است، نه صرفاً سیم‌کشی)
 * رجیستری‌ها همچنان در زمانِ DI از seed محلی پر می‌شوند (`CryptoModule`). یعنی کاتالوگ **هرگز
 * خالی نیست** و `getNetworkById`/`getNetworkByChainId` از اولین لحظه جواب می‌دهند. این عمداً
 * حفظ شده: کلیدسازی و اعتبارسنجی آدرس نباید منتظر شبکه بمانند، وگرنه یک بک‌اندِ کند به معنیِ
 * کیف‌پولی بدون آدرس است. [bootstrap] بعداً کاتالوگ را با نسخهٔ تأییدشده **جایگزین** می‌کند و
 * جایگزینی در رجیستری اتمی است، پس خواننده‌های هم‌زمان حالتِ نیمه‌کاره نمی‌بینند.
 *
 * چون شبکه‌های تازه ممکن است بعد از اولین رندر برسند، پس از تغییرِ واقعیِ کاتالوگ یک
 * [AppEvent.WalletNeedsRefresh] منتشر می‌شود تا صفحه‌ها دوباره بخوانند. [awaitReady] هم برای
 * مسیرهایی که واقعاً باید منتظر بمانند فراهم است.
 *
 * ### امنیت
 * ورودیِ این کلاس فقط از [ConfigManager] می‌آید که یا امضای secp256k1 را تأیید کرده، یا از کشِ
 * رمزنگاری‌شدهٔ آخرین-وضعیتِ-خوب خوانده، یا seed محلی را برگردانده. باندلی که امضایش رد شود
 * هرگز به این‌جا نمی‌رسد و اثرش این است که seed محلی سرِ جایش می‌ماند.
 *
 * علاوه بر آن، seed محلی به‌عنوان `trustedBaseline` پاس می‌شود تا رجیستری‌ها بازنویسیِ
 * فیلدهای هویتی (chainId / derivationPath / addressRegex و contractAddress دارایی‌ها) را
 * برای موجودی‌های شناخته‌شده رد کنند.
 */
@Singleton
class ConfigCatalogBootstrapper @Inject constructor(
    private val configManager: ConfigManager,
    private val localAssetProvider: LocalConfigAssetProvider,
    private val blockchainRegistry: BlockchainRegistry,
    private val assetRegistry: AssetRegistry,
    private val appEventBus: IAppEventBus
) {

    private val ready = CompletableDeferred<Boolean>()
    private val mutex = Mutex()

    /** `true` وقتی یک تلاشِ bootstrap تمام شده (چه اعمال شده باشد چه نه). */
    suspend fun awaitReady(): Boolean = ready.await()

    /**
     * باندل را می‌گیرد و روی رجیستری‌ها می‌نشاند. idempotent و امن در برابر شکست: هر خطایی
     * یعنی کاتالوگِ seed دست‌نخورده باقی می‌ماند.
     *
     * @return `true` اگر کاتالوگ از باندل اعمال شد.
     */
    suspend fun bootstrap(): Boolean = mutex.withLock {
        val applied = runCatching { applyOnce() }
            .onFailure { Timber.w(it, "Catalog bootstrap failed; keeping the local seed catalog") }
            .getOrDefault(false)
        if (!ready.isCompleted) ready.complete(applied)
        applied
    }

    private suspend fun applyOnce(): Boolean {
        if (!BuildConfig.CONFIG_BUNDLE_APPLY_ENABLED) {
            // کلیدِ قطع: دقیقاً رفتارِ قبلی (فقط seed محلی).
            Timber.i("Catalog bootstrap disabled by kill switch; local seed only")
            return false
        }

        val bundle = configManager.getValidatedConfig()

        // seed محلی هم مرجعِ هویت است و هم چیزی که رجیستری همین حالا دارد؛ اعمالِ دوبارهٔ آن
        // بی‌فایده است.
        val local = localAssetProvider.load()
        val baselineNetworks = ConfigBundleMapper.toNetworkConfigs(local)
        val baselineAssets = ConfigBundleMapper.toAssetConfigs(local)

        if (bundle.version == LocalConfigAssetProvider.LOCAL_VERSION) {
            Timber.i("Catalog bootstrap: only the local seed is available; nothing to apply")
            return false
        }

        val networks = ConfigBundleMapper.toNetworkConfigs(bundle)
        if (networks.isEmpty()) {
            // باندلی بدون شبکهٔ قابل‌استفاده نباید کاتالوگ را خالی کند.
            Timber.w("Catalog bootstrap: bundle v%s has no usable networks; keeping local seed", bundle.version)
            return false
        }

        val before = blockchainRegistry.getAllNetworks().map { it.id }.toSet()

        blockchainRegistry.applyConfig(networks, trustedBaseline = baselineNetworks)
        assetRegistry.applyConfig(
            ConfigBundleMapper.toAssetConfigs(bundle),
            trustedBaseline = baselineAssets
        )

        val after = blockchainRegistry.getAllNetworks().map { it.id }.toSet()
        Timber.i(
            "Catalog bootstrap applied bundle v%s: %d networks (added=%s, removed=%s)",
            bundle.version, after.size, (after - before), (before - after)
        )

        if (after != before) {
            // شبکه‌ها عوض شده‌اند؛ صفحه‌ها باید لیست‌هایشان را با کاتالوگ تازه بازبسازند. این
            // «کاتالوگ عوض شد» است نه «دیتای زنجیره عوض شد»: قبلاً WalletNeedsRefresh منتشر می‌شد و
            // چون جایگزینیِ seed محلی با باندلِ سرور در *هر* اجرای سرد مجموعهٔ شبکه‌ها را عوض می‌کند،
            // هر بار باز کردن اپ یک رفرشِ کاملِ بی‌قید‌و‌شرط راه می‌انداخت و TTLِ همهٔ کش‌ها را دور می‌زد.
            runCatching { appEventBus.postEvent(AppEvent.CatalogChanged) }
        }
        return true
    }
}
