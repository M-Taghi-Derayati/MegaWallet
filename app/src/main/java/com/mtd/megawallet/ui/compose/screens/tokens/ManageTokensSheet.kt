package com.mtd.megawallet.ui.compose.screens.tokens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtd.common_ui.R
import com.mtd.common_ui.theme.AssetIcon
import com.mtd.common_ui.theme.InterRegular
import com.mtd.common_ui.theme.InterRegularMedium
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansLight
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.IranSansRegularMedium
import com.mtd.common_ui.theme.MegaWalletTheme
import com.mtd.common_ui.theme.NetworkIcon
import com.mtd.core.utils.BalanceFormatter
import com.mtd.core.utils.FiatConversion
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.FiatCurrency
import com.mtd.megawallet.ui.compose.animations.constants.MainScreenConstants
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.components.AnimatedCounter
import com.mtd.megawallet.ui.compose.components.SHEET_BOTTOM_MARGIN
import com.mtd.megawallet.ui.compose.components.SHEET_SIDE_MARGIN
import com.mtd.megawallet.ui.compose.components.SearchInputField
import com.mtd.megawallet.viewmodel.tokens.ManageTokensViewModel
import com.mtd.megawallet.viewmodel.tokens.NetworkOption
import com.mtd.megawallet.viewmodel.tokens.TokenRowSource
import com.mtd.megawallet.viewmodel.tokens.TokenRowUi
import java.math.BigDecimal





@Composable
fun ManageTokensSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    viewModel: ManageTokensViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()


    val displayCurrency by viewModel.displayCurrency.collectAsStateWithLifecycle()
    val usdToIrrRate by viewModel.usdToIrrRate.collectAsStateWithLifecycle()

    LaunchedEffect(visible) {
        if (visible) viewModel.onOpened()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(MainScreenConstants.ZLayer.MANAGE_TOKENS)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(400)) +
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                ),
            exit = fadeOut(animationSpec = tween(300)) +
                slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier

                    .navigationBarsPadding()
                    .padding(horizontal = SHEET_SIDE_MARGIN)
                    .padding(vertical = SHEET_BOTTOM_MARGIN)
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .clip(RoundedCornerShape(MainScreenConstants.FAB_CORNER_RADIUS_EXPANDED))
                    .background(
                        if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.background
                    )
                    .clickable(enabled = false) {} // جلوی کلیکِ رد شدن به scrim پشت
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                DragHandle()

                Spacer(modifier = Modifier.height(8.dp))

                SheetHeader(onDismiss = onDismiss)

                Spacer(modifier = Modifier.height(16.dp))

                NetworkChips(
                    networks = state.networks,
                    selectedNetworkId = state.selectedNetworkId,
                    onSelect = viewModel::selectNetwork
                )

                Spacer(modifier = Modifier.height(16.dp))

                SearchInputField(
                    value = state.query,
                    label = "جست‌وجو",
                    placeholder = "نماد، نام یا آدرس قرارداد — در همهٔ شبکه‌ها",
                    onValueChange = viewModel::onQueryChange
                )

                // راهنما و خطا با انیمیشن باز/بسته می‌شوند: ظاهرشدنِ ناگهانی‌شان کلِ فهرست را یک
                // پله پایین می‌پراند، و آن پرش درست وسطِ تایپ‌کردنِ آدرس اتفاق می‌افتاد.
                AnimatedVisibility(
                    visible = state.isContractQuery,
                    enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                ) {
                    HintText(
                        text = if (state.hasMultipleResolveMatches) {
                            "این آدرس روی چند شبکه پیدا شد — شبکهٔ درست را خودتان انتخاب کنید"
                        } else {
                            "آدرس قرارداد شناسایی شد — نتیجه را از فهرست انتخاب کنید"
                        },
                        color = if (state.hasMultipleResolveMatches) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onTertiary
                    )
                }

                AnimatedVisibility(
                    visible = state.inlineError != null,
                    enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                ) {
                    HintText(
                        text = state.inlineError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = IranSansRegular
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)

                TokenResultList(
                    rows = state.rows,
                    isBusy = state.isLoading || state.isSearching,
                    hasQuery = state.query.isNotBlank(),
                    displayCurrency = displayCurrency,
                    usdToIrrRate = usdToIrrRate,
                    onToggle = viewModel::toggle
                )
            }
        }
    }
}

/** دستگیرهٔ شیت — تنها نشانهٔ بصری که این صفحه کشیدنی/بستنی است و نه یک صفحهٔ کامل. */
@Composable
private fun DragHandle() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
private fun HintText(
    text: String,
    color: Color,
    fontFamily: FontFamily = IranSansLight
) {
    Text(
        text = text,
        color = color,
        fontFamily = fontFamily,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SheetHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "مدیریت توکن‌ ها",
            color = MaterialTheme.colorScheme.tertiary,
            fontFamily = IranSansBold,
            fontSize = 20.sp
        )
        // ناحیهٔ لمسی ۴۴dp است ولی دایرهٔ دیده‌شده ۲۸dp: قبلاً کلِ IconButton روی ۲۲dp قفل شده بود،
        // یعنی هدفِ لمسی نصفِ حداقلِ توصیه‌شده — و بستنِ شیت کاری است که هر بار انجام می‌شود.
        IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "بستن",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * چیپِ اولِ فهرست «همهٔ شبکه‌ها» است و پیش‌فرض هم همان است — انتخابِ شبکه یک باریک‌کننده است،
 * نه قدمی که کاربر قبل از جست‌وجو مجبور به برداشتنش باشد.
 */
@Composable
private fun NetworkChips(
    networks: List<NetworkOption>,
    selectedNetworkId: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "__all__") {
            NetworkChip(
                label = "همهٔ شبکه‌ها",
                iconUrl = null,
                selected = selectedNetworkId == null,
                onClick = { onSelect(null) }
            )
        }
        items(items = networks, key = { it.id }) { network ->
            NetworkChip(
                label = network.label,
                iconUrl = network.iconUrl,
                selected = network.id == selectedNetworkId,
                onClick = { onSelect(network.id) }
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
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
            // فقط اختلافِ آلفای پس‌زمینه کافی نبود: چیپِ انتخاب‌شده و نشده در تمِ روشن تقریباً
            // یک‌رنگ دیده می‌شدند، و «فیلترِ فعال» چیزی است که کاربر باید با یک نگاه بفهمد.
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else Color.Transparent,
                shape = CircleShape
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconUrl != null) {
            NetworkIcon(
                iconUrl = iconUrl,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.tertiary,
            fontFamily = IranSansRegular,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/**
 * از کجا آمدنِ ردیف، آن‌طور که کاربر باید بخواندش.
 *
 * [CURATED] فهرستی است که سرور برای این شبکه منتشر کرده — و همان چیزی است که کاربر را از توکنِ
 * جعلیِ هم‌نام دور نگه می‌دارد. [SEARCH] بیرونِ آن فهرست است و باید هم همان‌طور به‌نظر برسد.
 */
private enum class TokenSection { CURATED, WALLET, SEARCH }

private val TokenRowUi.section: TokenSection
    get() = when (source) {
        TokenRowSource.DEFAULT -> TokenSection.CURATED
        TokenRowSource.CATALOG -> TokenSection.WALLET
        TokenRowSource.SEARCH -> TokenSection.SEARCH
    }

/**
 * فهرستِ نتایج، به‌علاوهٔ حالت‌های خالی و درحالِ‌بارگذاری.
 *
 * ⚠️ ترتیبِ `rows` عیناً همان است که ViewModel داده (ترتیبِ سرور) — این‌جا فقط جایی که سرفصل
 * عوض می‌شود یک تیتر تزریق می‌شود، هیچ گروه‌بندی یا مرتب‌سازیِ دوباره‌ای در کار نیست.
 */
@Composable
private fun TokenResultList(
    rows: List<TokenRowUi>,
    isBusy: Boolean,
    hasQuery: Boolean,
    displayCurrency: FiatCurrency,
    usdToIrrRate: CurrencyRate?,
    onToggle: (TokenRowUi) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            // شیمر فقط وقتی که **هیچ** ردیفی نداریم. اگر فهرست از قبل پر است، تازه‌سازی با نوارِ
            // باریکِ بالا اعلام می‌شود؛ پوشاندنِ ردیف‌های موجود با placeholder یعنی برداشتنِ
            // چیزی که کاربر همین حالا دارد می‌خواند.
            rows.isEmpty() && isBusy -> ShimmerManageTokensList()
            rows.isEmpty() -> EmptyState(hasQuery = hasQuery)
            else -> Column(modifier = Modifier.fillMaxSize()) {
                // ارتفاع همیشه رزرو است، چه نوار باشد چه نه: قبلاً اسپینر روی ردیفِ اول شناور
                // می‌شد و هر بار عوض‌کردنِ فیلتر، اولین توکن را زیرِ خودش می‌گرفت.
                Box(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                    if (isBusy) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                }

                // `weight` و نه `fillMaxSize`: دومی ارتفاعِ کاملِ والد را می‌گیرد و نوارِ ۲dpِ
                // بالا را به بیرونِ کادر هل می‌داد.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(items = rows, key = { _, row -> row.key }) { index, row ->
                        val section = row.section
                        if (index == 0 || rows[index - 1].section != section) {
                            SectionHeader(section = section)
                        }
                        TokenManageRow(
                            row = row,
                            displayCurrency = displayCurrency,
                            usdToIrrRate = usdToIrrRate,
                            onToggle = { onToggle(row) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/**
 * دو حالتِ خالیِ کاملاً متفاوت با دو راهنماییِ متفاوت: «هنوز چیزی نخواسته‌ای» در برابر «خواستی و
 * نبود». دومی باید بگوید قدمِ بعدی چیست، وگرنه کاربر فرض می‌کند توکنش پشتیبانی نمی‌شود.
 */
@Composable
private fun EmptyState(hasQuery: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (hasQuery) "نتیجه‌ای پیدا نشد" else "توکنی برای نمایش نیست",
            color = MaterialTheme.colorScheme.tertiary,
            fontFamily = IranSansBold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasQuery) {
                "اگر آدرس قرارداد را دارید همان را جای‌ گذاری کنید — توکن‌هایی که در فهرست‌ها نیستند فقط از این راه پیدا می‌شوند"
            } else {
                "یک شبکه انتخاب کنید یا نماد توکن را جست‌وجو کنید"
            },
            color = MaterialTheme.colorScheme.onTertiary,
            fontFamily = IranSansLight,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

/**
 * سرفصلِ هر بخش.
 *
 * تیترِ [TokenSection.SEARCH] عمداً ظرفِ رنگی و متنِ هشدار دارد و دو تیترِ دیگر ندارند: کلِ نکته
 * همین است که نتیجهٔ جست‌وجو با ردیفِ فهرستِ شاخص یک‌جور به‌نظر نرسد. متن هم اتهام نمی‌زند —
 * فقط می‌گوید این ردیف از فیلترِ فهرستِ شاخص رد نشده.
 */
@Composable
private fun SectionHeader(section: TokenSection) {
    Spacer(modifier = Modifier.height(16.dp))

    when (section) {
        TokenSection.CURATED -> SimpleSectionTitle(
            title = "توکن‌ های شاخص",
            subtitle = "فهرستی که برای این شبکه‌ها منتشر شده"
        )

        TokenSection.WALLET -> SimpleSectionTitle(
            title = "در کیف پول شما",
            subtitle = null
        )

        TokenSection.SEARCH -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = "نتایج جست‌وجو",
                color = MaterialTheme.colorScheme.tertiary,
                fontFamily = IranSansBold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "بیرون از فهرست شاخص. نماد و نام توکن تکراری‌ شدنی است — پیش از افزودن، شبکه و آدرس قرارداد را بررسی کنید.",
                color = MaterialTheme.colorScheme.onTertiary,
                fontFamily = IranSansLight,
                fontSize = 11.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun SimpleSectionTitle(title: String, subtitle: String?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.tertiary,
            fontFamily = IranSansBold,
            fontSize = 13.sp
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onTertiary,
                fontFamily = IranSansLight,
                fontSize = 11.sp
            )
        }
    }
}

/** قیمتِ نامعلوم. `$0` نیست و نباید بشود — «نمی‌دانیم» با «بی‌ارزش» یکی نیست. */
private const val PRICE_UNKNOWN = "—"

/**
 * نشانِ **منشأ**، نه نشانِ ایمنی.
 *
 * `true` ⇒ «ثبت‌شده در فهرست‌های معتبر». عمداً «تأییدشده» نوشته نمی‌شود: آن کلمه در بافتِ مالی
 * یعنی ممیزی یا توصیه، و این فقط یعنی «فهرستی که به آن اتکا می‌کنیم این قرارداد را منتشر کرده».
 * یک توکن می‌تواند ثبت‌شده باشد و تمامِ ارزشش را از دست بدهد.
 *
 * `false` ⇒ هشدارِ **خنثی**، نه اتهام: یک توکنِ واقعیِ تازه یا کم‌مخاطب هم دقیقاً همین‌طور است.
 * هیچ‌وقت باعثِ پنهان‌شدن یا غیرفعال‌شدنِ کلیدِ افزودن نمی‌شود.
 *
 * `null` ⇒ سرور چیزی نگفته (همهٔ ردیف‌های باندل)، پس نه نشان و نه هشدار.
 *
 * فرمش هم‌خانوادهٔ `AssetProvenanceMark`ِ لیستِ دارایی‌هاست — یک کاراکترِ کنارِ نماد. جملهٔ کاملِ
 * قبلی در ۱۰sp دو خط می‌شکست و ارتفاعِ ردیف‌ها را نامنظم می‌کرد؛ توضیحِ بلند حالا یک بار در
 * سرفصلِ بخش می‌آید، نه زیرِ تک‌تکِ ردیف‌ها.
 */
@Composable
private fun ProvenanceMark(verified: Boolean?) {
    if (verified == null) return
    Spacer(modifier = Modifier.width(4.dp))
    Text(
        text = if (verified) "•" else "!",
        color = if (verified) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.error,
        fontFamily = InterRegular,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.semantics {
            contentDescription = if (verified) {
                "ثبت‌ شده در فهرست ‌های معتبر"
            } else {
                "در فهرست‌های معتبر ثبت نشده"
            }
        }
    )
}

/**
 * قیمتِ یک توکن در **واحدِ پولِ انتخابیِ کاربر**، یا `null` وقتی قابلِ نمایش نیست.
 *
 * تبدیل از [FiatConversion] رد می‌شود و نه از یک ضربِ محلی — همان‌جا تنها جایی است که ضریبِ
 * ریال↔تومان را می‌شناسد، و دور زدنش یعنی ریسکِ ده‌برابر غلط بودنِ عدد.
 *
 * قاعدهٔ اعشارِ دلار هم دیگر این‌جا تکرار نمی‌شود: `BalanceFormatter.formatUsdValue` از قبل دقیقاً
 * همان کار را می‌کرد (زیرِ `$0.01` تا ۶ رقم، وگرنه ۲ رقم) و نسخهٔ محلیِ قبلی صرفاً یک کپیِ
 * بدونِ جداکنندهٔ هزارگان از آن بود.
 *
 * `null` یعنی «نمی‌دانیم» و دو منشأ دارد که در UI یک‌جور خوانده می‌شوند: قیمتی که سرور نداده، و
 * نرخِ تومانی که هنوز نیامده. هیچ‌کدام صفر نیستند.
 */
private fun formatTokenPrice(
    priceUsd: BigDecimal,
    currency: FiatCurrency,
    rate: CurrencyRate?
): String? {
    if (currency == FiatCurrency.TOMAN) {
        val toman = FiatConversion.usdToToman(priceUsd, rate) ?: return null
        // مقیاسِ نمایشیِ تومان صفر است، که برای **موجودی** درست است ولی برای **قیمتِ واحد** نه:
        // یک توکنِ ارزان زیرِ یک تومان قیمت دارد و با گردکردن «۰» می‌شود — همان چیزی که کلِ این
        // ستون برای پرهیز از آن ساخته شده. فقط در همین بازه اعشار باز می‌شود.
        if (toman > BigDecimal.ZERO && toman < BigDecimal.ONE) {
            return BalanceFormatter.formatNumberWithSeparator(
                number = toman,
                usePersianSeparator = true,
                minFractionDigits = 2,
                maxFractionDigits = 6
            )
        }
    }

    return BalanceFormatter
        .formatFiatValue(usdAmount = priceUsd, currency = currency, rate = rate, withSymbol = false)
        // نرخ نامعلوم ⇒ خودِ formatter جای‌نگه‌دار برمی‌گرداند؛ به `null` نگاشته می‌شود تا واحد هم
        // کنارش چاپ نشود. «— تومان» یعنی تبدیلِ شکست‌خورده، نه قیمت.
        .takeIf { it != FiatConversion.UNKNOWN_PLACEHOLDER }
}

/** قلمِ عدد به واحدِ پول گره خورده: ارقامِ فارسیِ تومان با قلمِ لاتین بدریخت درمی‌آیند. */
private fun currencyFontFamily(currency: FiatCurrency): FontFamily = when (currency) {
    FiatCurrency.USD -> InterRegularMedium
    FiatCurrency.TOMAN -> IranSansRegularMedium
}

/**
 * قیمتِ واحدِ توکن، در واحدِ پولِ انتخابیِ کاربر.
 *
 * دو حالتِ کاملاً جدا:
 *  - **معلوم** ⇒ [AnimatedCounter] به‌علاوهٔ واحد، هم‌شکلِ ستونِ ارزش در لیستِ دارایی‌ها. با عوض‌شدنِ
 *    تومان/دلار عدد انیمیت می‌شود و `styleVariantKey` باعث می‌شود قلم هم با آن عوض شود.
 *  - **نامعلوم** ⇒ فقط «—» و **بدونِ واحد**. سرور قیمت‌های ساختگیِ استخرهای بی‌نقدینگی را عمداً
 *    حذف می‌کند و صفر گرفتنِ آن یعنی دارایی کاربر بی‌صدا آب می‌رود؛ نرخِ نیامدهٔ تومان هم همین‌طور.
 */
@Composable
private fun TokenPrice(
    priceUsd: BigDecimal?,
    displayCurrency: FiatCurrency,
    usdToIrrRate: CurrencyRate?
) {
    val priceText = priceUsd?.let { formatTokenPrice(it, displayCurrency, usdToIrrRate) }

    if (priceText == null) {
        Text(
            text = PRICE_UNKNOWN,
            color = MaterialTheme.colorScheme.onTertiary,
            fontFamily = InterRegular,
            fontSize = 12.sp,
            maxLines = 1
        )
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedCounter(
            text = priceText,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.tertiary,
                fontFamily = currencyFontFamily(displayCurrency)
            ),
            animationDuration = WalletScreenConstants.ASSET_ANIMATION_DURATION,
            styleVariantKey = displayCurrency
        )
        // `fiatSymbol` و نه دو شاخهٔ دستی: «$» و «تومان» یک جا تعریف شده‌اند و همان‌جا هم باید بمانند.
       /* Text(
            text = BalanceFormatter.fiatSymbol(displayCurrency),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = WalletScreenConstants.ASSET_PRICE_SYMBOL_FONT_SIZE,
                color = MaterialTheme.colorScheme.tertiary,
                fontFamily = currencyFontFamily(displayCurrency)
            ),
            modifier = Modifier.padding(start = WalletScreenConstants.ASSET_PRICE_SYMBOL_PADDING_END)
        )*/

        if (displayCurrency == FiatCurrency.USD) {
            Text(
                text = "$",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = WalletScreenConstants.ASSET_PRICE_SYMBOL_FONT_SIZE,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = InterRegularMedium
                ),
                modifier = Modifier.padding(start = WalletScreenConstants.ASSET_PRICE_SYMBOL_PADDING_END)
            )
        }else{
            Text(
                text = " تومان",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = WalletScreenConstants.ASSET_PRICE_SYMBOL_FONT_SIZE,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = IranSansRegularMedium
                ),
                modifier = Modifier.padding(start = WalletScreenConstants.ASSET_PRICE_SYMBOL_PADDING_END)
            )
        }
    }
}

@Composable
private fun TokenManageRow(
    row: TokenRowUi,
    displayCurrency: FiatCurrency,
    usdToIrrRate: CurrencyRate?,
    onToggle: () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // آیکونِ ارز با بجِ شبکه روی گوشه — همان بلوکی که در لیستِ دارایی‌ها هست. قبلاً آیکونِ
            // شبکه یک تصویرِ ۱۲dp وسطِ خطِ دومِ متن بود، یعنی دو صفحه دو زبانِ بصریِ متفاوت داشتند.
            Box(modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_SIZE)) {
                AssetIcon(
                    iconUrl = row.iconUrl,
                    symbol = row.symbol,
                    contentDescription = null,
                    modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE)
                )

                Box(
                    modifier = Modifier
                        .size(WalletScreenConstants.ASSET_ICON_NETWORK_SIZE_LARGE)
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center
                ) {
                    // `ic_pls` این‌جا لوگو نیست، ماسکِ هم‌رنگِ پس‌زمینه است تا بجِ شبکه از آیکونِ
                    // ارزِ زیرش جدا دیده شود؛ خودِ لوگوی شبکه همچنان از `networkIconUrl` می‌آید.
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pls),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = if (isDark) Color.Black else Color.White
                    )
                    NetworkIcon(
                        iconUrl = row.networkIconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(WalletScreenConstants.ASSET_ICON_NETWORK_PADDING)
                    )
                }
            }

            Spacer(modifier = Modifier.width(WalletScreenConstants.ASSET_ICON_SPACING))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.symbol,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = InterRegular,
                        fontSize = 16.sp
                    )
                    ProvenanceMark(verified = row.verified)
                    Spacer(modifier = Modifier.weight(1f))
                    TokenPrice(
                        priceUsd = row.priceUsd,
                        displayCurrency = displayCurrency,
                        usdToIrrRate = usdToIrrRate
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // نامِ شبکه در نمای cross-network تزیین نیست: دو ردیفِ «USDT» فقط با همین از هم
                // تشخیص داده می‌شوند، و انتخابِ زنجیرهٔ اشتباه یعنی توکنی بدونِ موجودی.
                Text(
                    text = "${row.networkLabel} · ${row.name}",
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontFamily = IranSansLight,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // فقط حالتِ «ثبت‌نشده» یک خط توضیح می‌گیرد. ردیفِ بلندتر این‌جا عیب نیست — همان
                // ردیفی است که کاربر باید رویش مکث کند.
                if (row.verified == false) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ثبت‌ نشده در فهرست‌های معتبر — آدرس قرارداد را بررسی کنید",
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontFamily = IranSansLight,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // کوینِ اصلیِ شبکه هم پنهان‌شدنی است، ولی «حذف» نمی‌شود — همان کلید، معنیِ متفاوت.
        val added = row.isAdded
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (added) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable(enabled = !row.isBusy) { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            if (row.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = if (added) Icons.Default.Check else Icons.Default.Add,
                    // شبکه در توضیح می‌آید چون در نمای cross-network چند ردیفِ هم‌نماد وجود دارد
                    // و بدونِ آن دو کلیدِ متفاوت برای screen reader یکسان اعلام می‌شوند.
                    contentDescription = if (added) "پنهان کردن ${row.symbol} روی ${row.networkLabel}"
                    else "افزودن ${row.symbol} روی ${row.networkLabel}",
                    tint = if (added) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
}

// ============================================
// Previews
// ============================================

/**
 * ردیفِ نمونه برای پرویو. پیش‌فرض‌ها «حالتِ عادی» را می‌سازند و هر پرویو فقط همان فیلدی را عوض
 * می‌کند که موضوعش است.
 *
 * ⚠️ آدرس‌ها ساختگی‌اند ولی **عمداً با حروفِ بزرگ و کوچکِ درست** نوشته شده‌اند: در پرویو جایی
 * فرستاده نمی‌شوند، اما نمونهٔ lowercase‌شده همان چیزی است که بعداً کپی می‌شود و روی base58ِ ترون
 * آدرسِ معتبرِ اشتباه می‌سازد.
 */
private fun previewTokenRow(
    symbol: String = "USDT",
    name: String = "Tether USD",
    networkLabel: String = "ترون",
    verified: Boolean? = true,
    priceUsd: BigDecimal? = BigDecimal("0.9998"),
    isAdded: Boolean = true,
    isUserAdded: Boolean = false,
    isNative: Boolean = false,
    isBusy: Boolean = false,
    source: TokenRowSource = TokenRowSource.DEFAULT
) = TokenRowUi(
    key = "$networkLabel:$symbol",
    assetId = "preview_${symbol}_$networkLabel",
    networkId = "tron",
    networkLabel = networkLabel,
    networkIconUrl = null,
    symbol = symbol,
    name = name,
    decimals = 6,
    contractAddress = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
    iconUrl = null,
    isAdded = isAdded,
    isUserAdded = isUserAdded,
    isNative = isNative,
    source = source,
    verified = verified,
    priceUsd = priceUsd,
    isBusy = isBusy
)

/**
 * همهٔ حالت‌هایی که ارتفاع یا وزنِ بصریِ ردیف را عوض می‌کنند، پشتِ هم — چون کلِ هدفِ این چیدمان
 * این بود که ردیف‌ها کنارِ هم منظم بمانند و آن را فقط در مجاورت می‌شود دید، نه تک‌تک.
 *
 * در پرویو هیچ تصویرِ شبکه‌ای دانلود نمی‌شود، پس آیکون‌ها همان مسیرِ fallback را نشان می‌دهند:
 * drawableِ لوکال برای نمادهای شناخته‌شده و `SymbolAvatar`ِ شش‌ضلعی برای بقیه — که اتفاقاً همان
 * چیزی است که باید کنترل شود.
 */
@Composable
private fun TokenManageRowPreviewContent(
    displayCurrency: FiatCurrency = FiatCurrency.USD,
    usdToIrrRate: CurrencyRate? = PREVIEW_TOMAN_RATE
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        // افزوده‌شده، ثبت‌شده، با قیمت — حالتِ مرجع.
        PreviewRow(previewTokenRow(), displayCurrency, usdToIrrRate)

        // ثبت‌نشده و بدونِ قیمت: هم نشانِ «!» و هم خطِ هشدار، و «—» به‌جای `$0`.
        PreviewRow(
            previewTokenRow(
                symbol = "PEPE2",
                name = "Pepe 2.0",
                networkLabel = "اتریوم",
                verified = false,
                priceUsd = null,
                isAdded = false,
                source = TokenRowSource.SEARCH
            ),
            displayCurrency,
            usdToIrrRate
        )

        // ردیفِ باندل: `verified = null` ⇒ اصلاً نشان نمی‌گیرد.
        PreviewRow(
            previewTokenRow(
                symbol = "TRX",
                name = "TRON",
                verified = null,
                priceUsd = BigDecimal("0.2431"),
                isNative = true,
                source = TokenRowSource.CATALOG
            ),
            displayCurrency,
            usdToIrrRate
        )

        // قیمتِ خیلی کوچک: در دلار ۶ رقمِ اعشار می‌گیرد و در تومان زیرِ یک تومان می‌افتد — همان
        // موردی که گردکردن به مقیاسِ صفر «۰» نشانش می‌داد.
        PreviewRow(
            previewTokenRow(
                symbol = "WSTETH",
                name = "Wrapped Liquid Staked Ether 2.0",
                networkLabel = "آربیتروم",
                priceUsd = BigDecimal("0.000042"),
                isAdded = false,
                isUserAdded = true,
                source = TokenRowSource.SEARCH
            ),
            displayCurrency,
            usdToIrrRate
        )

        // درحالِ اعمالِ تغییر — کلید غیرفعال است و اسپینر جای آیکون را می‌گیرد.
        PreviewRow(
            previewTokenRow(symbol = "USDC", name = "USD Coin", isBusy = true),
            displayCurrency,
            usdToIrrRate
        )
    }
}

/** فقط برای اینکه دو پارامترِ مشترک در پنج ردیفِ پرویو تکرار نشوند. */
@Composable
private fun PreviewRow(
    row: TokenRowUi,
    displayCurrency: FiatCurrency,
    usdToIrrRate: CurrencyRate?
) {
    TokenManageRow(
        row = row,
        displayCurrency = displayCurrency,
        usdToIrrRate = usdToIrrRate,
        onToggle = {}
    )
}

/** نرخِ نمونه با واحدِ `TMN` — همان برچسبی که مسیرِ تولید (Wallex) می‌فرستد. */
private val PREVIEW_TOMAN_RATE = CurrencyRate(
    quoteCurrency = "TMN",
    rate = BigDecimal("102500"),
    lastUpdated = 0L
)

@Preview(name = "TokenManageRow — Light / USD", widthDp = 400)
@Composable
private fun TokenManageRowLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            TokenManageRowPreviewContent(displayCurrency = FiatCurrency.USD)
        }
    }
}

// `uiMode` لازم است و تزیینی نیست: ماسکِ `ic_pls` رنگش را از `isSystemInDarkTheme()` می‌گیرد نه از
// پارامترِ تم، پس بدونِ آن پرویوی تیره یک بجِ سفید روی پس‌زمینهٔ تیره نشان می‌داد — یعنی دقیقاً همان
// چیزی که این پرویو قرار است کنترلش کند، غلط رندر می‌شد.
@Preview(
    name = "TokenManageRow — Dark / USD",
    widthDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun TokenManageRowDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            TokenManageRowPreviewContent(displayCurrency = FiatCurrency.USD)
        }
    }
}

/** همان ردیف‌ها با واحدِ تومان — عرضِ ستونِ قیمت این‌جا بیشترین مقدارش را می‌گیرد. */
@Preview(name = "TokenManageRow — تومان", widthDp = 400)
@Composable
private fun TokenManageRowTomanPreview() {
    MegaWalletTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            TokenManageRowPreviewContent(displayCurrency = FiatCurrency.TOMAN)
        }
    }
}

/**
 * تومان **بدونِ نرخ** — حالتی که روی هر cold start قبل از آمدنِ نرخ واقعاً وجود دارد.
 * هر پنج ردیف باید «—» بدونِ واحد نشان بدهند، نه «۰ تومان».
 */
@Preview(name = "TokenManageRow — تومان، نرخِ نامعلوم", widthDp = 400)
@Composable
private fun TokenManageRowTomanNoRatePreview() {
    MegaWalletTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            TokenManageRowPreviewContent(
                displayCurrency = FiatCurrency.TOMAN,
                usdToIrrRate = null
            )
        }
    }
}
