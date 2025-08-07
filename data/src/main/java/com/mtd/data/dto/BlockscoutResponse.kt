package com.mtd.data.dto


import com.google.gson.annotations.SerializedName

// این کلاس، ساختار کلی پاسخ API را مدل می‌کند
data class BlockscoutResponse<T>(
    @SerializedName("items") val items: List<T>,
    @SerializedName("next_page_params") val nextPageParams: PageParams?
)

data class PageParams(
    @SerializedName("block_number") val blockNumber: Int,
    @SerializedName("index") val index: Int,
    @SerializedName("items_count") val itemsCount: Int
)