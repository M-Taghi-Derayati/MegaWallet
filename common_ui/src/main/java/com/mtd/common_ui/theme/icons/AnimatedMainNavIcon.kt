package com.mtd.common_ui.theme.icons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun stagedProgress(progress: Float, start: Float, end: Float): Float {
    if (end <= start) return progress.coerceIn(0f, 1f)
    return ((progress - start) / (end - start)).coerceIn(0f, 1f)
}

private fun stagedSlide(stage: Float): Float = 40f * (1f - stage)


@Composable
fun AnimatedWalletIcon(
    modifier: Modifier = Modifier,
    isFilled: Boolean,
    outlineColor: Color = Color(0xFF949494),
    mainFillColor: Color = MaterialTheme.colorScheme.tertiary,
    accentFillColor: Color = MaterialTheme.colorScheme.background,
    iconSize: Dp = 64.dp,
    onClick:()->Unit
) {
    // ── 1. EXACT PATH DATA FROM XML ─────────────────────────────
    val mainOutlinePath = remember {
        Path().apply {
            moveTo(275.93f, 89.92f); lineTo(362.32f, 89.92f)
            cubicTo(413.31f, 89.92f, 453.71f, 89.92f, 485.32f, 94.15f)
            cubicTo(517.85f, 98.51f, 544.19f, 107.7f, 564.96f, 128.41f)
            cubicTo(590.61f, 153.98f, 598.78f, 188.23f, 601.75f, 232.68f)
            cubicTo(617.77f, 239.71f, 629.86f, 254.57f, 631.2f, 273.4f)
            cubicTo(631.32f, 275.06f, 631.31f, 276.86f, 631.31f, 278.52f)
            cubicTo(631.31f, 278.67f, 631.31f, 278.82f, 631.31f, 278.97f)
            lineTo(631.31f, 385.03f)
            cubicTo(631.31f, 385.18f, 631.31f, 385.33f, 631.31f, 385.48f)
            cubicTo(631.31f, 387.14f, 631.32f, 388.94f, 631.2f, 390.6f)
            cubicTo(629.86f, 409.43f, 617.77f, 424.29f, 601.75f, 431.32f)
            cubicTo(598.78f, 475.77f, 590.61f, 510.02f, 564.96f, 535.59f)
            cubicTo(544.19f, 556.3f, 517.85f, 565.48f, 485.32f, 569.85f)
            cubicTo(453.71f, 574.08f, 413.31f, 574.08f, 362.32f, 574.08f)
            lineTo(275.93f, 574.08f)
            cubicTo(224.94f, 574.08f, 184.54f, 574.08f, 152.93f, 569.85f)
            cubicTo(120.39f, 565.48f, 94.06f, 556.3f, 73.29f, 535.59f)
            cubicTo(52.53f, 514.89f, 43.31f, 488.63f, 38.94f, 456.2f)
            cubicTo(34.69f, 424.68f, 34.69f, 384.41f, 34.69f, 333.56f)
            lineTo(34.69f, 330.44f)
            cubicTo(34.69f, 279.59f, 34.69f, 239.32f, 38.94f, 207.8f)
            cubicTo(43.31f, 175.37f, 52.53f, 149.11f, 73.29f, 128.41f)
            cubicTo(94.06f, 107.7f, 120.39f, 98.51f, 152.93f, 94.15f)
            cubicTo(184.54f, 89.92f, 224.94f, 89.92f, 275.93f, 89.92f)
            moveTo(559.66f, 435.75f); lineTo(505.9f, 435.75f)
            cubicTo(446.38f, 435.75f, 395.44f, 390.72f, 395.44f, 332.0f)
            cubicTo(395.44f, 273.28f, 446.38f, 228.25f, 505.9f, 228.25f)
            lineTo(559.66f, 228.25f)
            cubicTo(556.5f, 191.14f, 549.36f, 171.54f, 535.52f, 157.75f)
            cubicTo(523.78f, 146.04f, 507.69f, 139.03f, 479.77f, 135.28f)
            cubicTo(451.26f, 131.46f, 413.66f, 131.42f, 360.75f, 131.42f)
            lineTo(277.5f, 131.42f)
            cubicTo(224.59f, 131.42f, 186.99f, 131.46f, 158.48f, 135.28f)
            cubicTo(130.56f, 139.03f, 114.47f, 146.04f, 102.73f, 157.75f)
            cubicTo(90.98f, 169.46f, 83.94f, 185.5f, 80.19f, 213.33f)
            cubicTo(76.36f, 241.76f, 76.31f, 279.24f, 76.31f, 332.0f)
            cubicTo(76.31f, 384.76f, 76.36f, 422.23f, 80.19f, 450.67f)
            cubicTo(83.94f, 478.5f, 90.98f, 494.54f, 102.73f, 506.25f)
            cubicTo(114.47f, 517.96f, 130.56f, 524.97f, 158.48f, 528.72f)
            cubicTo(186.99f, 532.54f, 224.59f, 532.58f, 277.5f, 532.58f)
            lineTo(360.75f, 532.58f)
            cubicTo(413.66f, 532.58f, 451.26f, 532.54f, 479.77f, 528.72f)
            cubicTo(507.69f, 524.97f, 523.78f, 517.96f, 535.52f, 506.25f)
            cubicTo(549.36f, 492.46f, 556.5f, 472.86f, 559.66f, 435.75f)
            moveTo(580.63f, 269.76f)
            cubicTo(580.06f, 269.75f, 579.33f, 269.75f, 578.12f, 269.75f)
            lineTo(505.9f, 269.75f)
            cubicTo(466.4f, 269.75f, 437.06f, 299.04f, 437.06f, 332.0f)
            cubicTo(437.06f, 364.96f, 466.4f, 394.25f, 505.9f, 394.25f)
            lineTo(578.12f, 394.25f)
            cubicTo(579.33f, 394.25f, 580.06f, 394.25f, 580.63f, 394.24f)
            cubicTo(580.97f, 394.24f, 581.16f, 394.24f, 581.24f, 394.23f)
            lineTo(581.3f, 394.23f)
            cubicTo(587.03f, 393.88f, 589.48f, 390.03f, 589.67f, 387.72f)
            cubicTo(589.67f, 387.72f, 589.68f, 387.54f, 589.68f, 387.3f)
            cubicTo(589.69f, 386.8f, 589.69f, 386.15f, 589.69f, 385.03f)
            lineTo(589.69f, 278.97f)
            cubicTo(589.69f, 277.85f, 589.69f, 277.2f, 589.68f, 276.7f)
            cubicTo(589.68f, 276.46f, 589.67f, 276.28f, 589.67f, 276.28f)
            cubicTo(589.48f, 273.97f, 587.03f, 270.12f, 581.3f, 269.77f)
            cubicTo(581.3f, 269.77f, 581.17f, 269.76f, 580.63f, 269.76f)
            moveTo(194.25f, 228.25f)
            cubicTo(205.74f, 228.25f, 215.06f, 237.54f, 215.06f, 249.0f)
            lineTo(215.06f, 415.0f)
            cubicTo(215.06f, 426.46f, 205.74f, 435.75f, 194.25f, 435.75f)
            cubicTo(182.76f, 435.75f, 173.44f, 426.46f, 173.44f, 415.0f)
            lineTo(173.44f, 249.0f)
            cubicTo(173.44f, 237.54f, 182.76f, 228.25f, 194.25f, 228.25f)
            close()
            fillType = PathFillType.EvenOdd
        }
    }

    val circleOutlinePath = remember {
        Path().apply {
            moveTo(527.25f, 332.0f)
            cubicTo(527.25f, 347.28f, 514.83f, 359.67f, 499.5f, 359.67f)
            cubicTo(484.17f, 359.67f, 471.75f, 347.28f, 471.75f, 332.0f)
            cubicTo(471.75f, 316.72f, 484.17f, 304.33f, 499.5f, 304.33f)
            cubicTo(514.83f, 304.33f, 527.25f, 316.72f, 527.25f, 332.0f)
            close()
        }
    }

    val mainBodyFilledPath = remember {
        Path().apply {
            moveTo(584.86f, 221.38f)
            cubicTo(584.86f, 188.71f, 583.67f, 153.67f, 562.79f, 128.55f)
            cubicTo(560.77f, 126.13f, 558.63f, 123.78f, 556.34f, 121.49f)
            cubicTo(535.64f, 100.79f, 509.38f, 91.6f, 476.95f, 87.24f)
            cubicTo(445.43f, 83.0f, 405.16f, 83.0f, 354.31f, 83.0f)
            lineTo(295.86f, 83.0f)
            cubicTo(245.01f, 83.0f, 204.74f, 83.0f, 173.22f, 87.24f)
            cubicTo(140.78f, 91.6f, 114.53f, 100.79f, 93.82f, 121.49f)
            cubicTo(73.12f, 142.2f, 63.93f, 168.45f, 59.57f, 200.89f)
            cubicTo(55.33f, 232.4f, 55.33f, 272.68f, 55.33f, 323.52f)
            lineTo(55.33f, 326.64f)
            cubicTo(55.33f, 377.49f, 55.33f, 417.76f, 59.57f, 449.28f)
            cubicTo(63.93f, 481.71f, 73.12f, 507.97f, 93.82f, 528.68f)
            cubicTo(114.53f, 549.38f, 140.78f, 558.57f, 173.22f, 562.93f)
            cubicTo(204.74f, 567.17f, 245.01f, 567.17f, 295.85f, 567.17f)
            lineTo(354.31f, 567.17f)
            cubicTo(405.16f, 567.17f, 445.43f, 567.17f, 476.95f, 562.93f)
            cubicTo(509.38f, 558.57f, 535.64f, 549.38f, 556.34f, 528.68f)
            cubicTo(562.0f, 523.02f, 566.81f, 516.93f, 570.89f, 510.41f)
            cubicTo(583.35f, 490.51f, 584.86f, 466.11f, 584.86f, 442.63f)
            cubicTo(583.52f, 442.67f, 582.12f, 442.67f, 580.67f, 442.67f)
            lineTo(503.95f, 442.67f)
            cubicTo(441.1f, 442.67f, 387.33f, 394.64f, 387.33f, 332.0f)
            cubicTo(387.33f, 269.36f, 441.1f, 221.33f, 503.95f, 221.33f)
            lineTo(580.67f, 221.33f)
            cubicTo(582.11f, 221.33f, 583.52f, 221.33f, 584.86f, 221.38f)
            close()
        }
    }

    val dShapeFilledPath = remember {
        Path().apply {
            moveTo(586.21f, 221.44f)
            cubicTo(584.49f, 221.33f, 582.62f, 221.33f, 580.67f, 221.33f)
            lineTo(580.19f, 221.33f); lineTo(503.95f, 221.33f)
            cubicTo(441.1f, 221.33f, 387.33f, 269.36f, 387.33f, 332.0f)
            cubicTo(387.33f, 394.64f, 441.1f, 442.67f, 503.95f, 442.67f)
            lineTo(580.19f, 442.67f); lineTo(580.67f, 442.67f)
            cubicTo(582.62f, 442.67f, 584.49f, 442.67f, 586.21f, 442.56f)
            cubicTo(611.74f, 441.0f, 634.31f, 421.6f, 636.21f, 394.51f)
            cubicTo(636.34f, 392.73f, 636.33f, 390.82f, 636.33f, 389.05f)
            lineTo(636.33f, 388.56f); lineTo(636.33f, 275.44f); lineTo(636.33f, 274.96f)
            cubicTo(636.33f, 273.18f, 636.34f, 271.27f, 636.21f, 269.49f)
            cubicTo(634.31f, 242.4f, 611.74f, 223.0f, 586.21f, 221.44f)
            close()
        }
    }

    val circleFilledPath = remember {
        Path().apply {
            moveTo(497.19f, 361.51f)
            cubicTo(513.37f, 361.51f, 526.48f, 348.3f, 526.48f, 332.0f)
            cubicTo(526.48f, 315.7f, 513.37f, 302.49f, 497.19f, 302.49f)
            cubicTo(481.01f, 302.49f, 467.89f, 315.7f, 467.89f, 332.0f)
            cubicTo(467.89f, 348.3f, 481.01f, 361.51f, 497.19f, 361.51f)
            close()
        }
    }

    val leftBarFilledPath = remember {
        Path().apply {
            moveTo(172.92f, 408.08f)
            cubicTo(172.92f, 419.54f, 182.21f, 428.83f, 193.67f, 428.83f)
            cubicTo(205.13f, 428.83f, 214.42f, 419.54f, 214.42f, 408.08f)
            lineTo(214.42f, 242.08f)
            cubicTo(214.42f, 230.62f, 205.13f, 221.33f, 193.67f, 221.33f)
            cubicTo(182.21f, 221.33f, 172.92f, 230.62f, 172.92f, 242.08f)
            lineTo(172.92f, 408.08f)
            close()
        }
    }

    // ── 2. CASCADING SLIDE & ELASTIC SCALE ANIMATIONS ───────────
    // Outline scales down and fades out
    val progress by animateFloatAsState(
        targetValue = if (isFilled) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "wallet_nav_progress"
    )
    val outlineStage = stagedProgress(progress, 0f, 0.38f)
    val mainStage = stagedProgress(progress, 0.04f, 0.46f)
    val dShapeStage = stagedProgress(progress, 0.22f, 0.64f)
    val leftBarStage = stagedProgress(progress, 0.4f, 0.82f)
    val circleStage = stagedProgress(progress, 0.58f, 1f)

    val outlineAlpha = 1f - outlineStage
    val outlineScale = 1f - (0.08f * outlineStage)

    // Filled elements slide UP into place with staggered delays.
    // When turning OFF, the delays reverse for a premium "un-stacking" effect.
    val mainAlpha = mainStage
    val mainSlide = stagedSlide(mainStage)

    val dShapeAlpha = dShapeStage
    val dShapeSlide = stagedSlide(dShapeStage)

    val leftBarAlpha = leftBarStage
    val leftBarSlide = stagedSlide(leftBarStage)

    val circleAlpha = circleStage
    val circleSlide = stagedSlide(circleStage)

    // ── 3. CANVAS DRAWING ───────────────────────────────────────
    Canvas(modifier = modifier.size(iconSize).clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
    ) { onClick() }) {
        val scale = size.width / 664f // Map 664x664 XML viewport to Compose size

        scale(scale, scale, pivot = Offset.Zero) {
            // Draw Outline State
            withTransform({ scale(outlineScale, outlineScale, pivot = Offset(332f, 332f)) }) {
                drawPath(mainOutlinePath, color = outlineColor, alpha = outlineAlpha)
                drawPath(circleOutlinePath, color = outlineColor, alpha = outlineAlpha)
            }

            // Draw Filled State (Staggered Slide)
            withTransform({ translate(0f, mainSlide) }) {
                drawPath(mainBodyFilledPath, color = mainFillColor, alpha = mainAlpha)
            }
            withTransform({ translate(0f, dShapeSlide) }) {
                drawPath(dShapeFilledPath, color = accentFillColor, alpha = dShapeAlpha)
            }
            withTransform({ translate(0f, leftBarSlide) }) {
                drawPath(leftBarFilledPath, color = accentFillColor, alpha = leftBarAlpha)
            }
            withTransform({ translate(0f, circleSlide) }) {
                drawPath(circleFilledPath, color = mainFillColor, alpha = circleAlpha)
            }
        }
    }
}

@Composable
fun AnimatedClockIcon(
    modifier: Modifier = Modifier,
    isFilled: Boolean,
    outlineColor: Color = Color(0xFF949494),
    circleFillColor: Color = MaterialTheme.colorScheme.tertiary,
    handsFillColor: Color = MaterialTheme.colorScheme.background,
    iconSize: Dp = 64.dp,
    onClick:()->Unit
) {
    // ── 1. EXACT PATH DATA FROM XML ─────────────────────────────
    val circleOutlinePath = remember {
        Path().apply {
            moveTo(332f, 610.5f)
            cubicTo(484.8f, 610.5f, 608.67f, 486.26f, 608.67f, 333f)
            cubicTo(608.67f, 179.74f, 484.8f, 55.5f, 332f, 55.5f)
            cubicTo(179.2f, 55.5f, 55.33f, 179.74f, 55.33f, 333f)
            cubicTo(55.33f, 486.26f, 179.2f, 610.5f, 332f, 610.5f)
            close()
        }
    }

    val handsOutlinePath = remember {
        Path().apply {
            moveTo(332f, 222f)
            lineTo(332f, 333f)
            lineTo(401.17f, 402.38f)
        }
    }

    val circleFilledPath = remember {
        Path().apply {
            moveTo(333f, 610.5f)
            cubicTo(486.26f, 610.5f, 610.5f, 486.26f, 610.5f, 333f)
            cubicTo(610.5f, 179.74f, 486.26f, 55.5f, 333f, 55.5f)
            cubicTo(179.74f, 55.5f, 55.5f, 179.74f, 55.5f, 333f)
            cubicTo(55.5f, 486.26f, 179.74f, 610.5f, 333f, 610.5f)
            close()
        }
    }

    val handsFilledPath = remember {
        Path().apply {
            moveTo(333f, 201.19f)
            cubicTo(344.49f, 201.19f, 353.81f, 210.51f, 353.81f, 222f)
            lineTo(353.81f, 324.38f)
            lineTo(417.09f, 387.66f)
            cubicTo(425.22f, 395.79f, 425.22f, 408.96f, 417.09f, 417.09f)
            cubicTo(408.96f, 425.22f, 395.79f, 425.22f, 387.66f, 417.09f)
            lineTo(318.28f, 347.72f)
            cubicTo(314.38f, 343.81f, 312.19f, 338.52f, 312.19f, 333f)
            lineTo(312.19f, 222f)
            cubicTo(312.19f, 210.51f, 321.51f, 201.19f, 333f, 201.19f)
            close()
            fillType = PathFillType.EvenOdd
        }
    }

    // ── 2. CASCADING SLIDE & ELASTIC SCALE ANIMATIONS ───────────
    // Outline scales down and fades out
    val progress by animateFloatAsState(
        targetValue = if (isFilled) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "clock_nav_progress"
    )
    val outlineStage = stagedProgress(progress, 0f, 0.42f)
    val circleStage = stagedProgress(progress, 0.08f, 0.62f)
    val handsStage = stagedProgress(progress, 0.44f, 1f)

    val outlineAlpha = 1f - outlineStage
    val outlineScale = 1f - (0.08f * outlineStage)

    // Filled elements slide UP into place with staggered delays.
    // When turning OFF, the delays reverse for a premium "un-stacking" effect.
    val circleAlpha = circleStage
    val circleSlide = stagedSlide(circleStage)

    val handsAlpha = handsStage
    val handsSlide = stagedSlide(handsStage)

    // ── 3. CANVAS DRAWING ───────────────────────────────────────
    Canvas(modifier = modifier.size(iconSize).clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
    ) { onClick() }) {
        val scale = size.width / 664f // Map 664x666 XML viewport to Compose size

        scale(scale, scale, pivot = Offset.Zero) {
            val strokeWidth = 50f // Exact XML strokeWidth

            // Draw Outline State
            withTransform({ scale(outlineScale, outlineScale, pivot = Offset(332f, 333f)) }) {
                // Circle Outline (XML has strokeAlpha="0.5")
                drawPath(
                    path = circleOutlinePath,
                    color = outlineColor.copy(alpha = 0.5f),
                    alpha = outlineAlpha,
                    style = Stroke(width = strokeWidth)
                )

                // Hands Outline (XML has strokeLineCap="round" and strokeLineJoin="round")
                drawPath(
                    path = handsOutlinePath,
                    color = outlineColor,
                    alpha = outlineAlpha,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Draw Filled Circle (Staggered Slide)
            withTransform({ translate(0f, circleSlide) }) {
                drawPath(
                    path = circleFilledPath,
                    color = circleFillColor,
                    alpha = circleAlpha
                )
            }

            // Draw Filled Hands (Staggered Slide)
            withTransform({ translate(0f, handsSlide) }) {
                drawPath(
                    path = handsFilledPath,
                    color = handsFillColor,
                    alpha = handsAlpha
                )
            }
        }
    }
}

@Composable
fun AnimatedApertureIcon(
    modifier: Modifier = Modifier,
    isFilled: Boolean,
    outlineColor: Color = Color(0xFF949494),
    outerFillColor: Color = MaterialTheme.colorScheme.tertiary,
    innerFillColor: Color = MaterialTheme.colorScheme.background,
    iconSize: Dp = 64.dp,
    onClick:()->Unit
) {
    // ── 1. EXACT PATH DATA FROM XML ─────────────────────────────
    val outerOutlinePath = remember {
        Path().apply {
            moveTo(492.47f, 58.28f)
            lineTo(217.74f, 127.37f)
            cubicTo(177.62f, 137.36f, 136.95f, 178.15f, 126.99f, 218.39f)
            lineTo(58.1f, 493.95f)
            cubicTo(37.35f, 577.2f, 88.26f, 628.54f, 171.53f, 607.72f)
            lineTo(446.26f, 538.91f)
            cubicTo(486.1f, 528.91f, 527.05f, 487.85f, 537.01f, 447.89f)
            lineTo(605.9f, 172.05f)
            cubicTo(626.65f, 88.8f, 575.47f, 37.46f, 492.47f, 58.28f)
            close()
        }
    }

    val innerOutlinePath = remember {
        Path().apply {
            moveTo(332f, 430.13f)
            cubicTo(385.48f, 430.13f, 428.83f, 386.64f, 428.83f, 333f)
            cubicTo(428.83f, 279.36f, 385.48f, 235.88f, 332f, 235.88f)
            cubicTo(278.52f, 235.88f, 235.17f, 279.36f, 235.17f, 333f)
            cubicTo(235.17f, 386.64f, 278.52f, 430.13f, 332f, 430.13f)
            close()
        }
    }

    val outerFilledPath = remember {
        Path().apply {
            moveTo(580.47f, 83.56f)
            cubicTo(556.4f, 59.49f, 522.37f, 50.08f, 489.45f, 58.38f)
            lineTo(218.31f, 126.16f)
            cubicTo(172.66f, 137.51f, 137.53f, 172.92f, 126.18f, 218.29f)
            lineTo(58.4f, 489.7f)
            cubicTo(50.1f, 522.63f, 59.51f, 556.66f, 83.58f, 580.73f)
            cubicTo(101.84f, 598.71f, 125.91f, 608.67f, 150.81f, 608.67f)
            cubicTo(158.55f, 608.67f, 166.58f, 607.84f, 174.32f, 605.63f)
            lineTo(445.73f, 537.84f)
            cubicTo(491.11f, 526.5f, 526.52f, 491.36f, 537.86f, 445.71f)
            lineTo(605.65f, 174.3f)
            cubicTo(613.95f, 141.38f, 604.54f, 107.35f, 580.47f, 83.56f)
            close()
        }
    }

    val innerFilledPath = remember {
        Path().apply {
            moveTo(331.98f, 439.35f)
            cubicTo(391.26f, 439.35f, 439.32f, 391.29f, 439.32f, 332f)
            cubicTo(439.32f, 272.72f, 391.26f, 224.66f, 331.98f, 224.66f)
            cubicTo(272.69f, 224.66f, 224.63f, 272.72f, 224.63f, 332f)
            cubicTo(224.63f, 391.29f, 272.69f, 439.35f, 331.98f, 439.35f)
            close()
        }
    }

    // ── 2. CASCADING SLIDE & ELASTIC SCALE ANIMATIONS ───────────
    // Outline scales down and fades out
    val progress by animateFloatAsState(
        targetValue = if (isFilled) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "aperture_nav_progress"
    )
    val outlineStage = stagedProgress(progress, 0f, 0.42f)
    val outerStage = stagedProgress(progress, 0.08f, 0.62f)
    val innerStage = stagedProgress(progress, 0.44f, 1f)

    val outlineAlpha = 1f - outlineStage
    val outlineScale = 1f - (0.08f * outlineStage)

    // Filled elements slide UP into place with staggered delays.
    // When turning OFF, the delays reverse for a premium "un-stacking" effect.

    // Outer shape animates first
    val outerFillAlpha = outerStage
    val outerFillSlide = stagedSlide(outerStage)

    // Inner lens animates second
    val innerFillAlpha = innerStage
    val innerFillSlide = stagedSlide(innerStage)

    // ── 3. CANVAS DRAWING ───────────────────────────────────────
    Canvas(modifier = modifier.size(iconSize).clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
    ) { onClick() }) {
        // Map 666x666 XML viewport to Compose size
        val scale = size.width / 666f

        scale(scale, scale, pivot = Offset.Zero) {
            val strokeWidth = 40f // Exact XML strokeWidth

            // Draw Outline State
            withTransform({ scale(outlineScale, outlineScale, pivot = Offset(332f, 333f)) }) {
                // Outer Shape Outline
                drawPath(
                    path = outerOutlinePath,
                    color = outlineColor,
                    alpha = outlineAlpha,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Inner Lens Outline
                drawPath(
                    path = innerOutlinePath,
                    color = outlineColor,
                    alpha = outlineAlpha,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Draw Filled Outer Shape (Staggered Slide)
            withTransform({ translate(0f, outerFillSlide) }) {
                drawPath(
                    path = outerFilledPath,
                    color = outerFillColor,
                    alpha = outerFillAlpha
                )
            }

            // Draw Filled Inner Lens (Staggered Slide)
            withTransform({ translate(0f, innerFillSlide) }) {
                drawPath(
                    path = innerFilledPath,
                    color = innerFillColor,
                    alpha = innerFillAlpha
                )
            }
        }
    }
}



