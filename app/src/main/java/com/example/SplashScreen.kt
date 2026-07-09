package com.example

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onLoadingFinished: () -> Unit) {
    var progress by remember { mutableStateOf(0.0f) }
    var loadingStatus by remember { mutableStateOf("Initializing Custom Notifier...") }
    var isAnimatingIn by remember { mutableStateOf(false) }
    var isAnimatingOut by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = when {
            isAnimatingOut -> 0f
            isAnimatingIn -> 1f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "splash_alpha"
    )

    val scale by animateFloatAsState(
        targetValue = when {
            isAnimatingOut -> 1.06f
            isAnimatingIn -> 1f
            else -> 0.88f
        },
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "splash_scale"
    )

    LaunchedEffect(Unit) {
        // Smooth fade-in
        delay(100)
        isAnimatingIn = true

        // Precise 2-second simulation
        val duration = 2000L
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < duration) {
            val elapsed = System.currentTimeMillis() - startTime
            progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            
            loadingStatus = when {
                progress < 0.25f -> "Initializing Audio Engine..."
                progress < 0.5f -> "Configuring local settings..."
                progress < 0.75f -> "Optimizing visual layout..."
                else -> "Ready to start!"
            }
            delay(16) // Smooth update tick
        }
        progress = 1.0f
        
        // Smooth fade-out before navigation
        isAnimatingOut = true
        delay(400)
        onLoadingFinished()
    }

    // A premium off-white solid background that matches the branding paper texture exactly
    val paperBackgroundColor = Color(0xFFF3F2EE)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(paperBackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .alpha(alpha)
                .scale(scale),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Elegant brand illustration featuring your hand-drawn sketch
            Image(
                painter = painterResource(id = R.drawable.custom_notifier_icon_1783452599256),
                contentDescription = "Notifier Logo",
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(36.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "NOTIFIER",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                ),
                color = Color(0xFF2C2C2A)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Loading Circle and Status Text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    color = Color(0xFF2C2C2A), // Matches the hand-drawn sketch charcoal tone
                    strokeWidth = 3.5.dp,
                    trackColor = Color(0xFF2C2C2A).copy(alpha = 0.1f)
                )
                
                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = loadingStatus,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color(0xFF2C2C2A).copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
