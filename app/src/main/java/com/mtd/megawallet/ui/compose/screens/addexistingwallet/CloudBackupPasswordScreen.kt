package com.mtd.megawallet.ui.compose.screens.addexistingwallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.ui.compose.components.TopHeader

enum class CloudPasswordMode {
    CREATE_NEW_BACKUP,
    APPEND_TO_EXISTING_BACKUP,
    RESTORE_WALLETS_LIST
}

@Composable
fun CloudBackupPasswordScreen(
    onBack: () -> Unit,
    targetColor: Color = MaterialTheme.colorScheme.primary,
    isRecoveryMode: Boolean = true,
    horizontalPadding: androidx.compose.ui.unit.Dp = 24.dp,
    isLoading: Boolean = false,
    mode: CloudPasswordMode? = null,
    errorMessage: String? = null,
    onPasswordSubmit: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    val specialChars = "!@#$%^&*()_+=-[]{}|;':\",.<>/? "
    
    val strength = remember(password) {
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { it in specialChars }
        
        when {
            password.isEmpty() -> 0
            password.length >= 10 && hasLetter && hasDigit && hasSpecial -> 3
            password.length >= 8 && hasLetter && hasDigit -> 2
            else -> 1
        }
    }
    
    val effectiveMode = mode ?: if (isRecoveryMode) {
        CloudPasswordMode.RESTORE_WALLETS_LIST
    } else {
        CloudPasswordMode.CREATE_NEW_BACKUP
    }
    val isExistingPasswordMode = effectiveMode != CloudPasswordMode.CREATE_NEW_BACKUP
    val isPasswordValid = if (isExistingPasswordMode) password.length >= 8 else strength >= 2

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = horizontalPadding)
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        val headerTitle = when (effectiveMode) {
            CloudPasswordMode.CREATE_NEW_BACKUP -> "انتخاب رمز عبور امن"
            CloudPasswordMode.APPEND_TO_EXISTING_BACKUP -> "رمز عبور نسخه پشتیبان را وارد کنید"
            CloudPasswordMode.RESTORE_WALLETS_LIST -> "رمز عبور را وارد کنید"
        }
        val headerSubtitle = when (effectiveMode) {
            CloudPasswordMode.CREATE_NEW_BACKUP ->
                "برای ایمن‌سازی فایل پشتیبان در فضای ابری، یک رمز عبور قوی انتخاب کنید. این رمز برای بازیابی مجدد ضروری است"

            CloudPasswordMode.APPEND_TO_EXISTING_BACKUP ->
                "برای اضافه کردن این کیف پول به نسخه پشتیبان ابری قبلی، رمز عبور فعلی خود را وارد کنید"

            CloudPasswordMode.RESTORE_WALLETS_LIST ->
                "برای مشاهده لیست کیف پول‌های ذخیره شده، رمز عبور نسخه پشتیبان گوگل درایو را وارد کنید"
        }
             
        TopHeader(headerTitle, headerSubtitle)


        Spacer(modifier = Modifier.height(44.dp))


        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (password.isEmpty()) {
                Text(
                    text = when (effectiveMode) {
                        CloudPasswordMode.CREATE_NEW_BACKUP -> "رمز عبور جدید"
                        CloudPasswordMode.APPEND_TO_EXISTING_BACKUP -> "رمز عبور فعلی"
                        CloudPasswordMode.RESTORE_WALLETS_LIST -> "رمز عبور"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.5f),
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Normal)),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right,
                    fontSize = 20.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Right,
                        fontSize = 18.sp,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Normal))
                    ),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (isPasswordValid) onPasswordSubmit(password)
                        }
                    ),
                    cursorBrush = SolidColor(targetColor),
                    singleLine = true
                )

                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle Password Visibility",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFEF4444),
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Normal)),
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (effectiveMode == CloudPasswordMode.CREATE_NEW_BACKUP) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    val strengthLabel = when(strength) {
                        1 -> "ضعیف"
                        2 -> "خوب"
                        3 -> "بسیار عالی"
                        else -> ""
                    }
                    val labelColor = when(strength) {
                        1 -> Color(0xFFEF4444)
                        2 -> Color(0xFFF59E0B)
                        3 -> Color(0xFF22C55E)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    
                    Text(
                        text = strengthLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = labelColor,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.Bold)),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    
                    PasswordStrengthIndicator(strength = strength)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "برای یک رمز قوی، از ۱۰ کاراکتر یا بیشتر به همراه عدد و یک کاراکتر ویژه (!@#...) استفاده کنید",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Normal)),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Right,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        PrimaryButton(
            text = when (effectiveMode) {
                CloudPasswordMode.RESTORE_WALLETS_LIST -> "نمایش کیف پول‌ها"
                else -> "ادامه"
            },
            onClick = { onPasswordSubmit(password) },
            enabled = isPasswordValid,
            isLoading = isLoading,
            containerColor = targetColor
        )
    }
}


@Composable
fun PasswordStrengthIndicator(
    strength: Int
) {
    val barColor = when (strength) {
        1 -> Color(0xFFEF4444)
        2 -> Color(0xFFF59E0B)
        3 -> Color(0xFF22C55E)
        else -> Color.Gray.copy(alpha = 0.3f)
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 8.dp)
                .background(if (strength >= 1) barColor else Color.Gray.copy(alpha = 0.2f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 12.dp)
                .background(if (strength >= 2) barColor else Color.Gray.copy(alpha = 0.2f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 16.dp)
                .background(if (strength >= 3) barColor else Color.Gray.copy(alpha = 0.2f), CircleShape)
        )
    }
}


@Preview
@Composable
fun CloudBackupPreview(){
    MaterialTheme() {
        CloudBackupPasswordScreen(
            onBack = {},
            mode = CloudPasswordMode.CREATE_NEW_BACKUP,
            onPasswordSubmit = {}
        )
    }
}

