package com.mtd.megawallet.ui.compose.screens.security

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mtd.common_ui.R
import com.mtd.domain.security.AppLockManager

@Composable
fun LockedFingerprintOverlay(
    visible: Boolean,
    onFingerprintClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .clickable { onFingerprintClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint =MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(42.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "قفل شده",
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.95f),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily =  FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium))
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onFingerprintClick() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "ورود با اثر انگشت",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily =  FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium))
                    )
                }
            }
        }
    }
}

@Composable
fun PasscodeKeypadSheet(
    visible: Boolean,
    title: String,
    subtitle: String,
    errorMessage: String?,
    remainingLockoutSeconds: Int,
    onSubmitPasscode: (String) -> Unit,
    onCancel: (() -> Unit)? = null,
    cancelLabel: String = "بازگشت",
    onExitApp: () -> Unit
) {
    var digits by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(errorMessage, remainingLockoutSeconds, visible) {
        if (!visible) {
            digits = ""
        } else if (!errorMessage.isNullOrBlank() || remainingLockoutSeconds > 0) {
            digits = ""
        }
    }

    LaunchedEffect(digits) {
        if (digits.length == AppLockManager.PASSCODE_LENGTH) {
            onSubmitPasscode(digits)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center,
                    fontFamily =  FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.76f),
                    textAlign = TextAlign.Center,
                    fontFamily =  FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium))
                )

                Spacer(modifier = Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(AppLockManager.PASSCODE_LENGTH) { index ->
                        val filled = index < digits.length
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.tertiary else Color.Transparent
                                )
                                .then(
                                    if (!filled) Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                                    else Modifier
                                )
                        )
                    }
                }

                if (remainingLockoutSeconds > 0) {
                    Spacer(modifier = Modifier.height(15.dp))
                    Text(
                        text = "به دلیل تلاش ناموفق متعدد، ${remainingLockoutSeconds} ثانیه دیگر تلاش کنید",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        fontFamily =  FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium))
                    )
                } else if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        fontFamily =  FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium))
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                val disabled = remainingLockoutSeconds > 0
                KeypadRow(3, 2, 1, disabled) { digit ->
                    if (digits.length < AppLockManager.PASSCODE_LENGTH) digits += digit
                }
                Spacer(modifier = Modifier.height(12.dp))
                KeypadRow(6, 5, 4, disabled) { digit ->
                    if (digits.length < AppLockManager.PASSCODE_LENGTH) digits += digit
                }
                Spacer(modifier = Modifier.height(12.dp))
                KeypadRow(9, 8, 7, disabled) { digit ->
                    if (digits.length < AppLockManager.PASSCODE_LENGTH) digits += digit
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .size(88.dp)
                            .clickable(enabled = !disabled && digits.isNotEmpty(),indication = null, interactionSource = null) {
                                digits = digits.dropLast(1)
                            },
                        shape = CircleShape,
                        color = Color.Transparent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = null,
                                tint = if (!disabled && digits.isNotEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                            )
                        }
                    }
                    KeypadDigit(
                        label = "0",
                        enabled = !disabled,
                        onClick = {
                            if (digits.length < AppLockManager.PASSCODE_LENGTH) digits += "0"
                        }
                    )
                    Spacer(modifier = Modifier.size(88.dp))
                }

                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    onCancel?.let {
                        Text(
                            text = cancelLabel,
                            color =  MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily =  FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium)),
                            modifier = Modifier.clickable { it() }
                        )
                    } ?: Spacer(modifier = Modifier.size(1.dp))
                    Text(
                        text = "خروج از اپ",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily =  FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium)),
                        modifier = Modifier.clickable { onExitApp() }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun KeypadRow(
    first: Int,
    second: Int,
    third: Int,
    disabled: Boolean,
    onDigit: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeypadDigit(label = first.toString(), enabled = !disabled) { onDigit(first.toString()) }
        KeypadDigit(label = second.toString(), enabled = !disabled) { onDigit(second.toString()) }
        KeypadDigit(label = third.toString(), enabled = !disabled) { onDigit(third.toString()) }
    }
}

@Composable
private fun KeypadDigit(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(88.dp)
            .clickable(enabled = enabled, onClick = onClick,indication = null, interactionSource = null),
        shape = CircleShape,
        color =  MaterialTheme.colorScheme.surface
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Preview
@Composable
fun LockedFingerprintOverlayPreview(){
    MaterialTheme(darkColorScheme()) {
        LockedFingerprintOverlay(true,{})
    }
}