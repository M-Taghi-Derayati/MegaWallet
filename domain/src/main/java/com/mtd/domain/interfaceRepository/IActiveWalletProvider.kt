package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.core.Wallet
import kotlinx.coroutines.flow.StateFlow

interface IActiveWalletProvider {
    val activeWallet: StateFlow<Wallet?>
    val activeWalletId: StateFlow<String?>
}
