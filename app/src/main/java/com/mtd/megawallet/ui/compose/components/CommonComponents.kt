package com.mtd.megawallet.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mtd.common_ui.R

@Composable
fun TopHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            modifier = Modifier.padding(top = 10.dp),
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.tertiary,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.Bold))
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            color = MaterialTheme.colorScheme.onTertiary,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Light))
        )
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
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
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

            if (title != null) {
                TopHeader(
                    title,
                    subtitle = subtitle ?: "",
                    modifier = Modifier
                        .weight(1f)
                )
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
