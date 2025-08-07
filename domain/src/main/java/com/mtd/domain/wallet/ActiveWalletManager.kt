// در :domain/wallet/ActiveWalletManager.kt
package com.mtd.domain.wallet

import com.mtd.domain.model.Wallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveWalletManager @Inject constructor() {

    private val _activeWallet = MutableStateFlow<Wallet?>(null)
    val activeWallet = _activeWallet.asStateFlow()

    /**
     * کیف پول فعال را در حافظه تنظیم می‌کند (معمولاً پس از ورود یا باز کردن قفل).
     */
    fun setActiveWallet(wallet: Wallet) {
        _activeWallet.value = wallet
    }

    /**
     * اطلاعات کیف پول فعال را پاک می‌کند (هنگام قفل شدن یا خروج).
     */
    fun clearActiveWallet() {
        _activeWallet.value = null
    }

    /**
     * آدرس کاربر را برای یک شبکه خاص از کیف پول فعال برمی‌گرداند.
     */
    fun getAddressForNetwork(chainId: Long): String? {
        return _activeWallet.value?.keys?.find { it.chainId == chainId }?.address
    }
}