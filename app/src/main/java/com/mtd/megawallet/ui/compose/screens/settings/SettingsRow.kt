package com.mtd.megawallet.ui.compose.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular

/**
 * دستورِ زبانِ ردیف‌های تنظیمات — یک ردیف، سه پایانهٔ ممکن، و هیچ حالتِ چهارمی.
 *
 * تفاوتشان چیزی است که کاربر از رویش یاد می‌گیرد ضربه‌زدن چه می‌کند، پس عمداً محدود است: هر
 * ردیفی که جای دیگری می‌برد [Navigate] است و هر ردیفی که همان‌جا چیزی را عوض می‌کند [Value] یا
 * [Menu]. حالتِ سوم وقتی اضافه شد که ردیفی پیدا شد که شیت باز می‌کند ولی مقداری برای نشان‌دادن
 * ندارد؛ نوشتنِ «روشن/خاموش» برایش، مقداری می‌ساخت که با آنچه در شیت است دو تا بود.
 */
sealed interface SettingsRowTrailing {

    /** شِوران — این ردیف صفحهٔ دیگری باز می‌کند. */
    data object Navigate : SettingsRowTrailing

    /** مقدارِ فعلی + سه‌نقطه — این ردیف انتخابگری را همان‌جا باز می‌کند. */
    data class Value(val text: String) : SettingsRowTrailing

    /** فقط سه‌نقطه — شیتی باز می‌شود، ولی این ردیف مقداری برای نشان‌دادن ندارد. */
    data object Menu : SettingsRowTrailing
}

internal val SETTINGS_ROW_HORIZONTAL_PADDING = 24.dp
internal val SETTINGS_ROW_ICON_SLOT = 28.dp
internal val SETTINGS_TOP_BAR_ACTION_SIZE = 44.dp
private val SETTINGS_ROW_ICON_GAP = 16.dp

/**
 * فرورفتگیِ خط‌جداکننده: خط از همان‌جایی شروع می‌شود که عنوان شروع می‌شود، نه از لبهٔ صفحه.
 * `start` است نه `left` — در RTL باید از سمتِ راست فرو برود.
 */
internal val SETTINGS_DIVIDER_INDENT =
    SETTINGS_ROW_HORIZONTAL_PADDING + SETTINGS_ROW_ICON_SLOT + SETTINGS_ROW_ICON_GAP

/**
 * سرگروه — گروهِ اول عمداً سرگروه ندارد؛ بقیه دارند.
 *
 * هم‌ترازِ ستونِ آیکون‌هاست، نه ستونِ عنوان‌ها، تا گروه از ردیف‌هایش عقب‌تر بایستد و مرزِ گروه‌ها
 * بدونِ هیچ خطی دیده شود.
 */
@Composable
fun SettingsSectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = IranSansRegular,
        fontSize = 12.sp,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(
            start = SETTINGS_ROW_HORIZONTAL_PADDING,
            end = SETTINGS_ROW_HORIZONTAL_PADDING,
            top = 28.dp,
            bottom = 10.dp
        )
    )
}

/**
 * یک ردیفِ تنظیمات: [آیکون] [عنوان (+ زیرعنوان)] ......... [پایانه].
 *
 * [leading] یک اسلات است نه یک `ImageVector`، چون همین ردیف هم آیکونِ متریال می‌گیرد (تنظیمات) و
 * هم آیکونِ شبکه (دفترِ آدرس‌ها). اندازهٔ جای آیکون ثابت است تا فرورفتگیِ خط‌جداکننده در هر دو حالت
 * یکی بماند.
 */
@Composable
fun SettingsRow(
    title: String,
    trailing: SettingsRowTrailing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** زیرعنوانی که محتوایش لاتین است (آدرسِ کوتاه‌شده) با فونتِ لاتین بهتر خوانده می‌شود. */
    subtitleIsLatin: Boolean = false,
    showDivider: Boolean = true,
    leading: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() }
                .padding(
                    horizontal = SETTINGS_ROW_HORIZONTAL_PADDING,
                    // ردیفِ دارای زیرعنوان بلندتر است؛ فضای عمودی را خودِ متنِ دوم می‌سازد.
                    vertical = if (subtitle == null) 16.dp else 14.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(SETTINGS_ROW_ICON_SLOT),
                contentAlignment = Alignment.Center
            ) {
                leading()
            }

            Spacer(Modifier.width(SETTINGS_ROW_ICON_GAP))

            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = IranSansBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = if (subtitleIsLatin) InterMedium else IranSansRegular,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            SettingsRowTrailingContent(trailing)
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(
                    start = SETTINGS_DIVIDER_INDENT,
                    // بدونِ حاشیهٔ انتها، خط تا لبهٔ صفحه می‌رفت در حالی که خودِ ردیف از آن لبه
                    // فاصله داشت. هر دو start/end‌اند نه left/right — این صفحه راست‌به‌چپ است.
                    end = SETTINGS_ROW_HORIZONTAL_PADDING
                ),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
        }
    }
}

/** آیکونِ متریالِ ابتدای ردیف — تک‌رنگ و کم‌رنگ، هم‌اندازه در همهٔ ردیف‌ها. */
@Composable
fun SettingsRowIcon(
    icon: ImageVector,
    contentDescription: String? = null
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp)
    )
}

@Composable
fun SettingsRowIcon(
    icon: Int,
    contentDescription: String? = null
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp)
    )
}

@Composable
private fun SettingsRowTrailingContent(trailing: SettingsRowTrailing) {
    when (trailing) {
        // AutoMirrored است تا در RTL خودش برگردد؛ شِورانِ ثابت در فارسی به بیرونِ صفحه اشاره می‌کرد.
        SettingsRowTrailing.Navigate -> Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )

        is SettingsRowTrailing.Value -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = trailing.text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = IranSansRegular,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(4.dp))
            SettingsRowMoreIcon()
        }

        SettingsRowTrailing.Menu -> SettingsRowMoreIcon()
    }
}

/** سه‌نقطهٔ پایانِ ردیف — در هر دو پایانه‌ای که آن را دارند دقیقاً یک‌شکل. */
@Composable
private fun SettingsRowMoreIcon() {
    Icon(
        imageVector = Icons.Outlined.MoreVert,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
    )
}

/**
 * نوارِ بالای صفحه‌های تنظیمات: ضربدر در ابتدا، عنوانِ وسط‌چین، و انتهای خالی.
 *
 * ضربدر است نه فلش: این صفحه‌ها روی صفحهٔ زیرین می‌آیند و بسته می‌شوند، جای دیگری برنمی‌گردند —
 * و ضربدر برخلافِ فلش در RTL جهت ندارد که غلط بیفتد.
 */
@Composable
fun SettingsTopBar(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * کنشِ انتهای نوار، روبه‌روی ضربدر. هم‌اندازهٔ ضربدر است، پس چه باشد چه نباشد عنوان دقیقاً
     * وسط می‌ماند — نبودش با یک [Spacer]ِ هم‌اندازه پر می‌شود.
     */
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsCloseButton(onClose = onClose)

        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = IranSansBold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )

        if (trailing != null) trailing() else Spacer(Modifier.size(SETTINGS_TOP_BAR_ACTION_SIZE))
    }
}

/** دکمهٔ «درباره» در انتهای نوار — همان دایرهٔ ضربدر، با علامتِ پرسش. */
@Composable
fun SettingsInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(SETTINGS_TOP_BAR_ACTION_SIZE)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.HelpOutline,
            contentDescription = "درباره",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * همان نوار، ولی با فلشِ بازگشت به‌جای ضربدر.
 *
 * فقط برای صفحه‌هایی است که واقعاً «عقب» دارند — دفترِ آدرس‌ها زیرصفحهٔ تنظیمات است و بستنش
 * یعنی برگشتن به فهرست، نه بستنِ کلِ تنظیمات. فلش `AutoMirrored` است: در فارسی باید به راست
 * اشاره کند، و نسخهٔ ثابت درست به بیرونِ صفحه اشاره می‌کرد.
 */
@Composable
fun SettingsBackTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(SETTINGS_TOP_BAR_ACTION_SIZE)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "بازگشت",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = IranSansBold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )

        Spacer(Modifier.size(SETTINGS_TOP_BAR_ACTION_SIZE))
    }
}

/** ضربدرِ بستن — همان دایرهٔ کوچکِ شیت‌های برنامه، این‌بار در نوارِ بالای صفحه. */
@Composable
fun SettingsCloseButton(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(SETTINGS_TOP_BAR_ACTION_SIZE)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "بستن",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}
