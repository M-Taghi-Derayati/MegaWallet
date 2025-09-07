package com.mtd.core.utils

import kotlinx.coroutines.future.await
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.nio.charset.StandardCharsets


object TokenInfoHelper {

    private suspend fun call(web3j: Web3j, tokenAddress: String, encodedFunction: String): String {
        val response = web3j.ethCall(
            Transaction.createEthCallTransaction(null, tokenAddress, encodedFunction),
            DefaultBlockParameterName.LATEST
        ).sendAsync().await()
        if (response.hasError() || response.value.isNullOrBlank() || response.value == "0x") {
            // برای خطاهای قابل پیش‌بینی مثل "تابع وجود ندارد"، یک مقدار پیش‌فرض برمی‌گردانیم
            if (response.error?.message?.contains("execution reverted") == true) {
                return "" // رشته خالی به عنوان نشانه‌ای از عدم وجود تابع
            }
            throw IllegalStateException("Failed to call contract: ${response.error?.message}")
        }
        return response.value
    }

    /**
     * nonce را با دیکود کردن دستی مقدار هگز می‌خواند.
     */
    suspend fun getNonce(web3j: Web3j, tokenAddress: String, ownerAddress: String): BigInteger {
        val function = Function("nonces", listOf(Address(ownerAddress)), listOf(object : TypeReference<Uint256>() {}))
        val resultHex = call(web3j, tokenAddress, FunctionEncoder.encode(function))

        // خروجی هگز یک uint256 یک رشته 66 کاراکتری است (0x + 64 کاراکتر)
        // ما فقط باید پیشوند "0x" را حذف کرده و آن را به BigInteger تبدیل کنیم.
        if (resultHex.isBlank() || resultHex == "0x") return BigInteger.ZERO

        return try {
            // Numeric.decodeQuantity به درستی رشته هگز "0x..." را به BigInteger تبدیل می‌کند
            Numeric.decodeQuantity(resultHex)
        } catch (e: Exception) {
            BigInteger.ZERO
        }
    }

    /**
     * نام توکن را با دیکود کردن دستی رشته هگز می‌خواند.
     * این روش برای جلوگیری از باگ BigInteger در محیط تست اندروید طراحی شده است.
     */
    suspend fun getName(web3j: Web3j, tokenAddress: String): String {
        val function = Function("name", emptyList(), listOf(object : TypeReference<Utf8String>() {}))
        val resultHex = call(web3j, tokenAddress, FunctionEncoder.encode(function))
        if (resultHex.isBlank() || resultHex == "0x") return "Unknown Token"

        try {
            // --- منطق جدید و دستی برای دیکود کردن رشته ---
            val rawHex = Numeric.cleanHexPrefix(resultHex)

            // خروجی یک رشته داینامیک در ABI به این شکل است:
            // 1. آفست داده‌ها (۳۲ بایت)
            // 2. طول رشته (۳۲ بایت)
            // 3. خود بایت‌های رشته

            // ما به آفست و طول نیازی نداریم، فقط بایت‌های اصلی رشته رو می‌خوایم
            // معمولاً آفست 0x20 و طول بعد از اون میاد.
            if (rawHex.length < 128) { // حداقل باید آفست و طول رو داشته باشه
                return "Unknown Token"
            }

            // طول رشته رو از بخش دوم (بایت 32 تا 64) می‌خونیم
            val length = BigInteger(rawHex.substring(64, 128), 16).toInt()
            if (length == 0) return ""

            // خود رشته از بایت 64 شروع میشه
            val stringHex = rawHex.substring(128, 128 + length * 2)

            // تبدیل هگز به بایت و سپس به رشته UTF-8
            return String(Numeric.hexStringToByteArray(stringHex), StandardCharsets.UTF_8)

        } catch (e: Exception) {
            // اگر هر خطایی در پارس کردن رخ داد، مقدار پیش‌فرض را برگردان
            return "Unknown Token"
        }
    }

    /**
     * نسخه توکن را می‌خواند. این تابع هم از دیکود دستی استفاده می‌کند.
     */
    suspend fun getVersion(web3j: Web3j, tokenAddress: String): String {
        return try {
            val function = Function("version", emptyList(), listOf(object : TypeReference<Utf8String>() {}))
            val resultHex = call(web3j, tokenAddress, FunctionEncoder.encode(function))
            if (resultHex.isBlank() || resultHex == "0x") return "1"

            // استفاده از همان منطق دیکود دستی
            val rawHex = Numeric.cleanHexPrefix(resultHex)
            if (rawHex.length < 128) return "1"
            val length = BigInteger(rawHex.substring(64, 128), 16).toInt()
            if (length == 0) return "1"
            val stringHex = rawHex.substring(128, 128 + length * 2)
            String(Numeric.hexStringToByteArray(stringHex), StandardCharsets.UTF_8)

        } catch (e: Exception) {
            "1" // مقدار پیش‌فرض
        }
    }
}