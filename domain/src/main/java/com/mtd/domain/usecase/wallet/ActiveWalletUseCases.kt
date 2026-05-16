package com.mtd.domain.usecase.wallet

import com.mtd.domain.interfaceRepository.IActiveWalletProvider
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
