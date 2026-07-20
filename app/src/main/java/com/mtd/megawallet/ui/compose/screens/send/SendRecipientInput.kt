package com.mtd.megawallet.ui.compose.screens.send

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R
import com.mtd.common_ui.theme.InterRegular
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.IranSansRegularMedium
import com.mtd.common_ui.theme.MegaWalletTheme

/**
 * فیلد ورودی آدرس مقصد؛ در حالت آدرس معتبر به شکل «چیپ» نمایش داده می‌شود،
 * در غیر این صورت TextField با دکمهٔ جایگذاری/پاک‌کردن.
 */
@Composable
internal fun RecipientInputSection(
    recipientText: String,
    isValidAddress: Boolean,
    onRecipientChanged: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    readOnly: Boolean = false
) {
    val hasInput = recipientText.isNotBlank()
    val showInvalidAddressError = recipientText.isNotBlank() && !isValidAddress

    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "ارسال به",
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontFamily = IranSansRegular,
                        fontSize = 14.sp
                    )
                }



                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (isValidAddress && hasInput) {
                        // Address Chip/Pill mode
                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .widthIn(max = 230.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val displayAddress = if (recipientText.length > 12) {
                                "${recipientText.take(6)}...${recipientText.takeLast(6)}"
                            } else {
                                recipientText
                            }
                            Text(
                                text = displayAddress,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontFamily = InterRegular,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.widthIn(max = 180.dp)
                            )
                        }
                    } else {
                        BasicTextField(
                            value = recipientText,
                            onValueChange = onRecipientChanged,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                color = if (showInvalidAddressError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                fontSize = 15.sp,
                                fontFamily = InterRegular,
                                textAlign = TextAlign.Right
                            ),
                            decorationBox = { inner ->
                                if (recipientText.isBlank()) {
                                    Text(
                                        text = "آدرس مقصد را وارد کنید",
                                        color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.5f),
                                        fontFamily = IranSansRegular,
                                        fontSize = 15.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                inner()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Crossfade(
                    targetState = hasInput,
                    animationSpec = tween(durationMillis = 220),
                    label = "RecipientAction"
                ) { showClear ->
                    if (readOnly) {
                        // Empty box to keep layout consistent but hide buttons
                        Box(modifier = Modifier.width(34.dp))
                    } else if (showClear) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = onClear),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = onPaste)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "جایگذاری",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontFamily = IranSansRegular,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            if (showInvalidAddressError) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "آدرس وارد شده معتبر نیست",
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = IranSansRegular,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun ScanAddressRow(onScanClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onScanClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_scan),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = "QR اسکن کد",
                color = MaterialTheme.colorScheme.tertiary,
                fontFamily = IranSansRegularMedium,
                fontSize = 17.sp,
                textAlign = TextAlign.Right
            )
            Text(
                text = "برای اسکن آدرس ضربه بزنید",
                color = MaterialTheme.colorScheme.onTertiary,
                fontFamily = IranSansRegular,
                fontSize = 14.sp,
                textAlign = TextAlign.Right
            )
        }
    }
}

@Composable
internal fun HintState(
    text: String,
    isError: Boolean = false
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onTertiary,
            fontFamily = IranSansRegular,
            fontSize = 14.sp
        )
    }
}

// ============================================
// Previews
// ============================================

@Preview(name = "RecipientInput empty - Light")
@Composable
private fun RecipientInputEmptyLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                RecipientInputSection(
                    recipientText = "",
                    isValidAddress = false,
                    onRecipientChanged = {},
                    onPaste = {},
                    onClear = {}
                )
            }
        }
    }
}

@Preview(name = "RecipientInput valid - Dark")
@Composable
private fun RecipientInputValidDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                RecipientInputSection(
                    recipientText = "0x1234567890abcdef1234567890abcdef12345678",
                    isValidAddress = true,
                    onRecipientChanged = {},
                    onPaste = {},
                    onClear = {}
                )
            }
        }
    }
}

@Preview(name = "ScanAddressRow - Light")
@Composable
private fun ScanAddressRowLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) { ScanAddressRow(onScanClick = {}) }
        }
    }
}

@Preview(name = "HintState error - Dark")
@Composable
private fun HintStateDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.size(240.dp)) {
                HintState(text = "آدرس وارد شده معتبر نیست", isError = true)
            }
        }
    }
}
