package com.mtd.core.wallet

import com.mtd.core.keymanager.KeyManager
import com.mtd.domain.interfaceRepository.IActiveWalletProvider
import com.mtd.domain.model.core.Wallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveWalletManager @Inject constructor(
    private val keyManager: KeyManager
) : IActiveWalletProvider {

    private val _activeWallet = MutableStateFlow<Wallet?>(null)
    override val activeWallet = _activeWallet.asStateFlow()

    private val _activeWalletId = MutableStateFlow<String?>(null)
    override val activeWalletId = _activeWalletId.asStateFlow()

    fun unlockWallet(wallet: Wallet, secret: String) {
        // ۱. کلیدها را در کش KeyManager بارگذاری کن
        keyManager.loadKeysIntoCache(secret, wallet.hasMnemonic)

        // ۲. آبجکت Wallet را در StateFlow قرار بده
        _activeWallet.value = wallet
        _activeWalletId.value = wallet.id
    }

    fun updateWalletMetadata(wallet: Wallet) {
        _activeWallet.value = wallet
    }

    fun lockWallet() {
        keyManager.clearCache()
        _activeWallet.value = null
        _activeWalletId.value = null
    }

    /**
     * KAN-18: drop any in-memory derived keys belonging to [walletId]. Derived keys are only ever
     * cached for the currently-active wallet, so this is a no-op unless the deleted wallet is the
     * active one — in which case it locks (clears the [KeyManager] credential cache + state).
     */
    fun clearCacheForWallet(walletId: String) {
        if (_activeWalletId.value == walletId) {
            lockWallet()
        }
    }

    fun getAddressForNetwork(chainId: Long): String? {
        return _activeWallet.value?.keys?.find { it.chainId == chainId }?.address
    }
}
