package com.mtd.domain.model

/**
 * پوستهٔ برنامه — روشن، تاریک، یا هرچه سیستم می‌گوید.
 *
 * [SYSTEM] پیش‌فرض است و یک حالتِ سوم است نه «هیچ‌کدام»: کاربری که پوستهٔ گوشی‌اش شب‌ها خودکار
 * تاریک می‌شود انتظار دارد برنامه هم همین کار را بکند، و ذخیره‌کردنِ «روشن» برایش آن رفتار را
 * برای همیشه خاموش می‌کرد. تبدیلِ [SYSTEM] به روشن/تاریک کارِ لایهٔ UI است، چون فقط آن‌جا
 * می‌شود پیکربندیِ فعلیِ دستگاه را خواند.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        val DEFAULT = SYSTEM

        /** خواندنِ بردبارِ مقدارِ ذخیره‌شده؛ نامِ ناشناخته یا نبودِ مقدار به [DEFAULT] برمی‌گردد. */
        fun fromNameOrDefault(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
