package com.mtd.megawallet.ui.compose.animations

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush

/**
 * Paints an animated shimmer [Brush] in the DRAW phase instead of during composition.
 *
 * The shimmer's translate value comes from a `rememberInfiniteTransition().animateFloat(...)` state.
 * Reading that state in the composable body (e.g. `remember(translateAnim.value) { … }`) makes the
 * whole placeholder subtree **recompose on every animation frame**. Passing a [brushProvider] that
 * reads the animated state instead moves that read into [drawBehind]'s draw scope: the node redraws
 * each frame but never recomposes.
 *
 * Callers keep their own gradient geometry inside [brushProvider]; only the per-frame state read must
 * live there. Pair with a preceding `Modifier.clip(shape)` to get rounded shimmer blocks (the clip
 * applies to this node's drawing, including [drawBehind]).
 */
fun Modifier.shimmerBackground(brushProvider: () -> Brush): Modifier =
    drawBehind { drawRect(brush = brushProvider()) }
