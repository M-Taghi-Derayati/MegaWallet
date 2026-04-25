package com.mtd.megawallet.ui.compose.screens.history.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.mtd.common_ui.R
import com.mtd.domain.model.TransactionRecord
import com.mtd.megawallet.viewmodel.history.TransactionHistoryViewModel

@Composable
fun TransactionDetailsBottomSheet(
    visible: Boolean,
    transaction: TransactionRecord?,
    viewModel: TransactionHistoryViewModel,
    onDismiss: () -> Unit
) {
    // نگهداری آخرین تراکنش برای جلوگیری از پرش تصویر در هنگام بسته شدن (Exit Animation)
    val memoizedTransaction = remember(transaction) {
        if (transaction != null) transaction else null
    }
    
    // استفاده از یک State محلی که فقط وقتی تراکنش جدید میاد آپدیت میشه
    var activeTransaction by remember { mutableStateOf<TransactionRecord?>(null) }
    LaunchedEffect(transaction) {
        if (transaction != null) {
            activeTransaction = transaction
        }
    }

    androidx.activity.compose.BackHandler(enabled = visible) {
        onDismiss()
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            // استفاده از activeTransaction به جای transaction مستقیم
            val currentTx = activeTransaction
            if (currentTx != null) {
                val explorerUrl = remember(currentTx.hash, currentTx.networkName) {
                    viewModel.buildExplorerUrl(currentTx)
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 40.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            if (isSystemInDarkTheme()) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.background
                            }
                        )
                        .clickable(enabled = false) {}
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (transaction!!.isOutgoing) "جزئیات برداشت" else "جزئیات واریز",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium))
                        )

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = viewModel.formatTransactionFiat(transaction!!) ?: "مقدار دلاری نامشخص",
                            fontSize = 32.sp,
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = viewModel.formatTransactionAmount(transaction),
                            fontSize = 16.sp,
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                            color = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        TransactionStatusPill(
                            status = transaction!!.status,
                            isOutgoing = transaction.isOutgoing,
                            statusLabel = viewModel.getTransactionStatusLabel(transaction.status)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        TransactionTimeline(
                            status = transaction.status,
                            submittedTime = viewModel.formatTimelineSubmitted(transaction),
                            progressText = viewModel.formatPendingDuration(transaction),
                            completedTime = viewModel.formatTimelineCompleted(transaction)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        TransactionGeneralDetailsCard(
                            transaction = transaction,
                            viewModel = viewModel
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (explorerUrl == null) {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                )
                                .clickable(enabled = explorerUrl != null) {
                                    //explorerUrl?.let(uriHandler::openUri)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (explorerUrl == null) {
                                    "مرورگر شبکه برای این تراکنش در دسترس نیست"
                                } else {
                                    "مشاهده در مرورگر شبکه"
                                },
                                color = if (explorerUrl == null) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                fontSize = 16.sp,
                                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold))
                            )
                        }
                    }
                }
            }
        }
    }
}
