package com.mtd.domain.usecase.wallet

import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.core.WalletKey
import java.util.Locale
import javax.inject.Inject

/**
 * گسترشِ کلیدهای ذخیره‌شدهٔ کیف‌پول روی **کاتالوگِ فعلیِ شبکه‌ها**.
 *
 * `wallet.keys` لحظهٔ ساختِ کیف‌پول تولید و ذخیره می‌شود، پس عکسِ لحظه‌ایِ رجیستری در همان زمان
 * است. هر شبکه‌ای که بعداً از طریقِ باندلِ امضاشده اضافه شود در آن فهرست **نیست**، و بخش‌هایی که
 * مستقیم از `wallet.keys` می‌خوانند (سابقه) آن شبکه را نه نشان می‌دهند و نه اصلاً برایش درخواستی
 * می‌فرستند. صفحهٔ «دریافت» این مشکل را نداشت چون آدرسِ EVM را روی
 * `networkCatalog.getAllNetworkInfos()` باز می‌کرد — همان کاری که این‌جا یک‌بار و برای همه انجام
 * می‌شود تا دو جا دو رفتار نداشته باشند.
 *
 * قاعدهٔ تطبیق:
 * - اگر کلیدی دقیقاً با همان `networkId` هست، همان استفاده می‌شود.
 * - وگرنه اگر آدرسِ آن خانوادهٔ زنجیره **مستقل از شبکه** است (EVM و TVM: یک آدرس روی همهٔ
 *   زنجیره‌های آن خانواده معتبر است)، آدرسِ هم‌نوعِ موجود قرض گرفته می‌شود.
 * - وگرنه شبکه رد می‌شود. آدرسِ UTXO/XRP/SOLANA/TON مخصوصِ همان شبکه است و قرض‌دادنش آدرسِ
 *   غلط می‌سازد — مثلاً آدرسِ بیت‌کوین برای دوج‌کوین.
 *
 * خروجی [WalletKey] است تا مصرف‌کننده‌ها تغییر شکل ندهند، ولی ورودی‌های ساختگی
 * `derivationPath = null` و `publicKeyHex = ""` دارند: اینها **بستِ آدرس-به-شبکه برای خواندن**اند
 * و هرگز نباید به مسیرِ امضا بروند.
 */
class ExpandWalletKeysToNetworksUseCase @Inject constructor(
    private val networkCatalog: INetworkCatalog
) {
    operator fun invoke(keys: List<WalletKey>): List<WalletKey> {
        if (keys.isEmpty()) return emptyList()

        val byNetworkId = keys.associateBy { it.networkId.lowercase(Locale.US) }
        val firstOfType = keys.groupBy { it.networkType }.mapValues { (_, v) -> v.first() }

        return networkCatalog.getAllNetworkInfos()
            .mapNotNull { info ->
                byNetworkId[info.id.lowercase(Locale.US)]
                    ?: firstOfType[info.networkType]
                        ?.takeIf { info.networkType.hasNetworkAgnosticAddress() }
                        ?.let { donor ->
                            WalletKey(
                                networkId = info.id,
                                networkType = info.networkType,
                                // chainIdِ اهداکننده مالِ زنجیرهٔ دیگری است؛ قرض‌دادنش بدتر از نداشتن است.
                                chainId = null,
                                derivationPath = null,
                                address = donor.address,
                                publicKeyHex = "",
                                symbol = info.currencySymbol,
                                networkName = info.name
                            )
                        }
            }
            .distinctBy { it.networkId.lowercase(Locale.US) to it.address.lowercase(Locale.US) }
    }

    /** آیا یک آدرسِ این خانواده روی همهٔ شبکه‌های همان خانواده معتبر است؟ */
    private fun NetworkType.hasNetworkAgnosticAddress(): Boolean = when (this) {
        NetworkType.EVM, NetworkType.TVM -> true
        else -> false
    }
}
