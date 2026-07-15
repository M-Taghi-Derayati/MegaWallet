package com.mtd.data.repository

import com.mtd.core.keymanager.KeyManager
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.BalanceFormatter
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.domain.interfaceRepository.ICloudWalletBalanceCalculator
import com.mtd.domain.interfaceRepository.IMarketDataRepository
import com.mtd.domain.model.CloudWalletItem
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.assets.AssetConfig
import com.mtd.domain.model.assets.AssetPriceDto
import com.mtd.domain.model.core.NetworkType
import java.math.BigDecimal
import javax.inject.Inject

class CloudWalletBalanceCalculatorImpl @Inject constructor(
    private val keyManager: KeyManager,
    private val dataSourceFactory: dagger.Lazy<ChainDataSourceFactory>,
    private val assetRegistry: AssetRegistry,
    private val blockchainRegistry: BlockchainRegistry,
    private val marketDataRepository: IMarketDataRepository
) : ICloudWalletBalanceCalculator {

    override suspend fun calculateBalances(wallets: List<CloudWalletItem>): List<CloudWalletItem> {
        val allAssets = assetRegistry.getAllAssets()
        val pricesMap = getPricesMap(allAssets)

        return wallets.map { wallet ->
            val totalUsd = calculateSingleWalletBalance(wallet, allAssets, pricesMap)
            wallet.copy(
                balanceUsdt = BalanceFormatter.formatUsdValue(totalUsd).replace("$", "")
            )
        }
    }

    private suspend fun getPricesMap(allAssets: List<AssetConfig>): Map<String, AssetPriceDto> {
        val symbols = allAssets.map { it.symbol }.distinct()
        val ids = allAssets.map { it.id }.distinct()

        val resultPair: Pair<List<String>, List<String>> = Pair(symbols, ids)
        if (resultPair.first.isEmpty()) return emptyMap()

        return when (val result = marketDataRepository.getLatestPrices(resultPair)) {
            is ResultResponse.Success -> result.data.associateBy { it.assetId }
            is ResultResponse.Error -> emptyMap()
        }
    }

    private suspend fun calculateSingleWalletBalance(
        cloudWallet: CloudWalletItem,
        allAssets: List<AssetConfig>,
        pricesMap: Map<String, AssetPriceDto>
    ): BigDecimal {
        var total = BigDecimal.ZERO
        val keys = if (cloudWallet.isMnemonic) {
            keyManager.generateWalletKeysFromMnemonic(cloudWallet.key)
        } else {
            keyManager.generateWalletKeysFromPrivateKey(cloudWallet.key)
        }

        keys.forEach { key ->
            val chainId = key.chainId ?: return@forEach
            val dataSource = dataSourceFactory.get().create(chainId)
            val networkId = blockchainRegistry.getNetworkByName(key.networkName)?.id

            if (key.networkType == NetworkType.EVM) {
                val result = dataSource.getBalanceAssets(key.address)
                if (result is ResultResponse.Success) {
                    result.data.forEach { assetBalance ->
                        val assetConfig = allAssets.find { asset ->
                            asset.networkId == networkId &&
                                (
                                    asset.contractAddress.equals(assetBalance.contractAddress, true) ||
                                        (asset.contractAddress == null && assetBalance.contractAddress == null)
                                    )
                        }
                        if (assetConfig != null) {
                            total += calculateUsdValue(assetBalance.balance, assetConfig, pricesMap)
                        }
                    }
                }
            } else {
                val result = dataSource.getBalance(key.address)
                if (result is ResultResponse.Success) {
                    val assetConfig = allAssets.find { it.networkId == networkId }
                    if (assetConfig != null) {
                        total += calculateUsdValue(result.data, assetConfig, pricesMap)
                    }
                }
            }
        }
        return total
    }

    private fun calculateUsdValue(
        balance: BigDecimal,
        assetConfig: AssetConfig,
        pricesMap: Map<String, AssetPriceDto>
    ): BigDecimal {
        val balanceDecimal = BalanceFormatter.formatBalance(balance, assetConfig.decimals).toBigDecimal()
        val price = pricesMap[assetConfig.symbol]?.priceUsd ?: BigDecimal.ZERO
        return balanceDecimal * price
    }
}
