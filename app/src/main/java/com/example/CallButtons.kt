package com.example

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun RoundCallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    // First outward expanding pulse ring
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    
    // Second outward expanding pulse ring (offset by 1 second for a smooth alternating effect)
    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )

    // Gentle heartbeat scaling of the core button
    val buttonScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buttonScale"
    )

    Box(
        modifier = Modifier.size(110.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer radiating pulse ring 1
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(1f + pulse1 * 0.7f)
                .background(backgroundColor.copy(alpha = (1f - pulse1) * 0.45f), CircleShape)
        )
        
        // Outer radiating pulse ring 2
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(1f + pulse2 * 0.7f)
                .background(backgroundColor.copy(alpha = (1f - pulse2) * 0.45f), CircleShape)
        )

        // Main clickable button core
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(buttonScale)
                .background(backgroundColor, CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                .clickable(onClick = onClick)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
            )
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
fun SwipeToAnswerButton(onAccept: () -> Unit, onDismiss: () -> Unit) {
    val maxDrag = 110.dp
    val maxDragPx = with(LocalDensity.current) { maxDrag.toPx() }
    var offsetX by remember { mutableStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "swipe")
    
    // Pulsing scale for the container background
    val bgScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bgScale"
    )

    // Chevron slide guide animation
    val chevronOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "chevron"
    )

    Box(
        modifier = Modifier
            .width(280.dp)
            .height(72.dp)
            .scale(bgScale)
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(36.dp))
            .border(1.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(36.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Guided arrows pointing left (reject) and right (accept)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dismiss Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = null,
                    tint = Color(0xFFEF5350).copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "◀◀",
                    color = Color(0xFFEF5350).copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(x = (-chevronOffset).dp)
                )
            }

            // Accept Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "▶▶",
                    color = Color(0xFF66BB6A).copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(x = chevronOffset.dp)
                )
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = Color(0xFF66BB6A).copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Animated Swipe Instruction Text
        val textAlpha = (1f - (kotlin.math.abs(offsetX) / maxDragPx)).coerceIn(0f, 1f)
        if (textAlpha > 0.1f) {
            Text(
                text = "Swipe Side to Answer",
                color = Color.White.copy(alpha = textAlpha * 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Draggable Floating Pill/Handle
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .size(72.dp)
                .background(Color.White, CircleShape)
                .border(2.5.dp, if (offsetX < 0) Color(0xFFEF5350) else if (offsetX > 0) Color(0xFF66BB6A) else Color.White.copy(alpha = 0.3f), CircleShape)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > maxDragPx * 0.7f) {
                                onAccept()
                            } else if (offsetX < -maxDragPx * 0.7f) {
                                onDismiss()
                            }
                            offsetX = 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (offsetX < 0) Icons.Default.CallEnd else Icons.Default.Call,
                contentDescription = "Swipe handle",
                tint = if (offsetX < 0) Color(0xFFE53935) else if (offsetX > 0) Color(0xFF4CAF50) else Color.Black,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
