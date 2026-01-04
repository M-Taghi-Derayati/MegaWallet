package com.mtd.megawallet.viewmodel.news

import androidx.lifecycle.ViewModel
import com.mtd.data.repository.IWalletRepository
import com.mtd.megawallet.event.ImportData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel برای مدیریت وضعیت Modal Transition در WelcomeActivity.
 * شامل ردیابی وضعیت Modal و بررسی وجود کیف پول برای هدایت اولیه کاربر.
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val walletRepository: IWalletRepository
) : ViewModel() {
    
    private val _isModalActive = MutableStateFlow(false)
    val isModalActive: StateFlow<Boolean> = _isModalActive.asStateFlow()

    // نگهداری داده‌های ایمپورت شده به صورت موقت برای انتقال به صفحه ساخت (CreateWallet)
    var pendingImportData: ImportData? = null
        private set

    fun setImportData(data: ImportData?) {
        this.pendingImportData = data
    }
    
    /**
     * فعال کردن حالت Modal (وقتی صفحه‌ای مانند Add Existing Wallet باز می‌شود)
     */
    fun setModalActive(active: Boolean) {
        _isModalActive.value = active
    }

    suspend fun hasWallet(): Boolean {
        return walletRepository.hasWallet()
    }

    fun clearImportData() {
        pendingImportData = null
    }
}
