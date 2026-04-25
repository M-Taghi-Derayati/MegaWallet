package com.mtd.core.network

import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.core.WalletKey


interface BlockchainNetwork {
    val id: String
    val networkType: NetworkType
    val name: NetworkName
    val chainId: Long?
    val decimals: Int
    val iconUrl:String
    val webSocketUrl: String?
    val RpcUrlsEvm: List<String>
    val RpcUrls: List<String>
    val derivationPath: String
    val currencySymbol: String
    val explorers: List<String>
    val color: String?
    val regex: String?
    val faName: String?
    val isTestnet: Boolean
    /**
     * تولید کلید از روی mnemonic (عبارت بازیابی)
     */
    fun deriveKeyFromMnemonic(mnemonic: String): WalletKey

    /**
     * تولید کلید از روی private key
     */
    fun deriveKeyFromPrivateKey(privateKey: String): WalletKey


    /**
     * فقط کلید خصوصی را برای امضا استخراج می‌کند (بدون ساخت WalletKey کامل)
     */
    fun getPrivateKeyFromMnemonic(mnemonic: String): String

    fun getPrivateKeyFromPrivateKey(privateKey: String): String
}
