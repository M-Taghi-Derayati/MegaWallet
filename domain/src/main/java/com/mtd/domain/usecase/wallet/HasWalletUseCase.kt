package com.mtd.domain.usecase.wallet

import com.mtd.domain.interfaceRepository.IWalletRepository
import javax.inject.Inject

class HasWalletUseCase @Inject constructor(
    private val walletRepository: IWalletRepository
) {
    suspend operator fun invoke(): Boolean {
        return walletRepository.hasWallet()
    }
}
