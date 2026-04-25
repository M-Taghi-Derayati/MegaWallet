package com.mtd.core.utils

import com.google.gson.Gson
import org.web3j.crypto.Credentials
import org.web3j.crypto.Sign
import java.math.BigInteger

object TypedDataSigner {

    data class SignatureParts(
        val v: Int,
        val r: String,
        val s: String
    )

    fun signTypedData(
        credentials: Credentials,
        primaryType: String,
        types: Map<String, List<Map<String, String>>>,
        domain: Map<String, Any>,
        message: Map<String, Any>
    ): Sign.SignatureData {
        val typedData = mapOf(
            "types" to types,
            "primaryType" to primaryType,
            "domain" to domain,
            "message" to message
        )
        val jsonData = Gson().toJson(typedData)
        return Sign.signTypedData(jsonData, credentials.ecKeyPair)
    }

    fun signTypedDataHex(
        credentials: Credentials,
        primaryType: String,
        types: Map<String, List<Map<String, String>>>,
        domain: Map<String, Any>,
        message: Map<String, Any>
    ): String {
        val signature = signTypedData(credentials, primaryType, types, domain, message)
        val rHex = signature.r.joinToString("") { eachByte ->
            ((eachByte.toInt() and 0xFF).toString(16)).padStart(2, '0')
        }
        val sHex = signature.s.joinToString("") { eachByte ->
            ((eachByte.toInt() and 0xFF).toString(16)).padStart(2, '0')
        }
        val vHex = ((signature.v.firstOrNull()?.toInt() ?: 0) and 0xFF).toString(16).padStart(2, '0')
        return "0x$rHex$sHex$vHex"
    }

    fun toParts(signatureHex: String): SignatureParts {
        val cleaned = signatureHex.removePrefix("0x")
        require(cleaned.length == 130) { "Invalid signature length" }
        val r = "0x" + cleaned.substring(0, 64)
        val s = "0x" + cleaned.substring(64, 128)
        val v = BigInteger(cleaned.substring(128, 130), 16).toInt()
        return SignatureParts(v = v, r = r, s = s)
    }
}
