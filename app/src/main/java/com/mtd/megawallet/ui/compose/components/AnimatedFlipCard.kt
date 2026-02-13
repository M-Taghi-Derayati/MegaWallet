package com.mtd.megawallet.ui.compose.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
data class FlipCardTargets(
    val width: Dp,
    val height: Dp,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val cornerRadius: Dp = 16.dp,
    val cornerRadiusBoarder: Dp = cornerRadius,
    val contentAlpha: Float = 1f,
    val borderAlpha: Float = 0f
)

@Immutable
data class FlipCardAnimationSpec(
    val sizeSpec: FiniteAnimationSpec<Dp> = spring(dampingRatio = 0.82f, stiffness = 380f),
    val offsetSpec: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.82f, stiffness = 380f),
    val rotationSpec: FiniteAnimationSpec<Float> = tween(800, easing = FastOutSlowInEasing),
    val alphaSpec: FiniteAnimationSpec<Float> = tween(300),
    val cornerSpec: FiniteAnimationSpec<Dp> = spring(dampingRatio = 0.82f, stiffness = 380f),
    val scaleSpec: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.82f, stiffness = 380f)
)

@Composable
fun AnimatedFlipCard(
    targets: FlipCardTargets,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit = {},
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = 0.dp,
    cameraDistance: Float = 12f,
    backFaceRotationY: Float = 180f,
    flipCrossfadeDegrees: Float = 10f,
    animate: Boolean = true,
    animationSpec: FlipCardAnimationSpec = FlipCardAnimationSpec(),
    front: @Composable BoxScope.() -> Unit,
    back: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val transition = updateTransition(targetState = targets, label = "flipCardTransition")

    val width by if (animate) {
        transition.animateDp(label = "flipCardWidth", transitionSpec = { animationSpec.sizeSpec }) { it.width }
    } else {
        rememberUpdatedState(targets.width)
    }
    val height by if (animate) {
        transition.animateDp(label = "flipCardHeight", transitionSpec = { animationSpec.sizeSpec }) { it.height }
    } else {
        rememberUpdatedState(targets.height)
    }
    val offsetX by if (animate) {
        transition.animateFloat(label = "flipCardOffsetX", transitionSpec = { animationSpec.offsetSpec }) { it.offsetX }
    } else {
        rememberUpdatedState(targets.offsetX)
    }
    val offsetY by if (animate) {
        transition.animateFloat(label = "flipCardOffsetY", transitionSpec = { animationSpec.offsetSpec }) { it.offsetY }
    } else {
        rememberUpdatedState(targets.offsetY)
    }
    val rotationY by if (animate) {
        transition.animateFloat(label = "flipCardRotationY", transitionSpec = { animationSpec.rotationSpec }) { it.rotationY }
    } else {
        rememberUpdatedState(targets.rotationY)
    }
    val contentAlpha by if (animate) {
        transition.animateFloat(label = "flipCardContentAlpha", transitionSpec = { animationSpec.alphaSpec }) { it.contentAlpha }
    } else {
        rememberUpdatedState(targets.contentAlpha)
    }
    val borderAlpha by if (animate) {
        transition.animateFloat(label = "flipCardBorderAlpha", transitionSpec = { animationSpec.alphaSpec }) { it.borderAlpha }
    } else {
        rememberUpdatedState(targets.borderAlpha)
    }
    val cornerRadius by if (animate) {
        transition.animateDp(label = "flipCardCornerRadius", transitionSpec = { animationSpec.cornerSpec }) { it.cornerRadius }
    } else {
        rememberUpdatedState(targets.cornerRadius)
    }
    val cornerRadiusBoarder by if (animate) {
        transition.animateDp(label = "flipCardCornerRadiusBoarder", transitionSpec = { animationSpec.cornerSpec }) { it.cornerRadiusBoarder }
    } else {
        rememberUpdatedState(targets.cornerRadiusBoarder)
    }
    val scaleX by if (animate) {
        transition.animateFloat(label = "flipCardScaleX", transitionSpec = { animationSpec.scaleSpec }) { it.scaleX }
    } else {
        rememberUpdatedState(targets.scaleX)
    }
    val scaleY by if (animate) {
        transition.animateFloat(label = "flipCardScaleY", transitionSpec = { animationSpec.scaleSpec }) { it.scaleY }
    } else {
        rememberUpdatedState(targets.scaleY)
    }

    val shape = RoundedCornerShape(cornerRadius)
    val shapeBoarder = RoundedCornerShape(cornerRadiusBoarder)
    val crossfadeWindow = flipCrossfadeDegrees.coerceAtLeast(0.1f)
    val backAlpha = when {
        rotationY <= 90f - crossfadeWindow -> 0f
        rotationY >= 90f + crossfadeWindow -> 1f
        else -> ((rotationY - (90f - crossfadeWindow)) / (2f * crossfadeWindow)).coerceIn(0f, 1f)
    }
    val frontAlpha = 1f - backAlpha

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .graphicsLayer {
                translationX = offsetX
                translationY = offsetY
                this.rotationY = rotationY
                this.cameraDistance = cameraDistance * density.density
                alpha = contentAlpha
                this.scaleX = scaleX
                this.scaleY = scaleY
            }
    ) {
        Box(
            modifier = surfaceModifier
                .fillMaxSize()
                .clip(shape)
                .background(backgroundColor)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) { background() }
            if (frontAlpha > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = frontAlpha }
                ) { front() }
            }
            if (backAlpha > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            this.alpha = backAlpha
                            this.rotationY = backFaceRotationY
                        }
                ) { back() }
            }
        }

        if (borderWidth > 0.dp && borderAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(borderWidth, borderColor.copy(alpha = borderAlpha), shapeBoarder)
            )
        }
    }
}
