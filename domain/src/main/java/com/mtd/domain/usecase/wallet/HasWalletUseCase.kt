package com.mtd.domain.usecase.wallet

import com.mtd.domain.interfaceRepository.IWalletRepository
import javax.inject.Inject

class HasWalletUseCase @Inject constructor(
    private val walletRepository: dagger.Lazy<IWalletRepository>
) {
    suspend operator fun invoke(): Boolean {
        return walletRepository.get().hasWallet()
    }
}
