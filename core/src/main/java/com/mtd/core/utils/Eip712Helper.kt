package com.mtd.core.utils // یا هر پکیج مناسب دیگری

import com.google.gson.Gson
import com.mtd.core.model.Eip712Signature
import org.web3j.crypto.Credentials
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
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
    data class TypedData(
        val types: Map<String, List<Map<String, String>>>,
        val primaryType: String,
        val domain: Map<String, Any>,
        val message: PermitMessage
    )

    // خروجی تابع حالا شامل خود داده‌های امضا شده هم هست
    data class SignatureResult(
        val signature: Eip712Signature,
        val typedData: TypedData // برای استفاده در تست
    )

    /**
     * پیام ساختاریافته EIP-712 را برای Permit می‌سازد و آن را امضا می‌کند.
     */
    fun signPermit(
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
    }
}