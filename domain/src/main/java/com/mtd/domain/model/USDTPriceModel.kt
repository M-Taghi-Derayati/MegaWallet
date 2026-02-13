package com.mtd.domain.model


import com.google.gson.annotations.SerializedName

data class USDTPriceModel(
    @SerializedName("result")
    val result: Result
) {
    data class Result(
        @SerializedName("USDTTMN")
        val uSDTTMN: Double
    )
}