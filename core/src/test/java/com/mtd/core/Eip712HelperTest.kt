package com.mtd.core


import com.google.gson.Gson
import com.mtd.core.utils.Eip712Helper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.web3j.crypto.Credentials
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.crypto.StructuredDataEncoder
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.security.SignatureException

class Eip712HelperTest {

    // یک کلید خصوصی و آدرس ثابت برای تست‌های قابل تکرار
    private val TEST_PRIVATE_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
    private val TEST_ADDRESS = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
    private val credentials = Credentials.create(TEST_PRIVATE_KEY)

    @Test
    fun `signPermit should produce a valid and recoverable signature`() {
        // --- Arrange ---
        val tokenName = "Test Token"
        val tokenVersion = "1"
        val chainId = 11155111L
        val tokenAddress = "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7a9c"
        val spenderAddress = "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC"
        val amount = BigInteger("100000000")
        val nonce = BigInteger.ZERO
        val deadline = System.currentTimeMillis() / 1000L + 3600

        // --- Act ---
        val result = Eip712Helper.signPermit(
            credentials = credentials,
            domainName = tokenName,
            domainVersion = tokenVersion,
            chainId = chainId,
            verifyingContract = tokenAddress,
            spender = spenderAddress,
            value = amount,
            nonce = nonce,
            deadline = deadline
        )
        val signature = result.signature
        val typedDataJson = Gson().toJson(result.typedData) // <-- داده‌های JSON رو از نتیجه می‌گیریم

        // --- Assert ---
        // ۱. بررسی فرمت
        assertNotNull(signature)
        assertTrue("v should be 27 or 28", signature.v == 27 || signature.v == 28)

        // ۲. (مهم‌ترین بخش) بازیابی آدرس
        val signerAddress = recoverSignerAddress(typedDataJson, result.signature)

        assertEquals("Recovered address must match the original signer's address",
            TEST_ADDRESS.lowercase(),
            signerAddress.lowercase()
        )
    }

    private fun recoverSignerAddress(jsonData: String, signature: Eip712Helper.Eip712Signature): String {
        // --- بخش اصلاح شده و صحیح برای ساخت هش ---
        val encoder = StructuredDataEncoder(jsonData)
        val messageHash = encoder.hashStructuredData()
        // ---

        val signatureData = Sign.SignatureData(
            signature.v.toByte(),
            Numeric.hexStringToByteArray(signature.r),
            Numeric.hexStringToByteArray(signature.s)
        )

        try {
            val recoveredKey = Sign.signedMessageHashToKey(messageHash, signatureData)
            return "0x" + Keys.getAddress(recoveredKey)
        } catch (e: SignatureException) {
            fail("SignatureException during address recovery: ${e.message}")
            return ""
        }
    }
}