package com.mtd.megawallet.ui.compose.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ابعادِ مشترکِ فیلدهای بالای لیستِ دارایی.
 *
 * [RecipientInputSection] و [SearchInputField] دو ورودیِ متفاوت‌اند (آدرس مقصد در ارسال، جست‌وجو
 * در تبدیل) ولی باید هم‌قد و هم‌شکل بمانند؛ اعداد اینجا یک‌جا نگه داشته می‌شوند تا تغییرِ یکی،
 * دیگری را جا نگذارد.
 */
internal val FIELD_CORNER_RADIUS = 16.dp
internal val FIELD_PADDING_HORIZONTAL = 10.dp
internal val FIELD_PADDING_VERTICAL = 8.dp
internal val FIELD_LABEL_CORNER_RADIUS = 10.dp
internal val FIELD_LABEL_FONT_SIZE = 14.sp
internal val FIELD_TEXT_FONT_SIZE = 15.sp
internal val FIELD_ACTION_SIZE = 34.dp
internal val FIELD_ACTION_ICON_SIZE = 16.dp
