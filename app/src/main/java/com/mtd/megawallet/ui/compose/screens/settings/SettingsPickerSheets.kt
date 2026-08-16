package com.mtd.megawallet.ui.compose.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.domain.model.BlockchainConnectionMode
import com.mtd.domain.model.FiatCurrency
import com.mtd.domain.model.ThemeMode
import com.mtd.megawallet.ui.compose.components.AnimatedBottomSheetCard
import com.mtd.megawallet.ui.compose.components.PrimaryButton

/** واحدِ پول همان‌طور که در ردیف و در انتخابگر نوشته می‌شود. */
internal fun FiatCurrency.displayLabel(): String = when (this) {
    FiatCurrency.USD -> "USD"
    FiatCurrency.TOMAN -> "تومان"
}

/** حالتِ اتصال همان‌طور که در ردیف و در انتخابگر نوشته می‌شود. */
internal fun BlockchainConnectionMode.displayLabel(): String = when (this) {
    BlockchainConnectionMode.DIRECT -> "مستقیم"
    BlockchainConnectionMode.PROXY -> "پروکسی"
}

/** پوسته همان‌طور که در ردیف و در انتخابگر نوشته می‌شود. */
internal fun ThemeMode.displayLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "مثل سیستم"
    ThemeMode.LIGHT -> "روشن"
    ThemeMode.DARK -> "تاریک"
}

/**
 * انتخابگرِ پوسته — سه کاشیِ هم‌عرض، نه فهرستِ رادیویی.
 *
 * دکمهٔ تأیید ندارد و عمداً هم ندارد: انتخاب همان لحظه روی کلِ برنامه می‌نشیند و شیت که هنوز باز
 * است رنگ عوض می‌کند (چون `IThemeModeProvider.themeMode` را همان Activity که این را نشان می‌دهد
 * collect کرده)، پس خودِ تغییرِ رنگ تأییدِ کار است و دکمه فقط یک ضربهٔ اضافه بود.
 *
 * حالتِ انتخاب‌شده در نمونهٔ مرجع خیلی کم‌رنگ است؛ این‌جا علاوه بر پس‌زمینه، قابِ رنگی و رنگیِ
 * محتوا هم دارد تا در پوستهٔ تاریک هم واقعاً دیده شود.
 */
@Composable
fun ThemeModePickerSheet(
    visible: Boolean,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedBottomSheetCard(
        visible = visible,
        title = "پوسته",
        onDismiss = onDismiss
    ) {
        PickerSheetBody {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeMode.entries.forEach { mode ->
                    ThemeModeTile(
                        label = mode.displayLabel(),
                        icon = when (mode) {
                            ThemeMode.SYSTEM -> Icons.Outlined.PhoneAndroid
                            ThemeMode.LIGHT -> Icons.Outlined.LightMode
                            ThemeMode.DARK -> Icons.Outlined.DarkMode
                        },
                        selected = mode == selected,
                        // انتخاب و بستن یک حرکت است؛ کاشی که زده شد کارش تمام است.
                        onClick = {
                            onSelect(mode)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "«مثل سیستم» یعنی هرچه پوستهٔ گوشی باشد؛ اگر شب‌ها خودکار تاریک می‌شود، برنامه هم همراهش می‌شود.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = IranSansRegular,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/** یک کاشیِ پوسته: آیکون بالا، برچسب پایین، و پُرشدنِ گِرد. */
@Composable
private fun ThemeModeTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            color = contentColor,
            fontFamily = if (selected) IranSansBold else IranSansRegular,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * اعلان‌ها — یک کلید، نه دو گزینهٔ رادیویی.
 *
 * خاموش‌کردن واقعاً توکنِ دستگاه را از رله برمی‌دارد، نه اینکه فقط یک مقدار محلی را عوض کند؛
 * وگرنه کلید خاموش می‌شد و اعلان‌ها همچنان می‌رسیدند. آن کار در [SettingsViewModel.setPushEnabled]
 * انجام می‌شود و این شیت فقط همان را صدا می‌زند.
 *
 * ⚠️ عمداً فقط **یک** کلید دارد. نمونهٔ مرجع چند کلید نشان می‌دهد، ولی برنامه فقط همین یک ترجیح
 * را واقعاً ذخیره و اعمال می‌کند؛ کلیدِ دومی که به چیزی وصل نباشد، کلیدِ خراب است نه کلیدِ اضافه.
 */
@Composable
fun NotificationPickerSheet(
    visible: Boolean,
    enabled: Boolean,
    onSelect: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedBottomSheetCard(
        visible = visible,
        title = "اعلان‌ها",
        onDismiss = onDismiss
    ) {
        PickerSheetBody {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )

            Spacer(Modifier.height(16.dp))

            SwitchRow(
                label = "اعلان‌های دارایی",
                checked = enabled,
                onCheckedChange = onSelect
            )

            // توضیح **بیرونِ** جعبه می‌نشیند: داخلِ همان قاب، متن با خودِ کلید یک واحد دیده می‌شد
            // و ردیف را از یک کنترلِ ساده به یک بلوکِ شلوغ تبدیل می‌کرد.
            Spacer(Modifier.height(8.dp))
            SwitchCaption(
                "وقتی دارایی به کیف پول‌تان می‌رسد یا تراکنشی نهایی می‌شود خبر می‌گیرید. با خاموش ‌کردن، دستگاه از فهرست گیرنده‌های اعلان برداشته می‌شود."
            )

            Spacer(Modifier.height(16.dp))
            SwitchCaption(
                "این کلید جدا از مجوز اعلان اندروید است. اگر مجوز را در تنظیمات گوشی رد کرده باشید، اعلان‌ها حتی با کلید روشن هم نمایش داده نمی‌شوند."
            )

            Spacer(Modifier.height(22.dp))
            PrimaryButton(text = "تمام", onClick = onDismiss)
        }
    }
}

/** یک کلید در قابِ خاکستریِ گِرد: برچسب در ابتدا، کلید در انتها. */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = IranSansBold,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SwitchCaption(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = IranSansRegular,
        fontSize = 12.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

/**
 * انتخابگرِ واحدِ پول.
 *
 * فقط همین دو گزینه وجود دارد و باید داشته باشد: تومان است، نه ریال — هر عددی که کاربر می‌بیند
 * در واحدِ تومان محاسبه می‌شود و نوشتنِ «ریال» یعنی رقمی ده‌برابر.
 */
@Composable
fun FiatCurrencyPickerSheet(
    visible: Boolean,
    selected: FiatCurrency,
    onSelect: (FiatCurrency) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedBottomSheetCard(
        visible = visible,
        title = "واحد پول",
        onDismiss = onDismiss
    ) {
        PickerSheetBody {
            FiatCurrency.entries.forEachIndexed { index, currency ->
                PickerOptionRow(
                    title = currency.displayLabel(),
                    description = when (currency) {
                        FiatCurrency.USD -> "واحدی که قیمت‌ها با آن گرفته می‌شود؛ همیشه در دسترس است."
                        FiatCurrency.TOMAN -> "تبدیل از دلار با نرخ روز؛ تا نرخ نیامده مقدار نشان داده نمی‌شود."
                    },
                    selected = currency == selected,
                    showDivider = index != FiatCurrency.entries.lastIndex,
                    onClick = { onSelect(currency) }
                )
            }
        }
    }
}

/**
 * انتخابگرِ حالتِ اتصال.
 *
 * زیرِ هر گزینه یک خط توضیح دارد چون این انتخاب واقعاً یک معامله است — حریمِ خصوصی در برابر
 * پایداری — و دو دکمهٔ رادیویی بدونِ توضیح از کاربر تصمیمی می‌خواستند که مبنایش را نمی‌دانست.
 */
@Composable
fun ConnectionModePickerSheet(
    visible: Boolean,
    selected: BlockchainConnectionMode,
    onSelect: (BlockchainConnectionMode) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedBottomSheetCard(
        visible = visible,
        title = "اتصال",
        onDismiss = onDismiss
    ) {
        PickerSheetBody {
            BlockchainConnectionMode.entries.forEachIndexed { index, mode ->
                PickerOptionRow(
                    title = mode.displayLabel(),
                    description = when (mode) {
                        BlockchainConnectionMode.DIRECT ->
                            "برنامه خودش به نودهای شبکه وصل می‌شود. واسطه‌ای در میان نیست، اما اگر نودی در دسترس نباشد کندتر یا ناموفق می‌شود."

                        BlockchainConnectionMode.PROXY ->
                            "درخواست‌ها از سرور مگاولت رد می‌شوند. سریع‌تر و پایدارتر است."
                    },
                    selected = mode == selected,
                    showDivider = index != BlockchainConnectionMode.entries.lastIndex,
                    onClick = { onSelect(mode) }
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "کلید خصوصی در هیچ‌کدام از دو حالت از دستگاه خارج نمی‌شود؛ امضا همیشه همین ‌جا انجام می‌شود.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = IranSansRegular,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun PickerSheetBody(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
    ) {
        content()
    }
}

/** یک گزینهٔ انتخابگر: عنوان، یک خط توضیح، و تیکِ گزینهٔ فعلی. */
@Composable
private fun PickerOptionRow(
    title: String,
    description: String,
    selected: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = IranSansBold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = IranSansRegular,
                    fontSize = 12.sp,
                    lineHeight = 19.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            // فضای تیک همیشه گرفته می‌شود تا با عوض‌شدنِ انتخاب، متنِ گزینه‌ها جابه‌جا نشود.
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "انتخاب‌شده",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
        }
    }
}
