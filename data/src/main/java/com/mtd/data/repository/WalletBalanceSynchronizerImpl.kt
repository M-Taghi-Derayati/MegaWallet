package com.mtd.data.repository

import com.mtd.core.manager.CacheManager
import com.mtd.core.utils.BalanceFormatter
import com.mtd.domain.interfaceRepository.IAssetCatalog
import com.mtd.domain.interfaceRepository.ICachedWalletBalanceReader
import com.mtd.domain.interfaceRepository.IMarketDataRepository
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.IWalletBalanceSynchronizer
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.Asset
import com.mtd.domain.model.CachedAssetBalance
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.assets.AssetPriceDto
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

class WalletBalanceSynchronizerImpl @Inject constructor(
    private val walletRepository: IWalletRepository,
    private val marketDataRepository: IMarketDataRepository,
    private val cachedWalletBalanceReader: ICachedWalletBalanceReader,
    private val assetCatalog: IAssetCatalog,
    private val networkCatalog: INetworkCatalog,
    private val cacheManager: CacheManager
) : IWalletBalanceSynchronizer {

    override suspend fun syncWalletBalances(
        wallets: List<Wallet>,
        activeWalletId: String?,
        forceResync: Boolean
    ) {
        if (wallets.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        val lastPriceSync = cacheManager.get(LAST_PRICE_SYNC_TIME_KEY, Long::class.javaObjectType) ?: 0L
        if (forceResync || currentTime - lastPriceSync > PRICE_UPDATE_INTERVAL) {
            refreshAllPrices(wallets)
        }

        val lastBalanceSync = cacheManager.get(LAST_WALLETS_SYNC_TIME_KEY, Long::class.javaObjectType) ?: 0L
        if (forceResync || currentTime - lastBalanceSync > MIN_UPDATE_INTERVAL) {
            refreshAllBalances(wallets, activeWalletId, forceResync)
        }
    }

    private suspend fun refreshAllPrices(wallets: List<Wallet>) {
        val allAssets = assetCatalog.getAllAssetConfigs()
        val allCoinIds = allAssets.map { it.symbol }.distinct()
        val pricesResult = marketDataRepository.getLatestPrices(allCoinIds)

        if (pricesResult is ResultResponse.Success) {
            val pricesMap = pricesResult.data.associateBy { it.assetId }
            withContext(Dispatchers.IO) {
                wallets.forEach { wallet ->
                    allAssets.forEach { config ->
                        val priceInfo = pricesMap[config.symbol]
                        if (priceInfo != null) {
                            updateAssetPriceCache(wallet.id, config.id, priceInfo)
                        }
                    }
                }
            }
            cacheManager.put(LAST_PRICE_SYNC_TIME_KEY, System.currentTimeMillis())
        }
    }

    private suspend fun refreshAllBalances(
        wallets: List<Wallet>,
        activeWalletId: String?,
        forceResync: Boolean
    ) {
        val isActiveWalletCached = if (activeWalletId != null) {
            val total = cachedWalletBalanceReader.getCachedTotalBalance(activeWalletId)
            total != "$0" && total != "0"
        } else {
            false
        }

        val walletsToFetch = wallets.map { it.id }.filter { id ->
            forceResync || id != activeWalletId || !isActiveWalletCached
        }

        if (walletsToFetch.isEmpty()) {
            Timber.d("BATCH_DEBUG: Cache is fresh and skip active wallet. No network fetch needed.")
            return
        }

        Timber.d("BATCH_DEBUG: Fetching balances for ${walletsToFetch.size} wallets: $walletsToFetch")
        coroutineScope {
            networkCatalog.getAllNetworkInfos()
                .map { network ->
                    async {
                        val result = walletRepository.getBalancesForMultipleWallets(network.name, walletsToFetch)
                        if (result is ResultResponse.Success) {
                            withContext(Dispatchers.IO) {
                                result.data.forEach { (walletId, assets) ->
                                    assets.forEach { asset ->
                                        updateAssetBalanceCache(walletId, asset, network.name)
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        cacheManager.put(LAST_WALLETS_SYNC_TIME_KEY, System.currentTimeMillis())
    }

    private suspend fun updateAssetPriceCache(
        walletId: String,
        assetId: String,
        priceInfo: AssetPriceDto
    ) {
        val cacheKey = "$CACHE_KEY_PREFIX${walletId}_$assetId"
        val oldCache = cacheManager.get(cacheKey, CachedAssetBalance::class.java) ?: return

        cacheManager.put(
            cacheKey,
            oldCache.copy(
                priceUsdRaw = priceInfo.priceUsd,
                priceChange24h = priceInfo.priceChanges24h.toDouble()
            )
        )
    }

    private suspend fun updateAssetBalanceCache(
        walletId: String,
        asset: Asset,
        networkName: NetworkName
    ) {
        val networkId = networkCatalog.getNetworkInfoByName(networkName)?.id ?: return
        val assetConfig = assetCatalog.getAssetConfigsForNetwork(networkId).find {
            if (it.contractAddress == null) {
                asset.contractAddress == null && it.symbol.equals(asset.symbol, true)
            } else {
                it.contractAddress.equals(asset.contractAddress, true)
            }
        } ?: return

        val cacheKey = "$CACHE_KEY_PREFIX${walletId}_${assetConfig.id}"
        val oldCache = cacheManager.get(cacheKey, CachedAssetBalance::class.java)
        val priceUsd = oldCache?.priceUsdRaw ?: BigDecimal.ZERO
        val priceChange = oldCache?.priceChange24h ?: 0.0

        cacheManager.put(
            cacheKey,
            CachedAssetBalance(
                assetId = assetConfig.id,
                walletId = walletId,
                balance = BalanceFormatter.formatBalance(asset.balance, asset.decimals) + " " + asset.symbol,
                balanceRaw = asset.balance.movePointLeft(asset.decimals),
                priceUsdRaw = priceUsd,
                priceChange24h = priceChange,
                balanceUsdt = "...",
                balanceIrr = "..."
            )
        )
    }

    private companion object {
        const val CACHE_KEY_PREFIX = "asset_balance_"
        const val LAST_WALLETS_SYNC_TIME_KEY = "last_balance_sync_time"
        const val LAST_PRICE_SYNC_TIME_KEY = "last_price_sync_time"
        const val MIN_UPDATE_INTERVAL = 10 * 60 * 1000L
        const val PRICE_UPDATE_INTERVAL = 5 * 60 * 1000L
    }
}
