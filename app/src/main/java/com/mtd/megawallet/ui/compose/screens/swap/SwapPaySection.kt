package com.mtd.megawallet.ui.compose.screens.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.IranSansBoldMedium
import com.mtd.common_ui.theme.IranSansLightLight
import com.mtd.common_ui.theme.IranSansRegularMedium
import com.mtd.megawallet.viewmodel.swap.SwapPayToken
import com.mtd.megawallet.viewmodel.swap.SwapUiState

/**
 * فازِ اول: انتخابِ توکنی که خرج می‌شود، از **موجودی واقعیِ** کاربر.
 *
 * فقط شبکه‌های EVM: بستهٔ `prepare` تراکنشِ EVM (`to`/`data`/`value`) برمی‌گرداند و ریلِ اجرا
 * `TransactionParams.Evm` است، پس دارایی‌های UTXO/TVM اینجا اصلاً قابل انتخاب نیستند.
 */
@Composable
fun SwapPaySection(
    state: SwapUiState,
    onQueryChange: (String) -> Unit,
    onTokenSelected: (SwapPayToken, Rect) -> Unit,
    rowIcon: @Composable (SwapPayToken) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SwapSearchField(
            value = state.payQuery,
            placeholder = "جست‌وجو در دارایی‌های شما",
            onValueChange = onQueryChange
        )

        Spacer(Modifier.height(16.dp))

        val tokens = state.visiblePayTokens
        when {
            state.isLoadingPayTokens -> SwapHint("در حال خواندن دارایی‌ها…")

            state.payTokens.isEmpty() -> SwapHint(
                "دارایی قابل تبدیلی ندارید. تبدیل فعلاً روی شبکه‌های EVM انجام می‌شود."
            )

            tokens.isEmpty() -> SwapHint("دارایی‌ای با این نام پیدا نشد.")

            else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tokens.forEach { token ->
                    SwapPayRow(
                        token = token,
                        state = state,
                        icon = { rowIcon(token) },
                        onSelected = onTokenSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun SwapPayRow(
    token: SwapPayToken,
    state: SwapUiState,
    icon: @Composable () -> Unit,
    onSelected: (SwapPayToken, Rect) -> Unit
) {
    // موقعیتِ لوگو باید از بازسازی‌ها جان به در ببرد: مبدأِ پروازِ FLIP همین مستطیل است.
    val logoBounds = remember { mutableStateOf(Rect.Zero) }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = null) {
                onSelected(token, logoBounds.value)
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .onGloballyPositioned { logoBounds.value = it.boundsInRoot() },
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = token.option.faName ?: token.option.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontFamily = IranSansBoldMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${token.balanceDisplay} ${token.option.symbol}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontFamily = InterMedium
            )
        }

        Text(
            text = SwapFormat.fiat(
                raw = token.balanceRaw,
                decimals = token.option.decimals,
                priceUsd = token.priceUsd,
                currency = state.fiatCurrency,
                rate = state.usdToTomanRate
            ),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontFamily = InterMedium
        )
    }
}

@Composable
fun SwapSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontFamily = IranSansLightLight
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontFamily = IranSansRegularMedium
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SwapHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        fontFamily = IranSansLightLight,
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp)
    )
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
        SwapTokenLogo(iconUrl = iconUrl, contentDescription = null, size = 15.dp)
    }
}
