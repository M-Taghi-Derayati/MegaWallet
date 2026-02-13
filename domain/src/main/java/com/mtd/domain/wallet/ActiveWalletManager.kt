// در :domain/wallet/ActiveWalletManager.kt
package com.mtd.domain.wallet

import com.mtd.core.keymanager.KeyManager
import com.mtd.domain.model.Wallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveWalletManager @Inject constructor(
    private val keyManager: KeyManager
) {

    private val _activeWallet = MutableStateFlow<Wallet?>(null)
    val activeWallet = _activeWallet.asStateFlow()

    private val _activeWalletId = MutableStateFlow<String?>(null)
    val activeWalletId = _activeWalletId.asStateFlow()

    /**
     * کیف پول را "باز" می‌کند.
     * Mnemonic یا Private Key را دریافت کرده، کلیدها را در کش KeyManager بارگذاری می‌کند
     * و آبجکت Wallet را برای دسترسی عمومی تنظیم می‌کند.
     * این متد باید بعد از احراز هویت کاربر (مثلاً وارد کردن رمز عبور) فراخوانی شود.
     */
    fun unlockWallet(wallet: Wallet, secret: String) {
        // ۱. کلیدها را در کش KeyManager بارگذاری کن
        keyManager.loadKeysIntoCache(secret, wallet.hasMnemonic)

        // ۲. آبجکت Wallet را در StateFlow قرار بده
        _activeWallet.value = wallet
        _activeWalletId.value = wallet.id
    }

    /**
     * اطلاعات متادیتای کیف پول (مثل نام یا رنگ) را بدون تغییر کلیدها بروز می‌کند.
     */
    fun updateWalletMetadata(wallet: Wallet) {
        _activeWallet.value = wallet
    }

    /**
     * کیف پول را "قفل" می‌کند.
     * کش کلیدهای خصوصی را پاک کرده و اطلاعات عمومی کیف پول را هم پاک می‌کند.
     * این متد باید هنگام خروج یا قفل شدن اپ فراخوانی شود.
     */
    fun lockWallet() {
        // ۱. کش KeyManager را پاک کن
        keyManager.clearCache()

        // ۲. StateFlow را پاک کن
        _activeWallet.value = null
        _activeWalletId.value = null
    }




    /**
     * آدرس کاربر را برای یک شبکه خاص از کیف پول فعال برمی‌گرداند.
     */
    fun getAddressForNetwork(chainId: Long): String? {
        return _activeWallet.value?.keys?.find { it.chainId == chainId }?.address
    }
}