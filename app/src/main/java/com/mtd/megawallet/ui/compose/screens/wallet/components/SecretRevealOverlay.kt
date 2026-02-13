package com.mtd.megawallet.ui.compose.screens.wallet.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mtd.common_ui.R
import com.mtd.megawallet.ui.compose.animations.constants.MainScreenConstants
import com.mtd.megawallet.ui.compose.components.BottomSecuritySection
import com.mtd.megawallet.ui.compose.components.InputManualSection
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.ui.compose.components.UnifiedHeader
import com.mtd.megawallet.ui.compose.screens.wallet.ManualBackupVerifier

@Composable
fun SecretRevealOverlay(
    visible: Boolean,
    isMnemonic: Boolean,
    walletColor: Int ,
    methodType: String,
    isManualBackedUp: Boolean = false,
    isCloudBackedUp: Boolean = false,
    isVerifyingBackup: Boolean = false,
    isBackupSuccess: Boolean = false,
    isCloudActionLoading: Boolean = false,
    mnemonic: String = "",
    onStartVerification: () -> Unit = {},
    onStartCloudBackup: () -> Unit = {},
    onBackupConfirmed: () -> Unit = {},
    onClose: () -> Unit
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600)),
        exit = fadeOut(tween(600)),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(5000f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 20.dp) // Removed padding here to handle it inside states
        ) {
            
            AnimatedContent(
                targetState = isVerifyingBackup,
                transitionSpec = {
                    if (targetState) {
                        slideInHorizontally(animationSpec = tween(800)) { width -> width } + fadeIn(tween(800)) togetherWith
                                slideOutHorizontally(animationSpec = tween(800)) { width -> -width } + fadeOut(tween(800))
                    } else {
                        slideInHorizontally(animationSpec = tween(800)) { width -> -width } + fadeIn(tween(800)) togetherWith
                                slideOutHorizontally(animationSpec = tween(800)) { width -> width } + fadeOut(tween(800))
                    }.using(SizeTransform(clip = false))
                },
                label = "VerifyTransition"
            ) { isVerifying ->
                if (isVerifying) {
                     Column(modifier = Modifier
                         .fillMaxSize()
                         .statusBarsPadding()
                     ) {
                         ManualBackupVerifier(
                             mnemonic = mnemonic,
                             onBackupConfirmed = onBackupConfirmed
                         )
                     }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // متن‌های بالا
                        val titleText = if (isBackupSuccess) "تبریک!" else if (methodType == "cloud") "پشتیبان‌گیری ابری" else "پشتیبان‌گیری دستی"
                        val description = if (isBackupSuccess) {
                            "کیف پول شما با موفقیت پشتیبان‌گیری شد."
                        } else if (methodType == "cloud") {
                            if (!isMnemonic) {
                                "نسخه‌ی رمزگذاری شده‌ای از کلید خصوصی خود را در گوگل درایو ذخیره کنید"
                            } else {
                                "نسخه‌ی رمزگذاری شده‌ای از عبارت بازیابی مخفی خود را در گوگل درایو ذخیره کنید"
                            }
                        } else {
                            if (!isMnemonic) {
                                "کلید خصوصی خود را در محلی امن و تحت کنترلِ مستقیم خودتان ذخیره کنید"
                            } else {
                                "کلمات بازیابی خود را در محلی امن و تحت کنترلِ مستقیم خودتان ذخیره کنید"
                            }
                        }

                        UnifiedHeader(
                            onClose,
                            titleText,
                            description,
                            null,
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp, start = 20.dp, end = 20.dp)
                        )

                        if (!isBackupSuccess) {
                            InputManualSection(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 200.dp),
                                onClick = {
                                    if (mnemonic.isNotBlank()) {
                                        clipboardManager.setText(AnnotatedString(mnemonic))
                                    }
                                },
                                text = "کپی",
                                icon = Icons.Default.ContentCopy
                            )
                        }

                        val nextLevelDescription = if (isBackupSuccess) {
                            "اکنون می‌توانید با خیال راحت از کیف پول خود استفاده کنید."
                        } else if (methodType == "cloud") {
                            if (!isMnemonic) {
                                "نسخه ی رمزگذاری شده ای از کلید خصوصی خود را در گوگل درایو ذخیره کنید"
                            } else {
                                "نسخه ی رمزگذاری شده ای از عبارت بازیابی مخفی خود را در گوگل درایو ذخیره کنید"
                            }
                        } else {
                            if (!isMnemonic) {
                                "در مرحله بعد، از شما خواسته میشود تأیید کنید که از اهمیتِ نگهداری امنِ کلید خصوصی  خود آگاه هستید"
                            } else {
                                "در مرحله بعد، از شما خواسته میشود که جایگاه کلمات مشخصی را در عبارت بازیابی محرمانه خود تأیید کنید"
                            }
                        }

                        // دکمه اکشن نهایی
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            BottomSecuritySection(message = nextLevelDescription)
                            
                            val buttonText = when {
                                isBackupSuccess -> "پایان"
                                methodType == "cloud" && !isCloudBackedUp -> "تایید پشتیبان‌گیری ابری"
                                methodType == "manual" && !isManualBackedUp -> if(isMnemonic) "شروع تایید (۴ کلمه)" else "تایید ذخیره‌سازی"
                                else -> "متوجه شدم"
                            }
                             
                            val onButtonClick = when {
                                isBackupSuccess -> onClose
                                methodType == "cloud" && !isCloudBackedUp -> onStartCloudBackup
                                methodType == "manual" && !isManualBackedUp -> if(isMnemonic) onStartVerification else onBackupConfirmed
                                else -> onClose
                            }

                            PrimaryButton(
                                text = buttonText,
                                onClick = onButtonClick,
                                isLoading = methodType == "cloud" && !isBackupSuccess && isCloudActionLoading,
                                containerColor = Color(walletColor),
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                }
            }
        
        } // End of Box
    }
}


@Composable
fun SecretRecoveryPromptBottomSheet(
    visible: Boolean,
    isMnemonic: Boolean,
    onDismiss: () -> Unit,
    onReveal: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(9999f) // اطمینان از قرارگیری روی تمام لایه‌ها
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
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(300)
                    ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 40.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MainScreenConstants.FAB_CORNER_RADIUS_EXPANDED))
                    .background(if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
                    .clickable(enabled = false) {} // برای جلوگیری از کلیک روی لایه پشت
                    .padding(20.dp)
            ) {
                val titleText = if (isMnemonic) "عبارت بازیابی" else "کلید خصوصی"
                val descriptionText = if (isMnemonic)
                    "عبارت بازیابی، کلید اصلی کیف پول شماست. همیشه آن را مخفی و در جایی امن حفظ کنید"
                else
                    "کلید خصوصی شما تنها راه دسترسی و پشتیبان گیری از دارایی شماست. در هر لحظه از آن به شدت محافظت کنید"

                // Header Icon and Close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold))
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }


                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = descriptionText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Points
                SecurityPoint(
                    icon = Icons.Filled.Shield,
                    text = if (isMnemonic) "عبارت مخفی خود را در جای امن نگه دارید" else "کلید خود را در جای امن نگه دارید"
                )
                Spacer(modifier = Modifier.height(16.dp))
                SecurityPoint(
                    icon = Icons.Filled.Assignment,
                    text = "آن را با هیچ کس به اشتراک نگذارید"
                )
                Spacer(modifier = Modifier.height(16.dp))
                SecurityPoint(
                    icon = Icons.Default.Block,
                    text = "در صورت گم شدن، ما (یا هیچ کس دیگر) قادر به بازیابی آن نیستیم"
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    PrimaryButton(
                        text = "نمایش",
                        onClick = onReveal,
                        containerColor = Color(0xFF03A9F4),
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    PrimaryButton(
                        text = "لغو",
                        onClick = onDismiss,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityPoint(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onTertiary,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
        )
    }
}
