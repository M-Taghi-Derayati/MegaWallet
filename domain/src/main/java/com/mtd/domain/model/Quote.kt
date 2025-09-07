package com.mtd.domain.model

// این مدل نتیجه یک درخواست قیمت را نمایش می‌دهد
data class Quote(
    val quoteId: String,

    // اطلاعات دارایی مبدا (چیزی که کاربر می‌دهد)
    val fromAssetId: String, // <-- فیلد ضروری اضافه شد: e.g., "USDT-11155111"
    val fromAmount: String,
    val fromAssetSymbol: String,

    // اطلاعات دارایی مقصد (چیزی که کاربر می‌گیرد)
    val toAssetId: String,   // <-- فیلد ضروری اضافه شد: e.g., "MATIC-80001"
    val receiveAmount: String,
    val receiveAssetSymbol: String,

    // اطلاعات کارمزد
    val feeAmount: String,
    val feeAssetSymbol: String,

    // فیلد مهم برای سواپ‌های UTXO
    val depositAddress: String? = null
)