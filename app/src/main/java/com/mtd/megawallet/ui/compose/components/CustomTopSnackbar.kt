package com.mtd.megawallet.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * TASK-57 — the two flavours of top snackbar.
 *
 * [ERROR] is tappable and sticky (it may carry a "جزئیات" affordance); [SUCCESS] is a green
 * confirmation that auto-dismisses and never carries technical detail.
 */
enum class TopSnackbarStyle { ERROR, SUCCESS }

/**
 * Snackbar کاستوم که در بالای صفحه نمایش داده می‌شود
 * در حالت خطا قابل کلیک است و تا زمانی که کاربر روی آن کلیک نکند، باقی می‌ماند
 */
@Composable
fun CustomTopSnackbar(
    message: String,
    isVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TopSnackbarStyle = TopSnackbarStyle.ERROR,
    showDetailsAffordance: Boolean = false
) {
    val containerColor = when (style) {
        TopSnackbarStyle.ERROR -> MaterialTheme.colorScheme.errorContainer
        TopSnackbarStyle.SUCCESS -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when (style) {
        TopSnackbarStyle.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        TopSnackbarStyle.SUCCESS -> MaterialTheme.colorScheme.onPrimary
    }
    val icon = when (style) {
        TopSnackbarStyle.ERROR -> Icons.Default.Error
        TopSnackbarStyle.SUCCESS -> Icons.Default.CheckCircle
    }
    // TASK-17 — the snackbar is the only carrier of this information, so name it for TalkBack.
    val iconDescription = when (style) {
        TopSnackbarStyle.ERROR -> "خطا"
        TopSnackbarStyle.SUCCESS -> "انجام شد"
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(durationMillis = 300)
        ) + fadeIn(animationSpec = tween(durationMillis = 300)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(durationMillis = 250)
        ) + fadeOut(animationSpec = tween(durationMillis = 250)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 30.dp, vertical = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconDescription,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Only offered when there is technical detail to open — an inert "جزئیات" that
                // opens an empty dialog was the previous behaviour.
                if (showDetailsAffordance) {
                    Text(
                        text = "جزئیات",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}


@Preview
@Composable
fun CustomTopSnackbarErrorPreview() {
    MaterialTheme {
        CustomTopSnackbar(
            message = "ارسال تراکنش ناموفق بود. لطفاً دوباره تلاش کنید.",
            isVisible = true,
            onClick = {},
            style = TopSnackbarStyle.ERROR,
            showDetailsAffordance = true
        )
    }
}

@Preview
@Composable
fun CustomTopSnackbarSuccessPreview() {
    MaterialTheme {
        CustomTopSnackbar(
            message = "کپی شد",
            isVisible = true,
            onClick = {},
            style = TopSnackbarStyle.SUCCESS
        )
    }
}
