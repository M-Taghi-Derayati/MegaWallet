package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.CloudWalletItem

interface ICloudWalletBalanceCalculator {
    suspend fun calculateBalances(wallets: List<CloudWalletItem>): List<CloudWalletItem>
}
