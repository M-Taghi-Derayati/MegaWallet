package com.mtd.data.repository.assets

import com.mtd.core.registry.AssetRegistry
import com.mtd.domain.interfaceRepository.IAssetCatalog
import com.mtd.domain.interfaceRepository.IManageableAssetCatalog
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.IUserTokenRepository
import com.mtd.domain.interfaceRepository.ManageableAsset
import com.mtd.domain.model.assets.AssetConfig
import com.mtd.domain.model.assets.UserToken
import com.mtd.domain.model.assets.UserTokenSelection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **تنها** نقطهٔ ادغام: باندلِ امضاشده ([AssetRegistry]) + توکن‌های افزودهٔ کاربر − پنهان‌شده‌ها.
 *
 * این تنها چیزی است که به `IAssetCatalog` بایند می‌شود، پس هر مصرف‌کننده‌ای — لیستِ کیف پول،
 * همگام‌سازِ موجودی، منابعِ دادهٔ زنجیره، ارسال، تاریخچه — بدونِ اینکه بداند فهرستِ ادغام‌شده را
 * می‌گیرد. عمداً هیچ‌جای دیگری اجازهٔ ادغام ندارد: پخش‌کردنِ این منطق بین ViewModelها همان الگویی
 * است که این کدبیس قبلاً سرِ آیکون‌ها و `wallet.keys` هزینه‌اش را داده.
 *
 * وقتی انتخابِ کاربر خالی است، هر سه متد **دقیقاً همان شیءِ** رجیستری را برمی‌گردانند — بدون کپی،
 * بدون مرتب‌سازیِ دوباره — تا کاربری که چیزی اضافه نکرده هیچ تغییری نبیند.
 */
@Singleton
class MergedAssetCatalog @Inject constructor(
    private val bundle: AssetRegistry,
    private val networkCatalog: INetworkCatalog,
    private val userTokens: IUserTokenRepository
) : IAssetCatalog, IManageableAssetCatalog {

    override fun getAllAssetConfigs(): List<AssetConfig> {
        val selection = userTokens.current()
        val bundled = bundle.getAllAssets()
        if (selection.isEmpty) return bundled

        return bundled.filterNot { it.id in selection.hiddenAssetIds } +
            addedConfigs(selection, networkId = null, alreadyPresent = bundled)
    }

    override fun getAssetConfigsForNetwork(networkId: String): List<AssetConfig> {
        val selection = userTokens.current()
        val bundled = bundle.getAssetsForNetwork(networkId)
        if (selection.isEmpty) return bundled

        return bundled.filterNot { it.id in selection.hiddenAssetIds } +
            addedConfigs(selection, networkId = networkId, alreadyPresent = bundled)
    }

    override fun getAssetConfigById(id: String): AssetConfig? {
        val selection = userTokens.current()
        if (selection.isEmpty) return bundle.getAssetById(id)

        if (id in selection.hiddenAssetIds) return null
        bundle.getAssetById(id)?.let { return it }
        return selection.added.firstOrNull { it.assetId == id }
            ?.takeIf { isKnownNetwork(it.networkId) }
            ?.toAssetConfig()
    }

    /**
     * نمای صفحهٔ مدیریت: باندل + توکن‌های کاربر، **بدون** حذفِ پنهان‌شده‌ها — چون کاربر باید بتواند
     * چیزی را که پنهان کرده برگرداند. ترتیب: اول باندل (همان ترتیبِ کاتالوگ)، بعد افزوده‌های کاربر.
     */
    override fun getManageableAssets(networkId: String): List<ManageableAsset> {
        val selection = userTokens.current()
        val bundled = bundle.getAssetsForNetwork(networkId)

        val bundledKeys = bundled.mapNotNullTo(mutableSetOf()) { config ->
            config.contractAddress?.let { "${config.networkId}:${it.lowercase()}" }
        }

        val bundleRows = bundled.map { config ->
            ManageableAsset(config = config, isHidden = config.id in selection.hiddenAssetIds)
        }

        val userRows = selection.added
            .asSequence()
            .filter { it.networkId == networkId }
            .filter { isKnownNetwork(it.networkId) }
            .filter { it.dedupeKey !in bundledKeys }
            .distinctBy { it.dedupeKey }
            .map { token ->
                ManageableAsset(
                    config = token.toAssetConfig(),
                    isHidden = token.assetId in selection.hiddenAssetIds
                )
            }
            .toList()

        return bundleRows + userRows
    }

    /**
     * توکن‌های کاربر برای یک شبکه (یا همه، وقتی [networkId] برابر `null` است)، به شکلِ [AssetConfig].
     *
     * دو فیلتر لازم است:
     *  - شبکه‌ای که دیگر در رجیستری نیست کنار می‌رود — همان قاعده‌ای که [AssetRegistry] روی باندل
     *    اعمال می‌کند؛ وگرنه ردیفی می‌ماند که هیچ‌کس نمی‌تواند برایش موجودی بخواند.
     *  - قراردادی که همین حالا در باندل هست دوباره اضافه نمی‌شود، وگرنه یک توکن دو ردیف می‌شود.
     */
    private fun addedConfigs(
        selection: UserTokenSelection,
        networkId: String?,
        alreadyPresent: List<AssetConfig>
    ): List<AssetConfig> {
        if (selection.added.isEmpty()) return emptyList()

        val bundledKeys = alreadyPresent.mapNotNullTo(mutableSetOf()) { config ->
            config.contractAddress?.let { "${config.networkId}:${it.lowercase()}" }
        }

        return selection.added
            .asSequence()
            .filter { networkId == null || it.networkId == networkId }
            .filter { it.assetId !in selection.hiddenAssetIds }
            .filter { isKnownNetwork(it.networkId) }
            .filter { it.dedupeKey !in bundledKeys }
            .distinctBy { it.dedupeKey }
            .map { it.toAssetConfig() }
            .toList()
    }

    private fun isKnownNetwork(networkId: String): Boolean =
        networkCatalog.getNetworkInfoById(networkId) != null
}

/**
 * `isUserAdded = true` تنها این‌جا گذاشته می‌شود. مصرف‌کننده‌ها با همین فیلد تشخیص می‌دهند که یک
 * ردیف از باندل نیامده — مهم‌ترین مصرفش گاردِ مسیرِ gasless است.
 */
private fun UserToken.toAssetConfig(): AssetConfig = AssetConfig(
    id = assetId,
    name = name,
    symbol = symbol,
    decimals = decimals,
    networkId = networkId,
    contractAddress = contractAddress,
    iconUrl = iconUrl,
    faName = null,
    isUserAdded = true
)
