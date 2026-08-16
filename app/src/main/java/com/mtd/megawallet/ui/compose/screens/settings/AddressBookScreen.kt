package com.mtd.megawallet.ui.compose.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.NetworkIcon
import com.mtd.common_ui.theme.SymbolAvatar
import com.mtd.domain.model.contacts.SavedAddress
import com.mtd.megawallet.ui.compose.components.AnimatedBottomSheetCard
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.ui.compose.components.SearchInputField
import com.mtd.megawallet.viewmodel.settings.AddressBookViewModel

/**
 * دفترِ آدرس‌ها — همان مدخل‌هایی که سرِ ارسال و تبدیل انتخاب می‌شوند، این‌بار جایی که می‌شود
 * ساخت، ویرایش و حذفشان کرد.
 *
 * زبانِ بصریِ ردیف‌ها همان [SettingsRow] است: پایانهٔ «مقدار + سه‌نقطه» یعنی ضربه‌زدن همین‌جا
 * کاری باز می‌کند، نه اینکه صفحه‌ای عوض شود — و مقدارش نامِ شبکهٔ همان مدخل است.
 */
@Composable
fun AddressBookScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddressBookViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()

    // مدخلِ هر شیت جدا از دیده‌شدنش نگه داشته می‌شود تا هنگام بسته‌شدن، محتوا وسطِ انیمیشن خالی نشود.
    var editorEntry by remember { mutableStateOf<SavedAddress?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    var editorResetKey by remember { mutableIntStateOf(0) }

    var actionsEntry by remember { mutableStateOf<SavedAddress?>(null) }
    var actionsVisible by remember { mutableStateOf(false) }

    var deleteEntry by remember { mutableStateOf<SavedAddress?>(null) }

    // تازه‌ترین مدخل بالای فهرست: مدخلی که همین الان ساخته شده همان‌جایی است که کاربر نگاه می‌کند.
    val sorted = remember(entries) { entries.sortedByDescending { it.createdAtMillis } }

    fun openEditor(existing: SavedAddress?) {
        editorEntry = existing
        editorResetKey++
        editorVisible = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                // فلشِ بازگشت، نه ضربدر: این‌جا زیرصفحهٔ تنظیمات است و بستنش یعنی برگشتن به
                // فهرست، نه بستنِ کلِ تنظیمات.
                SettingsBackTopBar(title = "دفتر آدرس‌ها", onBack = onClose)

                if (sorted.isEmpty()) {
                    AddressBookEmptyState(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                    ) {
                        itemsIndexed(sorted, key = { _, entry -> entry.id }) { index, entry ->
                            SettingsRow(
                                title = entry.name,
                                subtitle = truncateForDisplay(entry.address),
                                subtitleIsLatin = true,
                                trailing = SettingsRowTrailing.Value(
                                    viewModel.networkLabelFor(entry.networkId)
                                ),
                                showDivider = index != sorted.lastIndex,
                                onClick = {
                                    actionsEntry = entry
                                    actionsVisible = true
                                },
                                leading = {
                                    AddressEntryAvatar(
                                        name = entry.name,
                                        iconUrl = viewModel.networkIconUrlFor(entry.networkId)
                                    )
                                }
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = SETTINGS_ROW_HORIZONTAL_PADDING)
                        .padding(bottom = 16.dp, top = 8.dp)
                ) {
                    PrimaryButton(
                        text = "افزودن آدرس",
                        onClick = { openEditor(null) }
                    )
                }
            }
        }

        AddressEditorSheet(
            visible = editorVisible,
            resetKey = editorResetKey,
            existing = editorEntry,
            networkOptions = viewModel.networkOptions,
            onSave = { name, address, networkId ->
                viewModel.save(
                    existing = editorEntry,
                    name = name,
                    address = address,
                    networkId = networkId
                )
                editorVisible = false
            },
            onDismiss = { editorVisible = false }
        )

        AddressActionsSheet(
            visible = actionsVisible,
            entry = actionsEntry,
            onEdit = {
                actionsVisible = false
                openEditor(actionsEntry)
            },
            onDelete = {
                actionsVisible = false
                deleteEntry = actionsEntry
            },
            onDismiss = { actionsVisible = false }
        )

        // حذف برگشت‌ناپذیر است و ردیف‌ها کنارِ هم و هم‌شکل‌اند؛ یک تأییدِ صریح فاصلهٔ بین
        // «سه‌نقطه را زدم» و «مخاطبم رفت» را پر می‌کند.
        deleteEntry?.let { entry ->
            AlertDialog(
                onDismissRequest = { deleteEntry = null },
                title = {
                    Text(
                        text = "حذف «${entry.name}»؟",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = IranSansBold,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Text(
                        text = "این آدرس از دفترچه پاک می‌شود. دارایی‌ای جابه‌جا نمی‌شود و تراکنش‌های گذشته دست‌نخورده می‌مانند.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = IranSansRegular,
                        fontSize = 13.sp,
                        lineHeight = 21.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.remove(entry.id)
                        deleteEntry = null
                    }) {
                        Text(
                            text = "حذف",
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = IranSansBold,
                            fontSize = 14.sp
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteEntry = null }) {
                        Text(
                            text = "انصراف",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = IranSansRegular,
                            fontSize = 14.sp
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

/**
 * آیکونِ ابتدای ردیف: نشانِ شبکهٔ مدخل، و اگر مدخل شبکه‌ای نگرفته باشد آواتارِ حرفیِ نامش.
 *
 * هیچ‌کدام `painterResource` نیستند — نشانِ شبکه از [NetworkIcon] می‌آید که منبعش `iconUrl`ِ
 * کاتالوگ است، پس شبکهٔ تازه بدونِ تغییرِ کد نشانِ درست می‌گیرد.
 */
@Composable
private fun AddressEntryAvatar(name: String, iconUrl: String?) {
    if (iconUrl != null) {
        NetworkIcon(
            iconUrl = iconUrl,
            contentDescription = null,
            modifier = Modifier.size(SETTINGS_ROW_ICON_SLOT)
        )
    } else {
        SymbolAvatar(
            symbol = name,
            contentDescription = null,
            modifier = Modifier.size(SETTINGS_ROW_ICON_SLOT)
        )
    }
}

/**
 * حالتِ خالی — تصویر، دو خط توضیح، و بس.
 *
 * دکمهٔ «افزودن آدرس» این‌جا تکرار نمی‌شود: همان دکمهٔ چسبیده به پایینِ صفحه در هر دو حالت
 * (خالی و پُر) سرِ جایش است، و دو دکمهٔ هم‌کار روی یک صفحه یعنی کاربر باید حدس بزند فرقشان چیست.
 */
@Composable
private fun AddressBookEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyContactsIllustration()

        Spacer(Modifier.height(24.dp))

        Text(
            text = "هنوز آدرسی ثبت نکرده‌اید",
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = IranSansBold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "آدرس‌هایی که این‌جا ثبت می‌کنید، سرِ ارسال و تبدیل با نام خودشان در فهرست گیرنده‌ها می‌آیند و لازم نیست دوباره تایپ شوند.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = IranSansRegular,
            fontSize = 13.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * دایرهٔ نقطه‌چین با نشانِ یک آدم و یک «به‌علاوه»ی کوچک.
 *
 * با `Canvas` کشیده می‌شود نه با فایلِ تصویری، چون تنها رنگش باید با پوسته عوض شود و یک وکتورِ
 * ثابت در پوستهٔ تاریک محو می‌شد. نشانِ «به‌علاوه» در `BottomEnd` می‌نشیند که در راست‌به‌چپ
 * خودش قرینه می‌شود.
 */
@Composable
private fun EmptyContactsIllustration() {
    val outlineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

    Box(
        modifier = Modifier.size(112.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 2.dp.toPx()
            drawCircle(
                color = outlineColor,
                radius = (size.minDimension - stroke) / 2f,
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 6.dp.toPx())
                    )
                )
            )
        }

        Icon(
            imageVector = Icons.Outlined.PersonOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(46.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * فرمِ ساخت و ویرایش.
 *
 * رفتارش عمداً همان کارتِ «آدرس جدید»ِ شیتِ انتخابِ گیرنده است — نام، آدرس، و شبکه‌ای که آدرس
 * برای آن ثبت می‌شود — ولی کدش جداست: آن کارت وسطِ یک فلویِ ارسال زندگی می‌کند و وابسته به
 * اعتبارسنجیِ همان فلو است، و کشیدنش به تنظیمات هر دو را به هم گره می‌زد.
 *
 * ⚠️ هیچ اعتبارسنجیِ آدرسی این‌جا انجام نمی‌شود و متنِ آدرس هم دست نمی‌خورد. صحتِ آدرس به شبکهٔ
 * مقصد بستگی دارد و همان‌جایی سنجیده می‌شود که مقصد معلوم است.
 */
@Composable
private fun AddressEditorSheet(
    visible: Boolean,
    resetKey: Int,
    existing: SavedAddress?,
    networkOptions: List<AddressBookViewModel.NetworkOption>,
    onSave: (name: String, address: String, networkId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    // کلیدِ resetKey: هر بار که شیت باز می‌شود فرم از نو ساخته می‌شود، وگرنه بازکردنِ دوبارهٔ
    // «افزودن» مقادیرِ دفعهٔ قبل را نشان می‌داد.
    var name by remember(resetKey) { mutableStateOf(existing?.name.orEmpty()) }
    var address by remember(resetKey) { mutableStateOf(existing?.address.orEmpty()) }
    var networkId by remember(resetKey) { mutableStateOf(existing?.networkId) }

    AnimatedBottomSheetCard(
        visible = visible,
        title = if (existing == null) "افزودن آدرس" else "ویرایش آدرس",
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // این شیت دو فیلدِ متنی دارد؛ بدونِ imePadding صفحه‌کلید دقیقاً روی دکمهٔ ذخیره می‌نشیند.
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            SearchInputField(
                value = name,
                label = "نام",
                placeholder = "این آدرس را چه بنامیم؟",
                onValueChange = { name = it }
            )

            Spacer(Modifier.height(10.dp))

            SearchInputField(
                value = address,
                label = "آدرس",
                placeholder = "آدرس مقصد",
                onValueChange = { address = it }
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "شبکه",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = IranSansRegular,
                fontSize = 12.sp,
                letterSpacing = 1.2.sp
            )

            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(networkOptions, key = { it.id ?: ANY_NETWORK_KEY }) { option ->
                    NetworkChip(
                        label = option.label,
                        iconUrl = option.iconUrl,
                        selected = option.id.equals(networkId, ignoreCase = true),
                        onClick = { networkId = option.id }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "«هر شبکه» یعنی این آدرس در فهرست گیرنده‌های همهٔ شبکه‌ها دیده می‌شود؛ برای آدرس‌های EVM که روی همهٔ زنجیره‌ها یکی‌اند مناسب است.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = IranSansRegular,
                fontSize = 11.sp,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(18.dp))

            PrimaryButton(
                text = "ذخیره",
                enabled = name.isNotBlank() && address.isNotBlank(),
                onClick = { onSave(name, address, networkId) }
            )
        }
    }
}

@Composable
private fun NetworkChip(
    label: String,
    iconUrl: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconUrl != null) {
            NetworkIcon(
                iconUrl = iconUrl,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontFamily = IranSansRegular,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** ویرایش یا حذف — همان چیزی که سه‌نقطهٔ کنارِ ردیف وعده‌اش را می‌دهد. */
@Composable
private fun AddressActionsSheet(
    visible: Boolean,
    entry: SavedAddress?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedBottomSheetCard(
        visible = visible,
        title = entry?.name.orEmpty(),
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            if (entry != null) {
                Text(
                    text = entry.address,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = InterMedium,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(
                        horizontal = SETTINGS_ROW_HORIZONTAL_PADDING,
                        vertical = 4.dp
                    )
                )
                Spacer(Modifier.height(12.dp))
            }

            AddressActionRow(
                icon = Icons.Outlined.Edit,
                title = "ویرایش",
                onClick = onEdit
            )

            AddressActionRow(
                icon = Icons.Outlined.Delete,
                title = "حذف از دفترچه",
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete
            )
        }
    }
}

/**
 * ردیفِ یک عمل در شیتِ عملیات — عمداً [SettingsRow] نیست: آن‌جا هر ردیف پایانه‌ای دارد که می‌گوید
 * ضربه چه می‌کند، این‌جا خودِ ردیف همان عمل است و پایانه چیزی برای گفتن ندارد. رنگِ خطرِ حذف هم
 * بخشی از پیام است و ریختنش در آن کامپوننت یعنی هر ردیفِ تنظیماتی هم می‌توانست رنگی شود.
 */
@Composable
private fun AddressActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = SETTINGS_ROW_HORIZONTAL_PADDING, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(SETTINGS_ROW_ICON_SLOT),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            color = tint,
            fontFamily = IranSansBold,
            fontSize = 16.sp
        )
    }
}

/** کلیدِ گزینهٔ «هر شبکه» در فهرست — چون شناسه‌اش `null` است و `key` نمی‌تواند `null` باشد. */
private const val ANY_NETWORK_KEY = "any-network"

/**
 * کوتاه‌سازیِ **فقط نمایشی** با نقطه‌چینِ وسط.
 *
 * ⚠️ خروجی‌اش هیچ‌جا مقایسه یا ذخیره نمی‌شود؛ خودِ آدرس همیشه کامل و دست‌نخورده حمل می‌شود.
 * حروف هم عوض نمی‌شود — base58 ترون به حروف حساس است.
 *
 * `internal` است چون فلویِ پشتیبانی هم آدرسِ کیف‌پول را با همین قاعده نشان می‌دهد؛ دو نسخه از
 * یک کوتاه‌سازی یعنی دو جا که ممکن است یکی‌شان روزی آدرس را دست‌کاری کند.
 */
internal fun truncateForDisplay(address: String): String =
    if (address.length > 16) "${address.take(8)}…${address.takeLast(8)}" else address
