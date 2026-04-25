package com.mtd.megawallet.ui.compose.screens.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R
import com.mtd.domain.model.TransactionStatus
import com.mtd.megawallet.ui.compose.theme.Green

@Composable
fun TransactionStatusPill(
    status: TransactionStatus,
    isOutgoing: Boolean,
    statusLabel: String,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, contentColor) = when (status) {
        TransactionStatus.PENDING -> Color(0xFFE3F2FD) to MaterialTheme.colorScheme.secondary
        TransactionStatus.CONFIRMED -> Color(0xFFE8F5E9) to Green
        TransactionStatus.FAILED -> Color(0xFFFFEBEE) to MaterialTheme.colorScheme.error
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(contentColor),
            contentAlignment = Alignment.Center
        ) {
            when (status) {
                TransactionStatus.PENDING -> {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                }

                TransactionStatus.CONFIRMED -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                TransactionStatus.FAILED -> {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Text(
            text = statusLabel,
            color = contentColor,
            fontSize = 14.sp,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold))
        )

        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Info",
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}
