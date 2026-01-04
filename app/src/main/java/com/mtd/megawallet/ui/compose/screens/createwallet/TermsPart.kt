package com.mtd.megawallet.ui.compose.screens.createwallet

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.megawallet.ui.compose.animations.constants.AnimationConstants
import com.mtd.megawallet.viewmodel.news.CreateWalletViewModel

/**
 * Terms and conditions data.
 */
private val TERMS = listOf(
    "من می‌پذیرم که مسئولیت کامل امنیت و تهیه نسخه پشتیبان از کیف پول‌هایم بر عهده اینجانب است، نه مگاولت",
    "من می‌پذیرم که استفاده از مگاولت برای هرگونه اهداف غیرقانونی اکیداً ممنوع و خلاف شرایط و ضوابط ما است",
    "من می‌پذیرم که مگاولت یک بانک، صرافی یا موسسه مالی متمرکز نیست",
    "من می‌پذیرم که اگر در هر زمانی دسترسی به کیف پول‌های خود را از دست بدهم، مگاولت هیچ مسئولیتی نداشته و به هیچ وجه قادر به کمک نیست"
)

/**
 * Component for terms acceptance step in create wallet flow.
 */
@Composable
fun TermsPart(
    viewModel: CreateWalletViewModel,
    modifier: Modifier = Modifier
) {
    val animatedButtonColor by animateColorAsState(
        targetValue = viewModel.selectedColor,
        animationSpec = tween(
            durationMillis = AnimationConstants.GENERATING_ANIMATION_DURATION
        ),
        label = "ButtonColorAnimation"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(top = 40.dp)
    ) {
        // Header
        Text(
            text = "قوانین امنیتی را تایید کنید",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily(Font(com.mtd.common_ui.R.font.vazirmatn_bold, FontWeight.Normal)),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Right,
            fontSize = 25.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "برای ادامه، باید موارد زیر را مطالعه کرده و تایید نمایید",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily(Font(com.mtd.common_ui.R.font.vazirmatn_medium, FontWeight.Light)),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Right,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Terms list
        Column(modifier = Modifier.weight(1f)) {
            TERMS.forEachIndexed { index, termText ->
                TermItem(
                    text = termText,
                    isSelected = when (index) {
                        0 -> viewModel.term1Accepted
                        1 -> viewModel.term2Accepted
                        2 -> viewModel.term3Accepted
                        3 -> viewModel.term4Accepted
                        else -> false
                    },
                    color = viewModel.selectedColor,
                    onToggle = {
                        when (index) {
                            0 -> viewModel.term1Accepted = !viewModel.term1Accepted
                            1 -> viewModel.term2Accepted = !viewModel.term2Accepted
                            2 -> viewModel.term3Accepted = !viewModel.term3Accepted
                            3 -> viewModel.term4Accepted = !viewModel.term4Accepted
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Footer text
        Text(
            text = "لطفاً با دقت تمام موارد را بررسی کنید، این موارد برای امنیت دارایی شما حیاتی هستند",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontFamily = FontFamily(Font(com.mtd.common_ui.R.font.vazirmatn_medium, FontWeight.Light)),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Right,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Continue button
        Button(
            onClick = { viewModel.nextStep() },
            enabled = viewModel.areTermsAccepted,
            modifier = Modifier
                .fillMaxWidth()
                .height(AnimationConstants.BUTTON_HEIGHT),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = animatedButtonColor,
            )
        ) {
            Text(
                text = "موارد فوق را قبول دارم، ادامه",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily(Font(com.mtd.common_ui.R.font.vazirmatn_medium, FontWeight.Bold))
            )
        }

    }
}

@Composable
private fun TermItem(
    text: String,
    isSelected: Boolean,
    color: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                onClick = onToggle,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily(Font(com.mtd.common_ui.R.font.vazirmatn_regular, FontWeight.Normal)),
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            textAlign = TextAlign.Right,
            fontSize = 15.sp
        )

        // Custom animated checkbox
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(
                    width = 2.dp,
                    color = if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)
                )
                .background(if (isSelected) color else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
