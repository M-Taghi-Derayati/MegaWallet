package com.mtd.megawallet.viewmodel.swap

import com.mtd.core.utils.BalanceFormatter
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.FiatCurrency
import java.math.BigDecimal
import java.util.Locale

/**
 * ساختِ فهرستِ انتخابِ ارزِ **دریافت**.
 *
 * فهرست از سه منبع پر می‌شود و ترتیبشان معنا دارد: (۱) چیزی که کاربر واقعاً دارد، (۲)
 * کیوریت‌شده‌های باندلِ امضاشده، (۳) نتیجهٔ جست‌وجو در جهانِ قابل‌تبدیلِ سرور. کاربری که بلد نیست
 * توکن اضافه کند باید دارایی‌های خودش را همان بالا ببیند، و کاربری که دنبالِ توکنِ خاصی است باید
 * با تایپ‌کردن به بقیهٔ ~۱۱۳۳۳ توکن برسد.
 *
 * دو نکته که این‌جا رعایت می‌شود و جای دیگری نمی‌شود کرد:
 *  - **ترتیبِ نتیجهٔ جست‌وجو دست‌کاری نمی‌شود.** سرور تطبیقِ دقیقِ نماد را اول می‌آورد، و همین
 *    است که USDTِ واقعی را بالاتر از توکن‌هایی نگه می‌دارد که فقط نامشان شبیه است.
 *  - **حذفِ تکراری با آدرسِ قرارداد** (حروف‌کوچک‌شده) انجام می‌شود، نه با نماد؛ سه منبع می‌توانند
 *    یک توکن را با نام‌های متفاوت بدهند.
 */

/**
 * یک نامزدِ دریافت، مستقل از این‌که از کدام منبع آمده.
 *
 * [isSponsorable] از `SwapToken.id != null` می‌آید و تنها چیزی است که تصمیمِ «کارمزد را اسپانسر
 * کن» را مجاز می‌کند؛ توکنِ کیوریت‌نشده قابلِ تبدیل هست ولی هرگز اسپانسر نمی‌شود.
 */
internal data class SwapReceiveCandidate(
    /**
     * شناسه‌ای که به `/quote` می‌رود وقتی آدرسِ قرارداد نداریم (ارزِ بومی، یا داراییِ کیوریت‌شده
     * که با شناسه‌اش شناخته می‌شود). با [assetId] — که فقط کلیدِ UI است — یکی نیست.
     */
    val optionId: String,
    val networkId: String,
    val networkName: String,
    val networkFaName: String?,
    val networkIconUrl: String?,
    val symbol: String,
    val name: String,
    val faName: String?,
    val iconUrl: String?,
    val decimals: Int,
    val contractAddress: String?,
    val isSponsorable: Boolean,
    /**
     * نشانِ **منشأ** از `SwapToken.verified` — نه ایمنی، نه ممیزی. `null` یعنی منبع چیزی نگفته
     * (همهٔ دارایی‌های باندل و موجودی‌های خودِ کاربر)، پس نه نشان می‌گیرد نه هشدار.
     */
    val verified: Boolean? = null,
    /** موجودیِ کاربر روی همین شبکه، اگر داشته باشد. `null` یعنی صفر — و صفر مانعِ انتخاب نیست. */
    val held: AssetItem? = null
)

/** کلیدِ یکتاییِ یک توکن روی یک شبکه. نمادِ یکسان روی دو شبکه دو توکنِ متفاوت است. */
internal fun SwapReceiveCandidate.dedupeKey(): String =
    networkId.lowercase(Locale.US) + ":" + (contractAddress?.lowercase(Locale.US) ?: NATIVE_KEY)

/** کلیدِ ردیف در فهرست. باید با چیزی که [buildReceiveGroups] می‌سازد یکی باشد. */
internal val SwapReceiveCandidate.assetId: String
    get() = assetIdFor(networkId, contractAddress, symbol)

/**
 * تبدیلِ نامزدها به همان `AssetItem`ی که `TokenList` و `ChooseBalanceBottomSheet` صفحهٔ ارسال
 * مصرف می‌کنند — تا دو صفحه یک شکل بمانند و کامپوننتِ سومی ساخته نشود.
 *
 * هر **توکن** یک ردیف است (نه هر جفتِ توکن-شبکه): نامزدهای هم‌نماد زیرِ یک سرگروه جمع می‌شوند و
 * انتخابِ شبکه در شیتِ بعدی انجام می‌شود. ترتیبِ ورودی حفظ می‌شود.
 */
internal fun buildReceiveGroups(
    candidates: List<SwapReceiveCandidate>,
    fiatCurrency: FiatCurrency,
    usdToIrrRate: CurrencyRate?
): List<AssetItem> {
    val seen = mutableSetOf<String>()
    val grouped = LinkedHashMap<String, MutableList<SwapReceiveCandidate>>()

    candidates.forEach { candidate ->
        if (!seen.add(candidate.dedupeKey())) return@forEach
        grouped.getOrPut(candidate.symbol.uppercase(Locale.US)) { mutableListOf() }.add(candidate)
    }

    return grouped.map { (_, members) ->
        val items = members.map { it.toAssetItem(fiatCurrency, usdToIrrRate) }
        val head = members.first()

        if (items.size == 1) {
            items.first()
        } else {
            val totalRaw = members.fold(BigDecimal.ZERO) { acc, c -> acc + (c.held?.balanceRaw ?: BigDecimal.ZERO) }
            val totalUsd = members.fold(BigDecimal.ZERO) { acc, c ->
                acc + ((c.held?.balanceRaw ?: BigDecimal.ZERO) * (c.held?.priceUsdRaw ?: BigDecimal.ZERO))
            }
            AssetItem(
                id = groupIdFor(head.symbol),
                networkId = GROUP_NETWORK_ID,
                name = head.name,
                faName = head.faName,
                symbol = head.symbol,
                networkName = "",
                networkFaName = null,
                iconUrl = head.iconUrl,
                networkIconUrl = null,
                balance = BalanceFormatter.formatBalance(totalRaw, head.decimals),
                balanceUsdt = fiatText(totalUsd, FiatCurrency.USD, null),
                balanceIrr = fiatText(totalUsd, FiatCurrency.TOMAN, usdToIrrRate),
                formattedDisplayBalance = fiatText(totalUsd, fiatCurrency, usdToIrrRate),
                balanceRaw = totalRaw,
                priceUsdRaw = head.held?.priceUsdRaw ?: BigDecimal.ZERO,
                decimals = head.decimals,
                contractAddress = head.contractAddress,
                isNativeToken = head.contractAddress == null,
                isGroupHeader = true,
                groupName = head.faName ?: head.name,
                groupAssets = items,
                // سرگروه محتاط‌ترین حالتِ اعضایش را نشان می‌دهد: اگر حتی یکی از زنجیره‌ها ثبت‌نشده
                // است، نشانِ منشأ روی کلِ ردیف ادعای درستی نیست.
                verified = when {
                    members.any { it.verified == false } -> false
                    members.all { it.verified == true } -> true
                    else -> null
                }
            )
        }
    }
}

private fun SwapReceiveCandidate.toAssetItem(
    fiatCurrency: FiatCurrency,
    usdToIrrRate: CurrencyRate?
): AssetItem {
    val balanceRaw = held?.balanceRaw ?: BigDecimal.ZERO
    val priceUsd = held?.priceUsdRaw ?: BigDecimal.ZERO
    val valueUsd = balanceRaw * priceUsd

    return AssetItem(
        id = assetIdFor(networkId, contractAddress, symbol),
        networkId = networkId,
        name = name,
        faName = faName,
        symbol = symbol,
        networkName = networkName,
        networkFaName = networkFaName,
        iconUrl = iconUrl,
        networkIconUrl = networkIconUrl,
        balance = BalanceFormatter.formatBalance(balanceRaw, decimals),
        balanceUsdt = fiatText(valueUsd, FiatCurrency.USD, null),
        balanceIrr = fiatText(valueUsd, FiatCurrency.TOMAN, usdToIrrRate),
        formattedDisplayBalance = fiatText(valueUsd, fiatCurrency, usdToIrrRate),
        balanceRaw = balanceRaw,
        priceUsdRaw = priceUsd,
        decimals = decimals,
        contractAddress = contractAddress,
        isNativeToken = contractAddress == null,
        isGroupHeader = false,
        verified = verified
    )
}

private fun fiatText(
    valueUsd: BigDecimal,
    currency: FiatCurrency,
    rate: CurrencyRate?
): String = "${BalanceFormatter.formatFiatValue(valueUsd, currency, rate, withSymbol = false)} "

/**
 * شناسهٔ پایدارِ یک توکن روی یک شبکه. آدرسِ قرارداد پایه است، نه نماد: دو توکنِ متفاوت می‌توانند
 * نمادِ یکسان داشته باشند و کلیدِ `LazyColumn` نباید بینشان قاطی کند.
 */
internal fun assetIdFor(networkId: String, contractAddress: String?, symbol: String): String =
    "swap_${networkId.lowercase(Locale.US)}_" +
        (contractAddress?.lowercase(Locale.US) ?: symbol.uppercase(Locale.US))

internal fun groupIdFor(symbol: String): String = "swapgroup_${symbol.uppercase(Locale.US)}"

private const val NATIVE_KEY = "native"

/** سرگروه شبکهٔ واقعی ندارد؛ همان قراردادی که `buildSendableAssetList` استفاده می‌کند. */
private const val GROUP_NETWORK_ID = "GROUP"
