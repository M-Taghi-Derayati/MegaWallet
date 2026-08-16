package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.Asset
import com.mtd.domain.model.HistoryAddress
import com.mtd.domain.model.HistoryPage
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.TransactionFeeDetails
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.core.Wallet
import com.mtd.domain.model.core.WalletKey


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
     * @param networkId شناسهٔ کانونی شبکه‌ای که می‌خواهیم دارایی‌های آن را بگیریم (TASK-53).
     * @return یک Result که در صورت موفقیت، حاوی لیستی از Asset است.
     */
    suspend fun getAssets(networkId: String): ResultResponse<List<Asset>>

    /**
     * لیست تمام کیف پول‌های ذخیره شده در دستگاه (فقط متادیتا) را برمی‌گرداند.
     *
     * ⚠️ `Wallet.keys` در خروجی این متد **همیشه خالی است**. آدرس‌ها از رمزِ رمزگشایی‌شدهٔ هر
     * کیف‌پول مشتق می‌شوند و این متد هیچ رمزی را باز نمی‌کند. برای آدرس‌ها
     * [getWalletKeysForWallets] را صدا بزنید.
     */
    suspend fun getAllWallets(): ResultResponse<List<Wallet>>

    /**
     * کلیدهای **عمومی** هر کیف‌پول: نگاشتِ `walletId` به فهرست [WalletKey].
     *
     * وجود دارد چون [getAllWallets] عمداً فقط متادیتا می‌دهد و [loadExistingWallet] فقط کیف‌پولِ
     * فعال را باز می‌کند؛ بدون این، هیچ صفحه‌ای نمی‌تواند آدرسِ کیف‌پولِ **غیرفعال** را نشان دهد.
     *
     * ⚠️ برای هر کیف‌پول رمزش رمزگشایی و کلیدها مشتق می‌شوند (همان کاری که
     * [getBalancesForMultipleWallets] از قبل می‌کرد)، ولی هیچ رمز یا کلیدِ خصوصی‌ای برنمی‌گردد —
     * [WalletKey] فقط آدرس و کلیدِ عمومی دارد. کیف‌پولِ فعال هم **عوض نمی‌شود**.
     *
     * کیف‌پولی که متادیتا یا رمزش پیدا نشود، از خروجی حذف می‌شود و خطا نمی‌دهد.
     */
    suspend fun getWalletKeysForWallets(
        walletIds: List<String>
    ): ResultResponse<Map<String, List<WalletKey>>>
 
    /**
     * کیف پول فعال سیستم را با استفاده از ID تغییر می‌دهد.
     */
    suspend fun switchActiveWallet(walletId: String): ResultResponse<Unit>
 
    /**
     * ID کیف پول فعال فعلی را برمی‌گرداند.
     */
    suspend fun getActiveWalletId(): String?
 
    suspend fun getTransactionHistory(networkId: String, userAddress: String): ResultResponse<List<TransactionRecord>>

    /**
     * Unified multi-network history (§1.7, `POST /api/mobile/v1/history`) — PROXY mode only.
     * Returns a cursor [HistoryPage]; [cursor] is opaque (null ⇒ first page). In DIRECT mode the
     * underlying data source returns [com.mtd.domain.model.error.ApiError.UnsupportedOperation] so
     * callers fall back to per-network aggregation. Max 25 [addresses] pairs (enforced downstream).
     */
    suspend fun getUnifiedHistory(
        addresses: List<HistoryAddress>,
        cursor: String? = null,
        limit: Int? = null
    ): ResultResponse<HistoryPage>

    suspend fun getTransactionFeeDetails(networkId: String, txId: String): ResultResponse<TransactionFeeDetails>
  
    suspend fun getActiveAddressForNetwork(networkId: String): String?

    /**
     * دریافت موجودی دارایی‌ها برای چندین کیف پول در یک شبکه خاص (Batch Fetching)
     * در این متد، آدرس‌ها به صورت داخلی و امن تولید می‌شوند.
     * @param networkId شناسهٔ کانونی شبکه مورد نظر (TASK-53)
     * @param walletIds لیست آیدی‌های کیف پول
     * @return مپ از <WalletId, List<Asset>>
     */
    suspend fun getBalancesForMultipleWallets(networkId: String, walletIds: List<String>): ResultResponse<Map<String, List<Asset>>>
 
    /**
     * بروزرسانی وضعیت پشتیبان‌گیری یک کیف پول خاص.
     */
    suspend fun updateBackupStatus(walletId: String, manual: Boolean? = null, cloud: Boolean? = null): ResultResponse<Unit>
}
