package com.app.pulsewear.presentation

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text


@Composable
fun PulsatingCircle() {

    // Detect system mode (light / dark)
    val isDark = isSystemInDarkTheme()

    // Background color for round screens (fixes clipping issue)
    val screenBackground = if (isDark) Color.Black else Color(0xFF101010)

    var selectedInterval by remember { mutableStateOf("30 sec") }
    var isRunning by remember { mutableStateOf(false) }
    var circleColor by remember { mutableStateOf(Color.Red) }

    val context = LocalContext.current

    fun intervalToMs(value: String): Long =
        when (value) {
            "30 sec" -> 30_000L
            "5 min"  -> 5 * 60_000L
            "10 min" -> 10 * 60_000L
            "15 min" -> 15 * 60_000L
            "30 min" -> 30 * 60_000L
            "60 min" -> 60 * 60_000L
            else -> 30_000L
        }

    // 🔥 Perfect pulsating animation
    val infinite = rememberInfiniteTransition()
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        )
    )

    // 🔥 This prevents your issue — guaranteed no white rectangle ever
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBackground),
        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            IntervalSpinner(
                selected = selectedInterval,
                onSelected = { selectedInterval = it }
            )

            // The pulsating interactive circle
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(scale)
                    .background(circleColor, CircleShape)
                    .clickable {
                        if (isRunning) {
                            isRunning = false
                            context.stopService(Intent(context, LocationService::class.java))
                            Toast.makeText(context, "Service Stopped", Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!isRunning) {
                    Button(
                        onClick = {
                            isRunning = true
                            val intent = Intent(context, LocationService::class.java)
                            intent.putExtra("intervalMs", intervalToMs(selectedInterval))
                            ContextCompat.startForegroundService(context, intent)
                            Toast.makeText(context, "Service Started", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(60.dp),
                        shape = CircleShape
                    ) {
                        Text("Start", fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    Text(
                        "Running",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}


// ------------------------------
// Spinner Component
// ------------------------------
@Composable
fun IntervalSpinner(selected: String, onSelected: (String) -> Unit) {

    var expanded by remember { mutableStateOf(false) }
    val options = listOf("30 sec", "5 min", "10 min", "15 min", "30 min", "60 min")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        Button(onClick = { expanded = true }) {
            Text(selected, fontSize = 12.sp)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach {
                DropdownMenuItem(
                    onClick = {
                        onSelected(it)
                        expanded = false
                    }
                ) {
                    Text(it, fontSize = 12.sp)
                }
            }
        }
    }
}
