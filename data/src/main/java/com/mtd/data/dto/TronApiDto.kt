package com.mtd.data.dto

import com.google.gson.annotations.SerializedName
import java.math.BigInteger


data class TronscanNormalResponse(
    val data: List<TrxData>
)
data class TrxData(
    val hash: String,
    val timestamp: Long,
    val ownerAddress: String,
    val toAddress: String,
    val amount: BigInteger,
    val cost: TrxCost,
    val confirmed: Boolean,
    val result: String? // "SUCCESS"
)
data class TrxCost(val net_fee: BigInteger, val energy_fee: BigInteger)

// برای تراکنش‌های توکن (USDT/TRC20)
data class TronscanTokenResponse(
    val trc20_transfer: List<TokenTransferData>
)
data class TokenTransferData(
    val transaction_id: String,
    val block_ts: Long,
    val from: String,
    val to: String,
    val value: BigInteger,
    val token_id: String, // همان آدرس قرارداد
    val symbol: String
)




// پاسخ پارامترهای شبکه (برای جلوگیری از هاردکد)
data class ChainParameterResponse(val chainParameter: List<ChainParameter>)
data class ChainParameter(val key: String, val value: Long?)

// پاسخ متد Trigger Constant (برای تخمین انرژی)
data class EnergyResponse(
    val energy_used: Long?,
    val result: TriggerResult?,
    val constant_result: List<String>?
)
data class TriggerResult(val result: Boolean, val message: String?)

// پاسخ ایجاد تراکنش خام (برای محاسبه پهنای باند)
data class CreateTransactionResponse(val raw_data_hex: String?, val txID: String?)

// پاسخ موجودی اکانت بومی
data class TronAccountResponse(
    @SerializedName("address") val address: String? = null,
    @SerializedName("balance") val balance: Long? = 0L,

    // پهنای باند رایگان (معمولاً اگر ۶۰۰ باشد یا مصرف نشده باشد، در JSON نمی‌آید)
    @SerializedName(
        value = "freeNetLimit",
        alternate = ["free_net_limit", "FreeNetLimit"]
    )
    val freeNetLimit: Long? = null,
    @SerializedName(
        value = "freeNetUsage",
        alternate = ["free_net_usage", "freeNetUsed", "FreeNetUsed"]
    )
    val freeNetUsage: Long? = null,

    // پهنای باند حاصل از فریز کردن (Net)
    @SerializedName(
        value = "netLimit",
        alternate = ["net_limit", "NetLimit", "TotalNetLimit"]
    )
    val netLimit: Long? = null,
    @SerializedName(
        value = "netUsage",
        alternate = ["net_usage", "netUsed", "NetUsed"]
    )
    val netUsage: Long? = null,

    // بخش منابع انرژی و جزئیات بیشتر
    @SerializedName(
        value = "account_resource",
        alternate = ["accountResource"]
    )
    val accountResource: AccountResource? = null
){
    val availableBandwidth: Long
        get() {
            val free = (freeNetLimit ?: 600L) - (freeNetUsage ?: 0L)
            val frozen = (netLimit ?: 0L) - (netUsage ?: 0L)
            return if (free + frozen < 0) 0 else (free + frozen)
        }

    val availableEnergy: Long
        get() {
            val limit = accountResource?.energyLimit ?: 0L
            val usage = accountResource?.energyUsage ?: 0L
            return if (limit - usage < 0) 0 else (limit - usage)
        }
}

data class AccountResource(
    @SerializedName(
        value = "energyLimit",
        alternate = ["energy_limit", "EnergyLimit"]
    )
    val energyLimit: Long? = null,
    @SerializedName(
        value = "energyUsage",
        alternate = ["energy_usage", "energy_used", "EnergyUsed"]
    )
    val energyUsage: Long? = null,
    @SerializedName("frozen_balance_for_energy") val frozenEnergy: FrozenBalance? = null
)

data class TronAccountResourceResponse(
    @SerializedName(
        value = "freeNetLimit",
        alternate = ["free_net_limit", "FreeNetLimit"]
    )
    val freeNetLimit: Long? = null,
    @SerializedName(
        value = "freeNetUsed",
        alternate = ["free_net_usage", "freeNetUsage", "FreeNetUsed"]
    )
    val freeNetUsed: Long? = null,
    @SerializedName(
        value = "netLimit",
        alternate = ["net_limit", "TotalNetLimit", "NetLimit"]
    )
    val netLimit: Long? = null,
    @SerializedName(
        value = "netUsed",
        alternate = ["net_usage", "netUsage", "NetUsed"]
    )
    val netUsed: Long? = null,
    @SerializedName(
        value = "EnergyLimit",
        alternate = ["energy_limit", "energyLimit"]
    )
    val energyLimit: Long? = null,
    @SerializedName(
        value = "EnergyUsed",
        alternate = ["energy_used", "energyUsage"]
    )
    val energyUsed: Long? = null
) {
    val availableBandwidth: Long
        get() {
            val free = (freeNetLimit ?: 600L) - (freeNetUsed ?: 0L)
            val staked = (netLimit ?: 0L) - (netUsed ?: 0L)
            val sum = free + staked
            return if (sum < 0L) 0L else sum
        }

    val availableEnergy: Long
        get() {
            val available = (energyLimit ?: 0L) - (energyUsed ?: 0L)
            return if (available < 0L) 0L else available
        }
}

data class FrozenBalance(
    @SerializedName("frozen_balance") val amount: Long? = 0L
)

// مدل ارسال درخواست برای ایجاد تراکنش
data class CreateTxRequest(
    val owner_address: String,
    val to_address: String,
    val amount: Long,
    val visible: Boolean = false
)

data class TriggerConstantRequest(
    val owner_address: String,
    val contract_address: String,
    val function_selector: String,
    val parameter: String,
    val visible: Boolean = true
)

data class TriggerSmartContractRequest(
    val owner_address: String,
    val contract_address: String,
    val function_selector: String,
    val parameter: String,
    val call_value: Long = 0L,
    val fee_limit: Long = 10_000_000L,
    val visible: Boolean = true
)

data class AccountRequest(
    val address: String,
    val visible: Boolean = true
)
