package com.mtd.domain.usecase.history

import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.core.NetworkName
import javax.inject.Inject

data class WalletAddressBookEntry(
    val address: String,
    val walletName: String,
    val walletColor: Int
)

class GetTransactionHistoryUseCase @Inject constructor(
    private val walletRepository: IWalletRepository
) {
    suspend operator fun invoke(
        networkName: NetworkName,
        userAddress: String
    ): ResultResponse<List<TransactionRecord>> {
        return walletRepository.getTransactionHistory(networkName, userAddress)
    }
}

class GetWalletAddressBookUseCase @Inject constructor(
    private val walletRepository: IWalletRepository
) {
    suspend operator fun invoke(): ResultResponse<List<WalletAddressBookEntry>> {
        return when (val result = walletRepository.getAllWallets()) {
            is ResultResponse.Success -> ResultResponse.Success(
                result.data.flatMap { wallet ->
                    wallet.keys.map { key ->
                        WalletAddressBookEntry(
                            address = key.address,
                            walletName = wallet.name,
                            walletColor = wallet.color
                        )
                    }
                }
            )

            is ResultResponse.Error -> ResultResponse.Error(result.exception)
        }
    }
}
