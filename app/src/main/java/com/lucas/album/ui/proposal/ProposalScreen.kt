package com.lucas.album.ui.proposal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lucas.album.R
import kotlin.random.Random

@Composable
fun ProposalScreen(onAnswerYes: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val infiniteTransition = rememberInfiniteTransition(label = "proposal-ambient")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "yes-pulse",
    )

    var noOffsetX by remember { mutableStateOf(0.dp) }
    var noOffsetY by remember { mutableStateOf(0.dp) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background,
                    ),
                    radius = 1200f,
                )
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500)) + slideInVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    initialOffsetY = { it / 2 },
                ),
            ) {
                Text(
                    text = stringResource(R.string.proposal_kicker),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 150)) + slideInVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    initialOffsetY = { it / 2 },
                ),
            ) {
                Text(
                    text = stringResource(R.string.proposal_question),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 300)) + slideInVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    initialOffsetY = { it / 2 },
                ),
            ) {
                Box(
                    modifier = Modifier
                        .scale(pulse)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAnswerYes()
                        }
                        .padding(horizontal = 48.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = stringResource(R.string.proposal_yes),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .offset(x = noOffsetX, y = noOffsetY)
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        // Jumps away on tap rather than tracking pre-touch hover — touchscreens
                        // don't reliably report finger position before contact.
                        noOffsetX = Random.nextInt(-160, 160).dp
                        noOffsetY = Random.nextInt(-80, 40).dp
                    }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.proposal_no),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
