package com.mtd.core.network

import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.core.WalletKey


interface BlockchainNetwork {
    /** TASK-53 — هویتِ کانونیِ شبکه. همهٔ تطبیق‌ها و کلیدزنی‌ها باید با این باشد. */
    val id: String
    val networkType: NetworkType

    /**
     * TASK-53 — alias قدیمی؛ برای شبکه‌ای که فقط در باندلِ سرور هست `null` می‌شود.
     * برای مقایسهٔ هویت از [id] استفاده کنید.
     */
    val name: NetworkName?
    val chainId: Long?
    val decimals: Int
    val iconUrl:String
    val webSocketUrl: String?
    val RpcUrlsEvm: List<String>
    val RpcUrls: List<String>
    val derivationPath: String
    val currencySymbol: String
    val explorers: List<String>
    /** TASK-51 — web-explorer tx page template (`{hash}`). Defaults to null so a network that hasn't
     *  declared one falls back to deriving the URL from [explorers]. */
    val explorerTxUrl: String? get() = null
    val color: String?
    val regex: String?
    val faName: String?
    val isTestnet: Boolean

    /**
     * TASK-53 — گویشِ API اکسپلورر (`etherscan` / `bscscan`). فقط برای EVM معنا دارد؛
     * پیش‌فرضِ `null` یعنی [com.mtd.domain.model.core.NetworkConfig.DEFAULT_EXPLORER_API].
     */
    val explorerApi: String? get() = null

    /**
     * TASK-53 — زنجیرهٔ OP-Stack که علاوه بر گسِ L2 هزینهٔ دادهٔ L1 هم دارد.
     * فقط برای EVM معنا دارد.
     */
    val hasL1DataFee: Boolean get() = false
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
