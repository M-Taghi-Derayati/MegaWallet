package com.mtd.megawallet.viewmodel.wallet

import com.mtd.core.manager.ErrorManager
import com.mtd.core.manager.ErrorSeverity
import com.mtd.domain.interfaceRepository.IAddressBookRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.contacts.SavedAddress
import com.mtd.domain.model.error.ErrorSurface
import com.mtd.domain.usecase.wallet.ExpandWalletKeysToNetworksUseCase
import com.mtd.domain.usecase.wallet.GetAllWalletsUseCase
import com.mtd.domain.usecase.wallet.GetWalletKeysForWalletsUseCase
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * دو منبعِ گیرندهٔ ممکن، در یک جا: کیف‌پولِ خودِ کاربر، و دفترِ آدرس‌ها.
 *
 * عمداً از [com.mtd.megawallet.viewmodel.MultiWalletViewModel] استفاده نمی‌شود: آن موقعِ
 * ساخته‌شدن `refreshAllWalletsBalances` را اجرا می‌کند، یعنی برای هر کیف‌پول یک رفت‌وبرگشتِ شبکه —
 * و این‌جا هیچ عددی لازم نیست. این ViewModel فقط دو خواندنِ محلی است و هیچ درخواستی به بک‌اند
 * نمی‌زند.
 *
 * نام و رنگ از [GetAllWalletsUseCase] می‌آید و آدرس‌ها از [GetWalletKeysForWalletsUseCase]؛ این دو
 * عمداً جدا هستند چون اولی فقط متادیتا می‌خواند و `keys` را همیشه خالی می‌دهد — آدرس‌ها از رمزِ
 * رمزگشایی‌شدهٔ هر کیف‌پول مشتق می‌شوند و همین کار است که باید صریح باشد.
 */
@HiltViewModel
class WalletAddressPickerViewModel @Inject constructor(
    private val getAllWalletsUseCase: GetAllWalletsUseCase,
    private val getWalletKeysForWalletsUseCase: GetWalletKeysForWalletsUseCase,
    private val expandWalletKeysToNetworksUseCase: ExpandWalletKeysToNetworksUseCase,
    private val addressBookRepository: IAddressBookRepository,
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    /**
     * یک کیف‌پول با آدرس‌هایش، کلیدخوردهٔ `WalletKey.networkId`.
     *
     * آدرس‌ها **دست‌نخورده** نگه داشته می‌شوند و فقط کلیدِ نگاشت کوچک می‌شود؛ base58 ترون به
     * حروف حساس است و یک آدرسِ کوچک‌شده روی آن زنجیره اصلاً آدرس نیست.
     */
    data class WalletAddresses(
        val walletId: String,
        val name: String,
        val color: Int,
        val addressesByNetworkId: Map<String, String>
    ) {
        fun addressOn(networkId: String): String? =
            addressesByNetworkId[networkId.lowercase(Locale.US)]
    }

    private val _wallets = MutableStateFlow<List<WalletAddresses>>(emptyList())
    val wallets = _wallets.asStateFlow()

    val savedAddresses = addressBookRepository.entries

    init {
        launchSafe(checkNetwork = false) { loadWallets() }
        // اپ در استارت هم prime می‌کند؛ این برای وقتی است که آن هنوز تمام نشده یا صفحه پیش از
        // آن ساخته شده. idempotent است، پس دوباره صدا زدنش بی‌ضرر است.
        launchSafe(checkNetwork = false) { addressBookRepository.prime() }
    }

    private suspend fun loadWallets() {
        val wallets = when (val result = getAllWalletsUseCase()) {
            is ResultResponse.Success -> result.data
            is ResultResponse.Error -> return reportLoadFailure(result.exception)
        }
        if (wallets.isEmpty()) return

        val keysByWalletId =
            when (val result = getWalletKeysForWalletsUseCase(wallets.map { it.id })) {
                is ResultResponse.Success -> result.data
                is ResultResponse.Error -> return reportLoadFailure(result.exception)
            }

        _wallets.value = wallets.mapNotNull { wallet ->
            // کلیدهای ذخیره‌شده عکسِ لحظهٔ ساختِ کیف‌پول‌اند و شبکه‌های بعدی در آن‌ها نیستند.
            // بدون این گسترش، پلی به شبکه‌ای که بعداً اضافه شده «کیف‌پولی روی این شبکه ندارید»
            // می‌داد، در حالی که آدرسِ EVM روی همهٔ زنجیره‌های EVM یکی است.
            val keys = expandWalletKeysToNetworksUseCase(keysByWalletId[wallet.id].orEmpty())
            if (keys.isEmpty()) return@mapNotNull null

            WalletAddresses(
                walletId = wallet.id,
                name = wallet.name,
                color = wallet.color,
                addressesByNetworkId = keys.associate { key ->
                    key.networkId.lowercase(Locale.US) to key.address
                }
            )
        }
    }

    /**
     * فهرست خالی می‌ماند و شیت خودش می‌گوید گیرنده‌ای روی این شبکه نیست؛ ورودیِ دستیِ آدرس همچنان
     * کار می‌کند، پس کاربر بن‌بست نمی‌خورد و اسنک‌بار لازم نیست.
     */
    private suspend fun reportLoadFailure(throwable: Throwable) {
        reportError(
            throwable = throwable,
            userAction = "loadWalletAddressesForPicker",
            surface = ErrorSurface.SILENT,
            severity = ErrorSeverity.LOW
        )
    }

    /**
     * ثبتِ یک آدرس در دفترچه.
     *
     * ⚠️ اعتبارسنجی این‌جا انجام **نمی‌شود** و نباید بشود: صحتِ آدرس به شبکهٔ مقصد بستگی دارد و
     * تنها جایی که آن را می‌داند همان فلویی است که آدرس را گرفته. صفحه فقط وقتی این را صدا می‌زند
     * که اعتبارسنجیِ خودش سبز شده باشد.
     */
    fun saveAddress(name: String, address: String, networkId: String?) {
        val trimmedName = name.trim()
        val trimmedAddress = address.trim()
        if (trimmedName.isEmpty() || trimmedAddress.isEmpty()) return

        launchSafe(checkNetwork = false) {
            addressBookRepository.upsert(
                SavedAddress(
                    id = UUID.randomUUID().toString(),
                    name = trimmedName,
                    address = trimmedAddress,
                    networkId = networkId,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun removeSavedAddress(id: String) {
        launchSafe(checkNetwork = false) { addressBookRepository.remove(id) }
    }
}
