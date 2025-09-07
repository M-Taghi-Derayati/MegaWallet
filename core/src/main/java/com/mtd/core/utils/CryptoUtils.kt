// In: core/src/main/java/com/mtd/core/utils/CryptoUtils.kt (یک فایل جدید بسازید)

package com.mtd.core.utils

import org.bitcoinj.base.exceptions.AddressFormatException
import org.bitcoinj.crypto.DumpedPrivateKey
import org.bitcoinj.crypto.ECKey
import org.bitcoinj.params.MainNetParams
import org.web3j.crypto.ECKeyPair
import java.math.BigInteger

object CryptoUtils {

    /**
     * یک کلاس داده برای نگهداری نتیجه اعتبارسنجی و کلید خصوصی استخراج شده.
     */
    data class PrivateKeyValidationResult(
        val isValid: Boolean,
        val privateKeyHex: String? = null // کلید خصوصی 64 کاراکتری خالص، در صورت معتبر بودن
    )

    /**
     * به صورت جامع بررسی می‌کند که آیا یک رشته ورودی، یک کلید خصوصی معتبر در فرمت‌های
     * هگز (با/بدون 0x) یا WIF بیت‌کوین است یا خیر.
     *
     * @param input رشته ورودی برای بررسی.
     * @return یک آبجکت `PrivateKeyValidationResult`.
     */
    fun validateAndExtractPrivateKey(input: String): PrivateKeyValidationResult {
        val trimmedInput = input.trim()

        // --- تلاش برای اعتبارسنجی به عنوان فرمت هگز ---
        val hexResult = validateAsHex(trimmedInput)
        if (hexResult.isValid) {
            return hexResult
        }

        // --- اگر هگز نبود، تلاش برای اعتبارسنجی به عنوان فرمت WIF ---
        val wifResult = validateAsWif(trimmedInput)
        if (wifResult.isValid) {
            return wifResult
        }

        // اگر هیچکدام از فرمت‌ها معتبر نبودند
        return PrivateKeyValidationResult(isValid = false)
    }

    /**
     * یک ورودی را به عنوان کلید خصوصی هگز (با/بدون 0x) اعتبارسنجی می‌کند.
     */
    private fun validateAsHex(input: String): PrivateKeyValidationResult {
        val privateKeyHex = if (input.startsWith("0x")) input.substring(2) else input

        if (privateKeyHex.length != 64 || !privateKeyHex.matches(Regex("[a-fA-F0-9]+"))) {
            return PrivateKeyValidationResult(false)
        }

        return try {
            val privateKeyBigInt = BigInteger(privateKeyHex, 16)
            if (privateKeyBigInt == BigInteger.ZERO) return PrivateKeyValidationResult(false)

            // اعتبارسنجی با bitcoinj
            ECKey.fromPrivate(privateKeyBigInt)

            // اگر معتبر بود، هگز خالص را برگردان
            PrivateKeyValidationResult(true, privateKeyHex)
        } catch (e: Exception) {
            PrivateKeyValidationResult(false)
        }
    }

    /**
     * یک ورودی را به عنوان کلید خصوصی WIF بیت‌کوین اعتبارسنجی می‌کند.
     */
    private fun validateAsWif(input: String): PrivateKeyValidationResult {
        return try {
            // کتابخانه bitcoinj خودش فرمت WIF را تشخیص می‌دهد و اعتبارسنجی می‌کند.
            // ما از MainNetParams استفاده می‌کنیم چون فرمت WIF برای mainnet و testnet یکسان است
            // و فقط در پیشوند تفاوت دارند که خود کتابخانه مدیریت می‌کند.
            val dumpedPrivateKey = DumpedPrivateKey.fromBase58(MainNetParams.get(), input)

            // کلید خصوصی را به صورت هگز استخراج می‌کنیم
            val ecKey = dumpedPrivateKey.key
            val privateKeyHex = ecKey.privateKeyAsHex

            PrivateKeyValidationResult(true, privateKeyHex)
        } catch (e: AddressFormatException) {
            // این خطا یعنی ورودی، فرمت Base58 معتبر WIF را ندارد.
            PrivateKeyValidationResult(false)
        } catch (e: Exception) {
            // خطاهای دیگر
            PrivateKeyValidationResult(false)
        }
    }
}