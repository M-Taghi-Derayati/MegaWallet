package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.contacts.SavedAddress
import kotlinx.coroutines.flow.StateFlow

/**
 * دفترِ آدرس‌ها — آدرس‌های پرکاربردِ کاربر، ماندگار روی دستگاه.
 *
 * برخلافِ [IUserTokenRepository] به کیف پول کلید نمی‌خورد: آدرسِ کسی که کاربر با او کار می‌کند به
 * این بستگی ندارد که کدام کیف پولِ خودش فعال است، و کلیدزدن به کیف پول فقط باعث می‌شد کاربر بعد
 * از سوئیچ، مخاطب‌هایش را گم کند.
 *
 * هیچ اعتبارسنجیِ آدرسی این‌جا انجام نمی‌شود؛ آن کارِ لایهٔ بالاست که می‌داند آدرس برای کدام شبکه
 * است. این‌جا فقط ذخیره‌سازی است.
 */
interface IAddressBookRepository {

    /** فهرستِ فعلی. تا [prime] کامل نشده خالی است. */
    val entries: StateFlow<List<SavedAddress>>

    /** خواندنِ ماندگار از دیسک به کشِ حافظه. خارج از ترد اصلی صدا زده شود؛ idempotent است. */
    suspend fun prime()

    /** افزودن یا به‌روزرسانی بر مبنای [SavedAddress.id]. */
    suspend fun upsert(entry: SavedAddress)

    suspend fun remove(id: String)

    /**
     * پاک‌کردنِ کلِ دفتر — فقط برای وقتی که آخرین کیف‌پول حذف می‌شود.
     *
     * دفتر به کیف‌پول کلید نمی‌خورد، پس با حذفِ یک کیف از میانِ چند تا **نباید** پاک شود؛
     * مخاطب‌ها مالِ کاربرند نه مالِ آن کیف.
     */
    suspend fun clear()
}
