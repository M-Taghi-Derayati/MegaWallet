package com.mtd.data.config

import com.mtd.data.dto.ConfigAssetDto
import com.mtd.data.dto.ConfigBundleDto
import com.mtd.data.dto.ConfigNetworkDto
import com.mtd.domain.model.assets.AssetConfig
import com.mtd.domain.model.core.NetworkConfig
import timber.log.Timber

/**
 * TASK-53 — نگاشتِ باندلِ سرور به مدل‌های دامنه‌ای که رجیستری‌ها می‌فهمند.
 *
 * ورودیِ ناقص **کنار گذاشته می‌شود، نه ترمیم**: شبکه‌ای بدون `networkId`/`type`/`derivationPath`
 * قابل استفاده نیست و ساختنِ پیش‌فرض برایش یعنی حدس‌زدنِ مسیرِ کلیدسازی — همان کاری که این تسک
 * می‌خواهد از آن جلوگیری کند. فیلدهای نمایشی (رنگ، نام فارسی، آیکون) اختیاری‌اند.
 */
object ConfigBundleMapper {

    fun toNetworkConfigs(bundle: ConfigBundleDto): List<NetworkConfig> =
        bundle.networks.orEmpty().mapNotNull { it.toNetworkConfigOrNull() }

    fun toAssetConfigs(bundle: ConfigBundleDto): List<AssetConfig> =
        bundle.assets.orEmpty().mapNotNull { it.toAssetConfigOrNull() }

    private fun ConfigNetworkDto.toNetworkConfigOrNull(): NetworkConfig? {
        val id = networkId?.trim()?.takeIf { it.isNotBlank() } ?: run {
            Timber.w("Bundle network dropped: missing networkId")
            return null
        }
        val type = type?.trim()?.takeIf { it.isNotBlank() } ?: run {
            Timber.w("Bundle network '%s' dropped: missing type", id)
            return null
        }
        val path = derivationPath?.trim()?.takeIf { it.isNotBlank() } ?: run {
            // بدون مسیرِ استخراج نمی‌توان کلید ساخت؛ حدس‌زدن ممنوع.
            Timber.w("Bundle network '%s' dropped: missing derivationPath", id)
            return null
        }

        return NetworkConfig(
            id = id,
            name = name?.trim().orEmpty(),
            networkType = type,
            chainId = chainId,
            derivationPath = path,
            rpcUrlsEvm = rpcUrlsEvm.orEmpty(),
            rpcUrls = rpcUrls.orEmpty(),
            currencySymbol = currencySymbol.orEmpty(),
            webSocketUrl = webSocketUrl,
            decimals = decimals ?: 18,
            regex = regex,
            iconUrl = iconUrl.orEmpty(),
            explorers = explorers.orEmpty(),
            explorerTxUrl = explorerTxUrl,
            color = color,
            faName = faName,
            isTestnet = isTestnet == true,
            explorerApi = explorerApi,
            hasL1DataFee = hasL1DataFee == true
        )
    }

    private fun ConfigAssetDto.toAssetConfigOrNull(): AssetConfig? {
        val id = assetId?.trim()?.takeIf { it.isNotBlank() } ?: run {
            // Silent until now: a whole bundle of assets could vanish with nothing in the log.
            Timber.w("Bundle asset dropped: missing assetId")
            return null
        }
        val network = networkId?.trim()?.takeIf { it.isNotBlank() } ?: run {
            Timber.w("Bundle asset '%s' dropped: missing networkId", id)
            return null
        }
        val sym = symbol?.trim()?.takeIf { it.isNotBlank() } ?: run {
            Timber.w("Bundle asset '%s' dropped: missing symbol", id)
            return null
        }

        return AssetConfig(
            id = id,
            name = name?.trim().orEmpty().ifBlank { sym },
            symbol = sym,
            decimals = decimals ?: 18,
            networkId = network,
            contractAddress = contractAddress?.trim()?.takeIf { it.isNotBlank() },
            iconUrl = iconUrl,
            faName = faName
        )
    }
}
