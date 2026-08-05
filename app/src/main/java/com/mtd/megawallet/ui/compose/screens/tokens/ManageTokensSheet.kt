package com.mtd.megawallet.ui.compose.screens.tokens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtd.common_ui.theme.AssetIcon
import com.mtd.common_ui.theme.InterRegular
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansLight
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.NetworkIcon
import com.mtd.megawallet.ui.compose.animations.constants.MainScreenConstants
import com.mtd.megawallet.ui.compose.components.SearchInputField
import com.mtd.megawallet.viewmodel.tokens.ManageTokensViewModel
import com.mtd.megawallet.viewmodel.tokens.NetworkOption
import com.mtd.megawallet.viewmodel.tokens.TokenRowSource
import com.mtd.megawallet.viewmodel.tokens.TokenRowUi

/**
 * شیتِ مدیریتِ توکن — از آیکونِ ذره‌بینِ هدر باز می‌شود.
 *
 * سه منبع در یک فهرست: توکن‌های held، بعد کاتالوگِ curated، و با تایپ نتایجِ جست‌وجو (که آدرسِ
 * دقیقِ قرارداد را هم می‌پذیرد — همان مسیرِ «واردکردن با آدرس»).
 */
@Composable
fun ManageTokensSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    viewModel: ManageTokensViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .clip(
                        RoundedCornerShape(
                            topStart = MainScreenConstants.FAB_CORNER_RADIUS_EXPANDED,
                            topEnd = MainScreenConstants.FAB_CORNER_RADIUS_EXPANDED
                        )
                    )
                    .background(
                        if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.background
                    )
                    .clickable(enabled = false) {} // جلوی کلیکِ رد شدن به scrim پشت
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
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
                    placeholder = "نماد، نام یا آدرس قرارداد",
                    onValueChange = viewModel::onQueryChange
                )

                if (state.isContractQuery) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "آدرس قرارداد شناسایی شد — نتیجه را از فهرست انتخاب کنید",
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontFamily = IranSansLight,
                        fontSize = 12.sp
                    )
                }

                state.inlineError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = IranSansRegular,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)

                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.rows.isEmpty() && !state.isLoading && !state.isSearching) {
                        Text(
                            text = if (state.query.isBlank()) "توکنی برای نمایش نیست"
                            else "نتیجه‌ای پیدا نشد",
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontFamily = IranSansRegular,
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items = state.rows, key = { it.key }) { row ->
                            TokenManageRow(row = row, onToggle = { viewModel.toggle(row) })
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }

                    if (state.isLoading || state.isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "مدیریت توکن‌ها",
            color = MaterialTheme.colorScheme.tertiary,
            fontFamily = IranSansBold,
            fontSize = 20.sp
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(22.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "بستن",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun NetworkChips(
    networks: List<NetworkOption>,
    selectedNetworkId: String?,
    onSelect: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = networks, key = { it.id }) { network ->
            val selected = network.id == selectedNetworkId
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                    .clickable { onSelect(network.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NetworkIcon(
                    iconUrl = network.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = network.label,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary,
                    fontFamily = IranSansRegular,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TokenManageRow(
    row: TokenRowUi,
    onToggle: () -> Unit
) {
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
            AssetIcon(
                iconUrl = row.iconUrl,
                symbol = row.symbol,
                contentDescription = null,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.symbol,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = InterRegular,
                        fontSize = 16.sp
                    )
                    if (row.source == TokenRowSource.HELD) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "دارایی شما",
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = IranSansLight,
                            fontSize = 10.sp
                        )
                    }
                }
                Text(
                    text = row.name,
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontFamily = IranSansLight,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
                    contentDescription = if (added) "پنهان کردن ${row.symbol}" else "افزودن ${row.symbol}",
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
