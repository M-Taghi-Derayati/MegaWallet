package com.mtd.megawallet.ui.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mtd.common_ui.R

@Composable
fun TopHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    titleTextAlign: TextAlign = TextAlign.Start,
    subtitleTextAlign: TextAlign = TextAlign.Start,
    titleTopPadding: Dp = 10.dp
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = horizontalAlignment
    ) {
        Text(
            modifier = Modifier.padding(top = titleTopPadding),
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = titleTextAlign,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.Bold))
        )
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = subtitleTextAlign,
                color = MaterialTheme.colorScheme.onTertiary,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Light))
            )
        }
    }
}

@Composable
fun UnifiedHeader(
    onBack: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    isClose: Boolean? = false,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (isClose != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)


                ) {
                    Icon(
                        imageVector = if (isClose) Icons.Default.Close else Icons.Default.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            if (title != null) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    TopHeader(
                        title = title,
                        subtitle = subtitle ?: "",
                        horizontalAlignment = Alignment.End,
                        titleTextAlign = TextAlign.End,
                        subtitleTextAlign = TextAlign.End,
                        titleTopPadding = 0.dp
                    )
                }
            }
        }
    }
}

@Composable
fun BottomSecuritySection(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = "Security Shield",
            tint = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .size(22.dp)
                .align(Alignment.TopCenter)
                .zIndex(5f)
        )
        Box(
            modifier = Modifier
                .zIndex(3f)
                .padding(12.dp)
                .threeSidedDashedGradientBorder(
                    color = MaterialTheme.colorScheme.surface,
                    cornerRadius = 24.dp,
                    strokeWidth = 1.5.dp
                )
                .height(70.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onTertiary.copy(0.9f),
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Light)),
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
            )
        }
    }
}
