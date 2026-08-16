package com.mtd.megawallet.ui.compose.screens.swap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.zIndex
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.domain.model.contacts.SavedAddress
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.ui.compose.components.RecipientInputSection
import com.mtd.megawallet.ui.compose.screens.send.ScanAddressRow
import com.mtd.megawallet.viewmodel.wallet.WalletAddressPickerViewModel.WalletAddresses

/**
 * انتخابِ گیرندهٔ خروجیِ تبدیل.
 *
 * چیدمانش عمداً هیچ چیزِ تازه‌ای اختراع نمی‌کند: فیلدِ آدرس و ردیفِ اسکن **همان کامپوننت‌های
 * صفحهٔ ارسال‌اند** ([RecipientInputSection] و [ScanAddressRow]) و با همان ترتیب و همان فاصله‌ها
 * چیده شده‌اند. کاربری که یک‌بار چیزی فرستاده، این‌جا هیچ الگوی جدیدی برای یاد گرفتن ندارد.
 *
 * فیلدِ بالا **ورودیِ آدرس** است، نه جست‌وجو؛ فهرست‌های پایین با آن فیلتر نمی‌شوند. هر چهار راهِ
 * رسیدن به یک آدرس (تایپ، جایگذاری، اسکن، انتخاب از فهرست) از همان [onAddressPicked] رد می‌شوند،
 * پس اعتبارسنجیِ شبکهٔ مقصد برای همه‌شان یکی است.
 */
@Composable
fun SwapDestinationSheet(
    visible: Boolean,
    networkName: String,
    /** آدرس‌های همین شبکه، از کیف‌پول‌های نام‌دارِ خودِ کاربر. */
    walletOptions: List<DestinationWalletOption>,
    /** دفترِ آدرس‌ها، از قبل برای همین شبکه فیلتر شده. */
    savedAddresses: List<SavedAddress>,
    selectedAddress: String?,
    manualInput: String,
    manualError: String?,
    onAddressPicked: (String) -> Unit,
    onScanRequested: () -> Unit,
    onPaste: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.zIndex(9998f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = Modifier.zIndex(9999f)
    ) {
        // انتخاب از فهرست همان‌جا تمام می‌شود: یک ضربه = یک گیرنده. نگه‌داشتنِ شیت باز بعد از
        // انتخاب یعنی خواستنِ یک تأییدِ دوم برای کاری که کاربر همین الان کرد.
        val pickAndClose: (String) -> Unit = { address ->
            onAddressPicked(address)
            onConfirm()
        }

        DestinationSheetSurface {
            SheetHeader(networkName = networkName, onDismiss = onDismiss)

            Spacer(Modifier.height(16.dp))

            Column(Modifier.padding(horizontal = SHEET_PADDING)) {
                RecipientInputSection(
                    recipientText = manualInput,
                    isValidAddress = manualInput.isNotBlank() && manualError == null,
                    onRecipientChanged = onAddressPicked,
                    onPaste = onPaste,
                    onClear = { onAddressPicked("") }
                )

                Spacer(Modifier.height(14.dp))

                ScanAddressRow(onScanClick = onScanRequested)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = SHEET_PADDING)
            ) {
                if (savedAddresses.isNotEmpty()) {
                    item {
                        SheetSectionHeader(
                            icon = Icons.Default.BookmarkBorder,
                            text = "آدرس‌های ثبت‌شده"
                        )
                    }
                    items(savedAddresses, key = { "saved-${it.id}" }) { entry ->
                        DestinationRow(
                            title = entry.name.ifBlank { shortenAddress(entry.address) },
                            subtitle = shortenAddress(entry.address),
                            isSelected = sameAddress(entry.address, selectedAddress),
                            onClick = { pickAndClose(entry.address) },
                            avatar = { SavedAddressAvatar() }
                        )
                    }
                }

                if (walletOptions.isNotEmpty()) {
                    item {
                        SheetSectionHeader(
                            icon = Icons.Default.AccountBalanceWallet,
                            text = "کیف‌ پول‌های من"
                        )
                    }
                    items(walletOptions, key = { "wallet-${it.walletId}" }) { option ->
                        DestinationRow(
                            title = option.name,
                            subtitle = shortenAddress(option.address),
                            isSelected = sameAddress(option.address, selectedAddress),
                            onClick = { pickAndClose(option.address) },
                            // رنگ و نامِ کیف‌پول همان‌هایی است که کاربر خودش انتخاب کرده و در
                            // بقیهٔ اپ می‌بیند؛ همان چیزی که یک کیف را از دیگری تشخیص‌پذیر می‌کند.
                            avatar = { WalletAvatar(name = option.name, color = Color(option.color)) }
                        )
                    }
                }

                if (savedAddresses.isEmpty() && walletOptions.isEmpty()) {
                    // حالتِ واقعی، نه استثنا: می‌شود به شبکه‌ای پل زد که هیچ کیف‌پولی رویش کلید
                    // ندارد. ورودیِ بالا همچنان کار می‌کند، پس کاربر بن‌بست نمی‌خورد.
                    item {
                        Text(
                            text = "گیرنده‌ای برای شبکهٔ $networkName ذخیره نشده. آدرس را وارد یا اسکن کنید.",
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontFamily = IranSansRegular,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }

            // تنها راهِ تمام‌کردنِ آدرسی که **دستی** وارد شده؛ انتخاب از فهرست خودش شیت را می‌بندد.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SHEET_PADDING)
                    .padding(bottom = 12.dp)
            ) {
                PrimaryButton(
                    text = "تأیید گیرنده",
                    onClick = onConfirm,
                    enabled = selectedAddress != null && manualError == null
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(networkName: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SHEET_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "ارسال به",
                color = MaterialTheme.colorScheme.tertiary,
                fontFamily = IranSansBold,
                fontSize = 26.sp
            )
            // شبکهٔ مقصد از عنوان جدا نمی‌شود: یک آدرسِ درست روی شبکهٔ اشتباه، آدرسِ اشتباه است.
            Text(
                text = "روی شبکهٔ $networkName",
                color = MaterialTheme.colorScheme.onTertiary,
                fontFamily = IranSansRegular,
                fontSize = 13.sp
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "بستن",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** سرِ هر گروه: یک آیکونِ کم‌رنگ و یک برچسبِ کم‌رنگ، با فاصلهٔ زیاد از گروهِ بالایی. */
@Composable
private fun SheetSectionHeader(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onTertiary,
            fontFamily = IranSansRegular,
            fontSize = 14.sp
        )
    }
}

/**
 * یک گیرندهٔ ممکن. بدون کارت و بدون کادر — ردیف روی خودِ سطحِ شیت می‌نشیند، دقیقاً مثل
 * [ScanAddressRow] که بالای همین فهرست است؛ آواتارِ ۴۴ هم هم‌اندازهٔ کاشیِ آن است تا کلِ ستون یک
 * ریتمِ عمودی داشته باشد.
 */
@Composable
private fun DestinationRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    avatar: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        avatar()

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.tertiary,
                fontFamily = IranSansBold,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onTertiary,
                fontFamily = InterMedium,
                fontSize = 13.sp
            )
        }

        if (isSelected) SelectedCheck()
    }
}

@Composable
private fun WalletAvatar(name: String, color: Color) {
    Box(
        modifier = Modifier
            .size(AVATAR_SIZE)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().take(1).ifBlank { "؟" },
            color = Color.White,
            fontFamily = IranSansBold,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun SavedAddressAvatar() {
    Box(
        modifier = Modifier
            .size(AVATAR_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.PersonOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** نشانهٔ «همینی است که الان انتخاب شده» — کم‌صدا، چون گزینهٔ دیگری را رد نمی‌کند. */
@Composable
private fun SelectedCheck() {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = "انتخاب‌شده",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp)
    )
}

/** قابِ شیت — همان سطحِ [SwapReceiveTokenSheet]، بدونِ کشیدنِ دستی چون داخلش فهرستِ اسکرولی است. */
@Composable
private fun DestinationSheetSurface(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
        ) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.4f))
            )
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** حاشیهٔ افقی، همان ۱۶ِ صفحهٔ ارسال تا دو صفحه روی یک خط بنشینند. */
private val SHEET_PADDING = 16.dp

/** هم‌اندازهٔ کاشیِ اسکنِ [ScanAddressRow]. */
private val AVATAR_SIZE = 44.dp

/** کیف‌پولی که روی شبکهٔ مقصد آدرس دارد. */
data class DestinationWalletOption(
    val walletId: String,
    val name: String,
    val color: Int,
    val address: String
)

/**
 * کیف‌پول‌هایی که روی [networkId] کلید دارند. کیف‌پولِ بدونِ کلید روی آن شبکه اصلاً نمایش داده
 * نمی‌شود؛ نشان‌دادنش به‌عنوانِ گزینهٔ غیرفعال فقط فهرست را شلوغ می‌کند بی‌آنکه کاری از کاربر بربیاید.
 */
fun List<WalletAddresses>.destinationOptionsFor(networkId: String?): List<DestinationWalletOption> {
    if (networkId.isNullOrBlank()) return emptyList()
    return mapNotNull { wallet ->
        wallet.addressOn(networkId)?.let { address ->
            DestinationWalletOption(
                walletId = wallet.walletId,
                name = wallet.name,
                color = wallet.color,
                address = address
            )
        }
    }
}

/**
 * مدخل‌هایی که برای این شبکه معنی دارند: یا صریحاً برای همین شبکه ثبت شده‌اند، یا شبکه‌ای
 * نگرفته‌اند (یعنی «هر شبکه‌ای»). اعتبارِ واقعیِ آدرس همچنان سرِ انتخاب سنجیده می‌شود.
 */
fun List<SavedAddress>.savedAddressesFor(networkId: String?): List<SavedAddress> {
    if (networkId.isNullOrBlank()) return emptyList()
    return filter { it.networkId == null || it.networkId.equals(networkId, ignoreCase = true) }
}

/**
 * نامی که خودِ کاربر روی این گیرنده گذاشته — نامِ کیف‌پولش یا مدخلِ دفترچه.
 *
 * `null` یعنی این آدرس در هیچ‌کدام از دو فهرست نیست؛ آن‌وقت فراخوان تصمیم می‌گیرد چه بنویسد.
 * هیچ نامِ ثابتی این‌جا ساخته نمی‌شود: «کیف پول من» برای کسی که کیفش را «کیف اتر» نامیده غلط است.
 */
fun resolveDestinationName(
    address: String?,
    walletOptions: List<DestinationWalletOption>,
    savedAddresses: List<SavedAddress>
): String? {
    if (address.isNullOrBlank()) return null
    walletOptions.firstOrNull { sameAddress(it.address, address) }?.let { return it.name }
    return savedAddresses.firstOrNull { sameAddress(it.address, address) }?.name
}

/**
 * کوتاه‌سازیِ **فقط نمایشی**، با نقطه‌چینِ وسط.
 *
 * ⚠️ هیچ‌جا با این مقدار مقایسه نمی‌شود و هیچ‌وقت به سرور نمی‌رود: خودِ آدرسِ کامل دست‌نخورده حمل
 * می‌شود، چون base58 ترون به حروف حساس است.
 */
internal fun shortenAddress(address: String): String =
    if (address.length > 14) "${address.take(6)}…${address.takeLast(6)}" else address

/** مقایسهٔ آدرس بدونِ دست‌زدن به خودِ رشته — فقط برای تشخیصِ ردیفِ انتخاب‌شده. */
private fun sameAddress(a: String?, b: String?): Boolean =
    a != null && b != null && a.trim().equals(b.trim(), ignoreCase = true)
