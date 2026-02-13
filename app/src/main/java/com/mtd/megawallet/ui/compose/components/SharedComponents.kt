package com.mtd.megawallet.ui.compose.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R
import com.mtd.megawallet.ui.compose.animations.constants.AnimationConstants

/**
 * Primary button component used throughout the app.
 * Provides consistent styling and behavior.
 *
 * @param text Button text
 * @param onClick Action to perform on click
 * @param enabled Whether button is enabled
 * @param modifier Additional modifier
 * @param containerColor Button background color (defaults to primary)
 * @param contentColor Button text color (defaults to onPrimary)
 * @param height Button height (defaults to 56.dp)
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
    height: androidx.compose.ui.unit.Dp = AnimationConstants.BUTTON_HEIGHT
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            disabledContentColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.Bold))
            )
        }
    }
}

/**
 * Secondary button with lighter styling.
 * Used for less prominent actions.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 52.dp
) {
    PrimaryButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        height = height
    )
}

/**
 * Standardized title text style.
 * Used for screen titles and major headings.
 */
@Composable
fun TitleText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 25.sp
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily(Font(R.font.vazirmatn_bold, FontWeight.Normal)),
        modifier = modifier.fillMaxWidth(),
        fontSize = fontSize
    )
}

/**
 * Standardized subtitle text style.
 * Used for descriptions and secondary information.
 */
@Composable
fun SubtitleText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontSize: TextUnit = 14.sp
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Light)),
        color = color,
        modifier = modifier.fillMaxWidth(),
        fontSize = fontSize
    )
}

/**
 * Standardized body text style.
 * Used for regular content text.
 */
@Composable
fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 14.sp
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Normal)),
        color = color,
        modifier = modifier,
        fontSize = fontSize
    )
}

