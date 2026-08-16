package com.mtd.megawallet.ui.compose.screens.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mtd.domain.model.AssetItem
import com.mtd.megawallet.ui.compose.components.HintState
import com.mtd.megawallet.ui.compose.components.SearchInputField
import com.mtd.megawallet.ui.compose.components.ShimmerAssetList
import com.mtd.megawallet.ui.compose.components.TokenList
import com.mtd.megawallet.viewmodel.swap.SwapUiState

/**
 * فازِ اول: انتخابِ توکنی که خرج می‌شود، از **موجودی واقعیِ** کاربر.
 *
 * ساختار و ظاهرش همان صفحهٔ ارسال است و از همان کامپوننت‌های مشترک ساخته می‌شود؛ تفاوتِ دو صفحه
 * فقط در فیلدِ بالای لیست است (آنجا آدرسِ مقصد، اینجا جست‌وجو).
 *
 * فهرست همهٔ دارایی‌های دارای موجودی را نشان می‌دهد و انتخابِ داراییِ غیرقابلِ اجرا صریحاً رد
 * می‌شود، تا کاربر دلیلش را بشنود نه اینکه دارایی‌اش را در فهرست پیدا نکند. ریلِ اجرا هر پا را
 * بر مبنای `family` می‌فرستد، پس EVM و ترون هر دو مبدأ معتبرند؛ زنجیره‌های UTXO نه.
 */
@Composable
fun SwapPaySection(
    state: SwapUiState,
    assets: List<AssetItem>,
    hasAnyAsset: Boolean,
    onQueryChange: (String) -> Unit,
    onAssetSelected: (AssetItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SearchInputField(
            value = state.payQuery,
            label = "جست‌وجو",
            placeholder = "جست‌وجو در دارایی‌های شما",
            onValueChange = onQueryChange
        )

        Spacer(Modifier.height(14.dp))

        when {
            // اسکلتِ ردیف‌ها به‌جای یک خط متن: فهرست چند شبکه را موازی می‌خواند و تا آمدنشان
            // کاربر باید ببیند چه چیزی قرار است بیاید، نه اینکه صفحه خالی بماند و بعد بپرد.
            state.isLoadingPayTokens -> ShimmerAssetList()

            !hasAnyAsset -> HintState("دارایی با موجودی برای تبدیل ندارید")

            assets.isEmpty() -> HintState("دارایی‌ای با این نام پیدا نشد")

            else -> TokenList(
                fiatCurrency = state.fiatCurrency,
                assets = assets,
                selectedAssetId = state.payToken?.option?.id,
                onTokenClick = onAssetSelected
            )
        }
    }
}

/**
 * حذفِ شبکه‌هایی که سرویسِ تبدیل رویشان کار نمی‌کند، **قبل از** گروه‌بندی — تا سرگروهی که فقط
 * زیرمجموعه‌های پشتیبانی‌نشده دارد اصلاً ساخته نشود.
 *
 * تا وقتی `/swap/chains` نیامده هیچ فیلتری اعمال نمی‌شود: نشان‌دادنِ فهرستِ خالی به‌خاطرِ پاسخی که
 * هنوز نرسیده بدتر از نشان‌دادنِ گزینه‌ای است که ممکن است رد شود.
 */
internal fun List<AssetItem>.retainSwappableNetworks(state: SwapUiState): List<AssetItem> {
    if (!state.swapChainsLoaded) return this
    return mapNotNull { asset ->
        if (asset.isGroupHeader) {
            val kept = asset.groupAssets.filter { state.isNetworkSwappable(it.networkId) }
            if (kept.isEmpty()) null else asset.copy(groupAssets = kept)
        } else {
            asset.takeIf { state.isNetworkSwappable(it.networkId) }
        }
    }
}

/** فیلترِ متنیِ فهرستِ پرداخت. گروه‌ها با نامِ خودشان پیدا می‌شوند، نه با نامِ زیرمجموعه‌ها. */
internal fun List<AssetItem>.filterByPayQuery(query: String): List<AssetItem> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { asset ->
        asset.symbol.contains(q, ignoreCase = true) ||
            asset.name.contains(q, ignoreCase = true) ||
            asset.faName?.contains(q, ignoreCase = true) == true
    }
}

/** دایرهٔ کوچکِ آیکونِ شبکه که روی گوشهٔ آیکونِ توکن می‌نشیند. */
@Composable
fun SwapNetworkBadge(
    iconUrl: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        SwapNetworkLogo(iconUrl = iconUrl, contentDescription = null, size = 15.dp)
    }
}
