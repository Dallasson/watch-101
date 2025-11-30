package com.app.pulsewear.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.provider.Settings
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

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val intervalMs = intent?.getLongExtra("intervalMs", 30_000L) ?: 30_000L

        startForeground(
            1,
            buildNotification("Tracking every ${intervalMs / 1000} sec")
        )

        scope.launch {
            while (isActive) {
                val loc = getLocationOnce()

                if (loc != null) {
                    val deviceId = Settings.Secure.getString(
                        contentResolver, Settings.Secure.ANDROID_ID
                    )

                    val ref = FirebaseDatabase.getInstance().reference
                        .child("pulse_data")
                        .child(deviceId)

                    val map = mapOf(
                        "timestamp" to System.currentTimeMillis(),
                        "latitude" to loc.latitude,
                        "longitude" to loc.longitude
                    )

                    ref.push().setValue(map)
                }

                delay(intervalMs)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    private suspend fun getLocationOnce(): Location? =
        suspendCoroutine { cont ->

            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                cont.resume(null)
                return@suspendCoroutine
            }

            fused.lastLocation.addOnSuccessListener { last ->
                if (last != null) {
                    cont.resume(last)
                } else {
                    val req = LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY, 0
                    ).setMaxUpdates(1).build()

                    fused.requestLocationUpdates(
                        req,
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

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "pulse_channel")
            .setContentTitle("PulseWear Running")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
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
