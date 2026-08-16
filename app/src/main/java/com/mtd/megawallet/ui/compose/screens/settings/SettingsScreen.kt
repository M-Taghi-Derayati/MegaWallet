package com.mtd.megawallet.ui.compose.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtd.common_ui.R
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.megawallet.BuildConfig
import com.mtd.megawallet.viewmodel.SettingsViewModel

/**
 * یک ردیفِ تنظیمات به‌صورتِ داده.
 *
 * فهرست عمداً با اندیس خوانده نمی‌شود: نسخهٔ قبلی `items[0]` تا `items[8]` را دستی به هم وصل
 * می‌کرد، یعنی افزودنِ یک ردیف در وسط، کلیکِ همهٔ ردیف‌های بعدی را یکی جابه‌جا می‌کرد. حالا هر
 * ردیف عملِ خودش را با خودش حمل می‌کند.
 */
private data class SettingsRowModel(
    val key: String,
    val icon: Int,
    val title: String,
    val subtitle: String? = null,
    val trailing: SettingsRowTrailing,
    val onClick: () -> Unit
)

/** گروهی از ردیف‌ها. [header] برابرِ `null` یعنی گروهِ اول، که سرگروه ندارد. */
private data class SettingsGroup(
    val header: String?,
    val rows: List<SettingsRowModel>
)

/**
 * تنظیمات.
 *
 * هفت بخش در سه گروه. تفاوتِ پایانهٔ ردیف‌ها معنی دارد و اتفاقی نیست: شِوران یعنی صفحه‌ای باز
 * می‌شود، و «مقدار + سه‌نقطه» یعنی انتخابی همان‌جا عوض می‌شود. هر ردیف کارِ واقعی می‌کند —
 * انتخابگرها ترجیح را ماندگار ذخیره می‌کنند و صفحه‌ها محتوای واقعی دارند.
 *
 * ناوبری مثلِ بقیهٔ برنامه با state انجام می‌شود نه NavHost: زیرصفحه‌ها همین‌جا روی همین صفحه
 * می‌آیند و با state خودشان بسته می‌شوند.
 */
@Composable
fun SettingsScreen(
    onSecurityClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val connectionMode by viewModel.connectionMode.collectAsStateWithLifecycle()
    val fiatCurrency by viewModel.fiatCurrency.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val pushEnabled by viewModel.pushEnabled.collectAsStateWithLifecycle()

    // زیرصفحهٔ جاری جدا از دیده‌شدنش نگه داشته می‌شود — همان الگوی شیت‌ها — تا هنگام بسته‌شدن،
    // صفحه وسطِ انیمیشنِ بیرون‌رفتن خالی نشود.
    var subScreen by remember { mutableStateOf(SettingsSubScreen.AddressBook) }
    var subScreenVisible by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showConnectionPicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showNotificationPicker by remember { mutableStateOf(false) }
    var showSupportFlow by remember { mutableStateOf(false) }

    // «درباره» ردیف نیست: از دکمهٔ انتهای نوارِ بالا باز می‌شود، روبه‌روی ضربدر — همان‌جایی که
    // کاربر انتظار دارد راهنمای یک صفحه باشد، و فهرست را هم از یک ردیفِ صرفاً خواندنی خلاص می‌کند.
    var showAbout by remember { mutableStateOf(false) }

    val groups = listOf(
        SettingsGroup(
            header = null,
            rows = listOf(
                SettingsRowModel(
                    key = "address-book",
                    icon = R.drawable.ic_address_book,
                    title = "دفتر آدرس ‌ها",
                    subtitle = "مدیریت مخاطب ‌ها و آدرس ‌ها",
                    trailing = SettingsRowTrailing.Navigate,
                    onClick = {
                        subScreen = SettingsSubScreen.AddressBook
                        subScreenVisible = true
                    }
                ),
                SettingsRowModel(
                    key = "security",
                    icon = R.drawable.ic_security,
                    title = "امنیت",
                    subtitle = "قفل برنامه، رمز عبور و اثر انگشت",
                    trailing = SettingsRowTrailing.Navigate,
                    onClick = onSecurityClick
                )
            )
        ),
        SettingsGroup(
            header = "تنظیمات",
            rows = listOf(
                SettingsRowModel(
                    key = "currency",
                    icon = R.drawable.ic_currency,
                    title = "واحد پول",
                    trailing = SettingsRowTrailing.Value(fiatCurrency.displayLabel()),
                    onClick = { showCurrencyPicker = true }
                ),
                SettingsRowModel(
                    key = "theme",
                    icon = R.drawable.ic_theme,
                    title = "پوسته",
                    trailing = SettingsRowTrailing.Value(themeMode.displayLabel()),
                    onClick = { showThemePicker = true }
                ),
                SettingsRowModel(
                    key = "notifications",
                    icon = R.drawable.ic_notification,
                    title = "اعلان‌ها",
                    // پایانهٔ بی‌مقدار: خودِ کلید داخلِ شیت است و نوشتنِ «روشن/خاموش» در ردیف،
                    // همان یک حقیقت را در دو جا نگه می‌داشت.
                    trailing = SettingsRowTrailing.Menu,
                    onClick = { showNotificationPicker = true }
                ),
                SettingsRowModel(
                    key = "connection",
                    icon = R.drawable.ic_link,
                    title = "اتصال",
                    trailing = SettingsRowTrailing.Value(connectionMode.displayLabel()),
                    onClick = { showConnectionPicker = true }
                )
            )
        ),
        SettingsGroup(
            header = "بیشتر",
            rows = listOf(
                SettingsRowModel(
                    key = "faq",
                    icon = R.drawable.ic_question,
                    title = "پرسش‌های پرتکرار",
                    trailing = SettingsRowTrailing.Navigate,
                    onClick = {
                        subScreen = SettingsSubScreen.Help
                        subScreenVisible = true
                    }
                ),
                SettingsRowModel(
                    key = "support",
                    icon = R.drawable.ic_support,
                    title = "تماس با پشتیبانی",
                    // شِوران نیست چون صفحه‌ای عوض نمی‌شود؛ شیت روی همین فهرست باز می‌شود.
                    trailing = SettingsRowTrailing.Menu,
                    onClick = { showSupportFlow = true }
                )
            )
        )
    )

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                SettingsTopBar(
                    title = "تنظیمات",
                    onClose = onClose,
                    trailing = { SettingsInfoButton(onClick = { showAbout = true }) }
                )

                LazyColumn(

                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                ) {
                    groups.forEach { group ->
                        if (group.header != null) {
                            item(key = "header-${group.header}") {
                                SettingsSectionHeader(group.header)
                            }
                        }

                        itemsIndexed(group.rows, key = { _, row -> row.key }) { index, row ->
                            SettingsRow(
                                title = row.title,
                                subtitle = row.subtitle,
                                trailing = row.trailing,
                                showDivider = index != group.rows.lastIndex,
                                onClick = row.onClick,
                                leading = { SettingsRowIcon(row.icon) }
                            )
                        }
                    }
                }

                AppVersionFooter()
            }
        }

        FiatCurrencyPickerSheet(
            visible = showCurrencyPicker,
            selected = fiatCurrency,
            onSelect = {
                viewModel.setFiatCurrency(it)
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false }
        )

        ThemeModePickerSheet(
            visible = showThemePicker,
            selected = themeMode,
            onSelect = {
                viewModel.setThemeMode(it)
                showThemePicker = false
            },
            onDismiss = { showThemePicker = false }
        )

        NotificationPickerSheet(
            visible = showNotificationPicker,
            enabled = pushEnabled,
            // زدنِ کلید شیت را نمی‌بندد: برخلافِ انتخابگرها، این‌جا کاربر ممکن است کلید را
            // برگرداند و توضیحِ زیرش را بخواند. بستن کارِ دکمهٔ «تمام» است.
            onSelect = { viewModel.setPushEnabled(it) },
            onDismiss = { showNotificationPicker = false }
        )

        ConnectionModePickerSheet(
            visible = showConnectionPicker,
            selected = connectionMode,
            onSelect = {
                viewModel.setConnectionMode(it)
                showConnectionPicker = false
            },
            onDismiss = { showConnectionPicker = false }
        )

        SupportFlowSheet(
            visible = showSupportFlow,
            onDismiss = { showSupportFlow = false }
        )

        AboutSheet(
            visible = showAbout,
            connectionMode = connectionMode,
            onDismiss = { showAbout = false }
        )

        // زیرصفحه‌ها از سمتِ «پایانِ» صفحه می‌آیند؛ در RTL یعنی از چپ، که همان جهتِ «جلو رفتن»
        // در فارسی است.
        AnimatedVisibility(
            visible = subScreenVisible,
            enter = fadeIn(animationSpec = tween(160)) +
                slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(320)),
            exit = fadeOut(animationSpec = tween(140)) +
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(260))
        ) {
            when (subScreen) {
                SettingsSubScreen.AddressBook -> AddressBookScreen(
                    onClose = { subScreenVisible = false },
                    modifier = Modifier.fillMaxSize()
                )

                SettingsSubScreen.Help -> HelpSupportScreen(
                    onClose = { subScreenVisible = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    BackHandler(enabled = subScreenVisible) { subScreenVisible = false }
}

/** زیرصفحه‌هایی که از داخلِ تنظیمات باز می‌شوند. */
private enum class SettingsSubScreen {
    AddressBook,
    Help
}

/**
 * نسخهٔ برنامه، چسبیده به پایینِ صفحه.
 *
 * ردیف نیست چون ضربه‌زدنش کاری نمی‌کند و در این صفحه ردیف یعنی «این‌جا کاری انجام می‌شود».
 *
 * متنِ فارسی و متنِ لاتین در دو `Text` جدا نوشته می‌شوند، نه یک رشتهٔ به‌هم‌چسبیده: در یک متنِ
 * راست‌به‌چپ، الگوریتمِ دوجهته یک تکهٔ لاتینِ چسبیده به فارسی را جابه‌جا می‌چیند و «۱.۰ · debug»
 * به شکلِ «debug · ۱.۰» خوانده می‌شود. جداکردنشان هر تکه را در جهتِ خودش نگه می‌دارد.
 */
@Composable
private fun AppVersionFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = SETTINGS_ROW_HORIZONTAL_PADDING)
            .padding(top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "نسخهٔ",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = IranSansRegular,
            fontSize = 12.sp
        )
        // فاصله با Spacer، نه با فاصلهٔ ابتدای رشته: فاصلهٔ چسبیده به یک تکهٔ لاتین در بندِ
        // راست‌به‌چپ خودش جهت ندارد و به آن سرِ رشته می‌پرد.
        Spacer(Modifier.width(5.dp))
        Text(
            text = "${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_TYPE}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = InterMedium,
            fontSize = 12.sp
        )
    }
}
