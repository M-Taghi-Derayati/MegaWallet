package com.mtd.data.dto

import com.google.gson.annotations.SerializedName

data class USDTPriceApiDto(
    @SerializedName("result")
    val result: Result
) {
    data class Result(
        @SerializedName("USDTTMN")
        val uSDTTMN: Double
    )
}