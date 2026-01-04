package com.mtd.core.utils // یا هر پکیج مناسب دیگری

import com.google.gson.Gson
import com.mtd.core.model.DomainDto
import com.mtd.core.model.ForwardRequestDto
import com.mtd.core.model.TypeDto
import org.web3j.crypto.Credentials
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigInteger

object Eip712Helper {

    // ساختار داده Permit
    data class PermitMessage(
        val owner: String,
        val spender: String,
        val value: String, // باید رشته باشد
        val nonce: String,   // باید رشته باشد
        val deadline: Long
    )

    // ساختار کامل EIP-712
   /* data class TypedData(
        val types: Map<String, List<Map<String, String>>>,
        val primaryType: String,
        val domain: Map<String, Any>,
        val message: PermitMessage
    )*/
    data class TypedData(
        val types: Map<String, List<Map<String, String>>>,
        val primaryType: String,
        val domain: Map<String, Any>,
        val message: Any
    )

    data class Eip712Signature(
        val v: Int,
        val r: String,
        val s: String
    )

    // مدل داده برای ساختار کامل JSON که Web3j انتظار دارد
    private data class Eip712TypedData(
        val types: Map<String, List<TypeDto>>,
        val primaryType: String,
        val domain: DomainDto,
        val message: ForwardRequestDto
    )

    // خروجی تابع حالا شامل خود داده‌های امضا شده هم هست
    data class SignatureResult(
        val signature: Eip712Signature,
        val typedData: TypedData // برای استفاده در تست
    )


    // ----------------- تابع جنرال -----------------
    fun signTypedData(
        credentials: Credentials,
        types: Map<String, List<Map<String, String>>>,
        primaryType: String,
        domain: Map<String, Any>,
        message: Any
    ): Sign.SignatureData? {
        val typedData = TypedData(types, primaryType, domain, message)
        val jsonData = Gson().toJson(typedData)

        return Sign.signTypedData(jsonData, credentials.ecKeyPair)

       /* val v = Numeric.toBigInt(signatureData.v).toInt()
        val r = Numeric.toHexString(signatureData.r)
        val s = Numeric.toHexString(signatureData.s)

        return SignatureResult(
            signature = Eip712Signature(v, r, s),
            typedData = typedData
        )*/
    }

    fun signPermit(
        credentials: Credentials,
        domainName: String,
        domainVersion: String,
        chainId: Long,
        verifyingContract: String,
        spender: String,
        value: BigInteger,
        nonce: BigInteger,
        deadline: Long
    ): SignatureResult {
        val domain = mapOf(
            "name" to domainName,
            "version" to domainVersion,
            "chainId" to chainId,
            "verifyingContract" to verifyingContract
        )

        val message = mapOf(
            "owner" to credentials.address,
            "spender" to spender,
            "value" to value,
            "nonce" to nonce,
            "deadline" to deadline
        )

        val types = mapOf(
            "EIP712Domain" to listOf(
                mapOf("name" to "name", "type" to "string"),
                mapOf("name" to "version", "type" to "string"),
                mapOf("name" to "chainId", "type" to "uint256"),
                mapOf("name" to "verifyingContract", "type" to "address")
            ),
            "Permit" to listOf(
                mapOf("name" to "owner", "type" to "address"),
                mapOf("name" to "spender", "type" to "address"),
                mapOf("name" to "value", "type" to "uint256"),
                mapOf("name" to "nonce", "type" to "uint256"),
                mapOf("name" to "deadline", "type" to "uint256")
            )
        )

        val signatureData= signTypedData(credentials, types, "Permit", domain, message)
        val typedData = TypedData(types, "Permit", domain, message)

        val v =Numeric.toBigInt(signatureData?.v).toInt()
        // r و s باید به صورت هگز با پیشوند 0x و طول 64 کاراکتر (32 بایت) باشند
        val r = Numeric.toHexString(signatureData?.r)
        val s = Numeric.toHexString(signatureData?.s)

        return SignatureResult(
            signature = Eip712Signature(v, r, s),
            typedData = typedData // <-- برگرداندن داده‌ها برای تست
        )
    }

    fun signForwardRequest(
        credentials: Credentials,
        domainName: String,
        domainVersion: String,
        chainId: Long,
        verifyingContract: String,
        from: String,
        to: String,
        value: BigInteger,
        gas: BigInteger,
        nonce: BigInteger,
        data: String
    ): String {
        val domain = mapOf(
            "name" to domainName,
            "version" to domainVersion,
            "chainId" to chainId,
            "verifyingContract" to verifyingContract
        )

        val message = mapOf(
            "from" to from,
            "to" to to,
            "value" to value.toString(),
            "gas" to gas.toString(),
            "nonce" to nonce.toString(),
            "data" to data
        )

        val types = mapOf(
            "EIP712Domain" to listOf(
                mapOf("name" to "name", "type" to "string"),
                mapOf("name" to "version", "type" to "string"),
                mapOf("name" to "chainId", "type" to "uint256"),
                mapOf("name" to "verifyingContract", "type" to "address")
            ),
            "ForwardRequest" to listOf(
                mapOf("name" to "from", "type" to "address"),
                mapOf("name" to "to", "type" to "address"),
                mapOf("name" to "value", "type" to "uint256"),
                mapOf("name" to "gas", "type" to "uint256"),
                mapOf("name" to "nonce", "type" to "uint256"),
                mapOf("name" to "data", "type" to "bytes")
            )
        )

        val signatureData= signTypedData(credentials, types, "ForwardRequest", domain, message)

        val r = signatureData?.r
        val s = signatureData?.s
        val v = signatureData?.v

        return Numeric.toHexString(r) +
                Numeric.toHexString(s).removePrefix("0x") +
                Numeric.toHexString(v).removePrefix("0x")
    }




    /**
     * پیام ساختاریافته EIP-712 را برای Permit می‌سازد و آن را امضا می‌کند.
     */
   /* fun signPermit(
        credentials: Credentials,
        domainName: String,
        domainVersion: String,
        chainId: Long,
        verifyingContract: String, // آدرس قرارداد توکن
        spender: String,           // آدرس PhoenixContract ما
        value: BigInteger,
        nonce: BigInteger,
        deadline: Long
    ): SignatureResult  {

        val domain = mapOf(
            "name" to domainName,
            "version" to domainVersion,
            "chainId" to chainId,
            "verifyingContract" to verifyingContract
        )

        val message = PermitMessage(
            owner = credentials.address,
            spender = spender,
            value = value.toString(),
            nonce = nonce.toString(),
            deadline = deadline
        )

        val types = mapOf(
            "EIP712Domain" to listOf(
                mapOf("name" to "name", "type" to "string"),
                mapOf("name" to "version", "type" to "string"),
                mapOf("name" to "chainId", "type" to "uint256"),
                mapOf("name" to "verifyingContract", "type" to "address")
            ),
            "Permit" to listOf(
                mapOf("name" to "owner", "type" to "address"),
                mapOf("name" to "spender", "type" to "address"),
                mapOf("name" to "value", "type" to "uint256"),
                mapOf("name" to "nonce", "type" to "uint256"),
                mapOf("name" to "deadline", "type" to "uint256")
            )
        )

        val typedData = TypedData(types, "Permit", domain, message)
        val jsonData = Gson().toJson(typedData)

        // متد signTypedData در Web3j انتظار یک بایت آرایه از رشته JSON را دارد
        val signatureData = Sign.signTypedData(jsonData, credentials.ecKeyPair)

        // استخراج v, r, s
        val v =Numeric.toBigInt(signatureData.v).toInt()
        // r و s باید به صورت هگز با پیشوند 0x و طول 64 کاراکتر (32 بایت) باشند
        val r = Numeric.toHexString(signatureData.r)
        val s = Numeric.toHexString(signatureData.s)

        return SignatureResult(
            signature = Eip712Signature(v, r, s),
            typedData = typedData // <-- برگرداندن داده‌ها برای تست
        )
    }*/

    /**
     * هر نوع داده ساختاریافته EIP-712 را امضا می‌کند.
     * این تابع برای امضای ForwardRequest در سواپ‌های Native استفاده می‌شود.
     *
     * @param credentials کیف پول کاربر برای امضا.
     * @param domain داده‌های Domain Separator.
     * @param types تعریف ساختارهای داده.
     * @param message پیامی که باید امضا شود.
     * @return امضای کامل به صورت یک رشته هگز (e.g., "0x...").
     */
    fun signTypedData(
        credentials: Credentials,
        domain: DomainDto,
        types: Map<String, List<TypeDto>>,
        message: ForwardRequestDto
    ): String {
        // ۱. ساخت آبجکت کامل Eip712TypedData
        val typedData = Eip712TypedData(
            types = types,
            primaryType = "ForwardRequest", // این باید با کلید اصلی در types یکی باشد
            domain = domain,
            message = message
        )

        // ۲. تبدیل آبجکت به رشته JSON
        // ما از یک نمونه Gson جدید استفاده می‌کنیم تا از تنظیمات سفارشی احتمالی جلوگیری کنیم
        val jsonData = Gson().toJson(typedData)

        Timber.d("EIP-712 JSON to sign: $jsonData")

        // ۳. امضای رشته JSON با استفاده از web3j
        // متد signTypedData در Web3j انتظار یک بایت آرایه از رشته JSON را دارد
         try {
           val signatureData= Sign.signTypedData(jsonData, credentials.ecKeyPair)
             // ۴. ترکیب r, s, v برای ساخت امضای نهایی ۶۵ بایتی
            val r = signatureData.r
            val s = signatureData.s
            val v = signatureData.v

             return Numeric.toHexString(r) +
                     Numeric.toHexString(s).removePrefix("0x") +
                     Numeric.toHexString(v).removePrefix("0x")
        } catch (e: Exception) {
            Timber.e(e)
             throw IllegalStateException("Wallet is locked or key not found.")
        }




        // تبدیل به هگز و الحاق

    }



    /**
     * یک رشته UTF-8 را به فرمت bytes32 تبدیل می‌کند، دقیقاً مانند ethers.js.
     * رشته به حداکثر 31 بایت محدود می‌شود تا از مشکلات null terminator جلوگیری شود.
     */
    fun encodeBytes32String(text: String): String {
        // ۱. رشته را به آرایه بایت با انکودینگ UTF-8 تبدیل می‌کنیم
        var textBytes = text.toByteArray(Charsets.UTF_8)

        // ۲. اگر طول از ۳۱ بایت بیشتر بود، خطا می‌دهیم (این بهترین رویه است)
        if (textBytes.size > 31) {
            // throw IllegalArgumentException("String is too long for bytes32: ${textBytes.size} bytes")
            // یا برای سازگاری، آن را کوتاه می‌کنیم
            Timber.w("String '${text}' is too long for bytes32, truncating to 31 bytes.")
            textBytes = textBytes.sliceArray(0 until 31)
        }

        // ۳. یک آرایه بایت جدید با طول دقیقاً 32 ایجاد می‌کنیم
        val paddedBytes = ByteArray(32)

        // ۴. بایت‌های رشته را در ابتدای آرایه جدید کپی می‌کنیم
        System.arraycopy(textBytes, 0, paddedBytes, 0, textBytes.size)

        // ۵. آرایه بایت نهایی را به رشته هگز تبدیل می‌کنیم
        return Numeric.toHexString(paddedBytes)
    }


}