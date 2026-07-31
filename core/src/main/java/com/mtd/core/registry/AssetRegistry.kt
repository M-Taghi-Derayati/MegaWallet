package com.mtd.core.registry

import android.content.Context
import com.mtd.core.utils.loadAssets
import com.mtd.domain.interfaceRepository.IAssetCatalog
import com.mtd.domain.model.assets.AssetConfig
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetRegistry @Inject constructor(
    private val blockchainRegistry: BlockchainRegistry
) : IAssetCatalog {

    /**
     * TASK-53 — همان دلیلِ [BlockchainRegistry]: [applyConfig] در زمان اجرا کاتالوگ را بازمی‌سازد
     * در حالی که UI هم‌زمان می‌خواند، پس ایندکس‌ها در یک snapshotِ تغییرناپذیر نگه داشته و یکجا
     * جابه‌جا می‌شوند.
     */
    private class Catalog(
        val byId: Map<String, AssetConfig> = emptyMap(),
        val byNetwork: Map<String, List<AssetConfig>> = emptyMap()
    )

    @Volatile
    private var catalog = Catalog()
    private val writeLock = Any()

    /**
     * دارایی‌ها را از فایل assets.json بارگذاری و در حافظه ثبت می‌کند.
     */
    fun loadAssetsFromAssets(context: Context) {
        replaceAll(loadAssets(context))
    }

    /**
     * TASK-53 — دارایی‌ها را از یک باندلِ **تأییدشده** بازمی‌سازد.
     *
     * محافظِ هویت: برای دارایی‌ای که در seed محلی هست، تغییرِ `contractAddress` از سمت سرور
     * **رد** می‌شود. این همان فیلدی است که تعیین می‌کند انتقالِ توکن به کدام قرارداد می‌رود؛
     * بازنویسیِ خاموشِ آن یعنی هدایتِ USDT کاربر به یک قراردادِ دیگر. دارایی‌های تازه آزادانه
     * پذیرفته می‌شوند.
     *
     * @return تعداد دارایی‌های ثبت‌شده.
     */
    fun applyConfig(
        assets: List<AssetConfig>,
        trustedBaseline: List<AssetConfig> = emptyList()
    ): Int {
        val baselineById = trustedBaseline.associateBy { it.id }

        val accepted = assets.map { asset ->
            val baseline = baselineById[asset.id] ?: return@map asset
            if (baseline.contractAddress.equals(asset.contractAddress, ignoreCase = true)) {
                asset
            } else {
                Timber.e(
                    "Rejected bundle override of contractAddress for asset '%s' (%s -> %s); " +
                        "keeping the locally shipped definition.",
                    asset.id, baseline.contractAddress, asset.contractAddress
                )
                baseline
            }
        }
        replaceAll(accepted)
        return catalog.byId.size
    }

    /** ساختِ snapshot تازه و جایگزینیِ اتمی. دارایی‌های شبکه‌های ثبت‌نشده کنار گذاشته می‌شوند. */
    private fun replaceAll(assets: List<AssetConfig>) {
        val allowedNetworkIds = blockchainRegistry.getAllNetworks().map { it.id }.toSet()
        val byId = mutableMapOf<String, AssetConfig>()
        val byNetwork = mutableMapOf<String, MutableList<AssetConfig>>()

        assets.filter { it.networkId in allowedNetworkIds }.forEach { asset ->
            byId[asset.id] = asset
            byNetwork.getOrPut(asset.networkId) { mutableListOf() }.add(asset)
        }

        synchronized(writeLock) {
            catalog = Catalog(byId.toMap(), byNetwork.mapValues { (_, v) -> v.toList() })
        }
    }

    /**
     * تمام دارایی‌های مربوط به یک شبکه خاص را برمی‌گرداند.
     * @param networkId شناسه شبکه (مطابق با id در networks.json).
     */
    fun getAssetsForNetwork(networkId: String): List<AssetConfig> {
        return catalog.byNetwork[networkId] ?: emptyList()
    }

    override fun getAssetConfigsForNetwork(networkId: String): List<AssetConfig> {
        return getAssetsForNetwork(networkId)
    }

    /**
     * تمام دارایی‌های پشتیبانی شده را برمی‌گرداند.
     */
    fun getAllAssets(): List<AssetConfig> {
        return catalog.byId.values.toList()
    }

    override fun getAllAssetConfigs(): List<AssetConfig> {
        return getAllAssets()
    }
    
    /**
     * یک دارایی خاص را با ID آن پیدا می‌کند.
     */
    fun getAssetById(id: String): AssetConfig? {
        return catalog.byId[id]
    }

    override fun getAssetConfigById(id: String): AssetConfig? {
        return getAssetById(id)
    }

    /**
     * یک دارایی خاص را با نماد و شناسه شبکه آن پیدا می‌کند.
     * این تابع به صورت بهینه در لیست دارایی‌ها جستجو می‌کند.
     *
     * @param symbol نماد ارز (e.g., "USDT")
     * @param networkId شناسه شبکه (e.g., "sepolia")
     * @return آبجکت AssetConfig یا null اگر پیدا نشود.
     */
    fun getAssetBySymbol(symbol: String, networkId: String): AssetConfig? {
        // ابتدا لیست دارایی‌های مربوط به آن شبکه را پیدا می‌کنیم
        val assetsOnNetwork = catalog.byNetwork[networkId] ?: return null

        // سپس در آن لیست کوچک، به دنبال نماد مورد نظر می‌گردیم
        return assetsOnNetwork.find { it.symbol.equals(symbol, ignoreCase = true) }
    }
}
