package com.mtd.data.mapper

import com.mtd.data.dto.HistoryItemDto
import com.mtd.domain.model.BitcoinTransaction
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.TokenTransferDetails
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import com.mtd.domain.model.TronTransaction
import com.mtd.domain.model.core.NetworkName
import java.math.BigInteger

/** Maps the polymorphic history DTO → the existing [TransactionRecord] domain models. */
fun HistoryItemDto.toDomain(): TransactionRecord {
    val net = NetworkName.entries.find { it.name.equals(network, ignoreCase = true) }
    val st = status.toTransactionStatus()
    return when (this) {
        is HistoryItemDto.EvmHistoryDto -> {
            val td = tokenDetails(fromAddress, toAddress, valueRaw, tokenSymbol, tokenDecimals, tokenAmountRaw, contractAddress)
            EvmTransaction(
                hash = hash, timestamp = timestamp, fee = feeRaw, status = st, networkName = net,
                fromAddress = fromAddress.orEmpty(), toAddress = toAddress.orEmpty(),
                // For a token transfer use the token amount, not native valueRaw (which may be 0).
                amount = td?.amount ?: valueRaw, isOutgoing = isOutgoing,
                gasPrice = gasPriceRaw, gasUsed = gasUsedRaw, nonce = nonce,
                contractAddress = contractAddress,
                tokenTransferDetails = td
            )
        }
        is HistoryItemDto.TronHistoryDto -> {
            val td = tokenDetails(fromAddress, toAddress, valueRaw, tokenSymbol, tokenDecimals, tokenAmountRaw, contractAddress)
            TronTransaction(
                hash = hash, timestamp = timestamp, fee = feeRaw, status = st, networkName = net,
                fromAddress = fromAddress.orEmpty(), toAddress = toAddress.orEmpty(),
                amount = td?.amount ?: valueRaw, isOutgoing = isOutgoing,
                bandwidthUsed = bandwidthUsed, energyUsed = energyUsed,
                contractAddress = contractAddress,
                tokenTransferDetails = td
            )
        }
        is HistoryItemDto.BitcoinHistoryDto -> BitcoinTransaction(
            hash = hash, timestamp = timestamp, fee = feeRaw, status = st, networkName = net,
            fromAddress = fromAddress, toAddress = toAddress,
            amount = valueRaw, isOutgoing = isOutgoing,
            feeRateSatsPerByte = feeRateSatPerVByte
        )
    }
}

private fun String.toTransactionStatus(): TransactionStatus = when (uppercase()) {
    "CONFIRMED" -> TransactionStatus.CONFIRMED
    "FAILED" -> TransactionStatus.FAILED
    else -> TransactionStatus.PENDING
}

private fun tokenDetails(
    from: String?, to: String?, value: BigInteger,
    symbol: String?, decimals: Int?, amount: BigInteger?, contract: String?
): TokenTransferDetails? {
    if (symbol.isNullOrBlank() || contract.isNullOrBlank()) return null
    return TokenTransferDetails(
        from = from.orEmpty(), to = to.orEmpty(),
        amount = amount ?: value, tokenSymbol = symbol,
        tokenDecimals = decimals ?: 18, contractAddress = contract
    )
}
