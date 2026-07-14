package com.lucas.album.ui.pin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lucas.album.R
import com.lucas.album.data.auth.PinManager

@Composable
fun PinScreen(viewModel: PinViewModel, onVerified: () -> Unit) {
    val digits by viewModel.digits.collectAsState()
    val shakeTrigger by viewModel.shakeTrigger.collectAsState()
    val haptics = LocalHapticFeedback.current

    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    -14f at 60
                    14f at 140
                    -10f at 220
                    10f at 300
                    0f at 400
                },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.pin_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.offset(x = shakeOffset.value.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(PinManager.PIN_LENGTH) { index ->
                PinDot(filled = index < digits.length)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        PinKeypad(
            onDigit = { d ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.onDigit(d, onVerified)
            },
            onBackspace = viewModel::onBackspace,
        )
    }
}

@Composable
private fun PinDot(filled: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (filled) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pin-dot-scale",
    )
    Box(
        modifier = Modifier
            .size(16.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (filled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant
            ),
    )
}

@Composable
private fun PinKeypad(onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit ->
                    KeypadButton(label = digit.toString(), onClick = { onDigit(digit) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Spacer(modifier = Modifier.size(72.dp))
            KeypadButton(label = "0", onClick = { onDigit('0') })
            KeypadButton(label = "⌫", onClick = onBackspace)
        }
    }
}

@Composable
private fun KeypadButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.headlineSmall)
    }
}
