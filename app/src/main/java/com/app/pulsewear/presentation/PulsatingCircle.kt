package com.app.pulsewear.presentation

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun PulsatingCircle() {
    val isDark = isSystemInDarkTheme()
    val screenBackground = if (isDark) Color.Black else Color(0xFF101010)

    var selectedInterval by remember { mutableStateOf("30 sec") }
    var isRunning by remember { mutableStateOf(false) }
    var circleColor by remember { mutableStateOf(Color.Red) }
    var showPicker by remember { mutableStateOf(false) }

    // elapsed seconds counter (UI)
    var elapsedSeconds by remember { mutableStateOf(0L) }

    val context = LocalContext.current

    fun intervalToMs(value: String): Long =
        when (value) {
            "30 sec" -> 30_000L
            "5 min" -> 5 * 60_000L
            "10 min" -> 10 * 60_000L
            "15 min" -> 15 * 60_000L
            "30 min" -> 30 * 60_000L
            "60 min" -> 60 * 60_000L
            else -> 30_000L
        }

    // Pulsating animation
    val infinite = rememberInfiniteTransition()
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        )
    )

    // Timer effect: counts seconds while isRunning and stops everything when reaches target
    LaunchedEffect(isRunning, selectedInterval) {
        if (!isRunning) {
            elapsedSeconds = 0L
            return@LaunchedEffect
        }

        // compute target seconds (in case selectedInterval changes while running)
        val targetMs = intervalToMs(selectedInterval)
        val targetSeconds = max(1L, targetMs / 1000L) // avoid zero

        elapsedSeconds = 0L
        // Count from 0 up to targetSeconds
        while (isRunning && elapsedSeconds < targetSeconds) {
            delay(1000L)
            elapsedSeconds += 1L
        }

        if (isRunning && elapsedSeconds >= targetSeconds) {
            // reached target -> stop service and reset UI
            context.stopService(Intent(context, LocationService::class.java))
            isRunning = false
            Toast.makeText(context, "Finished ($selectedInterval)", Toast.LENGTH_SHORT).show()
            elapsedSeconds = 0L
        }
    }

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

            IntervalButton(
                selected = selectedInterval,
                onClick = { showPicker = true }
            )

            // Pulsating interactive circle
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(scale)
                    .background(circleColor, CircleShape)
                    .clickable {
                        // tapping while running stops everything early
                        if (isRunning) {
                            context.stopService(Intent(context, LocationService::class.java))
                            isRunning = false
                            elapsedSeconds = 0L
                            Toast.makeText(context, "Service Stopped", Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!isRunning) {
                    // Start button when not running
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
                    // Show live timer: "12 sec" (Option 2)
                    Text(
                        text = "${elapsedSeconds} sec",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // WearOS picker dialog
        if (showPicker) {
            IntervalPickerDialog(
                selected = selectedInterval,
                onSelected = {
                    selectedInterval = it
                    showPicker = false
                },
                onDismiss = { showPicker = false }
            )
        }
    }
}

@Composable
fun IntervalButton(selected: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(width = 120.dp, height = 40.dp)
    ) {
        Text(selected, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

// --- WearOS Interval Picker Dialog (Dialog + ScalingLazyColumn) ---
@Composable
fun IntervalPickerDialog(
    selected: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf("30 sec", "5 min", "10 min", "15 min", "30 min", "60 min")

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black, shape = CircleShape)
                .padding(12.dp)
        ) {
            ScalingLazyColumn(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Text(
                        "Select Interval",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(6.dp),
                        color = Color.White
                    )
                }

                items(options.size) { index ->
                    Chip(
                        modifier = Modifier
                            .padding(6.dp)
                            .fillMaxWidth(),
                        label = { Text(options[index], fontSize = 14.sp) },
                        onClick = {
                            onSelected(options[index])
                        }
                    )
                }
            }
        }
    }
}
