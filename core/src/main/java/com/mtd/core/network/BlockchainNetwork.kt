package com.mtd.core.network

import com.mtd.core.model.NetworkName
import com.mtd.core.model.NetworkType
import com.mtd.core.model.WalletKey

interface BlockchainNetwork {
    val id: String
    val networkType: NetworkType
    val name: NetworkName
    val chainId: Long?
    val decimals: Int
    val iconUrl:String
    val webSocketUrl: String?
    val defaultRpcUrls: List<String>
    val currencySymbol: String
    val phoenixContractAddress: String?
    val blockExplorerUrl: String?
    val explorers: List<String>
    val color: String?
    val faName: String?
    /**
     * تولید کلید از روی mnemonic (عبارت بازیابی)
     */
    fun deriveKeyFromMnemonic(mnemonic: String): WalletKey

    /**
     * تولید کلید از روی private key
     */
    fun deriveKeyFromPrivateKey(privateKey: String): WalletKey


}