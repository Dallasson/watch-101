package com.app.pulsewear.presentation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.app.pulsewear.presentation.theme.PulseWearTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent {
            PulseWearApp()
        }
    }
}

@Composable
fun PulseWearApp() {
    PulseWearTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            PulsatingCircle()
        }
    }
}

@Composable
fun PulsatingCircle() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    val context = LocalContext.current

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Run progress and network logging when active
    LaunchedEffect(isRunning) {
        if (isRunning) {
            val totalDuration = 5 * 60 * 1000L // 5 minutes
            val step = 100L // every 100ms
            val steps = totalDuration / step
            var count = 0L

            while (isActive && count < steps) {
                delay(step)
                count++
                progress = count.toFloat() / steps
                logNetworkType(context)
            }
            // Stop automatically after completion
            isRunning = false
        }
    }

    Box(
        modifier = Modifier
            .size(100.dp)
            .scale(scale)
            .background(Color.Red, shape = CircleShape)
            .clickable {
                if (isRunning) {
                    isRunning = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (!isRunning) {
            Button(
                onClick = { isRunning = true },
                modifier = Modifier.size(60.dp),
                shape = CircleShape
            ) {
                Text("Start", fontSize = 12.sp)
            }
        } else {
            Text(
                text = "${(progress * 100).toInt()}%",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontSize = 18.sp,
                modifier = Modifier.clickable { isRunning = false }
            )
        }
    }
}

/** Logs network type to console **/
fun logNetworkType(context: Context) {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return
    val capabilities = cm.getNetworkCapabilities(network) ?: return
    val type = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Unknown"
    }
    println("📶 Network Type: $type")
}

@androidx.compose.ui.tooling.preview.Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun PreviewPulse() {
    PulseWearApp()
}
