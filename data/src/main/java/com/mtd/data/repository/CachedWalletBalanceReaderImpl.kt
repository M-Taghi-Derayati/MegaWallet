package com.mtd.data.repository

import com.mtd.core.manager.CacheManager
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.utils.BalanceFormatter
import com.mtd.domain.interfaceRepository.ICachedWalletBalanceReader
import com.mtd.domain.model.CachedAssetBalance
import java.math.BigDecimal
import javax.inject.Inject

class CachedWalletBalanceReaderImpl @Inject constructor(
    private val cacheManager: CacheManager,
    private val assetRegistry: AssetRegistry
) : ICachedWalletBalanceReader {

    override suspend fun getCachedTotalBalance(walletId: String): String {
        var totalUsdValue = BigDecimal.ZERO

        assetRegistry.getAllAssets().forEach { assetConfig ->
            val cacheKey = "$CACHE_KEY_PREFIX${walletId}_${assetConfig.id}"
            cacheManager.get(cacheKey, CachedAssetBalance::class.java)?.let { cached ->
                if (cached.balanceRaw > BigDecimal.ZERO && cached.priceUsdRaw > BigDecimal.ZERO) {
                    totalUsdValue = totalUsdValue.add(cached.balanceRaw.multiply(cached.priceUsdRaw))
                }
            }
        }

        return if (totalUsdValue <= BigDecimal.ZERO) {
            "$0"
        } else {
            "$" + BalanceFormatter.formatUsdValue(totalUsdValue, false)
        }
    }

    private companion object {
        const val CACHE_KEY_PREFIX = "asset_balance_"
    }
}
