package com.mtd.domain.usecase.wallet

import com.mtd.domain.interfaceRepository.IActiveWalletProvider
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.core.Wallet
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveActiveWalletUseCase @Inject constructor(
    private val activeWalletProvider: IActiveWalletProvider
) {
    operator fun invoke(): StateFlow<Wallet?> = activeWalletProvider.activeWallet
}

class GetActiveWalletUseCase @Inject constructor(
    private val activeWalletProvider: IActiveWalletProvider
) {
    operator fun invoke(): Wallet? = activeWalletProvider.activeWallet.value
}

class ObserveActiveWalletIdUseCase @Inject constructor(
    private val activeWalletProvider: IActiveWalletProvider
) {
    operator fun invoke(): StateFlow<String?> = activeWalletProvider.activeWalletId
}

class GetActiveWalletIdUseCase @Inject constructor(
    private val activeWalletProvider: IActiveWalletProvider
) {
    operator fun invoke(): String? = activeWalletProvider.activeWalletId.value
}

/**
 * آدرسِ کیف‌پولِ فعال روی یک شبکه.
 *
 * برخلافِ خواندنِ مستقیم از `wallet.keys` — که عکسِ لحظهٔ ساختِ کیف‌پول است و برای شبکه‌ای که بعداً
 * از باندل آمده `null` می‌دهد — این مسیر در صورتِ نبودِ کلید، آدرس را از secret مشتق می‌کند. هر جا
 * که یک آدرسِ **غلط یا خالی** به معنیِ درخواستِ خراب است (استعلامِ سوآپ که `tx.data` را برای همان
 * آدرس می‌سازد) باید از این استفاده شود، نه از `keys`.
 */
class GetActiveAddressForNetworkUseCase @Inject constructor(
    private val walletRepository: IWalletRepository
) {
    suspend operator fun invoke(networkId: String): String? =
        walletRepository.getActiveAddressForNetwork(networkId)
}
