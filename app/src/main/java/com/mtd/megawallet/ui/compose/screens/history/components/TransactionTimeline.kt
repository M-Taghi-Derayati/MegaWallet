package com.mtd.megawallet.ui.compose.screens.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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

@Composable
fun TransactionTimeline(
    status: TransactionStatus,
    submittedTime: String,
    progressText: String,
    completedTime: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(16.dp)
    ) {
        TimelineRow(
            state = StepState.COMPLETED,
            title = "ثبت شد",
            rightText = submittedTime,
            isLast = false,
            isLineActive = status != TransactionStatus.PENDING
        )

        TimelineRow(
            state = when (status) {
                TransactionStatus.PENDING -> StepState.CURRENT
                TransactionStatus.CONFIRMED -> StepState.COMPLETED
                TransactionStatus.FAILED -> StepState.COMPLETED
            },
            title = when (status) {
                TransactionStatus.PENDING -> "در حال بررسی"
                TransactionStatus.CONFIRMED -> "تأیید شبکه"
                TransactionStatus.FAILED -> "بررسی ناموفق"
            },
            rightText = progressText,
            isLast = false,
            isLineActive = status != TransactionStatus.PENDING
        )

        TimelineRow(
            state = when (status) {
                TransactionStatus.PENDING -> StepState.INACTIVE
                TransactionStatus.CONFIRMED -> StepState.COMPLETED
                TransactionStatus.FAILED -> StepState.FAILED
            },
            title = when (status) {
                TransactionStatus.PENDING -> "تکمیل"
                TransactionStatus.CONFIRMED -> "تکمیل شد"
                TransactionStatus.FAILED -> "ناموفق"
            },
            rightText = completedTime ?: if (status == TransactionStatus.FAILED) "بدون تایید" else "- -",
            isLast = true,
            isLineActive = false
        )
    }
}

private enum class StepState {
    COMPLETED,
    CURRENT,
    INACTIVE,
    FAILED
}

@Composable
private fun TimelineRow(
    state: StepState,
    title: String,
    rightText: String,
    isLast: Boolean,
    isLineActive: Boolean
) {
    val activeColor = MaterialTheme.colorScheme.secondary
    val inactiveColor = Color(0xFFBDBDBD)
    val inactiveBorderColor = Color(0xFFE0E0E0)
    val failedColor = MaterialTheme.colorScheme.error

    val tint = when (state) {
        StepState.COMPLETED, StepState.CURRENT -> activeColor
        StepState.FAILED -> failedColor
        StepState.INACTIVE -> inactiveColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        when (state) {
                            StepState.COMPLETED, StepState.CURRENT -> tint
                            StepState.FAILED -> failedColor.copy(alpha = 0.15f)
                            StepState.INACTIVE -> Color.Transparent
                        }
                    )
                    .border(
                        width = if (state == StepState.INACTIVE) 2.dp else 0.dp,
                        color = if (state == StepState.INACTIVE) inactiveBorderColor else Color.Transparent,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (state) {
                    StepState.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }

                    StepState.CURRENT -> {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }

                    StepState.FAILED -> {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = failedColor,
                            modifier = Modifier.size(10.dp)
                        )
                    }

                    StepState.INACTIVE -> Unit
                }
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .background(if (isLineActive) tint else inactiveBorderColor)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = tint,
                    fontSize = 14.sp,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold))
                )

                Text(
                    text = rightText,
                    color = if (state == StepState.INACTIVE) inactiveColor else tint,
                    fontSize = 13.sp,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
                )
            }
        }
    }
}
