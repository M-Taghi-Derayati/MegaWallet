package com.mtd.data.repository

import com.mtd.core.model.NetworkName
import com.mtd.core.model.NetworkType
import com.mtd.domain.model.Asset
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.Wallet
import java.math.BigInteger


interface IWalletRepository {

    /**
     * یک کیف پول جدید بر اساس کلمات بازیابی ایجاد و ذخیره می‌کند.
     * @return یک Result که در صورت موفقیت، حاوی آبجکت Wallet است.
     */
    suspend fun createNewWallet(): ResultResponse<Wallet>

    /**
     * یک کیف پول موجود را از طریق کلمات بازیابی وارد می‌کند.
     * @param mnemonic کلمات بازیابی وارد شده توسط کاربر.
     * @return یک Result که در صورت موفقیت، حاوی آبجکت Wallet است.
     */
    suspend fun importWalletFromMnemonic(mnemonic: String): ResultResponse<Wallet>

    /**
     * یک کیف پول موجود را از طریق کلید خصوصی وارد می‌کند.
     * (این متد برای سادگی فعلاً فقط برای شبکه‌های EVM کار می‌کند)
     * @param privateKey کلید خصوصی وارد شده توسط کاربر.
     * @return یک Result که در صورت موفقیت، حاوی آبجکت Wallet است.
     */
    suspend fun importWalletFromPrivateKey(privateKey: String): ResultResponse<Wallet>

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
     * کلمات بازیابی ذخیره شده را برای نمایش به کاربر (Export) برمی‌گرداند.
     * این متد باید فقط پس از احراز هویت قوی کاربر فراخوانی شود.
     * @return یک Result که در صورت موفقیت، حاوی کلمات بازیابی است.
     */
    suspend fun getSavedMnemonic(): ResultResponse<String?>

    /**
     * تمام اطلاعات مربوط به کیف پول فعلی را از حافظه امن پاک می‌کند.
     */
    suspend fun deleteWallet()


    /**
     * یک تراکنش را امضا کرده و به شبکه ارسال می‌کند.
     * @param networkType شبکه‌ای که تراکنش باید در آن ارسال شود.
     * @param toAddress آدرس مقصد.
     * @param amount مقدار ارسالی (در واحد Wei).
     * @return یک Result که در صورت موفقیت، حاوی هش تراکنش (Transaction Hash) است.
     */
    suspend fun sendTransaction(
        params: TransactionParams
    ): ResultResponse<String>

    /**
     * لیست تمام دارایی‌های کاربر (توکن‌ها) را برای یک شبکه خاص به همراه موجودی آن‌ها برمی‌گرداند.
     * @param networkType شبکه‌ای که می‌خواهیم دارایی‌های آن را بگیریم.
     * @return یک Result که در صورت موفقیت، حاوی لیستی از Asset است.
     */
    suspend fun getAssets(networkName: NetworkName): ResultResponse<List<Asset>>

    suspend fun getTransactionHistory( networkName: NetworkName, userAddress: String): ResultResponse<List<TransactionRecord>>

}

