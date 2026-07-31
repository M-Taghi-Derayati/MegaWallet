package com.mtd.domain.model

 enum class UtxoBackend {
        MEMPOOL,
        BLOCKCYPHER
    }

     data class SpendableUtxo(
        val txid: String,
        val vout: Int,
        val value: Long
    )

     data class BlockCypherRoute(
        val coin: String,
        val chain: String
    )