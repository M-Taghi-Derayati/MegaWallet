package com.mtd.domain.interfaceRepository

/**
 * TASK-53 — آیا شبکه‌های تست در فهرست‌های UI نشان داده شوند؟
 *
 * این یک ترجیحِ **نمایشی** است، نه فیلترِ ثبت. همهٔ شبکه‌ها همیشه در رجیستری ثبت می‌شوند تا
 * جست‌وجوی هویتی (`getNetworkById` / `getNetworkByChainId`) همیشه جواب بدهد؛ این پرچم فقط روی
 * APIهای فهرست‌کننده (مثل [INetworkCatalog.getAllNetworkInfos]) اثر می‌گذارد.
 *
 * دقیقاً از الگوی [IBlockchainConnectionModeProvider] پیروی می‌کند: خواندنِ همگام و بدون قفل از
 * یک کشِ درون‌حافظه‌ای، که از یک ترجیحِ ذخیره‌شده hydrate می‌شود.
 */
fun interface ITestnetVisibilityProvider {
    /** غیرمسدودکننده. تا وقتی کش hydrate نشده، پیش‌فرضِ نوعِ بیلد را برمی‌گرداند. */
    fun showTestnets(): Boolean
}
