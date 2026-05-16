package com.mtd.domain.usecase.wallet

import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.model.core.WalletKey
import javax.inject.Inject

class ApplyWalletKeySymbolsUseCase @Inject constructor(
    private val networkCatalog: INetworkCatalog
) {
    operator fun invoke(keys: List<WalletKey>): List<WalletKey> {
        return keys.onEach { key ->
            val networkInfo = networkCatalog.getNetworkInfoByName(key.networkName)
            key.symbol = (networkInfo?.currencySymbol ?: "ETH").lowercase()
        }
    }
}
