package com.mtd.domain.model.send

import com.mtd.domain.model.FeeOption

data class SendFeeQuote(
    val networkSymbol: String,
    val options: List<FeeOption>
)
