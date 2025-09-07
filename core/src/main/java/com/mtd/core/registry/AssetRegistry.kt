package com.mtd.core.registry

import android.content.Context
import com.mtd.core.assets.AssetConfig
import com.mtd.core.utils.loadAssets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetRegistry @Inject constructor() {

    private val assetsById = mutableMapOf<String, AssetConfig>()
    private val assetsByNetwork = mutableMapOf<String, MutableList<AssetConfig>>()

    /**
     * دارایی‌ها را از فایل assets.json بارگذاری و در حافظه ثبت می‌کند.
     */
    fun loadAssetsFromAssets(context: Context) {
        val assetList = loadAssets(context)
        assetList.forEach { asset ->
            registerAsset(asset)
        }
    }

    private fun registerAsset(asset: AssetConfig) {
        assetsById[asset.id] = asset
        assetsByNetwork.getOrPut(asset.networkId) { mutableListOf() }.add(asset)
    }

    /**
     * تمام دارایی‌های مربوط به یک شبکه خاص را برمی‌گرداند.
     * @param networkId شناسه شبکه (مطابق با id در networks.json).
     */
    fun getAssetsForNetwork(networkId: String): List<AssetConfig> {
        return assetsByNetwork[networkId] ?: emptyList()
    }

    /**
     * تمام دارایی‌های پشتیبانی شده را برمی‌گرداند.
     */
    fun getAllAssets(): List<AssetConfig> {
        return assetsById.values.toList()
    }
    
    /**
     * یک دارایی خاص را با ID آن پیدا می‌کند.
     */
    fun getAssetById(id: String): AssetConfig? {
        return assetsById[id]
    }
}