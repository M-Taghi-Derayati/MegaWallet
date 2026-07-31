package com.mtd.domain.usecase.wallet

import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.model.core.WalletKey
import javax.inject.Inject

class ApplyWalletKeySymbolsUseCase @Inject constructor(
    private val networkCatalog: INetworkCatalog
) {
    operator fun invoke(keys: List<WalletKey>): List<WalletKey> {
        return keys.onEach { key ->
            // TASK-53 — با شناسهٔ کانونی جست‌وجو می‌شود تا شبکه‌های فقط-در-باندل هم نماد بگیرند.
            val networkInfo = networkCatalog.getNetworkInfoById(key.networkId)
            key.symbol = (networkInfo?.currencySymbol ?: "ETH").lowercase()
        }
    }
}
