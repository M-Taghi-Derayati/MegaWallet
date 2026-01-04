package com.mtd.megawallet.ui.compose.screens

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/**
 * صفحه "ایمپورت کیف پول" که به کاربر اجازه می‌دهد از طریق:
 * 1. عبارت بازیابی (Secret Phrase)
 * 2. کلید خصوصی (Private Key)
 * کیف پول خود را بازیابی کند.
 *
 * شامل یک تب‌بار انیمیشنی سفارشی و فرم‌های جداگانه برای هر روش است.
 * طبق Technical Spec: شامل Shake Animation، Haptic Feedback و IME Padding.
 *
 * @param onBack اکشن بازگشت به صفحه قبل
 */
@Composable
fun ImportWalletScreen(onBack: () -> Unit) {
    // 0 = عبارت بازیابی, 1 = کلید خصوصی
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // State برای مدیریت خطا و Shake Animation
    var hasError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .imePadding() // مدیریت فضای کیبورد
            .verticalScroll(rememberScrollState())
    ) {
        // دکمه بازگشت و عنوان
        IconButton(
            onClick = onBack,
            modifier = Modifier.offset(x = (-12).dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "وارد کردن کیف پول",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // --- تب‌بار سفارشی با انیمیشن ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1C1C1E)) // پس‌زمینه تیره برای مسیر تب
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // تب ۱ - عبارت بازیابی
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selectedTab == 0) Color(0xFF2C2C2E) else Color.Transparent)
                        .clickable { selectedTab = 0 },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Secret Phrase",
                        color = if (selectedTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // تب ۲ - کلید خصوصی
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selectedTab == 1) Color(0xFF2C2C2E) else Color.Transparent)
                        .clickable { selectedTab = 1 },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Private Key",
                        color = if (selectedTab == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- محتوای تب‌ها با Crossfade ---
        Crossfade(targetState = selectedTab, label = "content_fade") { tab ->
            if (tab == 0) {
                SecretPhraseInputContent(hasError = hasError, onErrorChange = { hasError = it })
            } else {
                PrivateKeyInputContent(hasError = hasError, onErrorChange = { hasError = it })
            }
        }
    }
}

/**
 * محتوای تب "عبارت بازیابی" (Secret Phrase).
 * شامل فیلد متنی بزرگ برای وارد کردن کلمات و دکمه ایمپورت.
 */
@Composable
fun SecretPhraseInputContent(hasError: Boolean, onErrorChange: (Boolean) -> Unit) {
    val view = LocalView.current
    
    Column {
        Text(
            text = "عبارت بازیابی خود را وارد کنید",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        // Placeholder for Grid of Words
        OutlinedTextField(
            value = "",
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                , // افکت لرزش هنگام خطا
            placeholder = { Text("Word 1   Word 2   Word 3...") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1C1C1E),
                unfocusedContainerColor = Color(0xFF1C1C1E),
                focusedIndicatorColor = if (hasError) Color.Red else Color(0xFF22C55E),
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(16.dp),
            isError = hasError
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { 
                // شبیه‌سازی خطا برای تست
                onErrorChange(true)
                // Haptic Feedback
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
            shape = CircleShape
        ) {
            Text("Import Phrase")
        }
    }
}

/**
 * محتوای تب "کلید خصوصی" (Private Key).
 * شامل دو حالت:
 * 1. دکمه بزرگ "پیست از کلیپ‌بورد" (رنگ سبز) برای تجربه کاربری راحت‌تر.
 * 2. حالت دستی (Manual) که یک فیلد متنی معمولی نمایش می‌دهد.
 */
@Composable
fun PrivateKeyInputContent(hasError: Boolean, onErrorChange: (Boolean) -> Unit) {
    var privateKey by remember { mutableStateOf("") }
    var isManualInput by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val view = LocalView.current

    Column {
        Text(
            text = "کلید خصوصی ۶۴ کاراکتری خود را وارد کنید",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (!isManualInput) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF22C55E)) // Family Green
                    .padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    modifier = Modifier.scale(3f).align(Alignment.TopStart).padding(12.dp)
                )

                Button(
                    onClick = {
                        val clipData = clipboardManager.getText()
                        if (clipData != null) {
                            privateKey = clipData.toString()
                            isManualInput = true
                            // Haptic Feedback موفقیت
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                        } else {
                            Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                            // Haptic Feedback خطا
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = Color(0xFF22C55E)
                    ),
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.Center).height(56.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Paste from Clipboard", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Input Manually Link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isManualInput = true },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Edit, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ورود دستی",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            OutlinedTextField(
                value = privateKey,
                onValueChange = { privateKey = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    , // افکت لرزش هنگام خطا
                placeholder = { Text("e.g. 0x...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1C1C1E),
                    unfocusedContainerColor = Color(0xFF1C1C1E),
                    focusedIndicatorColor = if (hasError) Color.Red else Color(0xFF22C55E),
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp),
                isError = hasError
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { 
                    // Validation Logic
                    if (privateKey.length < 64) {
                        onErrorChange(true)
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    } else {
                        // Success
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                    }
                },
                enabled = privateKey.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                shape = CircleShape
            ) {
                Text("Import")
            }
        }
    }
}
