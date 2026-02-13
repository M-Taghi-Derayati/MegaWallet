package com.mtd.data.repository

import com.mtd.core.model.NetworkName
import com.mtd.domain.model.Asset
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.Wallet


interface IWalletRepository {

    /**
     * یک کیف پول جدید بر اساس کلمات بازیابی ایجاد و ذخیره می‌کند.
     * @param id ID اختیاری برای کیف پول (در صورت restore از cloud). اگر null باشد، UUID جدید تولید می‌شود.
     * @return یک Result که در صورت موفقیت، حاوی آبجکت Wallet است.
     */
    suspend fun createNewWallet(
        name: String, 
        color: Int, 
        id: String? = null,
        isManualBackedUp: Boolean = false,
        isCloudBackedUp: Boolean = false
    ): ResultResponse<Wallet>

    /**
     * یک کیف پول موجود را از طریق کلمات بازیابی وارد می‌کند.
     * @param mnemonic کلمات بازیابی وارد شده توسط کاربر.
     * @param id ID اختیاری برای کیف پول (در صورت restore از cloud). اگر null باشد، UUID جدید تولید می‌شود.
     * @return یک Result که در صورت موفقیت، حاوی آبجکت Wallet است.
     */
    suspend fun importWalletFromMnemonic(
        mnemonic: String, 
        name: String, 
        color: Int, 
        id: String? = null,
        isManualBackedUp: Boolean = true,
        isCloudBackedUp: Boolean = false
    ): ResultResponse<Wallet>

    /**
     * یک کیف پول موجود را از طریق کلید خصوصی وارد می‌کند.
     * (این متد برای سادگی فعلاً فقط برای شبکه‌های EVM کار می‌کند)
     * @param privateKey کلید خصوصی وارد شده توسط کاربر.
     * @param id ID اختیاری برای کیف پول (در صورت restore از cloud). اگر null باشد، UUID جدید تولید می‌شود.
     * @return یک Result که در صورت موفقیت، حاوی آبجکت Wallet است.
     */
    suspend fun importWalletFromPrivateKey(
        privateKey: String, 
        name: String, 
        color: Int, 
        id: String? = null,
        isManualBackedUp: Boolean = true,
        isCloudBackedUp: Boolean = false
    ): ResultResponse<Wallet>

    /**
     * کیف پول ذخیره شده فعلی را از حافظه امن بارگذاری می‌کند.
     * @return یک Result که در صورت موفقیت، حاوی آبجکت Wallet یا null (اگر کیف پولی وجود نداشته باشد) است.
     */
    suspend fun loadExistingWallet(): ResultResponse<Wallet?>

    /**
     * بررسی می‌کند که آیا کیف پولی در حافظه دستگاه ذخیره شده است یا خیر.
     */
    suspend fun hasWallet(): Boolean

    /**
     * کلمات بازیابی ذخیره شده را برای یک کیف پول خاص برمی‌گرداند.
     * این متد باید فقط پس از احراز هویت قوی کاربر فراخوانی شود.
     */
    suspend fun getMnemonic(walletId: String): ResultResponse<String?>

    /**
     * تمام اطلاعات مربوط به کیف پول فعلی را از حافظه امن پاک می‌کند.
     */
    suspend fun deleteWallet()

    /**
     * حذف یک کیف پول خاص با استفاده از ID.
     */
    suspend fun deleteWallet(walletId: String): ResultResponse<Unit>

    /**
     * به‌روزرسانی نام یک کیف پول خاص.
     */
    suspend fun updateWalletName(walletId: String, newName: String): ResultResponse<Unit>

    /**
     * به‌روزرسانی رنگ یک کیف پول خاص.
     */
    suspend fun updateWalletColor(walletId: String, newColor: Int): ResultResponse<Unit>

    /**
     * یک تراکنش را امضا کرده و به شبکه ارسال می‌کند.
     * @param params پارامترهای تراکنش.
     * @return یک Result که در صورت موفقیت، حاوی هش تراکنش است.
     */
    suspend fun sendTransaction(
        params: TransactionParams
    ): ResultResponse<String>

    /**
     * لیست تمام دارایی‌های کاربر (توکن‌ها) را برای یک شبکه خاص به همراه موجودی آن‌ها برمی‌گرداند.
     * @param networkName نام شبکه‌ای که می‌خواهیم دارایی‌های آن را بگیریم.
     * @return یک Result که در صورت موفقیت، حاوی لیستی از Asset است.
     */
    suspend fun getAssets(networkName: NetworkName): ResultResponse<List<Asset>>

    /**
     * لیست تمام کیف پول‌های ذخیره شده در دستگاه (فقط متادیتا) را برمی‌گرداند.
     */
    suspend fun getAllWallets(): ResultResponse<List<Wallet>>
 
    /**
     * کیف پول فعال سیستم را با استفاده از ID تغییر می‌دهد.
     */
    suspend fun switchActiveWallet(walletId: String): ResultResponse<Unit>
 
    /**
     * ID کیف پول فعال فعلی را برمی‌گرداند.
     */
    suspend fun getActiveWalletId(): String?
 
    suspend fun getTransactionHistory( networkName: NetworkName, userAddress: String): ResultResponse<List<TransactionRecord>>
 
    suspend fun getActiveAddressForNetwork(networkId: String): String?

    /**
     * دریافت موجودی دارایی‌ها برای چندین کیف پول در یک شبکه خاص (Batch Fetching)
     * در این متد، آدرس‌ها به صورت داخلی و امن تولید می‌شوند.
     * @param networkName شبکه مورد نظر
     * @param walletIds لیست آیدی‌های کیف پول
     * @return مپ از <WalletId, List<Asset>>
     */
    suspend fun getBalancesForMultipleWallets(networkName: NetworkName, walletIds: List<String>): ResultResponse<Map<String, List<Asset>>>
 
    /**
     * بروزرسانی وضعیت پشتیبان‌گیری یک کیف پول خاص.
     */
    suspend fun updateBackupStatus(walletId: String, manual: Boolean? = null, cloud: Boolean? = null): ResultResponse<Unit>
}
