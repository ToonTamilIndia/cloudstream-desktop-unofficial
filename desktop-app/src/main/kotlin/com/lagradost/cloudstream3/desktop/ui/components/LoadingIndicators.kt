package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CS3-inspired loading pill that appears at the center-bottom of a container.
 * Features a glassmorphic pill with animated dots and optional status text.
 *
 * Use this inside a Box with `Modifier.fillMaxSize()` and let the overlay
 * position itself at `Alignment.BottomCenter`.
 */
@Composable
fun CenterBottomLoadingPill(
    visible: Boolean,
    statusText: String? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(500)) { it / 2 },
        exit = fadeOut(tween(300)) + slideOutVertically(tween(400)) { it / 2 },
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.65f),
            shadowElevation = 16.dp,
            modifier = Modifier.padding(bottom = 48.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                PulsingDots()
                if (statusText != null) {
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = statusText,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * CS3-inspired animated three-dot pulsing indicator.
 */
@Composable
fun PulsingDots(
    dotSize: Dp = 8.dp,
    dotColor: Color = MaterialTheme.colorScheme.primary,
    spacing: Dp = 6.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsingDots")

    val delays = listOf(0, 150, 300)
    val scales = delays.map { delay ->
        infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 900
                    0.6f at 0 using LinearEasing
                    1.2f at 300 using FastOutSlowInEasing
                    0.6f at 600 using LinearEasing
                    0.6f at 900 using LinearEasing
                },
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(delay),
            ),
            label = "dotScale_$delay",
        )
    }

    val alphas = delays.map { delay ->
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 900
                    0.4f at 0 using LinearEasing
                    1f at 300 using FastOutSlowInEasing
                    0.4f at 600 using LinearEasing
                    0.4f at 900 using LinearEasing
                },
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(delay),
            ),
            label = "dotAlpha_$delay",
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        for (i in 0..2) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .scale(scales[i].value)
                    .alpha(alphas[i].value)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
    }
}

/**
 * Full-screen loading overlay for player screens.
 * Positions loading indicator at center of the screen.
 */
@Composable
fun PlayerLoadingOverlay(
    visible: Boolean,
    statusText: String? = null,
    tryingLinkText: String? = null,
) {
    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            // Center loading indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Spinning ring with accent glow
                Box(contentAlignment = Alignment.Center) {
                    // Outer glow ring
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(56.dp),
                    )
                    // Inner progress ring
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Status text
                if (statusText != null) {
                    Text(
                        text = statusText,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Link trying info
                if (tryingLinkText != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = tryingLinkText,
                        color = Color.White.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * Compact inline loading indicator for sections/cards.
 * Shows animated dots with optional text.
 */
@Composable
fun InlineSectionLoader(
    statusText: String = "Loading...",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PulsingDots(
                dotSize = 10.dp,
                dotColor = MaterialTheme.colorScheme.primary,
                spacing = 8.dp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Full-screen centered loader with animated dots for page-level loading.
 * Positioned at center of the screen.
 */
@Composable
fun PageLoadingIndicator(
    statusText: String = "Loading...",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
