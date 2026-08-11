package com.mtd.domain.model

import java.math.BigInteger

sealed class TransactionParams {
    data class Evm(
        /** TASK-53 — شناسهٔ کانونیِ شبکه؛ ارسال روی زنجیرهٔ فقط-در-باندل هم باید کار کند. */
        val networkId: String,
        val to: String,
        val amount: BigInteger,
        val data: String? = null,
        val gasPrice: BigInteger,
        val gasLimit: BigInteger,
        val assetId: String? = null,
        val feeLevel: String? = null
    ) : TransactionParams()

    data class Utxo(
        val chainId: Long,
        val toAddress: String,
        val amountInSatoshi: Long,
        val feeRateInSatsPerByte: Long,
        val assetId: String? = null
    ) : TransactionParams()

    data class Tvm(
        /** TASK-53 — شناسهٔ کانونیِ شبکه. */
        val networkId: String,
        val toAddress: String,
        val amount: BigInteger,
        val contractAddress: String? = null, // If null, it's native TRX. If set, it's TRC20.
        val feeLimit: Long = 10000000, // Default 10 TRX
        val contractFunction: String? = null,
        val contractParameter: String? = null,
        val assetId: String? = null,

        val feeLevel: String? = null
    ) : TransactionParams()

    /**
     * یک تراکنشِ ترونِ **از پیش ساخته‌شده** که فقط باید امضا و ارسال شود.
     *
     * از [Tvm] جداست و عمداً هم: آن یکی تراکنش را از روی امضای متنیِ تابع می‌سازد
     * (`transfer(address,uint256)`)، ولی این‌جا تراکنش از قبل وجود دارد و بازسازی‌اش یعنی امضای
     * چیزی غیر از آن‌چه سرور شبیه‌سازی کرده. نوعِ جدا باعث می‌شود هر `when`ی که روی
     * [TransactionParams] شاخه می‌زند مجبور شود این حالت را صریحاً ببیند، نه این‌که در شاخهٔ
     * TRONِ موجود گم شود.
     *
     * [rawDataJson] و [rawDataHex] هر دو لازم‌اند و باید **دست‌نخورده** به نود برسند؛ نود در
     * برابرِ همان بایت‌ها اعتبارسنجی می‌کند و هیچ‌کدام به‌تنهایی کافی نیست.
     */
    data class TvmPrepared(
        val networkId: String,
        val txId: String,
        val rawDataJson: String,
        val rawDataHex: String,
        val visible: Boolean = false,
        val assetId: String? = null
    ) : TransactionParams()
}