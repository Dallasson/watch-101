package com.app.pulsewear.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresPermission
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.app.pulsewear.presentation.theme.PulseWearTheme
import com.google.firebase.database.FirebaseDatabase
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine

class MainActivity : ComponentActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        // Ask for permissions if not granted
        val permissions = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions, PERMISSION_REQUEST_CODE)
        }

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
    val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    // State for circle color based on network
    var circleColor by remember { mutableStateOf(Color.Red) }

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )



            LaunchedEffect(isRunning) {
                if (isRunning) {
                    val totalDuration = 30 * 1000L // 30 seconds
                    val step = 1000L // every second
                    val steps = totalDuration / step
                    var count = 0L

                    // Use Android ID as unique device key
                    val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    val database = FirebaseDatabase.getInstance().reference
                        .child("pulse_data")
                        .child(deviceId) // single node per device

                    while (isActive && count < steps) {
                        delay(step)
                        count++
                        progress = count.toFloat() / steps

                        // Get network type
                        val networkTypeName = getNetworkType(context)

                        // Update circle color
                        circleColor = when (networkTypeName) {
                            "5G" -> Color.Green
                            "4G" -> Color.Yellow
                            "3G" -> Color(0xFFFFA500) // Orange
                            "2G" -> Color.Red
                            else -> Color.Gray
                        }

                        // Get location
                        val location = getLastLocation(context, fusedLocationClient)
                        val latitude = location?.latitude ?: 0.0
                        val longitude = location?.longitude ?: 0.0

                        Toast.makeText(
                            context,
                            "Current Network: $networkTypeName\nUser Location: $latitude, $longitude",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Upload to Firebase — update the same node each time
                        val data = mapOf(
                            "timestamp" to System.currentTimeMillis(),
                            "networkType" to networkTypeName,
                            "latitude" to latitude,
                            "longitude" to longitude
                        )
                        database.setValue(data) // overwrite the same node
                    }

                    isRunning = false
                }
            }


    Box(
        modifier = Modifier
            .size(100.dp)
            .scale(scale)
            .background(circleColor, shape = CircleShape)
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

@SuppressLint("MissingPermission")
fun getNetworkType(context: Context): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val network = cm.activeNetwork ?: return "Unknown"
    val capabilities = cm.getNetworkCapabilities(network) ?: return "Unknown"

    if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val networkType = try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                telephonyManager.networkType
            } else TelephonyManager.NETWORK_TYPE_UNKNOWN
        } catch (e: SecurityException) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }

        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            else -> "Unknown"
        }
    } else if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
        return "WiFi"
    }
    return "Unknown"
}

@SuppressLint("MissingPermission")
suspend fun getLastLocation(context: Context, fusedLocationClient: FusedLocationProviderClient): Location? {
    return if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        fusedLocationClient.lastLocation.awaitOrNull()
    } else null
}

/** Extension to convert Task to suspend function */
suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
    addOnCompleteListener {
        if (it.isSuccessful) cont.resume(it.result, null)
        else cont.resume(null, null)
    }
}

@androidx.compose.ui.tooling.preview.Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun PreviewPulse() {
    PulseWearApp()
}
