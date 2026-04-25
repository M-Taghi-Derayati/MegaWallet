package com.mtd.core.utils

import com.mtd.core.network.tron.TronUtils
import java.math.BigInteger

object TronAbiEncoder {

    fun encodeAddressAndUint256(addressBase58: String, amount: BigInteger): String {
        val addressHexNoPrefix = TronUtils.toHex(addressBase58).removePrefix("41")
        val paddedAddress = addressHexNoPrefix.padStart(64, '0')
        val paddedAmount = amount.toString(16).padStart(64, '0')
        return paddedAddress + paddedAmount
    }
}
