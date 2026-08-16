package com.mtd.data.repository.contacts

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mtd.domain.interfaceRepository.IAddressBookRepository
import com.mtd.domain.model.contacts.SavedAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * دفترِ آدرس‌ها، ماندگار در [SharedPreferences] — همان الگوی
 * [com.mtd.data.repository.assets.UserTokenRepositoryImpl]: JSON با Gson، کشِ حافظه، نوشتن روی IO.
 *
 * یک کلیدِ واحد و نه یکی به‌ازای هر مدخل: فهرست کوچک است (ده‌ها مدخل، نه هزاران) و خواندنِ آن یک
 * `getString` است، در حالی که کلیدِ per-entry برای هر بار نمایشِ فهرست یک پیمایشِ کلیدها می‌خواست.
 */
@Singleton
class AddressBookRepositoryImpl @Inject constructor(
    private val prefs: SharedPreferences,
    private val gson: Gson
) : IAddressBookRepository {

    private companion object {
        const val KEY = "address_book_entries"
    }

    private val _entries = MutableStateFlow<List<SavedAddress>>(emptyList())
    override val entries: StateFlow<List<SavedAddress>> = _entries.asStateFlow()

    override suspend fun prime() = withContext(Dispatchers.IO) {
        _entries.value = read()
    }

    override suspend fun upsert(entry: SavedAddress) = withContext(Dispatchers.IO) {
        val next = read().filterNot { it.id == entry.id } + entry
        write(next)
    }

    override suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        write(read().filterNot { it.id == id })
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        // از راهِ `write` می‌رود تا `entries` هم همان لحظه خالی شود؛ پاک‌کردنِ مستقیمِ کلید،
        // کشِ حافظه را دست‌نخورده می‌گذاشت و صفحه‌های باز هنوز فهرست را نشان می‌دادند.
        write(emptyList())
    }

    /**
     * مرتب‌سازی سرِ **نوشتن** انجام می‌شود و نه سرِ خواندن، تا هر بار بازشدنِ فهرست یک sort نخواهد.
     * تازه‌ترین مدخل بالا می‌آید؛ کسی که همین الان آدرسی ثبت کرده، احتمالاً همان را می‌خواهد.
     */
    private fun write(items: List<SavedAddress>) {
        val sorted = items.sortedByDescending { it.createdAtMillis }
        _entries.value = sorted
        runCatching { prefs.edit { putString(KEY, gson.toJson(sorted)) } }
            .onFailure { Timber.w(it, "ذخیرهٔ دفترِ آدرس‌ها ناموفق بود") }
    }

    /**
     * JSONِ خراب فهرست را خالی برمی‌گرداند و کرش نمی‌کند: بدترین حالت این است که کاربر مخاطب‌هایش
     * را نبیند، نه اینکه اپ بالا نیاید.
     */
    private fun read(): List<SavedAddress> = runCatching {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<SavedAddress>>() {}.type
        gson.fromJson<List<SavedAddress>>(json, type) ?: emptyList()
    }.getOrElse {
        Timber.w(it, "خواندنِ دفترِ آدرس‌ها ناموفق بود")
        emptyList()
    }
}
