package com.mtd.megawallet.viewmodel.settings

import com.mtd.core.manager.ErrorManager
import com.mtd.domain.interfaceRepository.IAddressBookRepository
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.model.contacts.SavedAddress
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject

/**
 * صفحهٔ مدیریتِ دفترِ آدرس‌ها.
 *
 * جدا از [com.mtd.megawallet.viewmodel.wallet.WalletAddressPickerViewModel] است چون کارِ دیگری
 * می‌کند: آن انتخابِ گیرنده در میانهٔ یک ارسال است و کیف‌پول‌های کاربر را هم بار می‌زند، این فقط
 * ویرایشِ خودِ دفترچه است و هیچ کیف‌پولی لازم ندارد. هر دو روی همان [IAddressBookRepository]
 * می‌نشینند، پس یک تغییر در یکی بلافاصله در دیگری دیده می‌شود.
 */
@HiltViewModel
class AddressBookViewModel @Inject constructor(
    private val addressBookRepository: IAddressBookRepository,
    private val networkCatalog: INetworkCatalog,
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    /**
     * یک شبکهٔ قابلِ انتخاب برای یک مدخل. [id] برابرِ `null` یعنی «هر شبکه» — همان معنایی که
     * [SavedAddress.networkId] با `null` دارد.
     */
    data class NetworkOption(
        val id: String?,
        val label: String,
        val iconUrl: String?
    )

    val entries: StateFlow<List<SavedAddress>> = addressBookRepository.entries

    /**
     * فهرست از کاتالوگ خوانده می‌شود، نه از یک `when` روی شبکه‌های شناخته‌شده؛ شبکهٔ تازه‌ای که به
     * networks.json اضافه شود بدونِ تغییرِ این فایل این‌جا هم پیدا می‌شود.
     */
    val networkOptions: List<NetworkOption> =
        listOf(NetworkOption(id = null, label = ANY_NETWORK_LABEL, iconUrl = null)) +
            networkCatalog.getAllNetworkInfos().map { info ->
                NetworkOption(
                    id = info.id,
                    label = info.faName ?: info.name?.name ?: info.id,
                    iconUrl = info.iconUrl
                )
            }

    init {
        // اپ در استارت هم prime می‌کند؛ این برای وقتی است که صفحه پیش از آن ساخته شده باشد.
        launchSafe(checkNetwork = false) { addressBookRepository.prime() }
    }

    /**
     * ثبت یا ویرایش. [existing] که داده شود، همان مدخل به‌روزرسانی می‌شود (چون `upsert` با `id`
     * کار می‌کند) و تاریخِ ساختش دست‌نخورده می‌ماند تا ترتیبِ فهرست با هر ویرایش به‌هم نریزد.
     *
     * ⚠️ روی [address] فقط `trim` انجام می‌شود و بس — نه کوچک‌سازی و نه هیچ نرمال‌سازیِ دیگری.
     * base58 ترون به حروف حساس است و آدرسِ کوچک‌شده روی آن زنجیره آدرسِ دیگری است، نه همان آدرس.
     */
    fun save(
        existing: SavedAddress?,
        name: String,
        address: String,
        networkId: String?
    ) {
        val trimmedName = name.trim()
        val trimmedAddress = address.trim()
        if (trimmedName.isEmpty() || trimmedAddress.isEmpty()) return

        launchSafe(checkNetwork = false) {
            addressBookRepository.upsert(
                SavedAddress(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = trimmedName,
                    address = trimmedAddress,
                    networkId = networkId,
                    createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis()
                )
            )
        }
    }

    fun remove(id: String) {
        launchSafe(checkNetwork = false) { addressBookRepository.remove(id) }
    }

    /** نامِ نمایشیِ شبکهٔ یک مدخل. `null` و شناسهٔ ناشناخته هر دو «هر شبکه» می‌شوند. */
    fun networkLabelFor(networkId: String?): String =
        networkOptionFor(networkId)?.label ?: ANY_NETWORK_LABEL

    fun networkIconUrlFor(networkId: String?): String? = networkOptionFor(networkId)?.iconUrl

    private fun networkOptionFor(networkId: String?): NetworkOption? =
        networkOptions.firstOrNull { it.id.equals(networkId, ignoreCase = true) }

    private companion object {
        const val ANY_NETWORK_LABEL = "هر شبکه"
    }
}
