package com.mtd.data.dto

import com.google.gson.annotations.SerializedName

/** بدنهٔ `POST /api/mobile/v1/networks/:networkId/tokens/held`. */
data class HeldTokensRequestDto(
    @SerializedName("address") val address: String
)

/**
 * `data` مشترکِ هر دو اندپوینتِ کشفِ توکن (held و search) — §5 قرارداد.
 */
data class TokenListDto(
    @SerializedName("count") val count: Int? = null,
    @SerializedName("tokens") val tokens: List<DiscoveredTokenDto>? = null
)

data class DiscoveredTokenDto(
    /** `null` یعنی این توکن در کاتالوگِ ما نیست و فقط با `contractAddress` قابلِ استفاده است. */
    @SerializedName("id") val id: String? = null,
    @SerializedName("symbol") val symbol: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("decimals") val decimals: Int? = null,
    @SerializedName("contractAddress") val contractAddress: String? = null,
    /** ممکن است خالی باشد — placeholder نشان بده. مسیرها content-addressed و immutable هستند. */
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("featured") val featured: Boolean? = null
)
