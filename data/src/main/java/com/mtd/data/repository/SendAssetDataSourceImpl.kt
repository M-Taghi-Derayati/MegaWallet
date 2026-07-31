package com.mtd.data.repository

import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.BalanceFormatter
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.domain.interfaceRepository.ISendAssetDataSource
import com.mtd.domain.model.Asset
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.FeeOption
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.core.Wallet
import com.mtd.domain.model.send.SendFeeQuote
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

class SendAssetDataSourceImpl @Inject constructor(
    private val dataSourceFactory: dagger.Lazy<ChainDataSourceFactory>,
    private val blockchainRegistry: BlockchainRegistry
) : ISendAssetDataSource {

    override suspend fun refreshAssetBalance(
        wallet: Wallet,
        asset: AssetItem
    ): ResultResponse<AssetItem?> {
        return try {
            val network = blockchainRegistry.getNetworkById(asset.networkId)
                ?: return ResultResponse.Success(null)
            val senderAddress = wallet.keys.find { it.networkName == network.name }?.address
                ?: return ResultResponse.Success(null)

            when (val result = dataSourceFactory.get().create(network).getBalanceAssets(senderAddress)) {
                is ResultResponse.Success -> {
                    val target = result.data.find {
                        if (asset.isNativeToken) {
                            it.contractAddress.isNullOrEmpty()
                        } else {
                            it.contractAddress?.equals(asset.contractAddress, ignoreCase = true) == true
                        }
                    }

                    if (target == null || target.balance == asset.balanceRaw) {
                        ResultResponse.Success(null)
                    } else {
                        // TASK-56 — balance only. The fiat strings are the caller's job now (see
                        // ISendAssetDataSource); producing them here meant the send screen could
                        // disagree with the wallet list about both format and currency.
                        ResultResponse.Success(
                            asset.copy(
                                balanceRaw = target.balance,
                                balance = BalanceFormatter.formatBalance(target.balance, asset.decimals)
                            )
                        )
                    }
                }

                is ResultResponse.Error -> ResultResponse.Error(result.exception)
            }
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun estimateFees(
        wallet: Wallet,
        asset: AssetItem,
        recipientAddress: String,
        amount: BigInteger
    ): ResultResponse<SendFeeQuote> {
        return try {
            val network = blockchainRegistry.getNetworkById(asset.networkId)
                ?: return ResultResponse.Error(IllegalStateException("Network ${asset.networkId} not found"))
            val senderAddress = wallet.keys.find { it.networkName == network.name }?.address
                ?: return ResultResponse.Error(IllegalStateException("Sender address for ${network.name} not found"))

            val domainAsset = Asset(
                name = asset.name,
                symbol = asset.symbol,
                decimals = asset.decimals,
                contractAddress = asset.contractAddress,
                balance = asset.balanceRaw
            )

            when (
                val result = dataSourceFactory.get().create(network).getFeeOptions(
                    fromAddress = senderAddress,
                    toAddress = recipientAddress,
                    asset = domainAsset,
                    amount = amount
                )
            ) {
                is ResultResponse.Success -> ResultResponse.Success(
                    SendFeeQuote(
                        networkSymbol = network.currencySymbol,
                        options = result.data.map { data ->
                            FeeOption(
                                level = data.level,
                                feeAmountDisplay = "${data.feeInCoin ?: BigDecimal.ZERO} ${network.currencySymbol}",
                                feeAmountUsdDisplay = "",
                                feeAmountIrrDisplay = "",
                                estimatedTime = data.estimatedTime,
                                feeInSmallestUnit = data.feeInSmallestUnit,
                                feeInCoin = data.feeInCoin,
                                gasPrice = data.gasPrice,
                                gasLimit = data.gasLimit,
                                feeRateInSatsPerByte = data.feeRateInSatsPerByte,
                                feeInUsd = data.feeInUsd
                            )
                        }
                    )
                )

                is ResultResponse.Error -> ResultResponse.Error(result.exception)
            }
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }
}
