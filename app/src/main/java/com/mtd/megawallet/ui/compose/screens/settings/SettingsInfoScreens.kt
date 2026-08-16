package com.mtd.megawallet.ui.compose.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.domain.model.BlockchainConnectionMode
import com.mtd.megawallet.BuildConfig
import com.mtd.megawallet.R
import com.mtd.megawallet.ui.compose.components.AnimatedBottomSheetCard
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.data.BuildConfig as DataBuildConfig

/**
 * درباره / توسعه‌دهنده — یک کارت با سرستونِ رنگی، نه یک صفحهٔ ساده.
 *
 * همه‌چیزش خوانده‌شده است، نه نوشته‌شده: نسخه و نوعِ بیلد از `BuildConfig`ِ برنامه، نشانیِ رله از
 * `BuildConfig`ِ ماژول data، و حالتِ اتصال از همان state که ردیفِ «اتصال» نشان می‌دهد. هیچ عددی
 * این‌جا دستی نوشته نشده تا با هر بیلد خودش درست بماند.
 *
 * دکمهٔ پایین **روی** محتوا می‌نشیند و متن از زیرش رد می‌شود؛ به همین دلیل ستونِ اسکرول یک
 * حاشیهٔ پایینیِ بزرگ دارد، وگرنه آخرین خطِ متن همیشه زیرِ دکمه گیر می‌کرد.
 */
@Composable
fun AboutSheet(
    visible: Boolean,
    connectionMode: BlockchainConnectionMode,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // عنوان عمداً خالی است: [AnimatedBottomSheetCard] نوارِ عنوانش را فقط وقتی می‌سازد که عنوان
    // خالی نباشد، و این‌جا جای آن را سرستونِ رنگی می‌گیرد. حاشیه‌های کناری و پایینی، گوشه‌ها،
    // پرده، انیمیشن و دکمهٔ بازگشت همه از همان کامپوننت می‌آیند تا این شیت با نُه شیتِ دیگرِ
    // برنامه یکی باشد.
    AnimatedBottomSheetCard(
        visible = visible,
        title = "",
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        AboutHeader(onClose = onDismiss)

        Column(
            modifier = Modifier
                // سقف دارد تا محتوای بلند شیت را تا کلِ صفحه بالا نکشد؛ کوتاه که باشد، شیت هم
                // کوتاه می‌ماند.
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp)
        ) {
            AboutSubheading("برنامه")
            InfoRow(label = "نسخه", value = BuildConfig.VERSION_NAME)
            InfoRow(label = "نوع بیلد", value = BuildConfig.BUILD_TYPE)
            InfoRow(
                label = "شناسه برنامه",
                value = BuildConfig.APPLICATION_ID,
                showDivider = false
            )

            AboutSubheading("اتصال")
            InfoRow(
                label = "حالت فعلی",
                value = connectionMode.displayLabel(),
                valueIsLatin = false
            )
            InfoRow(
                label = "نشانی سرور",
                value = DataBuildConfig.RELAYER_BASE_URL,
                showDivider = false
            )

            AboutSubheading("کلیدها")
            InfoParagraph(
                "مگاوالت یک کیف پول غیرامانی است: کلید خصوصی شما روی همین دستگاه ساخته و نگهداری می‌شود و هیچ‌وقت از آن خارج نمی‌شود. آنچه به سرور می‌رود فقط تراکنشِ امضاشده است."
            )
        }

        // بیرونِ ستونِ اسکرول، پس شناور نیست و آخرین سطرِ متن زیرش گیر نمی‌کند.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SETTINGS_ROW_HORIZONTAL_PADDING)
                .padding(top = 16.dp, bottom = 20.dp)
        ) {
            PrimaryButton(text = "متوجه شدم", onClick = onDismiss)
        }
    }
}

/**
 * سرستونِ رنگیِ کارت: نشانِ بزرگ و کم‌رنگ در پس‌زمینه، عنوان و زیرعنوانِ سفید رویش.
 *
 * نشان `clipToBounds` دارد چون از لبهٔ بلوک بیرون می‌زند تا «واترمارک» به نظر برسد نه آیکون؛
 * بدونِ آن، روی محتوای زیرِ سرستون می‌ریخت.
 */
@Composable
private fun AboutHeader(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .clipToBounds()
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_flat),
            contentDescription = null,
            alpha = 0.16f,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(190.dp)
                .offset(x = 54.dp, y = 26.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = SETTINGS_ROW_HORIZONTAL_PADDING,
                    // جای ضربدر در انتها خالی می‌ماند تا عنوان زیرش نرود.
                    end = SETTINGS_ROW_HORIZONTAL_PADDING + 40.dp,
                    top = 28.dp,
                    bottom = 30.dp
                )
        ) {
            Text(
                text = "مگاولت",
                color = Color.White,
                fontFamily = IranSansBold,
                fontSize = 24.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "کیف پول غیرامانی چندزنجیره‌ای",
                color = Color.White.copy(alpha = 0.82f),
                fontFamily = IranSansRegular,
                fontSize = 13.sp,
                lineHeight = 21.sp
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "بستن",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** زیرعنوانِ پررنگِ داخلِ متن — همان نقشِ سرگروه، ولی داخلِ بدنهٔ کارت. */
@Composable
private fun AboutSubheading(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = IranSansBold,
        fontSize = 15.sp,
        modifier = Modifier.padding(
            horizontal = SETTINGS_ROW_HORIZONTAL_PADDING,
            vertical = 12.dp
        )
    )
}

/**
 * راهنما و پشتیبانی.
 *
 * محتوایش ثابت است و عمداً هم ثابت می‌ماند: چیزهایی که کاربر واقعاً وسطِ کار گیر می‌کند و پاسخشان
 * به هیچ دادهٔ زنده‌ای وابسته نیست. جایی که پاسخ «بستگی دارد» باشد، این‌جا نوشته نمی‌شود.
 */
@Composable
fun HelpSupportScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxSize()) {
            SettingsTopBar(title = "راهنما و پشتیبانی", onClose = onClose)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                SettingsSectionHeader("پایه")
                HelpEntry(
                    question = "عبارت بازیابی را کجا نگه دارم؟",
                    answer = "بیرون از گوشی و بیرون از هر سرویس ابری. هر کسی که آن دوازده کلمه را داشته باشد به دارایی شما دسترسی کامل دارد، و ما هیچ نسخه‌ای از آن نداریم که برایتان بازیابی کنیم."
                )
                HelpEntry(
                    question = "کلیدم را چه کسی نگه می‌دارد؟",
                    answer = "خودتان. کلید روی همین دستگاه رمزگذاری و ذخیره می‌شود؛ امضای تراکنش هم همین‌جا انجام می‌شود و فقط نتیجهٔ امضاشده به شبکه می‌رود."
                )

                SettingsSectionHeader("ارسال و دریافت")
                HelpEntry(
                    question = "آدرس را روی شبکهٔ اشتباه فرستادم.",
                    answer = "تراکنش روی بلاکچین برگشت‌پذیر نیست. پیش از تأیید، شبکهٔ مقصد را در صفحهٔ تأیید ببینید؛ اگر آدرس گیرنده را در دفتر آدرس‌ها ثبت کنید، دفعهٔ بعد به‌جای تایپ‌کردن انتخابش می‌کنید."
                )
                HelpEntry(
                    question = "تراکنش در حال انتظار مانده است.",
                    answer = "شبکه شلوغ است و کارمزد پیشنهادی کفاف نداده. معمولاً خودش نهایی می‌شود؛ تا آن موقع موجودی‌تان کم نشده است.",
                    showDivider = false
                )

                SettingsSectionHeader("پشتیبانی")
                InfoParagraph(
                    "اگر مشکلی داشتید، نسخهٔ برنامه و نوع بیلد را از صفحهٔ «درباره» بردارید و همراه شرح مشکل بفرستید. هیچ‌وقت عبارت بازیابی یا کلید خصوصی‌تان را برای کسی نفرستید — هیچ پشتیبانی واقعی‌ای آن را از شما نمی‌خواهد."
                )
            }
        }
    }
}

/** یک سطرِ «برچسب ....... مقدار». مقدارهای لاتین با فونتِ لاتین، تا رقم‌ها به‌هم نریزند. */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueIsLatin: Boolean = true,
    showDivider: Boolean = true
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SETTINGS_ROW_HORIZONTAL_PADDING,
                    vertical = 14.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = IranSansRegular,
                fontSize = 14.sp
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = if (valueIsLatin) InterMedium else IranSansRegular,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = SETTINGS_ROW_HORIZONTAL_PADDING),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
        }
    }
}

@Composable
private fun HelpEntry(
    question: String,
    answer: String,
    showDivider: Boolean = true
) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(
                horizontal = SETTINGS_ROW_HORIZONTAL_PADDING,
                vertical = 14.dp
            )
        ) {
            Text(
                text = question,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = IranSansBold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = answer,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = IranSansRegular,
                fontSize = 13.sp,
                lineHeight = 23.sp
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = SETTINGS_ROW_HORIZONTAL_PADDING),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
        }
    }
}

@Composable
private fun InfoParagraph(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = IranSansRegular,
        fontSize = 13.sp,
        lineHeight = 23.sp,
        modifier = Modifier.padding(
            horizontal = SETTINGS_ROW_HORIZONTAL_PADDING,
            vertical = 12.dp
        )
    )
}
