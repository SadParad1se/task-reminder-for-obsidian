package com.sadparad1se.task_reminder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Gives brief, local confirmation without replaying successes when a row returns to composition. */
@Composable
internal fun TaskNotificationBell(
    fileUri: String,
    taskTitle: String,
    successCount: Long,
    onClick: () -> Unit
) {
    var handledCount by remember(fileUri) { mutableLongStateOf(successCount) }
    var ringing by remember(fileUri) { mutableStateOf(false) }
    val rotation = remember(fileUri) { Animatable(0f) }
    val waveProgress = remember(fileUri) { Animatable(1f) }
    val color = MaterialTheme.colorScheme.primary
    val successDescription = stringResource(R.string.task_notification_created)

    LaunchedEffect(fileUri, successCount) {
        if (successCount <= handledCount) return@LaunchedEffect
        handledCount = successCount
        rotation.snapTo(0f)
        waveProgress.snapTo(0f)
        ringing = true
        try {
            coroutineScope {
                launch {
                    rotation.animateTo(0f, keyframes {
                        durationMillis = 600
                        0f at 0 using FastOutSlowInEasing
                        12f at 70 using FastOutSlowInEasing
                        -10f at 170 using FastOutSlowInEasing
                        7f at 280 using FastOutSlowInEasing
                        -4f at 390 using FastOutSlowInEasing
                        2f at 490 using FastOutSlowInEasing
                        0f at 600
                    })
                }
                launch { waveProgress.animateTo(1f, tween(600, easing = LinearEasing)) }
            }
        } finally {
            // A cancelled, older pulse must not hide the replacement animation.
            if (handledCount == successCount) ringing = false
        }
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).semantics {
            if (ringing) {
                stateDescription = successDescription
                liveRegion = LiveRegionMode.Polite
            }
        }
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (ringing) {
                Canvas(Modifier.matchParentSize()) {
                    val progress = waveProgress.value
                    val alpha = (progress * 8f).coerceAtMost(1f) * (1f - progress)
                    val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    for (baseRadius in listOf(15.dp, 20.dp)) {
                        val radius = baseRadius.toPx() + 2.dp.toPx() * progress
                        for (angle in listOf(-30f, 150f)) {
                            drawArc(
                                color = color.copy(alpha = alpha),
                                startAngle = angle,
                                sweepAngle = 60f,
                                useCenter = false,
                                topLeft = center - Offset(radius, radius),
                                size = Size(radius * 2, radius * 2),
                                style = stroke
                            )
                        }
                    }
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_notification_bell),
                contentDescription = stringResource(R.string.create_task_notification, taskTitle),
                tint = color,
                modifier = Modifier.size(24.dp).graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.15f)
                    rotationZ = if (ringing) rotation.value else 0f
                }
            )
        }
    }
}
