package com.mtd.domain.model

data class AggregatedOrderBookDto(val bids: List<AggregatedOrderDto>, val asks: List<AggregatedOrderDto>)
data class AggregatedOrderDto(val price: Double, val quantity: Double, val exchangeId: String)