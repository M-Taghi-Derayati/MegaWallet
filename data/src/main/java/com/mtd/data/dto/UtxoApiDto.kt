package com.mtd.data.dto

import com.google.gson.annotations.SerializedName


data class AddressDataDto(
    @SerializedName("balance") val balance: Long? = null,
    @SerializedName("final_balance") val finalBalance: Long? = null,
    @SerializedName("txrefs") val txRefs: List<TxRefDto>? = null,
    @SerializedName("unconfirmed_txrefs") val unconfirmedTxRefs: List<TxRefDto>? = null
)

data class TxRefDto(
    @SerializedName("tx_hash") val txHash: String,
    @SerializedName("tx_output_n") val txOutputN: Int? = null,
    @SerializedName("tx_input_n") val txInputN: Int? = null,
    @SerializedName("value") val value: Long? = null,
    @SerializedName("confirmations") val confirmations: Int? = null,
    @SerializedName("confirmed") val confirmed: String? = null,
    @SerializedName("script") val script: String? = null
)

data class PushTxRequest(
    @SerializedName("tx") val tx: String
)

data class PushTxResponse(
    @SerializedName("tx") val tx: PushedTxDto? = null,
    @SerializedName("error") val error: String? = null
)

data class PushedTxDto(
    @SerializedName("hash") val hash: String? = null
)

data class ChainInfoDto(
    @SerializedName("high_fee_per_kb") val highFeePerKb: Long? = null,
    @SerializedName("medium_fee_per_kb") val mediumFeePerKb: Long? = null,
    @SerializedName("low_fee_per_kb") val lowFeePerKb: Long? = null
)

data class TransactionDto(
    @SerializedName("hash") val hash: String? = null,
    @SerializedName("fees") val fees: Long? = null,
    @SerializedName("received") val received: String? = null,
    @SerializedName("confirmed") val confirmed: String? = null,
    @SerializedName("confirmations") val confirmations: Int? = null
)
