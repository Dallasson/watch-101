package com.app.pulsewear.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.app.pulsewear.presentation.theme.PulseWearTheme

class MainActivity : ComponentActivity() {

    // Permissions required by the app
    private val neededPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE
    )

    // Launcher to request permissions
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val granted = perms.values.all { it }
            if (!granted) {
                Toast.makeText(
                    this,
                    "Permissions are required to run the app",
                    Toast.LENGTH_LONG
                ).show()
                finish() // close app if not granted
            } else {
                // Permissions granted, show app UI
                setContent { PulseWearApp() }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
    }

    // Check if all permissions are granted, else request them
    private fun checkAndRequestPermissions() {
        val allGranted = neededPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            // Permissions already granted
            setContent { PulseWearApp() }
        } else {
            // Ask for permissions
            permissionLauncher.launch(neededPermissions)
        }
    }
}

@Composable
fun PulseWearApp() {
    PulseWearTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            PulsatingCircle()
        }
    }
}
