package com.mtd.core.network

import com.mtd.core.model.NetworkType
import com.mtd.core.model.WalletKey

interface BlockchainNetwork {
    val networkType: NetworkType
    val name: String

    /**
     * تولید کلید از روی mnemonic (عبارت بازیابی)
     */
    fun deriveKeyFromMnemonic(mnemonic: String): WalletKey

    /**
     * تولید کلید از روی private key
     */
    fun deriveKeyFromPrivateKey(privateKey: String): WalletKey
}