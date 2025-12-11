package com.app.pulsewear.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LocationService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fused: FusedLocationProviderClient

    // Variable to store the tracking period (e.g., "5 min") for the Firebase node
    private var trackingPeriod: String = "Unknown_Session"

    // Define the internal BroadcastReceiver to listen for the "UPLOAD_LOCATION_NOW" signal
    private val locationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "UPLOAD_LOCATION_NOW") {
                // Trigger the location update and upload on every broadcast from the UI
                uploadLocationToFirebase()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        // Register the Receiver with the necessary security flag (API 33+ fix)
        val filter = IntentFilter("UPLOAD_LOCATION_NOW")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Use Context.RECEIVER_NOT_EXPORTED for internal-only broadcasts
            registerReceiver(locationUpdateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(locationUpdateReceiver, filter)
        }
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // Extract the tracking period from the intent passed by PulsatingCircle
        trackingPeriod = intent?.getStringExtra("TRACKING_PERIOD") ?: "Unknown_Session"

        // Start foreground service with the updated notification text
        startForeground(
            1,
            buildNotification("Tracking Location for: $trackingPeriod")
        )

        // The service runs passively, waiting for the UI to send the broadcast every second.

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()

        // Unregister the Receiver when the service is destroyed
        unregisterReceiver(locationUpdateReceiver)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null


    // ---------------------------------------------------
    // Core Logic: Location Acquisition and Firebase Upload
    // ---------------------------------------------------
    private fun uploadLocationToFirebase() = scope.launch {

        val loc = getLocationOnce()

        if (loc != null) {

            val deviceId = Settings.Secure.getString(
                contentResolver, Settings.Secure.ANDROID_ID
            )

            // Sanitize the period string for use as a Firebase key (e.g., "5 min" -> "5_min")
            val periodNode = trackingPeriod.replace(" ", "_").replace(".", "")

            // Construct the Firebase path: pulse_data/deviceId/Tracking_Period_Node/timestamped_data
            val ref = FirebaseDatabase.getInstance().reference
                .child("pulse_data")
                .child(deviceId)
                .child(periodNode) // Use the user-selected period as a node

            val map = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "latitude" to loc.latitude,
                "longitude" to loc.longitude,
                "networkType" to getNetworkType(),
                "speed" to loc.speed,
                "accuracy" to loc.accuracy
            )

            // Push data to Firebase
            ref.push().setValue(map)
        }
    }


    // ---------------------------------------------------
    // Get Single Accurate Location
    // ---------------------------------------------------
    @SuppressLint("MissingPermission")
    private suspend fun getLocationOnce(): Location? = suspendCoroutine { cont ->
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            cont.resume(null)
            return@suspendCoroutine
        }

        fused.lastLocation.addOnSuccessListener { last ->
            if (last != null) {
                cont.resume(last)
            } else {
                val request = LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, 0
                )
                    .setMaxUpdates(1)
                    .build()

                fused.requestLocationUpdates(
                    request,
                    object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            fused.removeLocationUpdates(this)
                            cont.resume(result.lastLocation)
                        }
                    },
                    mainLooper
                )
            }
        }
    }


    // ---------------------------------------------------
    // Safe Network Type Detection
    // ---------------------------------------------------
    @SuppressLint("MissingPermission")
    private fun getNetworkType(): String {

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(network) ?: return "none"

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "wifi"
        }

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val type = if (Build.VERSION.SDK_INT >= 30) {
                tm.dataNetworkType
            } else {
                tm.networkType
            }

            return when (type) {
                TelephonyManager.NETWORK_TYPE_NR -> "5g"
                TelephonyManager.NETWORK_TYPE_LTE -> "lte"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3g"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS -> "2g"
                else -> "unknown"
            }
        }

        return "none"
    }


    // ---------------------------------------------------
    // Notification UI
    // ---------------------------------------------------
    private fun buildNotification(text: String): Notification {

        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java), // Assuming MainActivity is your main activity
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "pulse_channel")
            .setContentTitle("PulseWear Tracking")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                "pulse_channel",
                "PulseWear Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(ch)
        }
    }
}