package com.mtd.megawallet.ui.compose.screens.wallet

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.mtd.common_ui.R
import com.mtd.megawallet.event.ReceiveUiState
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.components.UnifiedHeader
import com.mtd.megawallet.viewmodel.news.ReceiveViewModel
import java.util.EnumMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    onDismiss: () -> Unit,
    viewModel: ReceiveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedAddress by viewModel.selectedAddress.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    // Handle system back button
    BackHandler {
        onDismiss()
    }



    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header - Stylish Black Header


                    UnifiedHeader(onBack = onDismiss,"دریافت")




                // Network Selector Row
                when (val state = uiState) {
                    is ReceiveUiState.Success -> {
                        val allItems = state.addressGroups.flatMap { it.items }
                        
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(allItems) { item ->
                                NetworkChip(
                                    item = item,
                                    isSelected = selectedAddress?.id == item.id,
                                    onClick = { viewModel.selectAddress(item) }
                                )
                            }
                        }
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Main Content
                if (selectedAddress != null) {
                    ReceiveCard(
                        addressItem = selectedAddress!!,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(selectedAddress!!.address))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NetworkChip(
    item: ReceiveUiState.AddressItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.tertiary
    val context = LocalContext.current
    val imageLoader = remember { coil.ImageLoader(context) }
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(12.dp)),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.networkFaName ?: item.networkName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
            )

            val localIcon = remember(item.symbol, item.id) {
                val symbolIcon = item.symbol?.let { getLocalIconResId(it) } ?: 0
                if (symbolIcon != 0) symbolIcon else getNetworkIconResId(item.id)
            }

            if (localIcon != 0 && localIcon != R.drawable.ic_wallet) {
                Spacer(modifier = Modifier.width(8.dp))
                Image(
                    painter = painterResource(id = localIcon),
                    contentDescription = null,
                    modifier = Modifier.size(25.dp).clip(CircleShape),
                    contentScale = ContentScale.Fit
                )
            } else {
                item.iconUrl?.let {
                    Spacer(modifier = Modifier.width(8.dp))
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).clip(CircleShape),
                        contentScale = ContentScale.Fit,
                        imageLoader = imageLoader
                    )
                }
            }

        }
    }
}

@Composable
fun ReceiveCard(
    addressItem: ReceiveUiState.AddressItem,
    onCopy: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(320.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(Color(0xFF007AFF)), // رنگ آبی اختصاصی فمیلی (iOS Blue)
            contentAlignment = Alignment.Center
        ) {
            QRCodeDisplay(
                content = addressItem.address,
                modifier = Modifier.size(300.dp),
                symbol = addressItem.symbol ?: "ETH"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))


        // Supported Networks Display (Overlap effect) - Stable Layout
        Box(
            modifier = Modifier
                .height(65.dp) // Fixed height to prevent layout jumps
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = addressItem.supportedNetworkIds.isNotEmpty(),
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(400))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SupportedNetworksRow(
                        networkIds = addressItem.supportedNetworkIds,
                        iconUrls = addressItem.supportedNetworkIcons
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "شبکه‌های تحت پشتیبانی",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Address Display (Minimal and modern)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCopy() }
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface // استفاده از رنگ سیستم
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState = addressItem.address,
                    transitionSpec = {
                        fadeIn(tween(400)) togetherWith fadeOut(tween(400))
                    },
                    label = "AddressTextAnimation"
                ) { targetAddress ->
                    Text(
                        text = buildAnnotatedString {
                            if (targetAddress.length > 12) {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)) {
                                    append(targetAddress.take(6))
                                    append(" ")
                                }
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onTertiary)) {
                                    append(targetAddress.substring(6, targetAddress.length - 6))
                                }
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)) {
                                    append(" ")
                                    append(targetAddress.takeLast(6))
                                }
                            } else {
                                append(targetAddress)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily(Font(R.font.inter_regular)),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons Row (Share and Copy)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ReceiveActionButton(
                icon = Icons.Default.Share,
                label = "اشتراک‌گذاری",
                onClick = {
                    val sendIntent: android.content.Intent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, addressItem.address)
                        type = "text/plain"
                    }
                    val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                }
            )
            ReceiveActionButton(
                icon = painterResource(id = R.drawable.ic_wallet),
                label = "کپی آدرس",
                onClick = onCopy
            )
        }
    }
}

@Composable
internal fun SupportedNetworksRow(networkIds: List<String>, iconUrls: List<String>,tintIcon:Color=MaterialTheme.colorScheme.background) {
    val context = LocalContext.current
    val imageLoader = remember { coil.ImageLoader(context) }
    
    Row(
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val displayCount = 5
        val items = networkIds.zip(iconUrls.padWithNulls(networkIds.size)).take(displayCount)
        
        items.forEach { (id, url) ->
            val localIcon = getNetworkIconResId(id)
            Box(
                modifier = Modifier
                    .size(34.dp)
            ) {
                if (localIcon != 0 && localIcon != R.drawable.ic_wallet) {

                    Icon(
                        painter = painterResource(id = R.drawable.ic_pls),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = tintIcon
                    )

                    Image(
                        painter = painterResource(id = localIcon),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(WalletScreenConstants.ASSET_ICON_NETWORK_PADDING),
                        contentScale = ContentScale.Fit,
                        colorFilter = null
                    )
                } else if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        imageLoader = imageLoader
                    )
                }
            }
        }
        if (networkIds.size > displayCount) {
            Box(
                modifier = Modifier
                    .size(34.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+${networkIds.size - displayCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// Helper extension to handle zip with potentially shorter lists
internal fun <T> List<T>.padWithNulls(size: Int): List<T?> {
    return this.map { it as T? } + List(maxOf(0, size - this.size)) { null }
}

@Composable
private fun ReceiveActionButton(
    icon: Any,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            when (icon) {
                is androidx.compose.ui.graphics.vector.ImageVector -> {
                    Icon(
                        imageVector = icon, 
                        contentDescription = label, 
                        tint = MaterialTheme.colorScheme.tertiary, 
                        modifier = Modifier.size(28.dp)
                    )
                }
                is androidx.compose.ui.graphics.painter.Painter -> {
                    Icon(
                        painter = icon, 
                        contentDescription = label, 
                        tint = MaterialTheme.colorScheme.tertiary, 
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
        )
    }
}


@Composable
private fun QRCodeDisplay(
    content: String,
    modifier: Modifier = Modifier,
    symbol: String
) {
    val localIcon = remember(symbol) { getLocalIconResId(symbol) }
    val logoPainter = if (localIcon != 0) painterResource(id = localIcon) else painterResource(id = R.drawable.ic_wallet)
    
    val animationProgress = remember { androidx.compose.animation.core.Animatable(0f) }

    androidx.compose.runtime.LaunchedEffect(content) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF007AFF)) 
            .padding(12.dp), // کاهش پدینگ داخلی برای بزرگتر شدن بارکد
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val size = size.width
            val qrCodeWriter = QRCodeWriter()
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 0)
                // استفاده از بالاترین سطح تصحیح خطا برای امکان قرار دادن لوگو
                put(EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H)
            }
            
            try {
                val bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
                val matrixSize = bitMatrix.width
                val cellSize = size / matrixSize
                val primaryColor = Color.White
                
                for (x in 0 until matrixSize) {
                    for (y in 0 until matrixSize) {
                        if (bitMatrix[x, y]) {
                            
                            val isTopLeft = (x < 7 && y < 7)
                            val isTopRight = (x >= matrixSize - 7 && y < 7)
                            val isBottomLeft = (x < 7 && y >= matrixSize - 7)

                            // ایجاد فضای خالی در مرکز برای لوگو
                            val logoStart = matrixSize / 2 - (matrixSize / 6)
                            val logoEnd = matrixSize / 2 + (matrixSize / 6)
                            val isCenter = (x in logoStart..logoEnd && y in logoStart..logoEnd)

                            if (isTopLeft || isTopRight || isBottomLeft) {
                                val isOrigin = (isTopLeft && x == 0 && y == 0) || 
                                               (isTopRight && x == matrixSize - 7 && y == 0) || 
                                               (isBottomLeft && x == 0 && y == matrixSize - 7)
                                
                                if (isOrigin) {
                                    val cornerOffset = androidx.compose.ui.geometry.Offset(x * cellSize, y * cellSize)
                                    val fullCornerSize = cellSize * 7
                                    
                                    drawRoundRect(
                                        color = primaryColor,
                                        topLeft = cornerOffset + androidx.compose.ui.geometry.Offset(cellSize/2, cellSize/2),
                                        size = androidx.compose.ui.geometry.Size(fullCornerSize - cellSize, fullCornerSize - cellSize),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellSize * 1.5f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = cellSize * 1.0f),
                                        alpha = animationProgress.value
                                    )
                                    
                                    drawRoundRect(
                                        color = primaryColor,
                                        topLeft = cornerOffset + androidx.compose.ui.geometry.Offset(cellSize * 2, cellSize * 2),
                                        size = androidx.compose.ui.geometry.Size(cellSize * 3, cellSize * 3),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellSize * 0.8f),
                                        alpha = animationProgress.value
                                    )
                                }
                            } else if (!isCenter) {
                                val dotDelay = (x.toFloat() + y.toFloat()) / (2f * matrixSize)
                                val dotProgress = ((animationProgress.value * 1.4f) - (dotDelay * 0.4f)).coerceIn(0f, 1f)

                                if (dotProgress > 0f) {
                                    drawRoundRect(
                                        color = primaryColor,
                                        topLeft = androidx.compose.ui.geometry.Offset(x * cellSize + (cellSize * 0.05f), y * cellSize + (cellSize * 0.05f)),
                                        size = androidx.compose.ui.geometry.Size(cellSize * 0.9f * dotProgress, cellSize * 0.9f * dotProgress),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellSize * 0.3f),
                                        alpha = dotProgress
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) { }
        }
        
        // لوگوی وسط همراه با انیمیشن ورود
        if (animationProgress.value > 0.5f) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = logoPainter,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}



@Preview
@Composable
fun PreviewReceiveScreen() {
    MaterialTheme {



        ReceiveScreen({})

    }
}