package com.mtd.megawallet.ui.compose.screens.history.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun TransactionTimeline(
    modifier: Modifier = Modifier
) {
    val line1Progress = remember { Animatable(0f) }
    val line2Progress = remember { Animatable(0f) }

    var showNode2 by remember { mutableStateOf(false) }
    var showNode3 by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Step 1: Draw the first vertical line from Node 1 to Node 2
        line1Progress.animateTo(1f, animationSpec = tween(300, easing = FastOutSlowInEasing))

        // Step 2: Exactly when the first line finishes, Node 2 appears
        showNode2 = true
        delay(300) // Wait for Node 2's slide/fade animation to complete

        // Step 3: Draw the second vertical line from Node 2 to Node 3
        line2Progress.animateTo(1f, animationSpec = tween(300, easing = FastOutSlowInEasing))

        // Step 4: When the second line finishes, Node 3 appears
        showNode3 = true
    }

    val neonBlue = Color(0xFF2081E2)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF121212)) // Dark theme background
            .padding(24.dp)
    ) {
        // Node 1: Submitted (Always visible initially)
        TimelineNode(
            title = "Submitted",
            timestamp = "Apr 25 2026 - 18:28",
            iconColor = neonBlue,
            isFirst = true,
            isLast = false,
            lineProgress = line1Progress.value,
            visible = true
        )

        // Node 2: Pending (Appears sequentially)
        TimelineNode(
            title = "Pending",
            timestamp = "00:01",
            iconColor = neonBlue,
            isFirst = false,
            isLast = false,
            lineProgress = line2Progress.value,
            visible = showNode2
        )

        // Node 3: Completed (Appears sequentially)
        TimelineNode(
            title = "Completed",
            timestamp = "Apr 25 2026 - 18:28",
            iconColor = neonBlue,
            isFirst = false,
            isLast = true,
            lineProgress = 0f,
            visible = showNode3
        )
    }
}

@Composable
private fun TimelineNode(
    title: String,
    timestamp: String,
    iconColor: Color,
    isFirst: Boolean,
    isLast: Boolean,
    lineProgress: Float,
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300), initialOffsetY = { 20 })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Icon & Connecting Line
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(24.dp)
            ) {
                // Circular Icon
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(iconColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isFirst || isLast) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        // Dot for pending state
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                }

                // Vertical Line
                if (!isLast) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .width(2.dp)
                            .drawBehind {
                                val totalHeight = size.height
                                drawLine(
                                    color = iconColor,
                                    start = Offset(size.width / 2, 0f),
                                    end = Offset(size.width / 2, totalHeight * lineProgress),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = if (isLast) 0.dp else 40.dp) // Spacing between nodes
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timestamp,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
    }
}
