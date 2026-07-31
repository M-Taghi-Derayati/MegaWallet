package com.mtd.domain.model

 data class GenericUtxoSpec(
    val networkId: String,
    val uriScheme: String,
    val addressHeader: Int,
    val p2shHeader: Int,
    val dumpedPrivateKeyHeader: Int,
    val segwitHrp: String,
    val bip32P2pkhPub: Int,
    val bip32P2pkhPriv: Int,
    val bip32P2wpkhPub: Int,
    val bip32P2wpkhPriv: Int,
    val port: Int,
    val packetMagic: Int,
    val genesisEpochSeconds: Long,
    val genesisDifficultyBits: Long,
    val genesisNonce: Long,
    val maxTargetBits: Long,
    val hasMaxMoney: Boolean,
    val maxMoneySatoshis: Long
)