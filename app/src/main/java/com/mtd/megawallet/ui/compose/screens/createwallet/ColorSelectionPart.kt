package com.mtd.megawallet.ui.compose.screens.createwallet

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.viewmodel.news.CreateWalletViewModel

/**
 * Available wallet colors for selection.
 */
private val WALLET_COLORS = listOf(
    Color(0xFF22C55E), Color(0xFF3B82F6), Color(0xFFEF4444), Color(0xFFF59E0B),
    Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF06B6D4), Color(0xFF84CC16),
    Color(0xFF6366F1), Color(0xFFF97316), Color(0xFF14B8A6), Color(0xFFD946EF),
    Color(0xFFF43F5E), Color(0xFF0EA5E9), Color(0xFF8231E1), Color(0xFF10B981),
    Color(0xFFFB923C), Color(0xFF38BDF8), Color(0xFFC084FC), Color(0xFF94A3B8)
)

/**
 * Component for wallet color selection step in create wallet flow.
 */
@Composable
fun ColorSelectionPart(
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
            text = "نمای کیف پول خود را انتخاب کنید",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily(Font(com.mtd.common_ui.R.font.vazirmatn_bold, FontWeight.Normal)),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Right,
            fontSize = 25.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "رنگی را انتخاب کنید که برای شناسایی این کیف پول استفاده می شود.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily(Font(com.mtd.common_ui.R.font.vazirmatn_medium, FontWeight.Light)),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Right,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        // Color grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = WALLET_COLORS,
                key = { color -> color.hashCode() }
            ) { color ->
                ColorItem(
                    color = color,
                    isSelected = viewModel.selectedColor == color,
                    onClick = { viewModel.selectedColor = color }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1.3f))

        // Footer text
        Text(
            text = "این رنگ در کارت ها، نمودارها و برخی دکمه ها نمایش داده می شود.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontFamily = FontFamily(Font(com.mtd.common_ui.R.font.vazirmatn_medium, FontWeight.Light)),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Right,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Continue button
        PrimaryButton(
            text = "ادامه",
            onClick = { viewModel.nextStep() },
            containerColor = animatedButtonColor,
            height = AnimationConstants.BUTTON_HEIGHT
        )

    }
}

@Composable
private fun ColorItem(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 3.5.dp else 0.dp,
                color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
             Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}
