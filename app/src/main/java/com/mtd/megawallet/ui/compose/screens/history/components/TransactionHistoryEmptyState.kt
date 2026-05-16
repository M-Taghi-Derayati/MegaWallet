package com.mtd.megawallet.ui.compose.screens.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R

@Composable
fun TransactionHistoryEmptyState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-8).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EmptyActionIcon(
                        iconRes = R.drawable.ic_send,
                        background = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    EmptyActionIcon(
                        iconRes = R.drawable.ic_download,
                        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "No transactions yet",
                fontSize = 18.sp,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.Bold)),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your deposits, withdrawals, and pending transfers will appear here.",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                color = MaterialTheme.colorScheme.onTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyActionIcon(
    iconRes: Int,
    background: Color,
    tint: Color
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = tint
        )
    }
}
