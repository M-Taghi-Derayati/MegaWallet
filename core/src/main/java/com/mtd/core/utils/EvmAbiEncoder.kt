package com.mtd.core.utils

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import java.math.BigInteger

object EvmAbiEncoder {

    fun encodeTransfer(toAddress: String, amount: BigInteger): String {
        val function = Function(
            "transfer",
            listOf(Address(toAddress), Uint256(amount)),
            emptyList()
        )
        return FunctionEncoder.encode(function)
    }

    fun encodeApprove(spenderAddress: String, amount: BigInteger): String {
        val function = Function(
            "approve",
            listOf(Address(spenderAddress), Uint256(amount)),
            emptyList()
        )
        return FunctionEncoder.encode(function)
    }
}
