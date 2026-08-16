package com.mtd.data.repository

import com.mtd.core.registry.AssetRegistry
import com.mtd.domain.interfaceRepository.IAppCacheStore
import com.mtd.domain.interfaceRepository.ICachedWalletBalanceReader
import com.mtd.domain.model.CachedAssetBalance
import java.math.BigDecimal
import javax.inject.Inject

class CachedWalletBalanceReaderImpl @Inject constructor(
    private val cacheStore: IAppCacheStore,
    private val assetRegistry: AssetRegistry
) : ICachedWalletBalanceReader {

    override suspend fun getCachedTotalUsd(walletId: String): BigDecimal {
        var totalUsdValue = BigDecimal.ZERO

        assetRegistry.getAllAssets().forEach { assetConfig ->
            val cacheKey = "$CACHE_KEY_PREFIX${walletId}_${assetConfig.id}"
            cacheStore.get(cacheKey, CachedAssetBalance::class.java)?.let { cached ->
                if (cached.balanceRaw > BigDecimal.ZERO && cached.priceUsdRaw > BigDecimal.ZERO) {
                    totalUsdValue = totalUsdValue.add(cached.balanceRaw.multiply(cached.priceUsdRaw))
                }
            }
        }

        // TASK-56 — raw USD, unformatted. The caller applies the selected currency and the rate; a
        // "$…" string built here could never become تومان.
        return totalUsdValue.max(BigDecimal.ZERO)
    }

    // عمومی است چون `WalletRepositoryImpl` هنگام حذفِ کیف‌پول با همین پیشوند کش را پاک می‌کند.
    // تکرارِ رشته در دو جا یعنی یک روز یکی عوض می‌شود و آن یکی بی‌صدا جا می‌ماند.
    companion object {
        const val CACHE_KEY_PREFIX = "asset_balance_"
    }
}
